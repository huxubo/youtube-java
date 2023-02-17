package com.jschartner.youtube;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;
import static js.Io.concat;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.exoplayer2.Player;
import com.google.android.material.textfield.TextInputEditText;
import com.jschartner.youtubebase.Ffmpeg;
import com.jschartner.youtubebase.JexoFormat;
import com.jschartner.youtubebase.JexoPlayer;
import com.jschartner.youtubebase.JexoPlayerView;
import com.jschartner.youtubebase.Utils;
import com.jschartner.youtubebase.Youtube;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import js.Req;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link StartFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class StartFragment extends Fragment {
    private boolean doubleBackToExitIsPressedOnce;
    private JexoPlayerView jexoPlayerView;

    public StartFragment() {
        // Required empty public constructor
    }

    public static StartFragment newInstance() {
        StartFragment fragment = new StartFragment();
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

    public void toast(final Object ...os) {
        getMainActivity().toast(os);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        jexoPlayerView.setPlayer((Player) null);
        getMainActivity().setOnRadioButtonClickedListener(null);
    }

    private class Quality extends RecyclerView.ViewHolder {

       private final TextView qualityText;
       private final TextView sizeText;
       private int pos;

        public Quality(@NonNull View itemView, QualityAdapter qualityAdapter) {
            super(itemView);
            qualityText = itemView.findViewById(R.id.qualityText);
            sizeText = itemView.findViewById(R.id.sizeText);
            itemView.setOnClickListener((v) -> {
                qualityAdapter.select(pos);
            });
        }
    }

    interface OnSelected {
        void onSelected(int pos);
    }

    private class QualityAdapter extends RecyclerView.Adapter<Quality> {

        private List<String> names;
        private List<Long> lengths;
        private List<Integer> slots;
        private OnSelected onSelected;

        public int getFirstSlot() {
            return slots.get(0);
        }

        public QualityAdapter(final boolean selectedVideo, final JSONArray formats, final OnSelected onSelected) {
            this.names = new ArrayList<>();
            this.lengths = new ArrayList<>();
            this.slots = new ArrayList<>();
            for(int i=0;i<formats.length();i++) {
                JSONObject format = formats.optJSONObject(i);
                String name = null;
                if(selectedVideo) {
                    if(!Youtube.isVideoFormat(format)) continue;
                    Integer width = format.optInt("width");
                    name = JexoFormat.map(width.intValue());
                } else {
                    if(!Youtube.isAudioFormat(format)) continue;
                    long averageBitrate = !format.has("averageBitrate") ? -1 : format.optInt("averageBitrate")/1000;
                    name = String.valueOf(averageBitrate)+"kbps";
                }
                Long length = format.optLong("lengthInBytes");
                if(length == null) continue;
                if(name == null) continue;
                names.add(name);
                lengths.add(length.longValue()  / 1000000);
                slots.add(i);
            }
            this.onSelected = onSelected;
        }

        public void select(int pos) {
            if(onSelected != null) onSelected.onSelected(pos);
        }

        @NonNull
        @Override
        public Quality onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = ((LayoutInflater)
                    getActivity().getSystemService(LAYOUT_INFLATER_SERVICE)).inflate(R.layout.popup_window_list_item, null);
            Quality quality = new Quality(v, this);
            return quality;
        }

        @Override
        public void onBindViewHolder(@NonNull Quality holder, int position) {
            holder.pos = slots.get(holder.getAbsoluteAdapterPosition());
            holder.qualityText.setText(names.get(position));
            holder.sizeText.setText(String.valueOf(lengths.get(position)) + "MB");
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemCount() {
            return names.size();
        }
    }

    private class Selection {
        protected int format;
        protected boolean video;

        public Selection() {
            format = 0;
            video = true;
        }

        public String getFormatText(final JSONArray formats) {
            if(formats == null) {
                return null;
            }

            if(formats.length()<format) {
                return null;
            }
            JSONObject format = formats.optJSONObject(this.format);

            if(video) {
                return JexoFormat.map(format.optInt("width"));
            } else {
                return concat(format.optInt("averageBitrate") / 1000, "kbps");
            }
        }

        public String getSizeText(final JSONArray formats) {
            if(formats == null) {
                return null;
            }

            if(formats.length()<format) {
                return null;
            }

            JSONObject format = formats.optJSONObject(this.format);
            return concat(format.optLong("lengthInBytes") / 1000000, "MB");
        }
    }

    public void popupWindow(final View view, final String id) {
        toast("Fetching Video Formats ... ");

        Youtube.fetchVideo(id, (bytes) -> {
            final String response = Req.utf8(bytes);
            Youtube.fetchFormatsAndVideoInfoFromResponse(response, (formats, info) -> {

                AtomicInteger count = new AtomicInteger();

                for(int i=0;i<formats.length();i++) {
                    final JSONObject format = formats.optJSONObject(i);
                    final String url = format.optString("url");
                    if(url == null) {
                        count.set(count.get() + 1);
                        continue;
                    }
                    final int _i = i;

                    Youtube.async(() -> {
                        try{
                            return Req.head(url);
                        }
                        catch(IOException e) {
                            e.printStackTrace();
                            return null;
                        }
                    }, (result) -> {

                        try {
                            format.put("lengthInBytes", result == null ? 0 : result.len);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                        int current_count = count.get();
                        if(current_count + 1 == formats.length()) {
                            final String title = info.optString("title");
                            popupWindow1(view, formats, title == null ? "youtube-video" : title);
                        } else {
                            count.set(current_count + 1);
                        }
                    });
                }
            }, () -> toast("Failed to fetch Video Formats"));
        }, () -> toast("Failed to fetch Youtube"));
    }

    public void popupWindow1(final View view, final JSONArray formats, final String rawTitle) {

        final String downloadFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString();

        LayoutInflater inflater = (LayoutInflater)
                getActivity().getSystemService(LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(R.layout.popup_window, null);

        RecyclerView recyclerView = (RecyclerView) LayoutInflater.from(getContext()).inflate(R.layout.popup_window_list, null);
        PopupWindow recyclerWindow = new PopupWindow(recyclerView, FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, true);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        final String title = rawTitle.replaceAll("[^a-zA-Z0-9-ä-ü_\\.]", "_");
        Button okButton = popupView.findViewById(R.id.okButton);

        Button qualityButton = popupView.findViewById(R.id.qualityButton);
        TextView qualityText = popupView.findViewById(R.id.qualityText);

        Selection selection = new Selection();
        Runnable updateSelection = () -> {
            qualityText.setText(selection.getFormatText(formats));
            qualityButton.setText(selection.getSizeText(formats));
        };

        QualityAdapter[] adapter = new QualityAdapter[]{new QualityAdapter(selection.video, formats, (pos) -> {
            recyclerWindow.dismiss();
            selection.format = pos;

            updateSelection.run();
        })};
        selection.format = adapter[0].getFirstSlot();
        updateSelection.run();

        qualityButton.setOnClickListener((v) -> {
            recyclerView.setAdapter(adapter[0]);

            recyclerWindow.showAsDropDown(qualityButton, (int) -qualityButton.getX(), -qualityButton.getHeight() + 16);
        });

        TextInputEditText editText = popupView.findViewById(R.id.fileNameEditText);
        editText.setText(title);

        getMainActivity().setOnRadioButtonClickedListener((radioButtonView) -> {
            RadioButton radioButton = (RadioButton) radioButtonView;

            switch(radioButton.getId()) {
                case R.id.videoRadioButton:
                    selection.video = true;
                    break;
                case R.id.audioRadioButton:
                    selection.video = false;
                    break;
                default:
                    break;
            }

            adapter[0] = new QualityAdapter(selection.video, formats, (pos) -> {
                recyclerWindow.dismiss();
                selection.format = pos;

                updateSelection.run();
            });
            selection.format = adapter[0].getFirstSlot();
            updateSelection.run();
        });

        // create the popup window
        int width = LinearLayout.LayoutParams.MATCH_PARENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        boolean focusable = true; // lets taps outside the popup also dismiss it
        final PopupWindow popupWindow = new PopupWindow(popupView, width, height, focusable);
        popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0);

        Button backButton = popupView.findViewById(R.id.backButton);
        backButton.setOnClickListener((v) -> {
            popupWindow.dismiss();
        });

        okButton.setOnClickListener((v) -> {
            if(selection.video) {
                JSONObject videoFormat = formats.optJSONObject(selection.format);
                final String[] videoNames = Youtube.getNames(videoFormat, title, downloadFolder);
                if (videoNames == null) return;

                adapter[0] = new QualityAdapter(false, formats, null);
                JSONObject audioFormat = formats.optJSONObject(adapter[0].getFirstSlot());
                final String[] audioNames = Youtube.getNames(audioFormat, title, downloadFolder);
                if (audioNames == null) return;

                final String outputFilePath = concat(downloadFolder, "/", title, ".mp4");

                int[] i = {0};

                Runnable merge = () -> {
                    if (i[0] != 1) {
                        i[0]++;
                        return;
                    }
                    String result = Ffmpeg.mergeVideoAudio(videoNames[1], audioNames[1], outputFilePath);
                    if ("All went fine".equals(result)) {
                        new File(videoNames[1]).delete();
                        new File(audioNames[1]).delete();
                    } else {
                        getActivity().runOnUiThread(() -> Utils.toast(getActivity(), result));
                    }
                };

                Runnable failed = () -> {
                    getActivity().runOnUiThread(() -> Utils.toast(getActivity(), "Download failed"));
                };

                RunningDownloadManager downloadManager = getMainActivity().downloadManager;

                //VIDEO
                downloadManager.download(videoFormat.optString("url"), videoNames[0], videoNames[1], merge, failed);

                //AUDIO
                downloadManager.download(audioFormat.optString("url"), audioNames[0], audioNames[1], merge, failed);

                popupWindow.dismiss();
            } else {
                JSONObject format = formats.optJSONObject(selection.format);
                final String[] fileNames = Youtube.getNames(format, title, downloadFolder);
                final String fileTitle = fileNames[1];

                getMainActivity().downloadManager.download(formats.optJSONObject(selection.format).optString("url"), concat(title, Youtube.getSuffix(format)), fileNames[1], () -> {
                    getActivity().runOnUiThread(() -> Utils.toast(getActivity(), "Download Finished"));
                }, () -> {
                    getActivity().runOnUiThread(() -> Utils.toast(getActivity(), "Download failed"));
                });

                popupWindow.dismiss();
            }

        });
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ((AppCompatActivity) getActivity()).getSupportActionBar().show();
        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_start, container, false);

        //MainActivity
        ResultAdapter resultAdapter = getMainActivity().searchAdapter;
        JexoPlayer jexoPlayer = getMainActivity().jexoPlayer;
        History history = getMainActivity().history;

        //View
        SwipeRefreshLayout swipeLayout = view.findViewById(R.id.swipeLayout);
        swipeLayout.setOnRefreshListener(() -> {
            resultAdapter.free();
            history.refresh((result) -> {
                resultAdapter.refresh((JSONArray) result);
            });
            swipeLayout.setRefreshing(false);
        });

        LinearLayout jexoPlayerLayout = view.findViewById(R.id.playerLayout);
        jexoPlayerLayout.setVisibility(jexoPlayer.isEmpty() ? View.GONE : View.VISIBLE);
        jexoPlayerView = view.findViewById(R.id.playerView);
        jexoPlayer.setOnPlayWhenReadyChanged(((playWhenReady, reason) -> {
            if(playWhenReady) {
                jexoPlayerLayout.setVisibility(View.VISIBLE);
            }
        }));

        jexoPlayerView.setOnTouchListener(new OnSwipeTouchListener(getActivity()) {
            @Override
            public void onSwipeTop() {
                //NAVIGATE
                Navigation.findNavController(view).navigate(R.id.playerFragment);
            }

            @Override
            public void onSwipeLeft() {
                jexoPlayer.stop();
                jexoPlayerLayout.setVisibility(View.GONE);
            }
        });
        jexoPlayerView.setUseController(false);
        jexoPlayerView.setPlayer(jexoPlayer);
        ListView listView = view.findViewById(R.id.listView);
        listView.setAdapter(resultAdapter);

        resultAdapter.setOnContentClicked((v, position) -> {
            //ACTION
            final String videoId = resultAdapter.getItem(position).optString("videoId");
            getMainActivity().playVideo(videoId);

            //NAVIGATE
            if(getMainActivity().client == null)Navigation.findNavController(view).navigate(R.id.playerFragment);
        });

        resultAdapter.setOnItemLongClickListener((v, position) -> {
            getMainActivity().vibrate();
            final String videoId = resultAdapter.getItem(position).optString("videoId");
            popupWindow(view, videoId);
        });

        //OnBackPressed
        OnBackPressedCallback callback = new OnBackPressedCallback(true /* enabled by default */) {
            @Override
            public void handleOnBackPressed() {
                Object result = history.back();
                if (result != null) {
                    resultAdapter.refresh((JSONArray) result);
                    return;
                }

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