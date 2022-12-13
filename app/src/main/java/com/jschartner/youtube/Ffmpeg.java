package com.jschartner.youtube;

public class Ffmpeg {
    public final static native int mergeVideoAudio(final String videoPath, final String audioPath, final String outputPath);
}
