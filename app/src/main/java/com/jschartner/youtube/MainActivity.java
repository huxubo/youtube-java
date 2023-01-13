package com.jschartner.youtube;

import static js.Io.concat;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.exoplayer2.Player;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity {


    public static final String CHANNEL_ID = "com.jschartner.youtube.CHANNEL_ID";
    private static final String NOTIFICATION_CHANNEL_NAME = "com.jschartner.youtube.notification_channel.name";
    private static final String NOTIFICATION_CHANNEL_DESCRIPTION = "com.jschartner.youtube.notification_channel.description";

    private static final String downloadFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString();

    private JexoPlayer jexoPlayer;
    private JexoPlayerView jexoplayerView;

    private ListView listView;
    private ResultAdapter resultAdapter;

    private SwipeRefreshLayout swipeLayout;
    private History history;

    private boolean doubleBackToExitIsPressedOnce = false;

    private Vibrator vibrator;
    private RunningDownloadManager downloadManager;

    public static class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
        private WeakReference<ImageView> imageView;
        private Bitmap[] bitmaps;
        private int position;

        public DownloadImageTask(final Bitmap[] bitmaps, int position) {
            this.bitmaps = bitmaps;
            this.position = position;
            this.imageView = null;
        }

        public void setImageView(ImageView imageView) {
            this.imageView = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(String... urls) {
            String url = urls[0];
            Bitmap mIcon11 = null;

            try {
                InputStream in = new java.net.URL(url).openStream();
                mIcon11 = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return mIcon11;
        }

        protected void onPostExecute(Bitmap result) {
            bitmaps[position] = result;
            if (imageView != null) imageView.get().setImageBitmap(result);
        }
    }

    private static class ResultAdapter extends ArrayAdapter<JSONObject> {
        private Bitmap[] videoBitmaps;
        private DownloadImageTask[] videoTasks;
        private Bitmap[] channelBitmaps;
        private DownloadImageTask[] channelTasks;

        interface OnItemClickedListener {
            void onClick(View v, int position);
        }

        private OnItemClickedListener onContentClicked;
        private OnItemClickedListener onDownloadClicked;
        private OnItemClickedListener onItemLongClickListener;

        public void setOnItemLongClickListener(OnItemClickedListener onItemLongClickListener) {
            this.onItemLongClickListener = onItemLongClickListener;
        }

        public void setOnContentClicked(OnItemClickedListener onContentClicked) {
            this.onContentClicked = onContentClicked;
        }

        public void setOnDownloadClicked(OnItemClickedListener onDownloadClicked) {
            this.onDownloadClicked = onDownloadClicked;
        }

        public ResultAdapter(@NonNull Context context, int resource) {
            super(context, resource);
            videoBitmaps = new Bitmap[0];
            videoTasks = new DownloadImageTask[0];
            channelBitmaps = new Bitmap[0];
            channelTasks = new DownloadImageTask[0];
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View rowView = LayoutInflater.from(getContext()).inflate(R.layout.list_item, parent, false);
            TextView textView = rowView.findViewById(R.id.textView);
            ImageView imageView = rowView.findViewById(R.id.imageView);
            TextView authorView = rowView.findViewById(R.id.authorView);
            ImageView iconView = rowView.findViewById(R.id.iconView);
            TextView durationView = rowView.findViewById(R.id.duration);
            ImageView downloadButton = rowView.findViewById(R.id.downloadButton);

            if(onDownloadClicked != null) {
                downloadButton.setOnClickListener((v) -> {
                    onDownloadClicked.onClick(v, position);
                });
            }

            if(onContentClicked != null) {
                imageView.setOnClickListener((v) -> {
                    onContentClicked.onClick(v, position);
                });
            }

            if(onItemLongClickListener != null) {
                imageView.setOnLongClickListener((v) -> {
                    onItemLongClickListener.onClick(v, position);
                    return true;
                });
            }

            JSONObject json = getItem(position);

            try {
                String durationText = "LIVE";
                if(json.has("lengthText")) {
                    durationText = json.getJSONObject("lengthText")
                            .getString("simpleText");
                    downloadButton.setVisibility(View.VISIBLE);
                } else {
                    durationView.setBackgroundColor(Color.RED);
                }
                durationView.setText(durationText);
                durationView.setVisibility(View.VISIBLE);
            } catch(Exception e) {
                e.printStackTrace();
            }

            try {
                String finalText = json.getJSONObject("title")
                        .getJSONArray("runs")
                        .getJSONObject(0).getString("text");
                textView.setText(finalText);
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                String channelText = json.getJSONObject("ownerText")
                        .getJSONArray("runs")
                        .getJSONObject(0)
                        .getString("text");

                authorView.setText(channelText);
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (videoBitmaps[position] != null) {
                videoTasks[position] = null;
                imageView.setImageBitmap(videoBitmaps[position]);
            } else {
                videoTasks[position].setImageView(imageView);
            }

            if (channelBitmaps[position] != null) {
                channelTasks[position] = null;
                iconView.setImageBitmap(channelBitmaps[position]);
            } else {
                channelTasks[position].setImageView(iconView);
            }


            return rowView;
        }

        public void refresh(final JSONArray result) {
            if (result == null) return;
            clear();

            channelBitmaps = new Bitmap[result.length()];
            channelTasks = new DownloadImageTask[result.length()];

            videoBitmaps = new Bitmap[result.length()];
            videoTasks = new DownloadImageTask[result.length()];
            for (int i = 0; i < result.length(); i++) {
                JSONObject json = result.optJSONObject(i);
                if (json == null) continue;
                insert(json, getCount());
                String url = null;
                try {
                    JSONArray thumbnails = json.getJSONObject("thumbnail")
                            .getJSONArray("thumbnails");
                    JSONObject thumbnail = thumbnails.getJSONObject(thumbnails.length() - 1);
                    url = thumbnail.getString("url");
                } catch (Exception e) {
                    e.printStackTrace();
                }

                String channelUrl = null;
                try {
                    channelUrl = json.getJSONObject("channelThumbnailSupportedRenderers")
                            .getJSONObject("channelThumbnailWithLinkRenderer")
                            .getJSONObject("thumbnail")
                            .getJSONArray("thumbnails")
                            .getJSONObject(0)
                            .getString("url");
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (url != null) {
                    videoTasks[i] = new DownloadImageTask(videoBitmaps, i);
                    videoTasks[i].execute(url);
                }

                if (channelUrl != null) {
                    channelTasks[i] = new DownloadImageTask(channelBitmaps, i);
                    channelTasks[i].execute(channelUrl);
                }
            }

            notifyDataSetChanged();
        }
    }

    public void startPlayer() {
        Intent intent = new Intent(getApplicationContext(), PlayerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    public void startPlayer(final String id) {
        PlayerActivity.flush();
        Intent intent = new Intent(getApplicationContext(), PlayerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("id", id);
        startActivity(intent);
    }

    public void startFullscreenPlayer() {
        Intent intent = new Intent(getApplicationContext(), FullscreenPlayerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    public void startFullscreenPlayer(final String id) {
        Intent intent = new Intent(getApplicationContext(), FullscreenPlayerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("id", id);
        startActivity(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        jexoPlayer = Utils.getJexoPlayer(this);
        jexoplayerView.setPlayer(jexoPlayer);
        jexoplayerView.setVisibility(jexoPlayer.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onPause() {
        super.onPause();
        jexoplayerView.setPlayer((Player) null);
    }

    private void vibrate() {
        if (vibrator == null) {
            vibrator = (Vibrator) getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
        }
        long duration = 100;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            //deprecated in API 26
            vibrator.vibrate(duration);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent permissionIntent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(permissionIntent);
            }
        }

        history = new History(Youtube::search);

        downloadManager = new RunningDownloadManager(this, CHANNEL_ID);
        RunningDownload.setOnDownloadCanceledListener((notificationId) -> {
            if(!downloadManager.cancel(notificationId)) {
                Utils.toast(getApplicationContext(), concat("Failed to cancel download with id: ", notificationId));
            }
        });

        swipeLayout = findViewById(R.id.swipeLayout);
        swipeLayout.setOnRefreshListener(() -> {
            resultAdapter.refresh((JSONArray) history.refreshLoop());
            swipeLayout.setRefreshing(false);
        });

        resultAdapter = new ResultAdapter(this, R.layout.list_item);
        listView = findViewById(R.id.listView);
        listView.setAdapter(resultAdapter);

        resultAdapter.setOnDownloadClicked((v, position) -> {
            final String videoId = resultAdapter.getItem(position).optString("videoId");
            downloadVideo(videoId);
         });

        resultAdapter.setOnContentClicked((v, position) -> {
            final String videoId = resultAdapter.getItem(position).optString("videoId");

            boolean[] errorHappened = {false};
            jexoPlayer.setOnPlayerError((error) -> {
                if(errorHappened[0]) return;
                errorHappened[0] = true;
                Youtube.resetCache();
                jexoPlayer.playFirst(Youtube.allSources(videoId));
            });
            jexoPlayer.playFirst(Youtube.allSources(videoId));

            //startFullscreenPlayer(videoId);
            startPlayer(videoId);
        });

        resultAdapter.setOnItemLongClickListener((v, position) -> {
            vibrate();
            final String videoId = resultAdapter.getItem(position).optString("videoId");
            downloadAudio(videoId);
        });

        resultAdapter.refresh((JSONArray) history.searchLoop(null));

        //SEARCH
        jexoplayerView = findViewById(R.id.playerView);
        jexoPlayer = Utils.getJexoPlayer(this);
        jexoplayerView.setPlayer(jexoPlayer);

        jexoplayerView.setOnClickListener((v) -> startFullscreenPlayer());

        jexoplayerView.setOnTouchListener(new OnSwipeTouchListener(this) {
            @Override
            public void onSwipeTop() {
                //add new activity
                startPlayer();
            }

            @Override
            public void onSwipeLeft() {
                jexoPlayer.stop();
                jexoplayerView.setVisibility(jexoPlayer.isEmpty() ? View.GONE : View.VISIBLE);
            }
        });

        jexoplayerView.setUseController(false);

        Utils.createNotificationChannel(this, CHANNEL_ID, NOTIFICATION_CHANNEL_NAME, NOTIFICATION_CHANNEL_DESCRIPTION);
    }

    //pos.length >= 2
    //pos[0] := position of video
    //pos[1] := position of audio
    private boolean lookUpBestFormats(JSONArray formats, int[] pos) {
        if(formats == null) return false;
        if(pos == null) return false;
        if(pos.length < 2) return false;
        pos[0] = -1;
        pos[1] = -1;

        int videoPos = -1;
        int videoWidth = -1;
        int videoBitrate = -1;
        int audioPos = -1;
        int audioBitrate = -1;
        for (int i = 0; i < formats.length(); i++) {
            JSONObject format = formats.optJSONObject(i);
            if (Youtube.isVideoFormat(format) && format.has("width") && format.has("bitrate")) {
                int width = format.optInt("width");
                int bitRate = format.optInt("bitrate");
                if (width < 1920 && ((width > videoWidth) || (width == videoWidth && bitRate > videoBitrate))) {
                    videoWidth = width;
                    videoPos = i;
                    videoBitrate = bitRate;
                }
            }

            if (Youtube.isAudioFormat(format) && format.has("bitrate")) {
                int bitRate = format.optInt("bitrate");
                if (bitRate > audioBitrate) {
                    audioBitrate = bitRate;
                    audioPos = i;
                }
            }
        }

        pos[0] = videoPos;
        pos[1] = audioPos;

        if(audioPos == -1 || videoPos == -1) {
            Utils.toast(this, "Could not find Video and Audio");
        }

        return true;
    }

    private void downloadAudio(final String videoId) {
        final String title = Youtube.getTitle(videoId);
        if(title == null) return;

        JSONArray formats = Youtube.getFormats(videoId);
        int[] pos = new int[2];
        if(!lookUpBestFormats(formats, pos)) return;

        JSONObject format = formats.optJSONObject(pos[1]);
        final String fileTitle = concat(title, ".mp3");
        final String[] fileNames = Youtube.getNames(format, title, downloadFolder);

        downloadManager.download(format.optString("url"), fileTitle, fileNames[1], () -> {
            String result = Ffmpeg.transcodeToMp3(fileNames[1], concat(downloadFolder, "/", fileTitle));
            if("Finished transcoding".equals(result)) {
                new File(fileNames[1]).delete();
            } else {
                runOnUiThread(() -> Utils.toast(this, result));
            }
        }, () -> {
            runOnUiThread(() -> Utils.toast(getApplicationContext(), "Download failed"));
        });
    }

    private void downloadVideo(final String videoId) {
        final String title = Youtube.getTitle(videoId);
        if(title == null) return;

        JSONArray formats = Youtube.getFormats(videoId);
        if(formats == null) return;

        int[] pos = new int[2];
        if(!lookUpBestFormats(formats, pos)) return;

        int[] i = {0};

        JSONObject videoFormat = formats.optJSONObject(pos[0]);
        final String[] videoNames = Youtube.getNames(videoFormat, title, downloadFolder);
        if(videoNames == null) return;

        JSONObject audioFormat = formats.optJSONObject(pos[1]);
        final String[] audioNames = Youtube.getNames(audioFormat, title, downloadFolder);
        if(audioNames == null) return;

        final String outputFilePath = concat(downloadFolder, "/", title, ".mp4");

        Runnable merge = () -> {
            if(i[0]!=1) {
                i[0]++;
                return;
            }
            String result = Ffmpeg.mergeVideoAudio(videoNames[1], audioNames[1], outputFilePath);
            if("All went fine".equals(result)) {
                new File(videoNames[1]).delete();
                new File(audioNames[1]).delete();
            } else {
                runOnUiThread(() -> Utils.toast(this, result));
            }
        };

        Runnable failed = () -> {
            runOnUiThread(() -> Utils.toast(getApplicationContext(), "Download failed"));
        };

        //VIDEO
        downloadManager.download(videoFormat.optString("url"), videoNames[0], videoNames[1], merge, failed);

        //AUDIO
        downloadManager.download(audioFormat.optString("url"), audioNames[0], audioNames[1], merge, failed);
    }

    @Override
    public void onBackPressed() {

        Object result = history.back();
        if (result != null) {
            resultAdapter.refresh((JSONArray) result);
            return;
        }
        if (doubleBackToExitIsPressedOnce) {
            super.onBackPressed();
            jexoPlayer.stop();
            finish();
            return;
        }

        doubleBackToExitIsPressedOnce = true;
        new Handler(Looper.getMainLooper()).postDelayed(() -> doubleBackToExitIsPressedOnce = false, 2000);
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

                swipeLayout.requestFocus();

                resultAdapter.refresh((JSONArray) history.searchLoop(query));
                listView.setSelectionAfterHeaderView();

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
