package com.jschartner.youtube;

import static android.content.Context.CONNECTIVITY_SERVICE;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.IOException;

import js.Req;

public class Utils {
    private static JexoPlayer jexoPlayer = null;

    public static JexoPlayer getJexoPlayer(Context context) {
        if (jexoPlayer == null) {
            jexoPlayer = new JexoPlayer(context);
        }
        return jexoPlayer;
    }

    public static void playerLoop(final JexoPlayer jexoPlayer, final String id) {
        boolean[] errorHappened = {false};
        jexoPlayer.setOnPlayerError((error) -> {
            if (errorHappened[0]) return;
            errorHappened[0] = true;
            Youtube.resetCache();
            jexoPlayer.playFirst(Youtube.allSources(id));
        });

        jexoPlayer.playFirst(Youtube.allSources(id), Youtube.toMediaItem(id));

        final String newId = Youtube.getNextVideo(id);
        jexoPlayer.setOnMediaEnded(() -> {
            playerLoop(jexoPlayer, newId);
        });

        PlayerActivity.flush();
        PlayerActivity.load(id);
    }

    public static Req.Result onThread(final Req.Builder builder) {
        final Req.Result[] result = {null};

        Thread thread = new Thread(() -> {
            try {
                result[0] = builder.build();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        thread.start();

        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return result[0];
    }

    public static void toast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

    public static long getBandwidth(final Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            NetworkCapabilities nc = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
            return (long) nc.getLinkDownstreamBandwidthKbps();
        }
        return -1;
    }

    public static void createNotificationChannel(final Context context, final String channelId, final String notificationChannelName, final String notificationChannelDescription) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = notificationChannelName;
            String description = notificationChannelDescription;
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(channelId, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    //WORK IN PROGRESS
    public static Object optJSON(final JSONObject json, String... paths) {
        if (json == null) {
            return null;
        }

        Object current = json;

        for (int i = 0; i < paths.length; i++) {
            final String path = paths[0];
            if (!(current instanceof JSONObject)) {
                return null;
            }
            final JSONObject currentJSON = (JSONObject) current;

        }

        return current;
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
