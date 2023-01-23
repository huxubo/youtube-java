package com.jschartner.youtube;

import static js.Io.concat;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.Player;

import org.json.JSONArray;
import org.json.JSONObject;

public class PlayerActivity extends AppCompatActivity {

    private JexoPlayer jexoPlayer;
    private static JSONObject videoInfo;
    private JexoPlayerView jexoPlayerView;

    private TextView titleView;

    private static ListView listView;
    private static ResultAdapter resultAdapter;

    static boolean active = false;

    @Override
    public void onStart() {
        super.onStart();
        active = true;
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        jexoPlayer = Utils.getJexoPlayer(this);
        jexoPlayerView.setPlayer(jexoPlayer);

        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        String id = intent.getStringExtra("id");
        load(id);

        if (videoInfo != null) {
            init();
        }
    }

    private void init() {
        titleView.setText(videoInfo.optString("title"));
    }

    public static void flush() {
        videoInfo = null;
    }

    public static void load(String id) {
        if (id == null) {
            return;
        }
        boolean videoInfoWasNull = videoInfo == null;

        videoInfo = Youtube.getInfo(id);

        if (videoInfo == null) {
            return;
        }

        if (resultAdapter != null && videoInfoWasNull) {
            resultAdapter.refresh(Youtube.getRecommendedVideos(id));
        }
        if (listView != null) {
            listView.setSelectionFromTop(0, 0);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        jexoPlayerView.setPlayer((Player) null);
    }

    private static class ResultAdapter extends ArrayAdapter<JSONObject> {
        private Bitmap[] videoBitmaps;
        private MainActivity2.DownloadImageTask[] videoTasks;
        private Bitmap[] channelBitmaps;
        private MainActivity2.DownloadImageTask[] channelTasks;

        public ResultAdapter(@NonNull Context context, int resource) {
            super(context, resource);
            videoBitmaps = new Bitmap[0];
            videoTasks = new MainActivity2.DownloadImageTask[0];
            channelBitmaps = new Bitmap[0];
            channelTasks = new MainActivity2.DownloadImageTask[0];
        }

        interface OnItemClickedListener {
            void onClick(View v, int position);
        }

        private ResultAdapter.OnItemClickedListener onContentClicked;

        public void setOnContentClicked(ResultAdapter.OnItemClickedListener onContentClicked) {
            this.onContentClicked = onContentClicked;
        }

        public void refresh(final JSONArray result) {
            if (result == null) return;
            clear();

            videoBitmaps = new Bitmap[result.length()];
            videoTasks = new MainActivity2.DownloadImageTask[result.length()];

            channelBitmaps = new Bitmap[result.length()];
            channelTasks = new MainActivity2.DownloadImageTask[result.length()];
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
                    channelUrl = json.getJSONObject("channelThumbnail")
                            .getJSONArray("thumbnails")
                            .getJSONObject(0)
                            .getString("url");
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (url != null) {
                    videoTasks[i] = new MainActivity2.DownloadImageTask(videoBitmaps, i, false);
                    videoTasks[i].execute(url);
                }

                if (channelUrl != null) {
                    channelTasks[i] = new MainActivity2.DownloadImageTask(channelBitmaps, i, true);
                    channelTasks[i].execute(channelUrl);
                }
            }

            notifyDataSetChanged();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View rowView = LayoutInflater.from(getContext()).inflate(R.layout.list_item, parent, false);
            TextView textView = rowView.findViewById(R.id.textView);
            ImageView imageView = rowView.findViewById(R.id.imageView);
            ImageView iconView = rowView.findViewById(R.id.iconView);
            TextView authorView = rowView.findViewById(R.id.authorView);
            TextView durationView = rowView.findViewById(R.id.duration);
            TextView publishedView = rowView.findViewById(R.id.publishedView);

            JSONObject json = getItem(position);

            if (onContentClicked != null) {
                imageView.setOnClickListener((v) -> {
                    onContentClicked.onClick(v, position);
                });
            }

            try {
                String finalText = json.getJSONObject("title")
                        .getString("simpleText");
                textView.setText(finalText);
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                String viewText = null;
                if (json.has("viewCountText")) {
                    viewText = json.getJSONObject("viewCountText").optString("simpleText");
                } else if (json.has("shortViewCountText")) {
                    JSONObject shortViewCountText = json.getJSONObject("shortViewCountText");
                    viewText = shortViewCountText.optString("simpleText");
                }

                String channelText = json.getJSONObject("longBylineText")
                        .getJSONArray("runs")
                        .getJSONObject(0)
                        .getString("text");

                if (viewText != null && viewText.length() > 0) {
                    authorView.setText(concat(channelText, " - ", viewText));
                } else {
                    authorView.setText(channelText);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                String publishedString = json.optJSONObject("publishedTimeText")
                        .optString("simpleText");

                publishedView.setText(publishedString);
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                String durationText = "LIVE";
                if (json.has("lengthText")) {
                    durationText = json.getJSONObject("lengthText")
                            .getString("simpleText");
                } else {
                    durationView.setBackgroundColor(Color.RED);
                }
                durationView.setText(durationText);
                durationView.setVisibility(View.VISIBLE);
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
    }

    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        setContentView(R.layout.activity_player);

        PlayerActivity playerActivity = this;

        titleView = findViewById(R.id.playerTitleView);

        listView = findViewById(R.id.recommendationsListView);
        if (resultAdapter == null) {
            resultAdapter = new ResultAdapter(this, R.layout.list_item);
        }
        listView.setAdapter(resultAdapter);

        jexoPlayerView = findViewById(R.id.player_view);
        jexoPlayerView.setOnTouchListener(new OnSwipeTouchListener(this) {
            @Override
            public void onSwipeBottom() {
                playerActivity.onBackPressed();
            }

            @Override
            public void onDoubleRightClick() {
                if (jexoPlayer != null) jexoPlayer.seekTo(15000);
            }

            @Override
            public void onDoubleLeftClick() {
                if (jexoPlayer != null) jexoPlayer.seekTo(-15000);
            }
        });

        jexoPlayerView.setOnBackPressedListener(() -> {
            playerActivity.onBackPressed();
        });

        jexoPlayerView.setOnFullscreenPressedListener((v) -> {
            Intent intent = new Intent(getApplicationContext(), FullscreenPlayerActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });

        resultAdapter.setOnContentClicked((v, position) -> {
            final String videoId = resultAdapter.getItem(position).optString("videoId");

            Utils.playerLoop(jexoPlayer, videoId);
            init();
        });

        jexoPlayer = Utils.getJexoPlayer(this);

        //================================================================================================        
    }

    @Override
    public void onDestroy() {

        active = false;
        super.onDestroy();
    }
}


