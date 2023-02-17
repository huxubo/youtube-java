package com.jschartner.youtube;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.AsyncTask;
import android.widget.ImageView;

import java.io.InputStream;
import java.lang.ref.WeakReference;

public class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
    private WeakReference<ImageView> imageView;
    private Bitmap[] bitmaps;
    private int position;
    private boolean crop;

    public DownloadImageTask(final Bitmap[] bitmaps, int position, boolean crop) {
        this.bitmaps = bitmaps;
        this.position = position;
        this.imageView = null;
        this.crop = crop;
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

        if (!crop) {
            return mIcon11;
        }

        int width = mIcon11.getWidth();
        int height = mIcon11.getHeight();

        Bitmap output = Bitmap.createBitmap(mIcon11.getWidth(), mIcon11.getHeight(), Bitmap.Config.ARGB_8888);

        float radius = (float) width / 2;
        int cx = width / 2;
        int cy = height / 2;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                float d = (float) Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
                if (d >= radius) {
                    output.setPixel(x, y, Color.argb(0, 0, 0, 0));
                } else {
                    output.setPixel(x, y, mIcon11.getPixel(x, y));
                }
            }
        }

        return output;
    }

    protected void onPostExecute(Bitmap result) {
        bitmaps[position] = result;
        if (imageView != null) imageView.get().setImageBitmap(result);
    }
}