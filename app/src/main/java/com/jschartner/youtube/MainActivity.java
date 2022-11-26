package com.jschartner.youtube;

import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.exoplayer2.Player;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;

public class MainActivity extends AppCompatActivity {
    private JexoPlayer jexoPlayer;
    private JexoPlayerView jexoplayerView;

    private ListView listView;
    private ResultAdapter resultAdapter;

    private SwipeRefreshLayout swipeLayout;
    private History history;

    private boolean doubleBackToExitIsPressedOnce = false;

    private class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
        private ImageView imageView;
        private Bitmap[] bitmaps;
        private int position;

        public DownloadImageTask(final Bitmap[] bitmaps, int position) {
            this.bitmaps = bitmaps;
            this.position = position;
        }

        public void setImageView(ImageView imageView) {
            this.imageView = imageView;
        }

        @Override
        protected Bitmap doInBackground(String... urls) {
            String url = urls[0];
            Bitmap mIcon11 = null;

            try {
                InputStream in = new java.net.URL(url).openStream();
                mIcon11 = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return mIcon11;
        }

        protected void onPostExecute(Bitmap result) {
            bitmaps[position] = result;
            if(imageView!=null) imageView.setImageBitmap(result);
        }
    }

    private class ResultAdapter extends ArrayAdapter<JSONObject> {
        private Bitmap[] bitmaps;
        private DownloadImageTask[] tasks;

        public ResultAdapter(@NonNull Context context, int resource) {
            super(context, resource);
            bitmaps = new Bitmap[0];
            tasks = new DownloadImageTask[0];
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View rowView = LayoutInflater.from(getContext()).inflate(R.layout.list_item, parent, false);
            TextView textView = rowView.findViewById(R.id.textView);
            ImageView imageView = rowView.findViewById(R.id.imageView);

            JSONObject json = getItem(position);
            String finalText = json.optJSONObject("title")
		.optJSONArray("runs")
		.optJSONObject(0).optString("text");

            textView.setText(finalText);

            if(bitmaps[position] != null) {
                tasks[position] = null;
                imageView.setImageBitmap(bitmaps[position]);
            }
            else {
                tasks[position].setImageView(imageView);
            }

            return rowView;
        }

        public boolean refresh(final JSONArray result) {
            if(result == null) return false;
            clear();

            bitmaps = new Bitmap[result.length()];
            tasks = new DownloadImageTask[result.length()];
            for(int i=0;i<result.length();i++) {
                JSONObject json = result.optJSONObject(i);
		insert(json, getCount());
		JSONArray thumbnails = json.optJSONObject("thumbnail")
		    .optJSONArray("thumbnails");

                JSONObject thumbnail = thumbnails.optJSONObject(thumbnails.length() - 1);
                String url = thumbnail.optString("url");

		tasks[i] = new DownloadImageTask(bitmaps, i);
		tasks[i].execute(url);
            }

            notifyDataSetChanged();
            return true;
        }
    }

    public void startPlayer() {
        Intent intent = new Intent(getApplicationContext(), PlayerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    public void startPlayer(String id) {
        Intent intent = new Intent(getApplicationContext(), PlayerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	intent.putExtra("id", id);
	startActivity(intent);
    }

    @Override
    public void onResume() {
	super.onResume();
	jexoPlayer = Utils.getJexoPlayer(this);
	jexoplayerView.setPlayer(jexoPlayer);
	jexoplayerView.setVisibility(jexoPlayer.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onPause() {
	super.onPause();
	jexoplayerView.setPlayer((Player) null);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        history = new History(Youtube::search);

        swipeLayout = findViewById(R.id.swipeLayout);
        swipeLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
		@Override
		public void onRefresh() {
		    resultAdapter.refresh((JSONArray) history.refreshLoop());
		    swipeLayout.setRefreshing(false);
		}
	    });

        resultAdapter = new ResultAdapter(this, R.layout.list_item);
        listView = findViewById(R.id.listView);
        listView.setAdapter(resultAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		    final String videoId = resultAdapter.getItem(position).optString("videoId");
		    jexoPlayer.playFirst(Youtube.allSources(videoId));
		    startPlayer(videoId);
		}
	    });

        resultAdapter.refresh((JSONArray) history.searchLoop(null));

        //SEARCH
	jexoplayerView = findViewById(R.id.playerView);
	jexoPlayer = Utils.getJexoPlayer(this);
        jexoplayerView.setPlayer(jexoPlayer);

	jexoplayerView.setOnClickListener(new View.OnClickListener(){
		@Override
		public void onClick(View v) {
		    startPlayer();
		}
	    });	



	jexoplayerView.setOnTouchListener(new OnSwipeTouchListener(this) {
		@Override
		public void onSwipeTop() {
		    //add new activity
		    startPlayer();
		}

		@Override
		public void onSwipeLeft() {
		    jexoPlayer.stop();
		    jexoplayerView.setVisibility(jexoPlayer.isEmpty() ? View.GONE : View.VISIBLE);
		}
	    });
    }

    @Override
    public void onBackPressed() {
        Object result = history.back();
        if(result != null) {
	    resultAdapter.refresh((JSONArray) result);
	    return;
	}
	if (doubleBackToExitIsPressedOnce) {
            super.onBackPressed();
	    jexoPlayer.stop();
            finish();
            return;
        }

        doubleBackToExitIsPressedOnce = true;
        new Handler(Looper.getMainLooper())
	    .postDelayed(() -> {
                    doubleBackToExitIsPressedOnce = false;
	    }, 2000);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);

        MenuItem menuSearch = menu.findItem(R.id.myButton);
        SearchView searchView = (SearchView) menuSearch.getActionView();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        searchView.setIconified(false);
        searchView.setFocusable(true);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
		@Override
		public boolean onQueryTextSubmit(String query) {
		    searchView.setQuery("", false);
		    searchView.clearFocus();
		    menuSearch.collapseActionView();

		    swipeLayout.requestFocus();

		    resultAdapter.refresh((JSONArray) history.searchLoop(query));

		    return true;
		}

		@Override
		public boolean onQueryTextChange(String newText) {
		    return false;
		}
	    });

        return super.onCreateOptionsMenu(menu);
    }
}
