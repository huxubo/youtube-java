package com.jschartner.youtube;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

public class RunningDownload extends BroadcastReceiver {
    public final NotificationCompat.Builder builder;
    public final int notificationId;
    public Download download;
    boolean finished;


    private final static String ACTION_CANCEL_DOWNLOAD = "com.jschartner.youtube.RunningDownload.action-cancel-download";
    private static final String ACTION_CANCEL_DOWNLOAD_NOTIFICATION_ID = "com.jschartner.youtube.RunningDownload.notificationId";

    interface OnDownloadCanceledListener {
        void onCanceled(int notificationId);
    }

    private static OnDownloadCanceledListener onDownloadCanceledListener;

    public static void setOnDownloadCanceledListener(OnDownloadCanceledListener _onDownloadCanceledListener) {
        onDownloadCanceledListener = _onDownloadCanceledListener;
    }

    public RunningDownload() {
        builder = null;
        notificationId = -1;
    }

    public RunningDownload(final Context context,
                           final String url, final String fileTitle,
                           final String fileName, int notificationId,
                           final long bandwidth,
                           final Runnable onFinished, final Runnable onError,
                           final String channelId) {

        Intent cancelIntent = new Intent(context, RunningDownload.class);
        cancelIntent.setAction(ACTION_CANCEL_DOWNLOAD);
        cancelIntent.putExtra(ACTION_CANCEL_DOWNLOAD_NOTIFICATION_ID, notificationId);
        PendingIntent cancelPendingIntent = PendingIntent.getBroadcast(context, 0, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

        this.builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(fileTitle)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .addAction(R.drawable.ic_launcher_foreground, "Cancel", cancelPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        this.notificationId = notificationId;
        this.download = new Download(url, fileName, bandwidth);
        this.download.setOnDownloadFinished(() -> {
            builder.setContentText("Converting ... ");
            if(onFinished != null) onFinished.run();
            finished = true;
            builder.setContentText("Finished");
        });
        this.download.setOnError(() -> {
            builder.setProgress(0, 0, true);
            if(onError != null) onError.run();
            finished = true;
            builder.setContentText("Failed");
        });
        this.finished = false;
        new Thread(this.download).start();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if(intent == null) return;
        final String action = intent.getAction();
        if(action == null) return;
        if(!ACTION_CANCEL_DOWNLOAD.equals(action)) return;
        int notificationId = intent.getIntExtra(ACTION_CANCEL_DOWNLOAD_NOTIFICATION_ID, -1);
        if(notificationId == -1) return;
        if(onDownloadCanceledListener == null) return;
        onDownloadCanceledListener.onCanceled(notificationId);
    }
}