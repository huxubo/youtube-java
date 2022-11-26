package com.jschartner.youtube;

import android.widget.LinearLayout;
import android.graphics.Color;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import com.google.android.exoplayer2.ui.StyledPlayerView.ControllerVisibilityListener;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.MotionEvent;
import android.view.View;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.StyledPlayerView;

import org.json.JSONObject;

public class JexoPlayerView extends StyledPlayerView {

    private static final int SETTINGS_VIDEO_POSITION = 0;
    private static final int SETTINGS_VELOCITY_POSITION = 1;

    private JexoPlayer jexoPlayer;

    private TextView titleView;
    private TextView authorView;
    private ImageButton backButton;
    private LinearLayout upperLayout;

    private RecyclerView settingsView;
    private PopupWindow settingsWindow;
    private SettingsAdapter settingsAdapter;
    private int settingsWindowMargin;

    private int selectedMainSettingsPosition = -1;

    private SubSettingsAdapter subSettingsAdapter;

    private int[] playbackSpeedsMultBy100;
    private String[] playbackSpeedTexts;
    private int selectedPlaybackSpeedIndex;

    private JexoFormat videoFormats;
    private View.OnClickListener onClickListener;
    private OnBackPressedListener onBackPressedListener;

    interface OnBackPressedListener {
	void onBackPressed();
    }

    public void setPlayer(JexoPlayer jexoPlayer) {
        super.setPlayer(jexoPlayer.getPlayer());
        this.jexoPlayer = jexoPlayer;
    }

    public void setOnBackPressedListener(OnBackPressedListener onBackPressedListener) {
	this.onBackPressedListener = onBackPressedListener;
    }

    @Override
    public void setOnClickListener(View.OnClickListener onClickListener) {
        this.onClickListener = onClickListener;
    }

    public void removeOnClickListener() {
        this.onClickListener = null;
    }

    @Override
    public boolean performClick() {
        if(onClickListener!=null) onClickListener.onClick(this);
        else return super.performClick();
        return true;
    }

    private void updateSelectedVideoFormat() {
        if (jexoPlayer == null) {
            settingsWindow.dismiss();
            return;
        }
        videoFormats = jexoPlayer.getVideoFormats();
        if (videoFormats != null)
            settingsAdapter.setSubTextAtPosition(SETTINGS_VIDEO_POSITION, videoFormats.getSelectedLabel());
    }

    private void updateSelectedPlaybackSpeedIndex() {
        Player player = getPlayer();
        if (player == null) return;
        float speed = player.getPlaybackParameters().speed;
        int currentSpeedMultBy100 = Math.round(speed * 100);
        int closestMatchIndex = 0;
        int closestMatchDifference = Integer.MAX_VALUE;
        for (int i = 0; i < playbackSpeedsMultBy100.length; i++) {
            int difference = Math.abs(currentSpeedMultBy100 - playbackSpeedsMultBy100[i]);
            if (difference < closestMatchDifference) {
                closestMatchIndex = i;
                closestMatchDifference = difference;
            }
        }
        selectedPlaybackSpeedIndex = closestMatchIndex;
        settingsAdapter.setSubTextAtPosition(SETTINGS_VELOCITY_POSITION, playbackSpeedTexts[selectedPlaybackSpeedIndex]);
    }

    private void setPlaybackSpeed(float speed) {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        player.setPlaybackSpeed(speed);
    }

    private void updateSettingsWindowSize() {
        settingsView.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);

        int maxWidth = getWidth() - settingsWindowMargin * 2;
        int itemWidth = settingsView.getMeasuredWidth();
        int width = Math.min(itemWidth, maxWidth);
        settingsWindow.setWidth(width);

        int maxHeight = getHeight() - settingsWindowMargin * 2;
        int itemHeight = settingsView.getMeasuredHeight();
        int height = Math.min(itemHeight, maxHeight);
        settingsWindow.setHeight(height);
    }

    private void displaySettingsWindow(RecyclerView.Adapter<?> adapter) {
        settingsView.setAdapter(adapter);

        updateSettingsWindowSize();
        settingsWindow.dismiss();

        int xoff = getWidth() - settingsWindow.getWidth() - settingsWindowMargin;
        int yoff = -settingsWindow.getHeight() - settingsWindowMargin;

        settingsWindow.showAsDropDown(this, xoff, yoff);
    }

    private void onSubSettingsViewClicked(int pos) {
        if (selectedMainSettingsPosition == SETTINGS_VIDEO_POSITION) {
            if (jexoPlayer != null && videoFormats != null) {
                videoFormats.setSelected(pos);
                jexoPlayer.setFormat(videoFormats);
            }
        } else if (selectedMainSettingsPosition == SETTINGS_VELOCITY_POSITION) {
            if (pos != selectedPlaybackSpeedIndex) {
                float speed = playbackSpeedsMultBy100[pos] / 100.0f;
                setPlaybackSpeed(speed);
            }
        }
        settingsWindow.dismiss();
    }

    private void onSettingViewClicked(int pos) {
        if (pos == SETTINGS_VIDEO_POSITION) {
            if (videoFormats == null) {
                settingsWindow.dismiss();
                return;
            }
            selectedMainSettingsPosition = SETTINGS_VIDEO_POSITION;
            subSettingsAdapter.init(videoFormats.getLabels(), videoFormats.getSelected());
            displaySettingsWindow(subSettingsAdapter);
        } else if (pos == SETTINGS_VELOCITY_POSITION) {
            selectedMainSettingsPosition = SETTINGS_VELOCITY_POSITION;
            subSettingsAdapter.init(playbackSpeedTexts, selectedPlaybackSpeedIndex);
            displaySettingsWindow(subSettingsAdapter);
        } else {
	    settingsWindow.dismiss();
	    selectedMainSettingsPosition = -1;
        }
    }

    private class SubSettingViewHolder extends RecyclerView.ViewHolder {

        private final TextView textView;
        private final View checkView;

        public SubSettingViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.exo_text);
            checkView = itemView.findViewById(R.id.exo_check);
            itemView.setOnClickListener(v -> onSubSettingsViewClicked(getAbsoluteAdapterPosition()));
        }
    }

    private class SubSettingsAdapter extends RecyclerView.Adapter<SubSettingViewHolder> {

        private String[] texts;
        private int selectedIndex;

        public SubSettingsAdapter() {
            texts = new String[0];
        }

        public void init(String[] texts, int selectedIndex) {
            this.texts = texts;
            this.selectedIndex = selectedIndex;
        }

        @NonNull
        @Override
        public SubSettingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(getContext()).inflate(R.layout.exo_styled_sub_settings_list_item, null);
            return new SubSettingViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull SubSettingViewHolder holder, int position) {
            if (position < texts.length) {
                holder.textView.setText(texts[position]);
            }
            holder.checkView.setVisibility(position == selectedIndex ? VISIBLE : INVISIBLE);
        }

        @Override
        public int getItemCount() {
            return texts.length;
        }
    }

    private class SettingViewHolder extends RecyclerView.ViewHolder {

        private final TextView textView;
        private final TextView subTextView;
        private final ImageView imageView;

        public SettingViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.exo_main_text);
            subTextView = itemView.findViewById(R.id.exo_sub_text);
            imageView = itemView.findViewById(R.id.exo_icon);
            itemView.setOnClickListener(v -> onSettingViewClicked(getAbsoluteAdapterPosition()));
        }
    }

    private class SettingsAdapter extends RecyclerView.Adapter<SettingViewHolder> {

        private final String[] mainTexts;
        private final String[] subTexts;
        private final Drawable[] iconIds;

        public SettingsAdapter(String[] mainTexts, Drawable[] iconIds) {
            this.mainTexts = mainTexts;
            this.subTexts = new String[mainTexts.length];
            this.iconIds = iconIds;
        }

        @NonNull
        @Override
        public SettingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(getContext()).inflate(R.layout.exo_styled_settings_list_item, null);
            SettingViewHolder holder = new SettingViewHolder(v);

            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull SettingViewHolder holder, int position) {
            holder.textView.setText(mainTexts[position]);

            if (subTexts[position] == null) {
                holder.subTextView.setVisibility(GONE);
            } else {
                holder.subTextView.setText(subTexts[position]);
            }

            if (iconIds[position] == null) {
                holder.imageView.setVisibility(GONE);
            } else {
                holder.imageView.setImageDrawable(iconIds[position]);
            }
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemCount() {
            return mainTexts.length;
        }

        public void setSubTextAtPosition(int position, String subText) {
            this.subTexts[position] = subText;
        }
    }

    public void setInfo(JSONObject info) {
        if(info==null) return;
        if(titleView!=null) {
            String title = info.optString("title");
            titleView.setText(title == null ? "" : title);
        }
        if(authorView!=null) {
            String author = info.optString("author");
            authorView.setText(author == null ? "" : author);
        }
    }
    
    private void init() {
        setShowNextButton(false);
        setShowPreviousButton(false);
        setShowShuffleButton(false);
        setShowFastForwardButton(false);
        setShowRewindButton(false);

        titleView = findViewById(R.id.titleView);
        authorView = findViewById(R.id.authorView);
	backButton = findViewById(R.id.backButton);
	upperLayout = findViewById(R.id.upperLayout);
	backButton.setColorFilter(0xffffffff);

	backButton.setOnClickListener(new View.OnClickListener(){
		@Override
		public void onClick(View v) {
		    if(onBackPressedListener != null) {
			onBackPressedListener.onBackPressed();
		    }
		}
	    });

	setControllerVisibilityListener(new StyledPlayerView.ControllerVisibilityListener(){
		@Override
		public void onVisibilityChanged(int v) {
		}
	    });

        ImageButton button = findViewById(R.id.exo_custom_button);

        Resources resources = getContext().getResources();

        playbackSpeedsMultBy100 = resources.getIntArray(R.array.exo_speed_multiplied_by100);
        playbackSpeedTexts = resources.getStringArray(R.array.exo_playback_speeds);
        selectedPlaybackSpeedIndex = 3;

        subSettingsAdapter = new SubSettingsAdapter();

        Drawable[] settingIcons = new Drawable[2];
        settingIcons[SETTINGS_VIDEO_POSITION] = button.getDrawable();
        settingIcons[SETTINGS_VELOCITY_POSITION] = resources.getDrawable(R.drawable.speed);

        settingsAdapter = new SettingsAdapter(new String[]{"Video", "Speed"}, settingIcons);
        settingsView = (RecyclerView) LayoutInflater.from(getContext()).inflate(R.layout.exo_styled_settings_list, null);
        settingsView.setLayoutManager(new LinearLayoutManager(getContext()));
        settingsWindow = new PopupWindow(settingsView, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, true);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateSelectedPlaybackSpeedIndex();
                updateSelectedVideoFormat();
                displaySettingsWindow(settingsAdapter);
            }
        });
    }

    public JexoPlayerView(Context context) {
        super(context);
        init();
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
