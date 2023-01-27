package com.jschartner.youtube;

import static js.Io.concat;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadata;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.session.MediaButtonReceiver;

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
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
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
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;

public class JexoPlayer extends BroadcastReceiver {

    private MediaBrowserCompat mediaBrowser;
    private MediaBrowserCompat.SubscriptionCallback mediaBrowserCallback;
    private Activity activity;

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

    //NOTIFICATION
    private NotificationCompat.Builder notification;
    private int notificationId;
    private Intent notificationIntent;

    private static JexoPlayer staticReceivePlayer;

    //NOTIFICATION_ACTION
    private static String NOTIFICATION_ACTION_TYPE = "com.jschartner.youtube.jexoplayer.notifciation_action";
    private static int NOTIFICATION_ACTION_TYPE_PLAY_PAUSE = 0;
    private static int NOTIFICATION_ACTION_TYPE_NEXT = 1;
    private static int NOTIFICATION_ACTION_TYPE_PREV = 2;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (staticReceivePlayer == null) {
            return;
        }
        Player player = staticReceivePlayer.getPlayer();
        if(player == null) {
            return;
        }

        if(intent == null) {
            return;
        }
        Bundle extras = intent.getExtras();
        if(extras == null) {
            return;
        }

        int action_type = extras.getInt(NOTIFICATION_ACTION_TYPE, -1);
        if(action_type == NOTIFICATION_ACTION_TYPE_PLAY_PAUSE) {
            player.setPlayWhenReady(!player.getPlayWhenReady());
        } else if(action_type == NOTIFICATION_ACTION_TYPE_NEXT) {
            staticReceivePlayer.endMedia();
        } else if(action_type == NOTIFICATION_ACTION_TYPE_PREV) {
           player.seekTo(0);
        } else {
            Utils.toast(context, concat("JexoPlayer.BroadcastReceiver: Unknown action type: ", action_type));
        }
    }

    //BROWSER SERVICE
    public static class MediaService extends MediaBrowserServiceCompat {
        private static final String MY_MEDIA_ROOT_ID = "media_root_id";
        private static final String LOG_TAG = "JexoPlayer.MediaService";

        public MediaSessionCompat mediaSession;
        private PlaybackStateCompat.Builder stateBuilder;

        private static List<MediaBrowserCompat.MediaItem> mediaItems;

        public static void setMediaItems(List<MediaBrowserCompat.MediaItem> _mediaItems) {
            mediaItems = _mediaItems;
        }

        @Override
        public void onCreate() {
            super.onCreate();
            mediaSession = new MediaSessionCompat(this, LOG_TAG);
            mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
            stateBuilder = new PlaybackStateCompat.Builder().setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PLAY_PAUSE);
            mediaSession.setPlaybackState(stateBuilder.build());

            setSessionToken(mediaSession.getSessionToken());
        }

        @Nullable
        @Override
        public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
            return new BrowserRoot(MY_MEDIA_ROOT_ID, null);
        }

        @Override
        public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
            result.sendResult(mediaItems);
        }
    }

    //ON ERROR
    interface OnError {
        void onError(PlaybackException playbackException);
    }

    private OnError onPlayerError;

    public void setOnPlayerError(final OnError onPlayerError) {
        this.onPlayerError = onPlayerError;
    }

    //ON PLAY WHEN READY CHANGED
    interface OnPlayWhenReadyChanged {
        void onPlayWhenReadyChanged(boolean playWhenReady, int reason);
    }

    private OnPlayWhenReadyChanged onPlayWhenReadyChanged;

    public void setOnPlayWhenReadyChanged(final OnPlayWhenReadyChanged onPlayWhenReadyChanged) {
        this.onPlayWhenReadyChanged = onPlayWhenReadyChanged;
    }

    //ON MEDIA ENDED
    private Runnable onMediaEnded;

    public void setOnMediaEnded(final Runnable onMediaEnded) {
        this.onMediaEnded = onMediaEnded;
    }

    public void endMedia() {
        if(onMediaEnded != null) {
            onMediaEnded.run();
        }
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

    public JexoPlayer() {
        this.player = null;
        this.context = null;
    }

    public JexoPlayer(Context context) {
        //setMediaSourceFactory, setRenderersFactory ?
        this.player = new ExoPlayer.Builder(context).build();

        //ON ANY EVENT UPDATE NOTIFICATION, IF EXTISTS
        player.addListener(new Player.Listener() {
            @SuppressLint("RestrictedApi")
            @Override
            public void onEvents(Player player, Player.Events events) {
                Player.Listener.super.onEvents(player, events);
                if (notification != null && notificationIntent != null) {
                    NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

                    if (notificationManager == null) {
                        return;
                    }

                    NotificationCompat.Action action = new NotificationCompat.Action(player.getPlayWhenReady()
                            ? R.drawable.pause : R.drawable.play, "play_pause", PendingIntent.getBroadcast(context, 0, notificationIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE));

                    Intent nextIntent = new Intent(context, JexoPlayer.class);
                    nextIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    nextIntent.putExtra(NOTIFICATION_ACTION_TYPE, NOTIFICATION_ACTION_TYPE_NEXT);
                    PendingIntent nextPendingIntent = PendingIntent.getBroadcast(context, 1, nextIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

                    Intent prevIntent = new Intent(context, JexoPlayer.class);
                    prevIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    prevIntent.putExtra(NOTIFICATION_ACTION_TYPE, NOTIFICATION_ACTION_TYPE_PREV);
                    PendingIntent prevPendingIntent = PendingIntent.getBroadcast(context, 2, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);


                    notification.mActions.clear();
                    notification.addAction(R.drawable.prev, "prev", prevPendingIntent);
                    notification.addAction(action);
                    notification.addAction(R.drawable.next, "next", nextPendingIntent);

                    Notification actualNotification = notification.build();
                    actualNotification.flags |= Notification.FLAG_AUTO_CANCEL;
                    notificationManager.notify(notificationId, actualNotification);
                }
            }

            @Override
            public void onPlaybackStateChanged(int reason) {
                if (reason == 4 && onMediaEnded != null) {
                    onMediaEnded.run();
                }
            }

            @Override
            public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                if (onPlayWhenReadyChanged != null) {
                    onPlayWhenReadyChanged.onPlayWhenReadyChanged(playWhenReady, reason);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                Player.Listener.super.onPlayerError(error);
                if (onPlayerError != null) {
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

    public boolean isConnected() {
        return mediaBrowser != null && mediaBrowser.isConnected();
    }

    public boolean disconnect() {
        if(mediaBrowser  == null) {
            return false;
        }
        if(!mediaBrowser.isConnected()) {
            return false;
        }
        mediaBrowser.unsubscribe(mediaBrowser.getRoot());
        mediaBrowser.disconnect();
        mediaBrowser = null;
        mediaBrowserCallback = null;
        activity = null;
        return true;
    }

    public boolean connect(String channelId, Context context, Activity activity) {
        if(mediaBrowser != null) {
            return false;
        }
        JexoPlayer jexoPlayer = this;

        mediaBrowserCallback = new MediaBrowserCompat.SubscriptionCallback() {
            @Override
            public void onChildrenLoaded(@NonNull String parentId, List<MediaBrowserCompat.MediaItem> children) {
                if (children == null || children.size() == 0) {
                    return;
                }
                boolean success = jexoPlayer.showNotification2(channelId, 420,
                        "JexoPlayer.MediaService", children.get(0));
                if (!success) jexoPlayer.updateNotification2(children.get(0));
            }
        };

        MediaBrowserCompat[] mediaBrowser = {null};
        mediaBrowser[0] = new MediaBrowserCompat(context, new ComponentName(context, JexoPlayer.MediaService.class),
                new MediaBrowserCompat.ConnectionCallback() {
                    @Override
                    public void onConnected() {
                        MediaSessionCompat.Token token = mediaBrowser[0].getSessionToken();
                        MediaControllerCompat mediaController= new MediaControllerCompat(context, token);
                        MediaControllerCompat.setMediaController(activity, mediaController);

                        mediaBrowser[0].subscribe(mediaBrowser[0].getRoot(), mediaBrowserCallback);
                    }
                }, null);
        this.mediaBrowser = mediaBrowser[0];
        this.mediaBrowser.connect();
        this.activity = activity;
        return true;
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
        if (jexoFormat == null) return false;
        if (trackSelectionParameters == null) return false;
        if (player == null) return false;
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
        return playFirst(iterator, null);
    }

    public boolean playFirst(Iterator<String> iterator, MediaBrowserCompat.MediaItem mediaItem) {
        while (iterator.hasNext()) {
            final String next = iterator.next();
            if(next == null) continue;
            if (play(next)) {
                if (mediaItem != null && isConnected()) {
                    MediaService.setMediaItems(Arrays.asList(mediaItem));
                    mediaBrowser.unsubscribe(mediaBrowser.getRoot());
                    mediaBrowser.subscribe(mediaBrowser.getRoot(), mediaBrowserCallback);
                }
                return true;
            }
        }
        return false;
    }

    public boolean play(String data, MediaBrowserCompat.MediaItem mediaItem) {
        if (playDashSource(data)) {
            if (mediaItem != null && isConnected()) {
                MediaService.setMediaItems(Arrays.asList(mediaItem));
                mediaBrowser.unsubscribe(mediaBrowser.getRoot());
                mediaBrowser.subscribe(mediaBrowser.getRoot(), mediaBrowserCallback);
            }
            return true;
        }
        if (data != null && data.contains(".m3u8") && playM3u8(data)) {
            if (mediaItem != null && isConnected()) {
                MediaService.setMediaItems(Arrays.asList(mediaItem));
                mediaBrowser.unsubscribe(mediaBrowser.getRoot());
                mediaBrowser.subscribe(mediaBrowser.getRoot(), mediaBrowserCallback);
            }
            return true;
        }
        if (playDash(data)) {
            if (mediaItem != null && isConnected()) {
                MediaService.setMediaItems(Arrays.asList(mediaItem));
                mediaBrowser.unsubscribe(mediaBrowser.getRoot());
                mediaBrowser.subscribe(mediaBrowser.getRoot(), mediaBrowserCallback);
            }
            return true;
        }
        return false;
    }

    public boolean play(String data) {
        if (playDashSource(data)) return true;
        if (data != null && data.contains(".m3u8") && playM3u8(data)) return true;
        if (playDash(data)) return true;
        return false;
    }

    public void seek(long ms) {
        if(player != null) player.seekTo(player.getCurrentPosition() + ms);
    }

    public void seekTo(long ms) {
        if (player != null) player.seekTo(ms);
    }

    public boolean stop() {
        hideNotification2();

        try {
            player.setPlayWhenReady(false);
            player.stop();
            player.seekTo(0);
            empty = true;

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isEmpty() {
        return empty;
    }

    public Player getPlayer() {
        return player;
    }

    public void hideNotification2() {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        if (notificationManager == null) {
            return;
        }

        notificationManager.cancel(notificationId);
        notificationIntent = null;
        notification = null;
    }

    public boolean updateNotification2(final MediaBrowserCompat.MediaItem mediaItem) {
        if (mediaItem == null) {
            return false;
        }
        if (notification == null) {
            return false;
        }

        MediaDescriptionCompat description = mediaItem.getDescription();

        notification.setContentTitle(description.getTitle())
                .setContentText(description.getSubtitle())
                .setSubText(description.getDescription());

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        if (notificationManager == null) {
            return false;
        }
        Notification actualNotification = notification.build();
        actualNotification.flags |= Notification.FLAG_AUTO_CANCEL;
        notificationManager.notify(notificationId, actualNotification);

        return true;
    }

    public boolean showNotification2(final String channelId,
                                     final int notificationId,
                                     final String mediaBrowserServiceCompatClassName,
                                     final MediaBrowserCompat.MediaItem mediaItem) {
        if (channelId == null) {
            return false;
        }
        if (mediaBrowserServiceCompatClassName == null) {
            return false;
        }
        if (mediaItem == null) {
            return false;
        }
        if (player == null) {
            return false;
        }
        if (context == null) {
            return false;
        }

        if (notification != null) {
            return false;
        }
        if (notificationIntent != null) {
            return false;
        }


        staticReceivePlayer = this;

        MediaSessionCompat mediaSession = new MediaSessionCompat(context, mediaBrowserServiceCompatClassName);
        MediaDescriptionCompat description = mediaItem.getDescription();
        new MediaSessionConnector(mediaSession).setPlayer(player);

        mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f, SystemClock.elapsedRealtime())
                .setActions(PlaybackStateCompat.ACTION_SEEK_TO | PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_PLAY_PAUSE)
                .build());
        mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                .putLong(MediaMetadata.METADATA_KEY_DURATION, description.getExtras().getLong(MediaMetadata.METADATA_KEY_DURATION) * 1000)
                .build());

        //contentIntent
        Intent contentIntent = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());
        PendingIntent contentPendingIntent = PendingIntent.getActivity(context, 4, contentIntent, PendingIntent.FLAG_IMMUTABLE);

        //nextIntent
        Intent nextIntent = new Intent(context, JexoPlayer.class);
        nextIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        nextIntent.putExtra(NOTIFICATION_ACTION_TYPE, NOTIFICATION_ACTION_TYPE_NEXT);
        PendingIntent nextPendingIntent = PendingIntent.getBroadcast(context, 0, nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

        //prevIntent
        Intent prevIntent = new Intent(context, JexoPlayer.class);
        prevIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        prevIntent.putExtra(NOTIFICATION_ACTION_TYPE, NOTIFICATION_ACTION_TYPE_PREV);
        PendingIntent prevPendingIntent = PendingIntent.getBroadcast(context, 2, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

        //playPauseIntent
        this.notificationIntent = new Intent(context, JexoPlayer.class);
        this.notificationIntent.putExtra(NOTIFICATION_ACTION_TYPE, NOTIFICATION_ACTION_TYPE_PLAY_PAUSE);
        this.notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        this.notification = new NotificationCompat.Builder(context, channelId)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowCancelButton(true)
                        .setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(context,
                                PlaybackStateCompat.ACTION_STOP))
                )
                .addAction(new NotificationCompat.Action(R.drawable.prev, "prev", prevPendingIntent))
                .addAction(new NotificationCompat.Action(R.drawable.pause, "play_pause", PendingIntent.getBroadcast(context, 1,
                        this.notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE)))
                .addAction(new NotificationCompat.Action(R.drawable.next, "next", nextPendingIntent))

                .setContentTitle(description.getTitle())
                .setContentText(description.getSubtitle())
                .setSubText(description.getDescription())

                .setContentIntent(contentPendingIntent)
                .setSmallIcon(R.mipmap.ic_launcher_foreground);
        this.notification.setDefaults(0);
        this.notificationId = notificationId;

        return true;
    }
}
