package com.jschartner.youtube;

import static js.Io.concat;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.core.app.NotificationManagerCompat;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class RunningDownloadManager {
    private static final int BASE_NOTIFICATION_ID = 0;
    private static final int BASE_NOTIFICATION_ID_MAX = Integer.MAX_VALUE;
    private static final DecimalFormat dotTwo = new DecimalFormat("0.00");

    private final Context context;
    private int currentNotificationId;
    private final List<RunningDownload> runningDownloads;
    private Thread notificationThread;

    private final String channelId;

    public RunningDownloadManager(final Context context, final String channelId) {
        this.context = context;
        this.currentNotificationId = BASE_NOTIFICATION_ID;
        this.runningDownloads = new ArrayList<>();
        this.channelId = channelId;
    }

    public boolean cancel(int notificationId) {
        for(int i=0;i<runningDownloads.size();i++) {
            RunningDownload runningDownload = runningDownloads.get(i);
            if(runningDownload.notificationId != notificationId) continue;
            if(runningDownload.download == null) continue;
            runningDownload.download.abort();
            runningDownload.builder.setContentText("Canceled");
            runningDownload.finished = true;
            return true;
        }
        return false;
    }

    public boolean download(final String url,
                            final String fileTitle,
                            final String fileName,
                            final Runnable onFinished,
                            final Runnable onError) {
        if(url == null) return false;
        if(fileTitle == null) return false;
        if(fileName == null) return false;

        if(currentNotificationId == BASE_NOTIFICATION_ID_MAX) {
            Utils.toast(context, "Please restart the app");
            return false;
        }

        runningDownloads.add(new RunningDownload(context, url, fileTitle, fileName, currentNotificationId++,
                Utils.getBandwidth(context), onFinished, onError, channelId));
        startNotificationThread();

        return true;
    }

    @SuppressLint("RestrictedApi")
    private void startNotificationThread() {
        if(notificationThread != null) return;
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationThread = new Thread(() -> {
            while(runningDownloads.size() > 0) {
                List<RunningDownload> toBeRemoved = new ArrayList<>();
                for(int i=0;i<runningDownloads.size();i++) {
                    RunningDownload runningDownload = runningDownloads.get(i);
                    if(runningDownload.finished) {
                        runningDownload.builder.setProgress(0, 0, false);
                        runningDownload.builder.setOngoing(false);
                        runningDownload.builder.mActions.clear();
                        runningDownload.download = null;
                        toBeRemoved.add(runningDownload);
                    } else {
                        //DISPLAY progress
                        int contentLength = runningDownload.download.getContentLength();
                        String contentLengthString = dotTwo.format(contentLength * 0.000001);

                        int progress = runningDownload.download.getProgress();
                        String progressString = contentLength == 0 ? "0" : dotTwo.format(100.0f * ((float) progress)/((float) contentLength));
                        long duration = runningDownload.download.duration();
                        double speed = ((double) progress * 1000 / (double) duration * 8e-6) ;
                        runningDownload.builder.setContentText(concat(progressString, "%", " - ", contentLengthString, " MB - ", dotTwo.format(speed), "MB/s"));
                        runningDownload.builder.setProgress(contentLength, progress, false);
                    }

                    notificationManager.notify(runningDownload.notificationId, runningDownload.builder.build());
                }
                for(int i=0;i<toBeRemoved.size();i++) {
                    runningDownloads.remove(toBeRemoved.get(i));
                }
                try{
                    Thread.sleep(1000);
                } catch(InterruptedException e) {
                    e.printStackTrace();
                }
            }
            notificationThread = null;
        });
        notificationThread.start();
    }
}
