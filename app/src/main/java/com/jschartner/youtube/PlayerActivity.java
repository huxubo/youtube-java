package com.jschartner.youtube;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

//cd E:\Documents\tu\android\ExoPlayer3 && gradlew installDebug && adb shell monkey -p com.example.exoplayer 1
public class PlayerActivity extends AppCompatActivity {

    private JexoPlayer jexoPlayer;
    private JexoPlayerView jexoPlayerView;

    @Override
    public void onResume() {
        super.onResume();

        jexoPlayer.playMediaSource(Utils.getMediaSource());
    }

    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        setContentView(R.layout.activity_player);

        jexoPlayer = new JexoPlayer(this);
        jexoPlayerView = findViewById(R.id.player_view);
        jexoPlayerView.setPlayer(jexoPlayer);
    }
}
