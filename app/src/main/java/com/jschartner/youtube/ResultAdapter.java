package com.jschartner.youtube;

import static js.Io.concat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

public class ResultAdapter extends ArrayAdapter<JSONObject> {
    private Bitmap[] videoBitmaps;
    private DownloadImageTask[] videoTasks;
    private Bitmap[] channelBitmaps;
    private DownloadImageTask[] channelTasks;
    private final boolean recommendations;

    interface OnItemClickedListener {
        void onClick(View v, int position);
    }

    private ResultAdapter.OnItemClickedListener onContentClicked;
    private ResultAdapter.OnItemClickedListener onDownloadClicked;
    private ResultAdapter.OnItemClickedListener onItemLongClickListener;

    public void setOnItemLongClickListener(ResultAdapter.OnItemClickedListener onItemLongClickListener) {
        this.onItemLongClickListener = onItemLongClickListener;
    }

    public void setOnContentClicked(ResultAdapter.OnItemClickedListener onContentClicked) {
        this.onContentClicked = onContentClicked;
    }

    public void setOnDownloadClicked(ResultAdapter.OnItemClickedListener onDownloadClicked) {
        this.onDownloadClicked = onDownloadClicked;
    }

    public ResultAdapter(@NonNull Context context, int resource, boolean recommendations) {
        super(context, resource);
        videoBitmaps = new Bitmap[0];
        videoTasks = new DownloadImageTask[0];
        channelBitmaps = new Bitmap[0];
        channelTasks = new DownloadImageTask[0];
        this.recommendations = recommendations;
    }

    public View getViewSearch(int position, View convertView, ViewGroup parent) {
        View rowView = LayoutInflater.from(getContext()).inflate(R.layout.list_item, parent, false);
        TextView textView = rowView.findViewById(R.id.textView);
        ImageView imageView = rowView.findViewById(R.id.imageView);
        TextView authorView = rowView.findViewById(R.id.authorView);
        ImageView iconView = rowView.findViewById(R.id.iconView);
        TextView durationView = rowView.findViewById(R.id.duration);
        //ImageView downloadButton = rowView.findViewById(R.id.downloadButton);
        TextView publishedView = rowView.findViewById(R.id.publishedView);

        /*
        if (onDownloadClicked != null) {
            downloadButton.setOnClickListener((v) -> {
                onDownloadClicked.onClick(v, position);
            });
        }
         */

        if (onContentClicked != null) {
            imageView.setOnClickListener((v) -> {
                onContentClicked.onClick(v, position);
            });
        }

        if (onItemLongClickListener != null) {
            imageView.setOnLongClickListener((v) -> {
                onItemLongClickListener.onClick(v, position);
                return true;
            });
        }

        JSONObject json = getItem(position);

        try {
            String durationText = "LIVE";
            if (json.has("lengthText")) {
                durationText = json.getJSONObject("lengthText")
                        .getString("simpleText");
                //downloadButton.setVisibility(View.VISIBLE);
            } else {
                durationView.setBackgroundColor(Color.RED);
            }
            durationView.setText(durationText);
            durationView.setVisibility(View.VISIBLE);
        } catch (Exception e) {
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
            String viewText = json.getJSONObject("viewCountText")
                    .optString("simpleText");
            if (viewText == null) {
                JSONObject shortViewCountText = json.getJSONObject("shortViewCountText");
                viewText = shortViewCountText.optJSONObject("accesibility")
                        .optJSONObject("accessibilityData")
                        .optString("label");
            }

            String channelText = json.getJSONObject("ownerText")
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
            String publishedString = null;
            if(json.has("publishedTimeText")) {
                JSONObject publishedTimeText = json.optJSONObject("publishedTimeText");
                publishedString = publishedTimeText.optString("simpleText");
            }
            publishedView.setText(publishedString);
        } catch (Exception e) {
            e.printStackTrace();
        }

        boolean start_thread = true;

        if (videoBitmaps[position] != null) {
            videoTasks[position] = null;
            imageView.setImageBitmap(videoBitmaps[position]);
            start_thread = false;
        }

        if (channelBitmaps[position] != null) {
            channelTasks[position] = null;
            iconView.setImageBitmap(channelBitmaps[position]);
            start_thread = false;
        }

        if(start_thread) {
            load_bitmap(json, position, imageView, iconView);
        }

        return rowView;
    }

    public Bitmap fill(int color) {
        int width = 120;
        int height = 120;
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for(int y=0;y<height;y++) {
            for(int x=0;x<width;x++) {
                result.setPixel(y, x, color);
            }
        }
        return result;
    }

    public Bitmap black() {
        return fill(0xff000000);
    }

    public View getViewRecommendation(int position, View convertView, ViewGroup parent) {
        View rowView = LayoutInflater.from(getContext()).inflate(R.layout.list_item, parent, false);
        TextView textView = rowView.findViewById(R.id.textView);
        ImageView imageView = rowView.findViewById(R.id.imageView);
        ImageView iconView = rowView.findViewById(R.id.iconView);
        TextView authorView = rowView.findViewById(R.id.authorView);
        TextView durationView = rowView.findViewById(R.id.duration);
        TextView publishedView = rowView.findViewById(R.id.publishedView);

        JSONObject json = getItem(position);

        if (onContentClicked != null) {
            rowView.setOnClickListener((v) -> {
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
            String publishedString = null;
            if(json.has("publishedTimeText")) {
                JSONObject publishedTimeText = json.optJSONObject("publishedTimeText");
                publishedString = publishedTimeText.optString("simpleText");
            }
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

        boolean start_thread = true;

        if (videoBitmaps[position] != null) {
            videoTasks[position] = null;
            imageView.setImageBitmap(videoBitmaps[position]);
            start_thread = false;
        }

        if (channelBitmaps[position] != null) {
            channelTasks[position] = null;
            iconView.setImageBitmap(channelBitmaps[position]);
            start_thread = false;
        }

        if(start_thread ) {
            load_bitmap(json, position, imageView, iconView);
        }

        return rowView;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if(recommendations) {
            return getViewRecommendation(position, convertView, parent);
        } else {
            return getViewSearch(position, convertView, parent);
        }
    }

    public void free() {
        clear();
        notifyDataSetChanged();
        for(int i=0;i<channelTasks.length;i++) {
            if(channelTasks[i] != null) {
                channelTasks[i].cancel(true);
            }
        }
        for(int i=0;i<videoTasks.length;i++) {
            if(videoTasks[i] != null) {
                videoTasks[i].cancel(true);
            }
        }
    }

    private void load_bitmap(JSONObject json, int index, ImageView imageView, ImageView iconView) {
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
            if(json.has("channelThumbnailSupportedRenderers")) {
                channelUrl = json.getJSONObject("channelThumbnailSupportedRenderers")
                        .getJSONObject("channelThumbnailWithLinkRenderer")
                        .getJSONObject("thumbnail")
                        .getJSONArray("thumbnails")
                        .getJSONObject(0)
                        .getString("url");
            } else if (json.has("channelThumbnail")) {
                channelUrl = json.getJSONObject("channelThumbnail")
                        .getJSONArray("thumbnails")
                        .getJSONObject(0)
                        .optString("url");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (url != null) {
            DownloadImageTask task = new DownloadImageTask(videoBitmaps, index, false);
            task.setImageView(imageView);
            task.execute(url);
            videoTasks[index] = task;
        }

        if (channelUrl != null) {
            DownloadImageTask task = new DownloadImageTask(channelBitmaps, index, true);
            task.setImageView(iconView);
            task.execute(channelUrl);
            channelTasks[index] = task;
        }

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
        }

        notifyDataSetChanged();
    }
}
