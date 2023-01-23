package com.jschartner.youtube;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.exoplayer2.Player;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link FullscreenPlayerFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class FullscreenPlayerFragment extends Fragment {

    private JexoPlayerView jexoPlayerView;
    private WindowInsetsControllerCompat windowInsetsController;

    public FullscreenPlayerFragment() {
        // Required empty public constructor
    }

    public static FullscreenPlayerFragment newInstance() {
        FullscreenPlayerFragment fragment = new FullscreenPlayerFragment();
        return fragment;
    }

    public MainActivity getMainActivity() {
        return (MainActivity) getActivity();
    }

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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ((AppCompatActivity) getActivity()).getSupportActionBar().hide();
        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        ////////////////////////////////////////////////////////////////////////
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
        ////////////////////////////////////////////////////////////////////////

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_fullscreen_player, container, false);

        JexoPlayer jexoPlayer = getMainActivity().jexoPlayer;
        jexoPlayerView = view.findViewById(R.id.player_view);
        jexoPlayerView.setOnBackPressedListener(() -> {
            Navigation.findNavController(view).popBackStack();
            Navigation.findNavController(view).popBackStack();
        });
        jexoPlayerView.setOnFullscreenPressedListener((v) -> {
            Navigation.findNavController(view).popBackStack();
        });
        jexoPlayerView.setPlayer(jexoPlayer);



        return view;
    }
}