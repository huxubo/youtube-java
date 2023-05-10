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

import com.jschartner.youtubebase.JexoPlayer;
import com.jschartner.youtubebase.Utils;
import com.jschartner.youtubebase.Youtube;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import js.Req;

/*
 - TODOS:
 * Loading animation, when refreshing or loading Video
 * Finish Playerfragment, with title, description, view, download ...
 * Reset Quality Settings every Video, display Quality Settings, Show remaining Time on Player
 * MAYBE LOGIN ??
 * MAYBE COMMENTS
 * MAYBE INFINITE Scrolling
 * Better networking: check more frequently, maybe ping
 * sick animations

 */

public class MainActivity extends AppCompatActivity {

    protected History history;
    protected JexoPlayer jexoPlayer;
    protected ResultAdapter searchAdapter;
    protected ResultAdapter recommendationAdapter;
    protected RunningDownloadManager downloadManager;

    protected Client client;
    protected List<String> remoteIps;

    protected JSONObject currentVideo;

    private Runnable onCurrentVideoChanged = null;

    protected void setOnCurrentVideoChanged(final Runnable onCurrentVideoChanged) {
        this.onCurrentVideoChanged = onCurrentVideoChanged;
    }

    //BEGIN RADIO BUTTON

    private OnRadioButtonClickedListener onRadioButtonClickedListener;

    protected interface OnRadioButtonClickedListener {
        void onRadioButtonClicked(View view);
    }

    public void setOnRadioButtonClickedListener(final OnRadioButtonClickedListener onRadioButtonClickedListener) {
        this.onRadioButtonClickedListener = onRadioButtonClickedListener;
    }

    public void onRadioButtonClicked(View view) {
        if (onRadioButtonClickedListener != null) {
            onRadioButtonClickedListener.onRadioButtonClicked(view);
        }
    }

    //END RADIO BUTTON

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

    public void toast(final Object... os) {
        Utils.toast(this, concat(os));
    }

    //TODO: publishedTimeText and viewCountText is in initialData[contents][results][results][contents]
    public void playVideo(@NonNull final String id) {

        if(client != null) {
            toast(concat("Playing video on remote ..."));
            new Thread(() -> {
                try{
                    client.send(id);
                }
                catch(IOException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> toast("Could not start the video on the remote"));
                }
            }).start();

            return;
        }

        recommendationAdapter.free();

        Youtube.fetchVideo(id, (bytes) -> {
            final String response = Req.utf8(bytes);

            Youtube.fetchFormatsAndVideoInfoFromResponse(response, (formats, info) -> {
                final String lengthString = info.optString("lengthSeconds");
                if (lengthString == null) {
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
                        return state < 3;
                    }

                    @Override
                    public String next() {
                        switch (state++) {
                            case 0:
                                try {
                                    return Youtube.buildMpd(formats, Integer.parseInt(lengthString));
                                } catch (final JSONException e) {
                                    return null;
                                }
                            case 1:
                                try {
                                    return info.getString("hlsManifestUrl");
                                } catch (final JSONException e) {
                                    return null;
                                }
                            case 2:
                                try {
                                    return info.getString("dashManifestUrl");
                                } catch (final JSONException e) {
                                    return null;
                                }
                            default:
                                return null;
                        }
                    }
                }, Youtube.toMediaItem(id, info));

                final JSONObject initialData = Youtube.getInitialDataFromResponse(response);

                try{
                    JSONObject videoRenderer = initialData.getJSONObject("contents")
                            .getJSONObject("twoColumnWatchNextResults")
                            .getJSONObject("results")
                            .getJSONObject("results")
                            .getJSONArray("contents")
                            .getJSONObject(0)
                            .getJSONObject("videoPrimaryInfoRenderer");

                    String publishedTimeText = null;
                    if(videoRenderer.has("relativeDateText")) {
                        JSONObject relativeDateText = videoRenderer.getJSONObject("relativeDateText");
                        publishedTimeText = relativeDateText.optString("simpleText");
                    }

                    String viewCountText = null;
                    if(videoRenderer.has("viewCount")) {
                        JSONObject viewCount = videoRenderer.optJSONObject("viewCount");
                        if(viewCount.has("videoViewCountRenderer")) {
                            JSONObject videoViewCountRenderer = viewCount.optJSONObject("videoViewCountRenderer");
                            if(videoViewCountRenderer.has("viewCount")) {
                                JSONObject viewCountInner = videoViewCountRenderer.optJSONObject("viewCount");
                                viewCountText = viewCountInner.optString("simpleText");
                            }
                        }
                    }

                    info.put("publishedTimeText", publishedTimeText);
                    info.put("viewCountText", viewCountText);
                }
                catch(final JSONException e) {
                    e.printStackTrace();
                }


                currentVideo = info;
                if(onCurrentVideoChanged != null) {
                    onCurrentVideoChanged.run();
                }

            }, () -> toast("Failed to fetch Formats"));

            final JSONObject initialData = Youtube.getInitialDataFromResponse(response);
            if (initialData == null) {
                toast("Failed to parse initialData");
                return;
            }

            jexoPlayer.setOnFirstFrameRendered(() -> {
                final JSONArray recommendations = Youtube.getRecommendedVideosFromInitialData(initialData);
                if (recommendations == null) {
                    toast("Failed to parse the recommendedVideos");
                    return;
                }
                recommendationAdapter.refresh(recommendations);
            });

            final String nextVideoId = Youtube.getNextVideoIdFromInitialData(initialData);
            if (nextVideoId != null) {
                jexoPlayer.setOnMediaEnded(() -> {
                    playVideo(nextVideoId);
                });
            }

        }, () -> toast("Failed to fetch Youtube"));
    }

    private class Crawler implements Runnable {

        private final Consumer<List<String>> onThen;

        public Crawler(final Consumer<List<String>> onThen) {
            this.onThen = onThen;
        }

        @Override
        public void run() {
            int timeout = 1000;
            List<String> ips = new ArrayList<>();

            final String subnet = "192.168.178";
            //1 .. 255
            for (int i = 49; i <= 49; i++) {
                final String host = concat(subnet, ".", i);
                try {
                    if (InetAddress.getByName(host).isReachable(timeout)) {
                        ips.add(host);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }

            if (onThen != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    onThen.accept(ips);
                }
            }
        }
    }

    private class Client {
        private final String host;
        private final int port;
        private ClientThread clientThread;

        private Socket socket;
        private PrintStream printStream;

        private int TIMEOUT_MS = 5000;

        public Client(final String host, final int port) {
            this.host = host;
            this.port = port;

            this.clientThread = null;
            this.socket = null;
            this.printStream = null;
        }

        public boolean connected() {
            if (socket == null) {
                return false;
            }
            return socket.isConnected();
        }

        public void disconnect() {
            if (socket != null) {
                try {
                    socket.close();
                    socket = null;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (printStream != null) {
                try {
                    printStream.close();
                    printStream = null;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            clientThread = null;
        }

        private void print(final String message) throws IOException {
            printStream.println(message);
            printStream.flush();
        }

        public void send(final String message) throws IOException {
            if (!connected()) {
                clientThread = new ClientThread();
                print(message);
                clientThread.start();
            } else {
                print(message);
            }
        }

        private class ClientThread extends Thread implements Runnable {
            public ClientThread()
                    throws IOException {
                socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
                printStream = new PrintStream(socket.getOutputStream());
            }

            @Override
            public void run() {
                try {
                    Thread.sleep(1000 * 60);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                disconnect();
            }
        }
    }

    private boolean toggleConnectionToRemote() {
        boolean wasConnected = client != null;

        if (wasConnected) {
            if(client.connected()) {
                client.disconnect();
            }
            client = null;
        } else {
            final String ip = remoteIps.get(0);
            client = new Client(ip, 8080);
            toast(concat("Now connected to ", ip));
        }

        return wasConnected;
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

        new Thread(new Crawler((ips) -> {
            remoteIps = ips;
        })).start();
    }

    @Override
    public void onDestroy() {
        if (jexoPlayer.isConnected()) {
            jexoPlayer.disconnect();
        }
        jexoPlayer.stop();
        Youtube.close();
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return true;
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

        MenuItem menuConnect = menu.findItem(R.id.connectButton);
        menuConnect.getIcon().setAlpha(80);
        menuConnect.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                return false;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                return false;
            }
        });

        menuConnect.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                boolean wasConnected = toggleConnectionToRemote();
                menuConnect.getIcon().setAlpha(!wasConnected ? 255 : 80);
                return true;
            }
        });

        return super.onCreateOptionsMenu(menu);
    }


}
