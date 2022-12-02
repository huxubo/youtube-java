package com.jschartner.youtube;

import android.os.Bundle;

import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.exoplayer2.Player;
import org.json.JSONObject;

//cd E:\Documents\tu\android\ExoPlayer3 && gradlew installDebug && adb shell monkey -p com.example.exoplayer 1
public class PlayerActivity extends AppCompatActivity {

    private JexoPlayer jexoPlayer;
    private JexoPlayerView jexoPlayerView;
    private static JSONObject videoInfo;

    @Override
    public void onResume() {
	super.onResume();
	jexoPlayer = Utils.getJexoPlayer(this);
	jexoPlayerView.setPlayer(jexoPlayer);
	Intent intent = getIntent();
	if(intent != null) {
	    String id = intent.getStringExtra("id");
	    if(id != null) {
		videoInfo = Youtube.getInfo(id);
	    }
	}
        if(videoInfo != null) {
	    jexoPlayerView.setInfo(videoInfo);
	}
	hideSystemBars();
    }

    @Override
    public void onPause() {
	super.onPause();
	jexoPlayerView.setPlayer((Player) null);
    }

    private void hideSystemBars() {
	WindowInsetsControllerCompat windowInsetsController =
	    ViewCompat.getWindowInsetsController(getWindow().getDecorView());
	if (windowInsetsController == null) {
	    return;
	}
	// Configure the behavior of the hidden system bars
	windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
	// Hide both the status bar and the navigation bar
	windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
    }


    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        setContentView(R.layout.activity_player);

	PlayerActivity playerActivity = this;

        jexoPlayerView = findViewById(R.id.player_view);
	jexoPlayerView.setOnTouchListener(new OnSwipeTouchListener(this){
		@Override
		public void onSwipeBottom() {
		    onBackPressed();
		}

		@Override
		public void onDoubleClick() {
		    if(jexoPlayer != null) jexoPlayer.seekTo(15000);
		}
	    });
	
	jexoPlayerView.setOnBackPressedListener(new JexoPlayerView.OnBackPressedListener(){
		@Override
		public void onBackPressed() {
		    playerActivity.onBackPressed();
		}
	    });
    }
}
