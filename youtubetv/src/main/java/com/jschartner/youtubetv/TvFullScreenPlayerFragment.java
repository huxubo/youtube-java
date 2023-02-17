package com.jschartner.youtubetv;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;

import com.google.android.exoplayer2.Player;
import com.jschartner.youtubebase.JexoPlayer;
import com.jschartner.youtubebase.JexoPlayerView;

import org.json.JSONObject;

public class TvFullScreenPlayerFragment extends Fragment {

    private JexoPlayerView jexoPlayerView;
    private WindowInsetsControllerCompat windowInsetsController;
    private boolean doubleBackToExitIsPressedOnce;

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        jexoPlayerView.setPlayer((Player) null);
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        windowInsetsController =
                WindowCompat.getInsetsController(getActivity().getWindow(), getActivity().getWindow().getDecorView());
        // Configure the behavior of the hidden system bars.
        windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    private TvMainActivity getMainActivity() {
        return (TvMainActivity) getActivity();
    }

    public void toast(final Object ...os) {
        getMainActivity().toast(os);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_tv_full_screen_player, container, false);

        ////////////////////////////////////////////////////////////////////////
        ((AppCompatActivity) getActivity()).getSupportActionBar().hide();
        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
        ////////////////////////////////////////////////////////////////////////

        JexoPlayer jexoPlayer = getMainActivity().jexoPlayer;
        jexoPlayerView = view.findViewById(R.id.player_view);
        jexoPlayerView.setPlayer(jexoPlayer);
        jexoPlayerView.enableBackButton(true);
        jexoPlayerView.enableFullScreenButton(true);
        jexoPlayerView.enableFullscreenMode(true);

        getMainActivity().setOnCurrentVideoChanged(() -> {
            //wow
            new Thread(() -> {
                while(getMainActivity() == null) {}
                getMainActivity().runOnUiThread(() -> {
                    JSONObject video = getMainActivity().currentVideo;
                    jexoPlayerView.setTitle(video.optString("title"));
                    jexoPlayerView.setAuthor(video.optString("author"));
                });
            }).start();
        });

        OnBackPressedCallback callback = new OnBackPressedCallback(true /* enabled by default */) {
            @Override
            public void handleOnBackPressed() {
                if(doubleBackToExitIsPressedOnce) {
                    jexoPlayer.stop();
                    getActivity().finish();
                    return;
                }

                doubleBackToExitIsPressedOnce = true;
                new Handler(Looper.getMainLooper()).postDelayed(() -> doubleBackToExitIsPressedOnce = false, 2000);
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), callback);

        return view;
    }
}