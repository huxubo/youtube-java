package com.jschartner.youtube;

import android.content.Context;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.ui.DefaultTrackNameProvider;
import com.google.android.exoplayer2.ui.TrackNameProvider;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class JexoFormat {

    private String[] labels;
    private List<Integer>[] indices;
    private int selected;
    private TrackGroup trackGroup;
    private String selectedLabel;

    private static TrackNameProvider trackNameProvider;

    private static TrackNameProvider getTrackNameProvider(Context context) {
        if (trackNameProvider == null) {
            trackNameProvider = new DefaultTrackNameProvider(context.getResources());
        }
        return trackNameProvider;
    }

    public String getSelectedLabel() {
        return selected == 0
                ? new StringBuilder(labels[0]).append(" - ").append(selectedLabel).toString()
                : selectedLabel;
    }

    public String[] getLabels() {
        return labels;
    }

    List<Integer> getIndices() {
        return indices[selected];
    }

    public int getSelected() {
        return selected;
    }

    public void setSelected(int selected) {
        this.selected = selected;
    }

    public TrackGroup getTrackGroup() {
        return trackGroup;
    }

    private String map(int width) {
        switch (width) {
            case 256:
                return "140p";
            case 426:
                return "240p";
            case 640:
                return "360p";
            case 854:
                return "480p";
            case 1280:
                return "720p";
            case 1920:
                return "1080p";
            case 2560:
                return "1440p";
            case 3840:
                return "2160p";
            default:
                return String.valueOf(width);
        }
    }

    public JexoFormat(Context context, Tracks.Group tracksGroup, boolean auto) {
        trackGroup = tracksGroup.getMediaTrackGroup();

        Map<Integer, List<Integer>> formats = new HashMap<>();
        List<Integer> allIndices = new ArrayList<>();

        int bestWidth = 0;
        for (int i = 0; i < tracksGroup.length; i++) {
            int width = tracksGroup.getTrackFormat(i).width;
            if (width == Format.NO_VALUE) continue;
            if (!tracksGroup.isTrackSupported(i)) continue;
            if (tracksGroup.isTrackSelected(i) && bestWidth < width) {
                selected = i;
                bestWidth = width;
            }
            List<Integer> indices = formats.get(width);
            if (indices == null) {
                indices = new ArrayList<>();
            }
            indices.add(i);
            formats.put(width, indices);
            allIndices.add(i);
        }

        indices = (List<Integer>[]) Array.newInstance(ArrayList.class, formats.size() + 1);
        indices[0] = allIndices;
        labels = new String[formats.size() + 1];
        labels[0] = "Auto";

        Integer widths[] = new Integer[formats.size()];

        int j = 0;
        for (Integer key : formats.keySet()) {
            widths[j++] = key;
        }

        Arrays.sort(widths, Collections.reverseOrder());

        boolean found = false;
        for (int i = 0; i < widths.length; i++) {
            labels[i + 1] = map(widths[i]);
            indices[i + 1] = formats.get(widths[i]);
            if (!found && indices[i + 1].contains(selected)) {
                selected = i + 1;
                selectedLabel = labels[i + 1];
                found = true;
            }
        }

        if (auto) selected = 0;
    }
}
