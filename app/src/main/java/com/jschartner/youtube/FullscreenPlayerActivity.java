package com.jschartner.youtube;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.exoplayer2.Player;

import org.json.JSONObject;

//cd E:\Documents\tu\android\ExoPlayer3 && gradlew installDebug && adb shell monkey -p com.example.exoplayer 1
public class FullscreenPlayerActivity extends AppCompatActivity {

    private JexoPlayer jexoPlayer;
    private JexoPlayerView jexoPlayerView;
    private static JSONObject videoInfo;
    private WindowInsetsControllerCompat windowInsetsController;

    @Override
    public void onResume() {
        super.onResume();
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());

        jexoPlayer = Utils.getJexoPlayer(this);
        jexoPlayerView.setPlayer(jexoPlayer);

        Intent intent = getIntent();
        if (intent != null) {
            String id = intent.getStringExtra("id");
            if (id != null) {
                //videoInfo = Youtube.getInfo(id);
            }
        }
        if (videoInfo != null) {
            jexoPlayerView.setInfo(videoInfo);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        jexoPlayerView.setPlayer((Player) null);
    }

    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        setContentView(R.layout.activity_fullsscreen_player);

        windowInsetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        // Configure the behavior of the hidden system bars.
        windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        // Add a listener to update the behavior of the toggle fullscreen button when
        // the system bars are hidden or revealed.
        getWindow().getDecorView().setOnApplyWindowInsetsListener((view, windowInsets) -> {
            // You can hide the caption bar even when the other system bars are visible.
            // To account for this, explicitly check the visibility of navigationBars()
            // and statusBars() rather than checking the visibility of systemBars().
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (windowInsets.isVisible(WindowInsetsCompat.Type.navigationBars())
                        || windowInsets.isVisible(WindowInsetsCompat.Type.statusBars())) {
                } else {
                }
            }
            return view.onApplyWindowInsets(windowInsets);
        });

        FullscreenPlayerActivity playerActivity = this;

        jexoPlayerView = findViewById(R.id.player_view);
        jexoPlayerView.setOnTouchListener(new OnSwipeTouchListener(this) {
            @Override
            public void onDoubleRightClick() {
                if (jexoPlayer != null) jexoPlayer.seekTo(15000);
            }

            @Override
            public void onDoubleLeftClick() {
                if (jexoPlayer != null) jexoPlayer.seekTo(-15000);
            }
        });

        jexoPlayerView.setOnBackPressedListener(new JexoPlayerView.OnBackPressedListener() {
            @Override
            public void onBackPressed() {
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars());
                playerActivity.onBackPressed();
            }
        });
    }
}
