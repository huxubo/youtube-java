package com.jschartner.youtube;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.google.android.exoplayer2.source.MediaSource;

public class Utils {
    private static MediaSource mediaSource;

    public static void setMediaSource(MediaSource _mediaSource) {
        mediaSource = _mediaSource;
    }

    public static MediaSource getMediaSource() {
        return mediaSource;
    }

    public static void toast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public static void makeDraggable(View view) {
        final float[] dX = new float[1];
        final float[] dY = new float[1];
        final int[] lastAction = new int[1];

        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dX[0] = view.getX() - event.getRawX();
                        dY[0] = view.getY() - event.getRawY();
                        lastAction[0] = MotionEvent.ACTION_DOWN;
                        break;

                    case MotionEvent.ACTION_MOVE:
                        view.setY(event.getRawY() + dY[0]);
                        view.setX(event.getRawX() + dX[0]);
                        lastAction[0] = MotionEvent.ACTION_MOVE;
                        break;

                    case MotionEvent.ACTION_UP:
                        //ON CLICK
                        if (lastAction[0] == MotionEvent.ACTION_DOWN) {

                        }
                        //ON STOP DRAG
                        else {
                            float x = event.getRawX() + dX[0];
                            float y = event.getRawY() + dY[0];
                            view.setY(y);
                            view.setX(x);
                        }

                        break;

                    default:
                        return false;
                }
                return true;
            }
        });
    }
}
