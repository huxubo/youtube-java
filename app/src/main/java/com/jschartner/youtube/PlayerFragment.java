package com.jschartner.youtube;

import static js.Io.concat;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.exoplayer2.Player;
import com.jschartner.youtubebase.JexoPlayer;
import com.jschartner.youtubebase.JexoPlayerView;

import org.json.JSONObject;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link PlayerFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class PlayerFragment extends Fragment {

    private JexoPlayerView jexoPlayerView;
    private TextView titleView;
    private TextView durationView;

    public PlayerFragment() {
        // Required empty public constructor
    }

    public static PlayerFragment newInstance() {
        PlayerFragment fragment = new PlayerFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public MainActivity getMainActivity() {
        return (MainActivity) getActivity();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        jexoPlayerView.setPlayer((Player) null);
        titleView.setText("");
        durationView.setText("");
    }

    private void toast(final Object... os) {
        getMainActivity().toast(os);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment

        ((AppCompatActivity) getActivity()).getSupportActionBar().hide();
        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        JexoPlayer jexoPlayer = getMainActivity().jexoPlayer;
        ResultAdapter resultAdapter = getMainActivity().recommendationAdapter;

        View view = inflater.inflate(R.layout.fragment_player, container, false);

        ListView listView = view.findViewById(R.id.recommendationsListView);
        listView.setAdapter(resultAdapter);
        resultAdapter.setOnContentClicked((v, pos) -> {
            final JSONObject result = resultAdapter.getItem(pos);
            final String videoId = result.optString("videoId");
            getMainActivity().playVideo(videoId);

            listView.setSelectionFromTop(0, 0);
        });

        jexoPlayerView = view.findViewById(R.id.player_view);
        jexoPlayerView.setPlayer(jexoPlayer);

        jexoPlayerView.setOnBackPressedListener((_view) -> {
            Navigation.findNavController(view).popBackStack();
        });

        jexoPlayerView.setOnTouchListener(new OnSwipeTouchListener(getActivity()) {
            @Override
            public void onSwipeBottom() {
                Navigation.findNavController(view).popBackStack();
            }
        });
        jexoPlayerView.setOnFullscreenPressedListener((v) -> {
            Navigation.findNavController(view).navigate(R.id.fullscreenPlayerFragment);
        });

        titleView = view.findViewById(R.id.playerTitleView);
        durationView = view.findViewById(R.id.playerDurationView);

        final Runnable update = () -> {
            JSONObject video = getMainActivity().currentVideo;
            if(video != null) {
                titleView.setText(video.optString("title"));
                durationView.setText(concat(video.optString("viewCountText"), " - ", video.optString("publishedTimeText")));
            }
        };

        update.run();
        getMainActivity().setOnCurrentVideoChanged(update);

        return view;
    }
}