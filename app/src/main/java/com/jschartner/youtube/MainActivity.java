package com.jschartner.youtube;

import static js.Io.concat;

import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.Iterator;

public class MainActivity extends AppCompatActivity {

    protected History history;
    protected JexoPlayer jexoPlayer;
    protected ResultAdapter searchAdapter;
    protected ResultAdapter recommendationAdapter;
    protected RunningDownloadManager downloadManager;

    /*
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
    */

    public void playVideo(@NonNull final String id) {
        Youtube.fetchFormatsAndVideoInfo(id, (formats, info) -> {
            final String lengthString = info.optString("lengthSeconds");
            if(lengthString == null) {
                Utils.toast(this, "Can not find length in videoDetails");
                return;
            }

            jexoPlayer.setOnPlayerError((error) -> {
               playVideo(id);
            });
            jexoPlayer.playFirst(new Iterator<String>() {
                int state = 0;

                @Override
                public boolean hasNext() {
                    return state<3;
                }

                @Override
                public String next() {
                    switch(state++) {
                        case 0:
                            try {
                                return Youtube.buildMpd(formats, Integer.parseInt(lengthString));
                            }
                            catch(final JSONException e) {
                                return null;
                            }
                        case 1:
                            try{
                                return info.getString("hlsManifestUrl");
                            } catch(final JSONException e) {
                                return null;
                            }
                        case 2:
                            try{
                                return info.getString("dashManifestUrl");
                            } catch(final JSONException e) {
                                return null;
                            }
                        default:
                            return null;
                    }
                }
            }, Youtube.toMediaItem(id, info));
        }, () -> {
            Utils.toast(this, "Failed to fetch Formats");
        });
    }

    @Override
    public void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        setContentView(R.layout.activity_main);

        jexoPlayer = new JexoPlayer(this);

        history = new History((keyword, onThen) -> {
            Youtube.fetchSearchResults(keyword, (results) -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    onThen.accept(results);
                }
            }, null);
        });
        searchAdapter = new ResultAdapter(this, R.layout.list_item, false);
        recommendationAdapter = new ResultAdapter(this, R.layout.list_item, true);
        history.search(null, (result) -> {
            searchAdapter.refresh((JSONArray) result);
        });

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

                searchAdapter.free();
                history.search(query, (result) -> {
                    searchAdapter.refresh((JSONArray) result);
                });

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
