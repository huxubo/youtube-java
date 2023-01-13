package com.jschartner.youtube;

import android.content.Context;
import android.net.Uri;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.database.DatabaseProvider;
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider;
import com.google.android.exoplayer2.ext.cronet.CronetDataSource;
import com.google.android.exoplayer2.ext.cronet.CronetUtil;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.common.collect.ImmutableList;

import org.chromium.net.CronetEngine;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Iterator;
import java.util.concurrent.Executors;

public class JexoPlayer {
    private static final String DOWNLOAD_CONTENT_DIRECTORY = "downloads";

    private final ExoPlayer player;
    private final Context context;
    private TrackSelectionParameters trackSelectionParameters;
    private boolean videoAutoSelection;
    private boolean empty;

    private static HlsMediaSource.Factory hlsMediaSourceFactory;
    private static DashMediaSource.Factory dashMediaSourceFactory;

    private static DataSource.Factory dataSourceFactory;
    private static DataSource.Factory httpDataSourceFactory;
    private static DatabaseProvider databaseProvider;
    private static File downloadDirectory;
    private static Cache downloadCache;

    interface OnError {
        void onError(PlaybackException playbackException);
    }

    private OnError onPlayerError;

    public void setOnPlayerError(final OnError onPlayerError) {
        this.onPlayerError = onPlayerError;
    }

    private static synchronized DatabaseProvider getDatabaseProvider(Context context) {
        if (databaseProvider == null) {
            databaseProvider = new StandaloneDatabaseProvider(context);
        }
        return databaseProvider;
    }

    private static synchronized File getDownloadDirectory(Context context) {
        if (downloadDirectory == null) {
            downloadDirectory = context.getExternalFilesDir(null);
            if (downloadDirectory == null) {
                downloadDirectory = context.getFilesDir();
            }
        }
        return downloadDirectory;
    }

    private static CacheDataSource.Factory buildReadOnlyCacheDataSource(DataSource.Factory upstreamFactory, Cache cache) {
        return new CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setCacheWriteDataSinkFactory(null)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }

    public static synchronized DataSource.Factory getHttpDataSourceFactory(Context context) {
        if (httpDataSourceFactory == null) {
            context = context.getApplicationContext();
            CronetEngine cronetEngine = CronetUtil.buildCronetEngine(context);
            if (cronetEngine != null) {
                httpDataSourceFactory =
                        new CronetDataSource.Factory(cronetEngine, Executors.newSingleThreadExecutor());
            }
        }
        return httpDataSourceFactory;
    }

    private static synchronized Cache getDownloadCache(Context context) {
        if (downloadCache == null) {
            File downloadContentDirectory =
                    new File(getDownloadDirectory(context), DOWNLOAD_CONTENT_DIRECTORY);

            downloadCache =
                    new SimpleCache(downloadContentDirectory,
                            new NoOpCacheEvictor(),
                            getDatabaseProvider(context));
        }
        return downloadCache;
    }

    public static synchronized DataSource.Factory getDataSourceFactory(Context context) {
        if (dataSourceFactory == null) {
            context = context.getApplicationContext();
            DefaultDataSource.Factory upstreamFactory =
                    new DefaultDataSource.Factory(context, getHttpDataSourceFactory(context));
            dataSourceFactory = buildReadOnlyCacheDataSource(upstreamFactory, getDownloadCache(context));
        }
        return dataSourceFactory;
    }

    private HlsMediaSource.Factory getHlsMediaSourceFactory() {
        if (hlsMediaSourceFactory == null) {
            hlsMediaSourceFactory = new HlsMediaSource.Factory(getDataSourceFactory(context));
        }
        return hlsMediaSourceFactory;
    }

    private DashMediaSource.Factory getDashMediaSourceFactory() {
        if (dashMediaSourceFactory == null) {
            dashMediaSourceFactory = new DashMediaSource.Factory(getDataSourceFactory(context));
        }
        return dashMediaSourceFactory;
    }

    public JexoPlayer(Context context) {
        //setMediaSourceFactory, setRenderersFactory ?
        this.player = new ExoPlayer.Builder(context).build();
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                Player.Listener.super.onPlayerError(error);
                if(onPlayerError != null) {
                    onPlayerError.onError(error);
                }
            }
        });

        this.context = context.getApplicationContext();
        this.trackSelectionParameters = new TrackSelectionParameters.Builder(this.context).build();
        this.player.setTrackSelectionParameters(trackSelectionParameters);
        this.player.setAudioAttributes(AudioAttributes.DEFAULT, true);
        this.videoAutoSelection = true;
	    this.empty = true;
    }

    public JexoFormat getVideoFormats() {
        if (player == null) return null;
        Tracks tracks = player.getCurrentTracks();
        if (tracks == null) return null;

        ImmutableList<Tracks.Group> trackGroups = tracks.getGroups();
        for (int i = 0; i < trackGroups.size(); i++) {
            Tracks.Group trackGroup = trackGroups.get(i);
            if (trackGroup.getMediaTrackGroup().type == C.TRACK_TYPE_VIDEO) {
                return new JexoFormat(trackGroup, videoAutoSelection);
            }
        }
        return null;
    }

    public boolean setFormat(JexoFormat jexoFormat) {
        if(jexoFormat == null) return false;
        if(trackSelectionParameters == null) return false;
        if(player == null) return false;
        trackSelectionParameters = trackSelectionParameters.buildUpon()
                        .setOverrideForType(new TrackSelectionOverride(jexoFormat.getTrackGroup(), jexoFormat.getIndices()))
                        .build();
        player.setTrackSelectionParameters(trackSelectionParameters);
        videoAutoSelection = jexoFormat.getSelected() == 0;
        return true;
    }

    public void playMediaSource(MediaSource source) {
        if (source == null) return;
        player.setMediaSource(source);
        player.setPlayWhenReady(true);
        player.prepare();
	    empty = false;
    }

    private boolean playLinkWithFactory(String link, MediaSource.Factory factory) {
        if (link == null) return false;
        MediaSource source = null;
        try {
            source = factory.createMediaSource(MediaItem.fromUri(Uri.parse(link)));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        playMediaSource(source);
        return true;
    }

    public boolean playM3u8(String m3u8Link) {
        return playLinkWithFactory(m3u8Link, getHlsMediaSourceFactory());
    }

    public boolean playDash(String dashLink) {
        return playLinkWithFactory(dashLink, getDashMediaSourceFactory());
    }

    public boolean playDashSource(String dashString) {
        if (dashString == null) return false;

        MediaSource source;
        try {
            DashManifest manifest = (new DashManifestParser()).parse(Uri.parse(""), new ByteArrayInputStream(dashString.getBytes()));
            source = getDashMediaSourceFactory().createMediaSource(manifest);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        playMediaSource(source);
        return true;
    }

    public boolean playFirst(Iterator<String> iterator) {
        while (iterator.hasNext()) {
            if (play(iterator.next())) return true;
        }
        return false;
    }

    public boolean play(String data) {
        if (playDashSource(data)) return true;
        if (data != null && data.contains(".m3u8") && playM3u8(data)) return true;
        if (playDash(data)) return true;
        return false;
    }

    public void seekTo(long ms) {
        if(player!=null) player.seekTo(player.getCurrentPosition() + ms);
    }

    public boolean stop() {
	try{
	    player.setPlayWhenReady(false);
	    player.stop();
	    player.seekTo(0);
	    empty = true;
	    return true;
	}
	catch(Exception e) {
	    return false;
	}
    }

    public boolean isEmpty() {
	return empty;
    }

    public Player getPlayer() {
        return player;
    }
}
