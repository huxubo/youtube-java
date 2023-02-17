package com.jschartner.youtubebase;

public class Ffmpeg {

    static {
        //avutil avformat avcodec swscale swresample
        final String[] libs = {"avutil", "avformat", "avcodec", "swscale", "swresample"};
        for(int i=0;i<libs.length;i++) {
            System.loadLibrary(libs[i]);
        }

        System.loadLibrary("ffmpeg-lib");
    }

    public final static native String mergeVideoAudio(final String videoPath, final String audioPath, final String outputPath);

    public final static native String transcodeToMp3(final String inputPath, final String outputPath);

    public final static native String getVideoCodec(final String filePath);
}
