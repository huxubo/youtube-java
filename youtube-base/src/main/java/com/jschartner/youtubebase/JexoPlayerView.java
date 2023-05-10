package com.jschartner.youtubebase;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.exoplayer2.ui.StyledPlayerView;

//https://github.com/google/ExoPlayer/blob/release-v2/library/ui/src/main/java/com/google/android/exoplayer2/ui/PlayerView.java
public class JexoPlayerView extends StyledPlayerView {

    private ImageButton backButton;
    private ImageButton fullScreenButton;
    private LinearLayout durationLayout;
    private TextView titleView;
    private TextView authorView;

    public void enableFullscreenMode(final boolean enable) {
        if(durationLayout == null) {
            return;
        }

        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) durationLayout.getLayoutParams();
        params.horizontalBias = enable ? 0.02f : 0.04f;
        params.verticalBias = enable ? 0.90f : 0.86f;
        durationLayout.setLayoutParams(params);
    }

    public void enableBackButton(final boolean enable) {
        if(backButton == null) {
            return;
        }

        backButton.setVisibility(enable ? View.VISIBLE : View.GONE);
    }

    public void enableFullScreenButton(final boolean enable) {
        if(fullScreenButton == null) {
            return;
        }

        fullScreenButton.setVisibility(enable ? View.VISIBLE : View.GONE);
    }

    public void setTitle(final String title) {
        if(titleView == null) {
            return;
        }

        titleView.setText(title);
    }

    public void setAuthor(final String author) {
        if(authorView == null) {
            return;
        }

        authorView.setText(author != null ? author.trim() : author);
    }

    //BEGIN ONFULLSCREENPRESSEDLISTENER

    private View.OnClickListener onFullscreenPressedListener;

    public void setOnFullscreenPressedListener(View.OnClickListener onFullscreenPressedListener) {
        this.onFullscreenPressedListener = onFullscreenPressedListener;
    }
    //END ONFULLSCREENPRESSEDLISTENER

    //BEGIN ONBACKPRESSEDLISTENER

    private View.OnClickListener onBackPressedListener;

    public void setOnBackPressedListener(View.OnClickListener onBackPressedListener) {
        this.onBackPressedListener = onBackPressedListener;
    }

    //BEGIN ONBACKPRESSEDLISTENER

    public void setPlayer(JexoPlayer player) {
        setPlayer(player.getPlayer());
    }

    public JexoPlayerView(Context context) {
        super(context);
    }

    private void init() {

        titleView = findViewById(R.id.titleView);
        authorView = findViewById(R.id.authorView);
        durationLayout = findViewById(R.id.durationLayout);

        //TODO: Add button onPressed Effect
        //https://stackoverflow.com/questions/5327553/android-highlight-an-imagebutton-when-clicked
        fullScreenButton = findViewById(R.id.fullScreenButton);
        fullScreenButton.setColorFilter(Color.WHITE);
        fullScreenButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(onFullscreenPressedListener != null) {
                    onFullscreenPressedListener.onClick(view);
                }
            }
        });

        backButton = findViewById(R.id.backButton);
        backButton.setColorFilter(Color.WHITE);
        backButton.setOnClickListener(new View.OnClickListener() {
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
