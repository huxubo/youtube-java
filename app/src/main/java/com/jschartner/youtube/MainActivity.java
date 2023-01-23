package com.jschartner.youtube;

import static js.Io.concat;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.SearchView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;

public class MainActivity extends AppCompatActivity {

    protected History history;
    protected JexoPlayer jexoPlayer;
    protected ResultAdapter searchAdapter;
    protected ResultAdapter recommendationAdapter;
    protected RunningDownloadManager downloadManager;

    public void playVideo(final String id) {
        //PLAY VIDEO
        boolean[] errorHappened = {false};
        jexoPlayer.setOnPlayerError((error) -> {
            if (errorHappened[0]) return;
            errorHappened[0] = true;
            Youtube.resetCache();
            jexoPlayer.playFirst(Youtube.allSources(id));
        });

        final String newId = Youtube.getNextVideo(id);
        jexoPlayer.setOnMediaEnded(() -> {
            playVideo(newId);
        });

        jexoPlayer.playFirst(Youtube.allSources(id), Youtube.toMediaItem(id));

        //RECOMMENDATIONS
        recommendationAdapter.refresh(Youtube.getRecommendedVideos(id));
    }

    @Override
    public void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        setContentView(R.layout.activity_main);

        jexoPlayer = new JexoPlayer(this);


        history = new History(Youtube::search);
        searchAdapter = new ResultAdapter(this, R.layout.list_item, false);
        searchAdapter.refresh((JSONArray) history.searchLoop(null));

        recommendationAdapter = new ResultAdapter(this, R.layout.list_item, true);


        final String CHANNEL_ID = "a";
        final String NOTIFICATION_CHANNEL_NAME = "b";
        final String NOTIFICATION_CHANNEL_DESCRIPTION = "c";

        Utils.createNotificationChannel(this, CHANNEL_ID, NOTIFICATION_CHANNEL_NAME, NOTIFICATION_CHANNEL_DESCRIPTION);
        jexoPlayer.connect(CHANNEL_ID, this, this);

        downloadManager = new RunningDownloadManager(this, CHANNEL_ID);
        RunningDownload.setOnDownloadCanceledListener((notificationId) -> {
            if (!downloadManager.cancel(notificationId)) {
                Utils.toast(getApplicationContext(), concat("Failed to cancel download with id: ", notificationId));
            }
        });
    }

    @Override
    public void onDestroy() {
        if(jexoPlayer.isConnected()) {
            jexoPlayer.disconnect();
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);

        MenuItem menuSearch = menu.findItem(R.id.myButton);
        SearchView searchView = (SearchView) menuSearch.getActionView();

        searchView.setIconified(false);
        searchView.setFocusable(true);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchView.setQuery("", false);
                searchView.clearFocus();
                menuSearch.collapseActionView();

                //swipeLayout.requestFocus();

                searchAdapter.refresh((JSONArray) history.searchLoop(query));
                //listView.setSelectionFromTop(0, 0);

                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        return super.onCreateOptionsMenu(menu);
    }
}
