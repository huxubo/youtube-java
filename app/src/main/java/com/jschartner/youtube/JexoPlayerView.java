package com.jschartner.youtube;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.ui.StyledPlayerView;

//https://github.com/google/ExoPlayer/blob/release-v2/library/ui/src/main/java/com/google/android/exoplayer2/ui/PlayerView.java
public class JexoPlayerView extends StyledPlayerView {



    //BEGIN ONFULLSCREENPRESSEDLISTENER

    private OnClickListener onFullscreenPressedListener;

    public void setOnFullscreenPressedListener(OnClickListener onFullscreenPressedListener) {
        this.onFullscreenPressedListener = onFullscreenPressedListener;
    }
    //END ONFULLSCREENPRESSEDLISTENER

    //BEGIN ONBACKPRESSEDLISTENER

    private OnClickListener onBackPressedListener;

    public void setOnBackPressedListener(OnClickListener onBackPressedListener) {
        this.onBackPressedListener = onBackPressedListener;
    }

    //BEGIN ONBACKPRESSEDLISTENER

    public void setPlayer(JexoPlayer player) {
        //TODO
        setPlayer(player.getPlayer());
    }

    public JexoPlayerView(Context context) {
        super(context);
    }

    private void init() {

        //TODO: Add button onPressed Effect
        //https://stackoverflow.com/questions/5327553/android-highlight-an-imagebutton-when-clicked
        ImageButton fullScreenButton = findViewById(R.id.fullScreenButton);
        fullScreenButton.setColorFilter(Color.WHITE);
        fullScreenButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if(onFullscreenPressedListener != null) {
                    onFullscreenPressedListener.onClick(view);
                }
            }
        });

        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setColorFilter(Color.WHITE);
        backButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if(onBackPressedListener != null) {
                    onBackPressedListener.onClick(view);
                }
            }
        });
    }

    public JexoPlayerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public JexoPlayerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
}
