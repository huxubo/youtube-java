package com.jschartner.youtube;

import static js.Io.concat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class Download implements Runnable {

    private class DownloadThread implements Runnable {
        final String url;
        final String fileName;
        final long startByte;
        final long endByte;
        boolean errorHappened;
        boolean abort;

        public DownloadThread(String url, String fileName, long startByte, long endByte) {
            this.url = url;
            this.fileName = fileName;
            this.startByte = startByte;
            this.endByte = endByte;
        }

        @Override
        public void run() {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Range", concat("bytes=", startByte, "-", endByte));
                conn.connect();

                InputStream inputStream = conn.getInputStream();

                FileOutputStream outputStream = new FileOutputStream(fileName);
                int read;
                byte[] bytes = new byte[BYTE_BUFFER_SIZE];
                while ((read = inputStream.read(bytes)) != -1) {
                    if (abort) break;
                    outputStream.write(bytes, 0, read);
                    addContentLength((int) read);
                }

                outputStream.close();
                inputStream.close();

                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
                errorHappened = true;
            }
        }
    }

    private static final int NUMBER_OF_THREADS_REGULAR = 63;
    private static final int BYTE_BUFFER_SIZE = 8192 << 2;

    private static final RuntimeException runtimeException = new RuntimeException("You are not supposed to cancel the Download, if it is not started yet");

    private Runnable onDownloadFinished;
    private Runnable onError;
    private final String url;
    private final String fileName;
    private final int numberOfThreads;
    private DownloadThread[] downloads;
    private Thread[] threads;
    private boolean abortStart;
    private long currentContentLength;
    private long finalContentLength;
    private final long start;

    synchronized void addContentLength(long addedContentLength) {
        currentContentLength += addedContentLength;
    }

    public void setOnError(final Runnable onError) {
        this.onError = onError;
    }

    public void setOnDownloadFinished(final Runnable onDownloadFinished) {
        this.onDownloadFinished = onDownloadFinished;
    }

    public Download(final String url, final String fileName, final long bandwidth) {
        abortStart = false;
        onDownloadFinished = null;
        onError = null;
        this.url = url;
        this.fileName = fileName;
        this.downloads = null;
        this.threads = null;
        if (bandwidth == -1) numberOfThreads = 8;
        else numberOfThreads = (int) bandwidth * NUMBER_OF_THREADS_REGULAR / 70000 + 1;
        start = System.currentTimeMillis();
    }

    public Download(final String url, final String fileName) {
        this(url, fileName, -1);
    }

    //errorHappened := joinThreads failed
    private boolean joinThreads() throws InterruptedException {
        boolean result = true;
        for (int i = 0; i < numberOfThreads; i++) {
            threads[i].join();
            if (downloads[i].errorHappened) result = false;
        }
        if(abortStart) return false;
        return result;
    }

    private void cleanUp() {
        for (int i = 0; i < numberOfThreads; i++) {
            String partFileName = concat(fileName, ".part", i);
            final File partFile = new File(partFileName);
            if (partFile.exists()) new Thread(partFile::delete).start();
        }
        File file = new File(fileName);
        if(file.exists()) file.delete();
    }

    public long duration() {
        return System.currentTimeMillis() - start;
    }

    public int getContentLength() {
        return (int) finalContentLength;
    }

    public int getProgress() {
        return (int) currentContentLength;
    }

    public boolean abort() {
        if (threads == null || downloads == null) {
            abortStart = true;
            return true;
        }

        try {
            for (int i = 0; i < numberOfThreads; i++) {
                if(downloads[i] != null) downloads[i].abort = true;
            }

            joinThreads();
            cleanUp();
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    @Override
    public void run() {
        try {
            downloads = new DownloadThread[numberOfThreads];
            threads = new Thread[numberOfThreads];

            if(abortStart) return;

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("HEAD");
            conn.connect();

            int responseCode = conn.getResponseCode();

            //BAD RESPONSE CODE
            if (responseCode < 100 || responseCode > 399) {
                if (onError != null) onError.run();
                return;
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                finalContentLength = conn.getContentLengthLong();
            } else {
                finalContentLength = conn.getContentLength();
            }

            conn.disconnect();

            if (finalContentLength <= 0) {
                if (onError != null) onError.run();
                return;
            }
            currentContentLength = 0;

            final long partialSize = finalContentLength / numberOfThreads;

            //START THREADS
            for (int i = 0; i < numberOfThreads; i++) {
                long startByte = i * partialSize;
                long endByte = (i + 1) * partialSize - 1;
                if (i == numberOfThreads - 1) endByte = finalContentLength;
                downloads[i] = new DownloadThread(url, concat(fileName, ".part", i), startByte, endByte);
                threads[i] = new Thread(downloads[i]);
                threads[i].start();
            }

            //JOIN THREADS
            if (!joinThreads()) {
                cleanUp();
                if (onError != null) onError.run();
                return;
            }

            //COLLECT OUTPUT
            FileOutputStream outputStream = new FileOutputStream(fileName);
            for (int i = 0; i < numberOfThreads; i++) {
                String partFileName = concat(fileName, ".part", i);
                FileInputStream inputStream = new FileInputStream(partFileName);
                int read;
                byte[] bytes = new byte[BYTE_BUFFER_SIZE];
                while ((read = inputStream.read(bytes)) != -1) {
                    outputStream.write(bytes, 0, read);
                }
                inputStream.close();
                new Thread(() -> new File(partFileName).delete()).start();
            }
            outputStream.close();

            if (onDownloadFinished != null) onDownloadFinished.run();

            downloads = null;
            threads = null;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
