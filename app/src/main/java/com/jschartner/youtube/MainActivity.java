package com.jschartner.youtube;

import static js.Io.concat;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

import js.Req;

public class MainActivity extends AppCompatActivity {

    protected History history;
    protected JexoPlayer jexoPlayer;
    protected ResultAdapter searchAdapter;
    protected ResultAdapter recommendationAdapter;
    protected RunningDownloadManager downloadManager;

    //BEGIN RADIO BUTTON

    private OnRadioButtonClickedListener onRadioButtonClickedListener;

    protected interface OnRadioButtonClickedListener {
        void onRadioButtonClicked(View view);
    }

    public void setOnRadioButtonClickedListener(final OnRadioButtonClickedListener onRadioButtonClickedListener) {
        this.onRadioButtonClickedListener = onRadioButtonClickedListener;
    }

    public void onRadioButtonClicked(View view) {
        if(onRadioButtonClickedListener != null) {
            onRadioButtonClickedListener.onRadioButtonClicked(view);
        }
    }

    //END RADIO BUTTON

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

    public void vibrate() {
        Vibrator vibrator = (Vibrator) getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
        long duration = 100;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            //deprecated in API 26
            vibrator.vibrate(duration);
        }
    }

    public void toast(final Object ...os) {
        Utils.toast(this, concat(os));
    }

    public void playVideo(@NonNull final String id) {
        recommendationAdapter.free();

        Youtube.fetchVideo(id, (bytes) -> {
            final String response = Req.utf8(bytes);

            Youtube.fetchFormatsAndVideoInfoFromResponse(response, (formats, info) -> {
                final String lengthString = info.optString("lengthSeconds");
                if(lengthString == null) {
                    Utils.toast(this, "Can not find length in videoDetails");
                    return;
                }

                jexoPlayer.setOnPlayerError((error) -> {
                    toast("fetching Youtube again ...");
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
            }, () -> toast("Failed to fetch Formats"));

            final JSONObject initialData = Youtube.getInitialDataFromResponse(response);
            if(initialData == null) {
                toast("Failed to parse initialData");
                return;
            }

            final JSONArray recommendations = Youtube.getRecommendedVideosFromInitialData(initialData);
            if(recommendations == null) {
                toast("Failed to parse the recommendedVideos");
                return;
            }
            recommendationAdapter.refresh(recommendations);

            final String nextVideoId = Youtube.getNextVideoIdFromInitialData(initialData);
            if(nextVideoId !=null ) {
                jexoPlayer.setOnMediaEnded(() -> {
                    playVideo(nextVideoId);
                });
            }

        }, () -> toast("Failed to fetch Youtube"));
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
        Youtube.close();
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
