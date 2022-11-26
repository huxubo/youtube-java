package com.jschartner.youtube;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.exoplayer2.Player;

import androidx.core.view.WindowInsetsControllerCompat;

//cd E:\Documents\tu\android\ExoPlayer3 && gradlew installDebug && adb shell monkey -p com.example.exoplayer 1
public class PlayerActivity extends AppCompatActivity {

    private JexoPlayerView jexoPlayerView;

    @Override
    public void onResume() {
	super.onResume();
	jexoPlayerView.setPlayer(Utils.getJexoPlayer(this));
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

        jexoPlayerView = findViewById(R.id.player_view);
	jexoPlayerView.setOnTouchListener(new OnSwipeTouchListener(this){
		@Override
		public void onSwipeBottom() {
		    onBackPressed();
		}
	    });
    }
}
