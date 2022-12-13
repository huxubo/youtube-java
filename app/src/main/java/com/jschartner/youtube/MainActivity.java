package com.jschartner.youtube;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
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

import java.io.InputStream;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_TAKE_GALLERY_VIDEO = 69;

    private JexoPlayer jexoPlayer;
    private JexoPlayerView jexoplayerView;

    private ListView listView;
    private ResultAdapter resultAdapter;

    private SwipeRefreshLayout swipeLayout;
    private History history;

    private boolean doubleBackToExitIsPressedOnce = false;

    private long enqueue;
    private DownloadManager downloadManager;
    private long downloadStartTime;

    Vibrator vibrator;

    private class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
        private ImageView imageView;
        private Bitmap[] bitmaps;
        private int position;

        public DownloadImageTask(final Bitmap[] bitmaps, int position) {
            this.bitmaps = bitmaps;
            this.position = position;
        }

        public void setImageView(ImageView imageView) {
            this.imageView = imageView;
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
            if (imageView != null) imageView.setImageBitmap(result);
        }
    }

    private class ResultAdapter extends ArrayAdapter<JSONObject> {
        private Bitmap[] videoBitmaps;
        private DownloadImageTask[] videoTasks;
        private Bitmap[] channelBitmaps;
        private DownloadImageTask[] channelTasks;

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

            JSONObject json = getItem(position);
            String finalText = json.optJSONObject("title")
                    .optJSONArray("runs")
                    .optJSONObject(0).optString("text");

            String channelText = json.optJSONObject("ownerText")
                    .optJSONArray("runs")
                    .optJSONObject(0)
                    .optString("text");

            authorView.setText(channelText);
            textView.setText(finalText);

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

        public boolean refresh(final JSONArray result) {
            if (result == null) return false;
            clear();

            channelBitmaps = new Bitmap[result.length()];
            channelTasks = new DownloadImageTask[result.length()];

            videoBitmaps = new Bitmap[result.length()];
            videoTasks = new DownloadImageTask[result.length()];
            for (int i = 0; i < result.length(); i++) {
                JSONObject json = result.optJSONObject(i);
                insert(json, getCount());
                JSONArray thumbnails = json.optJSONObject("thumbnail")
                        .optJSONArray("thumbnails");

                JSONObject thumbnail = thumbnails.optJSONObject(thumbnails.length() - 1);
                String url = thumbnail.optString("url");

                String channelUrl = json.optJSONObject("channelThumbnailSupportedRenderers")
                        .optJSONObject("channelThumbnailWithLinkRenderer")
                        .optJSONObject("thumbnail")
                        .optJSONArray("thumbnails")
                        .optJSONObject(0)
                        .optString("url");

                videoTasks[i] = new DownloadImageTask(videoBitmaps, i);
                videoTasks[i].execute(url);

                channelTasks[i] = new DownloadImageTask(channelBitmaps, i);
                channelTasks[i].execute(channelUrl);
            }

            notifyDataSetChanged();
            return true;
        }
    }

    public void startPlayer() {
        Intent intent = new Intent(getApplicationContext(), PlayerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    public void startPlayer(final String id) {
        Intent intent = new Intent(getApplicationContext(), PlayerActivity.class);
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
        if(vibrator == null) {
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

        history = new History(Youtube::search);

        swipeLayout = findViewById(R.id.swipeLayout);
        swipeLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                resultAdapter.refresh((JSONArray) history.refreshLoop());
                swipeLayout.setRefreshing(false);
            }
        });

        resultAdapter = new ResultAdapter(this, R.layout.list_item);
        listView = findViewById(R.id.listView);
        listView.setAdapter(resultAdapter);
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                vibrate();

                final String videoId = resultAdapter.getItem(position).optString("videoId");
                JSONObject info = Youtube.getInfo(videoId);
                final String title = info.optString("title");

                JSONArray formats = Youtube.getFormats(videoId);
                int minPos = -1;
                int minBitRate = Integer.MAX_VALUE;
                for(int i=formats.length()-1;i>=0;i--) {
                    JSONObject format = formats.optJSONObject(i);
                    if(Youtube.isAudioFormat(format)) {
                        if(!format.has("averageBitrate")) continue;
                        String _mime = format.optString("mimeType");
                        if(_mime.contains("webm")) continue;
                        int averageBitrate = format.optInt("averageBitrate");
                        if(averageBitrate < minBitRate) {
                            minPos = i;
                            minBitRate = averageBitrate;
                        }
                    }
                }

                if(minPos == -1) {
                    Utils.toast(getApplicationContext(), "No audio found");

                    return true;
                }

                JSONObject format = formats.optJSONObject(minPos);
                String formatCode = String.valueOf(format.opt("itag"));
                String _mime = format.optString("mimeType");
                String[] parts = _mime.split(";");
                String[] mimeParts = parts[0].split("/");
                String mime = mimeParts[mimeParts.length - 1];

                final String name = new StringBuilder(title)
                        .append("_")
                        .append(formatCode)
                        .append(".")
                        .append(("webm".equals(mime)) ? "webm" : "m4a")
                        .toString();

                Utils.toast(getApplicationContext(), name);
                downloadFile(name, format.optString("url"));
                return true;
            }
        });
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final String videoId = resultAdapter.getItem(position).optString("videoId");
                jexoPlayer.playFirst(Youtube.allSources(videoId));
                startPlayer(videoId);
            }
        });

        resultAdapter.refresh((JSONArray) history.searchLoop(null));

        //SEARCH
        jexoplayerView = findViewById(R.id.playerView);
        jexoPlayer = Utils.getJexoPlayer(this);
        jexoplayerView.setPlayer(jexoPlayer);

        jexoplayerView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startPlayer();
            }
        });

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

        registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(enqueue);
                Cursor c = downloadManager.query(query);
                if(c.moveToFirst()) {
                    int stateIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    int sizeIndex = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                    if(DownloadManager.STATUS_SUCCESSFUL == c.getInt(stateIndex)) {
                        long duration = System.currentTimeMillis() - downloadStartTime;
                        long sizeInBytes = c.getInt(sizeIndex);
                        Utils.toast(getApplicationContext(), "Download is finished ("+((float) sizeInBytes/1000000)+" mb). It took "+(duration)+" ms");
                    }
                }
            }
        }
    };

    private void downloadFile(final String fileName, final String url) {
        downloadManager = (DownloadManager) getApplicationContext().getSystemService(DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE)
                .setAllowedOverRoaming(false)
                .setTitle(fileName);
        downloadStartTime = System.currentTimeMillis();
        enqueue = downloadManager.enqueue(request);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
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
        new Handler(Looper.getMainLooper())
                .postDelayed(() -> {
                    doubleBackToExitIsPressedOnce = false;
                }, 2000);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);

        MenuItem menuSearch = menu.findItem(R.id.myButton);
        SearchView searchView = (SearchView) menuSearch.getActionView();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

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
