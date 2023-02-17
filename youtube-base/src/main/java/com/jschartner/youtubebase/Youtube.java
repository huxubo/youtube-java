package com.jschartner.youtubebase;

import android.media.MediaMetadata;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import js.Io;
import js.Req;

public class Youtube {

    private static long engine;

    private static final native long initEngine();

    private static final synchronized native String runScript(String code, long pointer);

    private static final native void closeEngine(long pointer);

    static {
        System.loadLibrary("duktape-lib");
        engine = initEngine();
    }

    public static void close() {
        closeEngine(engine);
    }

    public static final MediaBrowserCompat.MediaItem toMediaItem(final String id, final JSONObject videoInfo) {
        if(id == null) {
            return null;
        }

        if(videoInfo == null) {
            return null;
        }

        final Bundle songDuration = new Bundle();
        songDuration.putLong(MediaMetadata.METADATA_KEY_DURATION, Long.parseLong(videoInfo.optString("lengthSeconds")));
        return new MediaBrowserCompat.MediaItem(
                new MediaDescriptionCompat.Builder()
                        .setMediaId(id)
                        .setTitle(videoInfo.optString("title"))
                        .setSubtitle(videoInfo.optString("author"))
                        .setDescription("")
                        .setExtras(songDuration)
                        .build(),
                MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);
    }

    public static String getSuffix(final JSONObject format) {
        if(format == null) return null;
        if (!format.has("mimeType")) return null;

        final boolean isVideo = isVideoFormat(format);
        final boolean isAudio = isAudioFormat(format);

        if(!(isVideo ^ isAudio)) return null;

        final boolean video = isVideo;

        String _mime = format.optString("mimeType");
        String[] parts = _mime.split(";");
        if(parts.length == 0) return null;
        String[] mimeParts = parts[0].split("/");
        if(mimeParts.length == 0) return null;
        String mime = mimeParts[mimeParts.length - 1];

        return Io.concat(video ? "_video" : "", ".",
                video
                        ? (("webm".equals(mime)) ? "webm" : "mp4")
                        : (("webm".equals(mime)) ? "webm" : "m4a"));
    }

    //[fileTitle, filePath]
    public static String[] getNames(final JSONObject format, final String title, final String prefix) {
        if(format == null) return null;
        if(title == null || title.length() == 0) return null;

        final String suffix = getSuffix(format);
        if(suffix == null) return null;
        return new String[]{Io.concat(title, suffix), Io.concat(prefix, "/", title, suffix)};
    }

    private static Map<String, String> getQueryMap(final String query) {
        String[] params = query.split("&");
        Map<String, String> map = new HashMap<>();

        for (String param : params) {
            String[] parts = param.trim().split("=");
            if (parts.length < 2) continue;
            String name = parts[0];
            String value = parts[1];
            map.put(name, value);
        }
        return map;
    }

    private static Pair<JSONArray, Boolean> getFormatsFromInitialPlayerResponse(final JSONObject streamingData) {
        if(streamingData == null) {
            return null;
        }

        JSONArray result = new JSONArray();


        boolean needDecryption = false;
        JSONArray formats = streamingData.optJSONArray("formats");
        if(formats != null) {
            for(int i=0;i<formats.length();i++) {
                final JSONObject format = formats.optJSONObject(i);
                result.put(format);
                needDecryption = needDecryption | format.has("signatureCipher");
            }
        }

        JSONArray adaptiveFormats = streamingData.optJSONArray("adaptiveFormats");
        if(formats != null) {
            for(int i=0;i<adaptiveFormats.length();i++) {
                final JSONObject format = adaptiveFormats.optJSONObject(i);
                result.put(format);
                needDecryption = needDecryption | format.has("signatureCipher");
            }
        }

        return new Pair<>(result, Boolean.valueOf(needDecryption));
    }

    private static Runnable maybeOnError(final Runnable onError) {
        return () -> {
            if(onError != null) {
                onError.run();
            }
        };
    }

    public interface Consumer2<T, V> {
        void accept(T t, V v);
    }

    public static <T> void async(final Supplier<T> supplier, final Consumer<T> consumer) {
        new Async<T>(consumer).execute(supplier);
    }

    public static void async(final Runnable run, final Runnable onThen) {
        new Async<Void>((x) -> onThen.run()).execute((() -> {
            run.run();
            return null;
        }));
    }

    public static void fetch(@NonNull final String url, final Consumer<byte[]> onThen, final Runnable onError) {
        new Request(onThen, onError).execute(url);
    }

    public static void fetchVideo(@NonNull final String id, final Consumer<byte[]> onThen, final Runnable onError) {
        new Request(onThen, onError).execute(Io.concat("https://www.youtube.com/watch?v=", id));
    }

    public static void fetchSearchResults(final String keyword, @NonNull final Consumer<JSONArray> onThen, final Runnable _onError) {
        if(keyword == null) {
            fetchSearchResults(onThen, _onError);
            return;
        }

        Runnable onError = maybeOnError(_onError);
        fetch(Io.concat("https://www.youtube.com/results?search_query=", keyword), (bytes) -> {
            final String response = Req.utf8(bytes);

            int pos = response.indexOf("ytInitialData = ");
            if (pos == -1) {
                onError.run();
                return;
            }

            int _pos = pos + "ytInitialData = ".length();

            int __pos = response.indexOf("</script>", _pos);
            if(__pos < 0) {
                onError.run();
                return;
            }

            final JSONObject initialData;
            try {
                initialData =new JSONObject(response.substring(_pos, __pos));
            } catch (JSONException e) {
                e.printStackTrace();
                onError.run();
                return;
            }

            JSONArray results = new JSONArray();

            try {
                JSONArray _contents = initialData
                        .getJSONObject("contents")
                        .getJSONObject("twoColumnSearchResultsRenderer")
                        .getJSONObject("primaryContents")
                        .getJSONObject("sectionListRenderer")
                        .getJSONArray("contents");

                for (int j = 0; j < _contents.length(); j++) {
                    JSONObject _content = _contents.getJSONObject(j);
                    JSONObject itemSectionRenderer = _content.optJSONObject("itemSectionRenderer");
                    if(itemSectionRenderer == null) {
                        continue;
                    }
                    JSONArray contents = itemSectionRenderer.optJSONArray("contents");
                    if(contents == null) {
                        continue;
                    }

                    for (int i = 0; i < contents.length(); i++) {
                        JSONObject result = contents.getJSONObject(i);
                        JSONObject videoRenderer = result.optJSONObject("videoRenderer");
                        if(videoRenderer != null) {
                            results.put(videoRenderer);
                        }

                        JSONObject shelfRenderer = result.optJSONObject("shelfRenderer");
                        if(shelfRenderer == null) {
                            continue;
                        }
                        JSONObject content = shelfRenderer.optJSONObject("content");
                        if(content == null) {
                            continue;
                        }
                        JSONObject verticalListRenderer = content.optJSONObject("verticalListRenderer");
                        if(verticalListRenderer == null) {
                            continue;
                        }
                        JSONArray items = verticalListRenderer.optJSONArray("items");
                        if(items == null) {
                            continue;
                        }

                        for (int k = 0; k < items.length(); k++) {
                            JSONObject shelf = items.getJSONObject(k);
                            JSONObject shelVideoRenderer = shelf.optJSONObject("videoRenderer");
                            if(shelVideoRenderer == null) {
                                continue;
                            }
                            results.put(shelVideoRenderer);
                        }
                    }
                }

            } catch (JSONException e) {
                e.printStackTrace();
                onError.run();
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                onThen.accept(results);
            }

        }, onError);
    }

    public static String getNextVideoIdFromInitialData(@NonNull final JSONObject initialData) {
        try{
            return initialData.getJSONObject("contents")
                    .getJSONObject("twoColumnWatchNextResults")
                    .getJSONObject("autoplay")
                    .getJSONObject("autoplay")
                    .getJSONArray("sets")
                    .getJSONObject(0)
                    .getJSONObject("autoplayVideo")
                    .getJSONObject("watchEndpoint")
                    .optString("videoId");
        }
        catch(JSONException e) {
            return null;
        }
    }

    public static JSONArray getRecommendedVideosFromInitialData(@NonNull final JSONObject initialData) {
        final JSONArray result = new JSONArray();

        try{
            final JSONArray results = initialData
                    .getJSONObject("contents")
                    .getJSONObject("twoColumnWatchNextResults")
                    .getJSONObject("secondaryResults")
                    .getJSONObject("secondaryResults")
                    .getJSONArray("results");

            for(int i=0;i<results.length();i++) {
                JSONObject _result = results.getJSONObject(i);
                JSONObject _compactVideoRenderer = _result.optJSONObject("compactVideoRenderer");
                if(_compactVideoRenderer != null) {
                    result.put(_compactVideoRenderer);
                }
                JSONObject itemSectionRenderer = _result.optJSONObject("itemSectionRenderer");
                if(itemSectionRenderer == null) {
                    continue;
                }
                JSONArray contents = itemSectionRenderer.optJSONArray("contents");
                if(contents == null) {
                    continue;
                }

                for(int j=0;j<contents.length();j++) {
                    JSONObject content = contents.optJSONObject(j);
                    JSONObject compactVideoRenderer = content
                            .optJSONObject("compactVideoRenderer");
                    if(compactVideoRenderer == null) {
                        continue;
                    }
                    result.put(compactVideoRenderer);
                }
            }

        } catch(JSONException e) {
            e.printStackTrace();
            return null;
        }

        return result;
    }

    public static JSONObject getInitialDataFromResponse(@NonNull final String response) {
        final List<String> matches = Utils.match(response, "ytInitialData = (.*)\\}\\}\\};");
        if (matches == null) {
            return null;
        }

        try {
            return new JSONObject(Io.concat(matches.get(1), "}}}"));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void fetchSearchResults(@NonNull final Consumer<JSONArray> onThen, final Runnable _onError) {
        Runnable onError = maybeOnError(_onError);
        fetch("https://www.youtube.com", (bytes) -> {
            final String response = Req.utf8(bytes);

            final JSONObject initialData = getInitialDataFromResponse(response);
            if(initialData == null) {
                onError.run();
                return;
            }

            final JSONArray results = new JSONArray();

            try {
                JSONArray contents = initialData
                        .getJSONObject("contents")
                        .getJSONObject("twoColumnBrowseResultsRenderer")
                        .getJSONArray("tabs")
                        .getJSONObject(0)
                        .getJSONObject("tabRenderer")
                        .getJSONObject("content")
                        .getJSONObject("richGridRenderer")
                        .getJSONArray("contents");

                for (int i = 0; i < contents.length(); i++) {
                    JSONObject result = contents.getJSONObject(i);
                    JSONObject richSectionRenderer = result.optJSONObject("richSectionRenderer");
                    if(richSectionRenderer != null) {
                        JSONObject content = richSectionRenderer.optJSONObject("content");
                        if(content != null) {
                            JSONObject richShelfRenderer = content.optJSONObject("richShelfRenderer");
                            if(richShelfRenderer != null) {
                                JSONArray _contents = richShelfRenderer.optJSONArray("contents");
                                if(_contents != null) {
                                    for(int j=0;j<_contents.length();j++) {
                                        JSONObject __content = _contents.getJSONObject(j);
                                        JSONObject richItemRenderer = __content.optJSONObject("richItemRenderer");
                                        if(richItemRenderer == null) {
                                            continue;
                                        }

                                        JSONObject _content = richItemRenderer.optJSONObject("content");
                                        if(_content == null) {
                                            continue;
                                        }

                                        JSONObject videoRenderer = _content.optJSONObject("videoRenderer");
                                        if(videoRenderer == null) {
                                            continue;
                                        }
                                        results.put(videoRenderer);
                                    }
                                }
                            }
                        }
                    }

                    JSONObject richItemRenderer = result.optJSONObject("richItemRenderer");
                    if(richItemRenderer == null) {
                        continue;
                    }

                    JSONObject content = richItemRenderer.optJSONObject("content");
                    if(content == null) {
                        continue;
                    }

                    JSONObject videoRenderer = content.optJSONObject("videoRenderer");
                    if(videoRenderer == null) {
                        continue;
                    }

                    results.put(videoRenderer);
                }

            } catch (JSONException e) {
                e.printStackTrace();
                onError.run();
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                onThen.accept(results);
            }
        }, onError);
    }

    public static JSONObject getInitialPlayerResponseFromResponse(@NonNull final String response) {
        final List<String> matches = Utils.match(response, "ytInitialPlayerResponse = (.*)\\}\\}\\};");
        if(matches == null) {
            return null;
        }

        try {
            return new JSONObject(Io.concat(matches.get(1), "}}}"));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void fetchInitialPlayerResponse(@NonNull final String id, @NonNull final Consumer2<JSONObject, String> onThen, final Runnable _onError) {
        Runnable onError = maybeOnError(_onError);
        fetch(Io.concat("https://www.youtube.com/watch?v=", id), (bytes) -> {
            final String response = Req.utf8(bytes);
            final JSONObject json = getInitialPlayerResponseFromResponse(response);
            if(json == null) {
                onError.run();
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                onThen.accept(json, response);
            }
        }, onError);
    }

    public static void fetchVideoInfo(@NonNull final String id, @NonNull final Consumer<JSONObject> onThen, final Runnable _onError) {
        Runnable onError = maybeOnError(_onError);
        fetchInitialPlayerResponse(id, (initialPlayerResponse, response) -> {
            final JSONObject videoDetails = initialPlayerResponse.optJSONObject("videoDetails");
            if(videoDetails == null) {
                onError.run();
                return;
            }

            onThen.accept(videoDetails);
        }, onError);
    }

    public static void fetchFormatsAndVideoInfoFromResponse(@NonNull final String response, @NonNull final Consumer2<JSONArray, JSONObject> onThen, final Runnable _onError) {
        Runnable onError = maybeOnError(_onError);
        final JSONObject initialPlayerResponse = getInitialPlayerResponseFromResponse(response);
        if(initialPlayerResponse == null) {
            onError.run();
            return;
        }

        final JSONObject videoDetails = initialPlayerResponse.optJSONObject("videoDetails");
        if(videoDetails == null) {
            onError.run();
            return;
        }

        final JSONObject streamingData = initialPlayerResponse.optJSONObject("streamingData");
        if(streamingData == null) {
            onError.run();
            return;
        }

        try{
            if (videoDetails.has("isLive") && videoDetails.optBoolean("isLive")) {
                videoDetails.put("hlsManifestUrl", streamingData.optString("hlsManifestUrl"));
            }
            videoDetails.put("dashManifestUrl", streamingData.optString("dashManifestUrl"));
        } catch(final JSONException e) {
            e.printStackTrace();
            onError.run();
            return;
        }

        final Pair<JSONArray, Boolean> _formats = getFormatsFromInitialPlayerResponse(streamingData);
        if (_formats == null) {
            onError.run();
            return;
        }

        /////////////////////////////////////////////////////////////////////////////////

        final List<String> jsFileUrlMatches = Utils.match(response, "\\/s\\/player\\/[A-Za-z0-9]+\\/[A-Za-z0-9_.]+\\/[A-Za-z0-9_]+\\/base\\.js");
        if (jsFileUrlMatches == null) {
            onError.run();
            return;
        }

        if (!_formats.second) {
            onThen.accept(_formats.first, videoDetails);
            return;
        }

        fetch(Io.concat("https://www.youtube.com", jsFileUrlMatches.get(0)), (bytes) -> {
            final String jsFileContent = Req.utf8(bytes);

            final List<String> decodeFunctionMatches = Utils.match(jsFileContent, "function.*\\.split\\(\\\"\\\"\\).*\\.join\\(\\\"\\\"\\)\\}");
            if (decodeFunctionMatches == null) {
                onError.run();
                return;
            }

            String decodeFunction = decodeFunctionMatches.get(0);
            final List<String> varNameMatches = Utils.match(decodeFunction, "\\.split\\(\\\"\\\"\\);([a-zA-Z0-9]+)\\.");
            if (varNameMatches == null) {
                onError.run();
                return;
            }

            final List<String> varDeclaresMatches = Utils.match(jsFileContent, "(var " + varNameMatches.get(1) + "=\\{[\\s\\S]+\\}\\};)[a-zA-Z0-9]+\\.[a-zA-Z0-9]+\\.prototype");
            if (varDeclaresMatches == null) {
                onError.run();
                return;
            }

            Function<String, String> decoder = signatureCipher -> {
                Map<String, String> params = getQueryMap(signatureCipher);
                String url = Req.decode(params.get("url"));
                String signature = Req.decode(params.get("s"));

                String expr = Io.concat("\"use-strict\";",
                        varDeclaresMatches.get(1),
                        "(", decodeFunction, ")(\"", signature, "\");");

                String val = runScript(expr, engine);
                String script = Io.concat("encodeURIComponent(\"", val, "\")");

                return url + Io.concat("&sig=", runScript(script, engine));
            };

            final JSONArray formats = _formats.first;
            for (int i = 0; i < formats.length(); i++) {
                final JSONObject format = formats.optJSONObject(i);
                if (format == null) continue;
                final String signatureCipher = format.optString("signatureCipher");
                if (signatureCipher == null) continue;

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        format.put("url", decoder.apply(signatureCipher));
                    }
                    format.put("signatureCipher", null);
                } catch (JSONException e) {
                    e.printStackTrace();
                    onError.run();
                    return;
                }
            }

            onThen.accept(formats, videoDetails);

        }, onError);
    }

    public static void fetchFormatsAndVideoInfo(@NonNull final String id, @NonNull final Consumer2<JSONArray, JSONObject> onThen, final Runnable _onError) {
        Runnable onError = maybeOnError(_onError);
        fetchInitialPlayerResponse(id, (initialPlayerResponse, response) -> {
            final JSONObject videoDetails = initialPlayerResponse.optJSONObject("videoDetails");
            if(videoDetails == null) {
                onError.run();
                return;
            }

            final JSONObject streamingData = initialPlayerResponse.optJSONObject("streamingData");
            if(streamingData == null) {
                onError.run();
                return;
            }

            try{
                if (videoDetails.has("isLive") && videoDetails.optBoolean("isLive")) {
                    videoDetails.put("hlsManifestUrl", streamingData.optString("hlsManifestUrl"));
                }
                videoDetails.put("dashManifestUrl", streamingData.optString("dashManifestUrl"));
            } catch(final JSONException e) {
                e.printStackTrace();
                onError.run();
                return;
            }

            final Pair<JSONArray, Boolean> _formats = getFormatsFromInitialPlayerResponse(streamingData);
            if (_formats == null) {
                onError.run();
                return;
            }

            /////////////////////////////////////////////////////////////////////////////////

            final List<String> jsFileUrlMatches = Utils.match(response, "\\/s\\/player\\/[A-Za-z0-9]+\\/[A-Za-z0-9_.]+\\/[A-Za-z0-9_]+\\/base\\.js");
            if (jsFileUrlMatches == null) {
                onError.run();
                return;
            }

            if (!_formats.second) {
                onThen.accept(_formats.first, videoDetails);
                return;
            }

            fetch(Io.concat("https://www.youtube.com", jsFileUrlMatches.get(0)), (bytes) -> {
                final String jsFileContent = Req.utf8(bytes);

                final List<String> decodeFunctionMatches = Utils.match(jsFileContent, "function.*\\.split\\(\\\"\\\"\\).*\\.join\\(\\\"\\\"\\)\\}");
                if (decodeFunctionMatches == null) {
                    onError.run();
                    return;
                }

                String decodeFunction = decodeFunctionMatches.get(0);
                final List<String> varNameMatches = Utils.match(decodeFunction, "\\.split\\(\\\"\\\"\\);([a-zA-Z0-9]+)\\.");
                if (varNameMatches == null) {
                    onError.run();
                    return;
                }

                final List<String> varDeclaresMatches = Utils.match(jsFileContent, "(var " + varNameMatches.get(1) + "=\\{[\\s\\S]+\\}\\};)[a-zA-Z0-9]+\\.[a-zA-Z0-9]+\\.prototype");
                if (varDeclaresMatches == null) {
                    onError.run();
                    return;
                }

                Function<String, String> decoder = signatureCipher -> {
                    Map<String, String> params = getQueryMap(signatureCipher);
                    String url = Req.decode(params.get("url"));
                    String signature = Req.decode(params.get("s"));

                    String expr = Io.concat("\"use-strict\";",
                            varDeclaresMatches.get(1),
                            "(", decodeFunction, ")(\"", signature, "\");");

                    String val = runScript(expr, engine);
                    String script = Io.concat("encodeURIComponent(\"", val, "\")");

                    return url + Io.concat("&sig=", runScript(script, engine));
                };

                final JSONArray formats = _formats.first;
                for (int i = 0; i < formats.length(); i++) {
                    final JSONObject format = formats.optJSONObject(i);
                    if (format == null) continue;
                    final String signatureCipher = format.optString("signatureCipher");
                    if (signatureCipher == null) continue;

                    try {
                        format.put("url", decoder.apply(signatureCipher));
                        format.put("signatureCipher", null);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        onError.run();
                        return;
                    }
                }

                onThen.accept(formats, videoDetails);

            }, onError);
        }, onError);
    }

    private static class Async<T> extends AsyncTask<Supplier<T>, Void, T> {

        private final Consumer<T> onThen;

        public Async(final Consumer<T> onThen) {
            this.onThen = onThen;
        }

        @Override
        protected void onPostExecute(T result) {
            super.onPostExecute(result);

            if(onThen != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    onThen.accept(result);
                }
            }
        }

        @Override
        protected T doInBackground(Supplier<T>... suppliers) {
            if(suppliers == null || suppliers.length == 0) {
                return null;
            }
            Supplier<T> supplier = suppliers[0];

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                return supplier.get();
            }
            return null;
        }
    }

    private static class Request extends AsyncTask<String, Void, byte[]> {

        private final Consumer<byte[]> onThen;
        private final Runnable onError;

        public Request(final Consumer<byte[]> onThen, final Runnable onError) {
            this.onThen = onThen;
            this.onError = onError;
        }

        @Override
        protected void onPostExecute(byte[] result) {
            super.onPostExecute(result);

            if(result == null) {
                if(onError != null) {
                    onError.run();
                }
            } else {
                if(onThen != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        onThen.accept(result);
                    }
                }
            }
        }

        @Override
        protected byte[] doInBackground(String... urls) {
            if(urls == null || urls.length == 0) {
                return null;
            }

            final String url = urls[0];

            final Req.Result result;
            try{
                result = Req.get(url);
            } catch(IOException e) {
                e.printStackTrace();
                return null;
            }

            if(!result.ok) {
                Log.i("YOUTUBE.REQUEST", "Request failed with requestCode: "+String.valueOf(result.responseCode));
                return null;
            }

            return result.data;
        }
    }

    //UTIL
    public static boolean isVideoFormat(final JSONObject format) {
        if (format == null) return false;
        if (format.has("audioSampleRate")) return false;
        if (!format.has("width")) return false;
        return true;
    }

    public static boolean isAudioFormat(final JSONObject format) {
        if (format == null) return false;
        if (!format.has("audioSampleRate")) return false;
        if (format.has("width")) return false;
        return true;
    }

    public static String buildMpd(JSONArray formats, int lengthSeconds) throws JSONException {

        JSONObject best = null;
        int widthM = Integer.MIN_VALUE;

        for (int i = 0; i < formats.length(); i++) {
            JSONObject format = formats.getJSONObject(i);
            if (format.has("width")) {
                int _width = format.getInt("width");

                if (_width > widthM) {
                    best = format;
                    widthM = _width;
                }
            }
        }

        if (best == null) {
            return null;
        }

        StringBuilder builder = new StringBuilder();

        int lenHr = lengthSeconds / 3600; // 1
        int _lenSec = lengthSeconds - lenHr * 3600; // 210
        int lenMin = _lenSec / 60; // 3
        int lenSec = _lenSec - lenMin * 60; //

        String mediaPresentation = new StringBuilder("PT")
                .append(lenHr)
                .append("H")
                .append(lenMin)
                .append("M")
                .append(lenSec)
                .append(".00S").toString();

        //minBufferTime=\"PT1.500000S\"
        builder.append("<?xml version=\"1.0\"?>\n<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:sand=\"urn:mpeg:dash:schema:sand:2016\" xsi:schemaLocation=\"urn:mpeg:dash:schema:mpd:2011 DASH-MPD.xsd urn:mpeg:dash:schema:sand:2016 SAND-MPD.xsd\" minBufferTime=\"PT1.500000S\" type=\"static\" mediaPresentationDuration=\"")
                .append(mediaPresentation)
                .append("\" >\n");
        builder.append("\t<BaseURL></BaseURL>\n\n");
        builder.append("\t<Period id=\"\" start =\"0\" duration=\"")
                .append(mediaPresentation)
                .append("\">\n");

        //VIDEO2
        builder.append("\t\t<AdaptationSet segmentAlignment=\"true\" maxWidth=\"")
                .append(best.getInt("width"))
                .append("\" startsWithSAP=\"1\" maxHeight=\"")
                .append(best.getInt("height"))
                .append("\" maxFrameRate=\"")
                .append(best.getInt("fps"))
                .append("\" par=\"16:9\" subsegmentStartsWithSAP=\"1\" scanType=\"progressive\">\n");

        for (int i = 0; i < formats.length(); i++) {
            buildVideo(builder, formats.getJSONObject(i));
        }

        builder.append("\t\t</AdaptationSet>\n");
        //AUDIO

        builder.append("\t\t<AdaptationSet segmentAlignment=\"true\" startsWithSAP=\"1\"")
                .append(" scanType=\"progressive\" >\n");

        for (int i = 0; i < formats.length(); i++) {
            buildAudio(builder, formats.getJSONObject(i));
        }

        builder.append("\t\t</AdaptationSet>\n");

        builder.append("\t</Period>\n\n\n");

        builder.append("\t<Metrics metrics=\"BufferLevel\">\n")
                .append("\t\t<Reporting schemeIdUri=\"urn:mpeg:dash:sand:channel:2016\" value=\"channel-reporting\"/>\n")
                .append("\t\t<Range duration=\"PT5S\"/>\n")
                .append("\t</Metrics>\n")
                .append("\t<sand:Channel id=\"channel-reporting\" schemeIdUri=\"urn:mpeg:dash:sand:channel:http:2016\" endpoint=\"https://sand-http-test-dane.herokuapp.com/metrics\"/>\n");

        builder.append("</MPD>");

        return builder.toString().replaceAll("&", "&#38;");
    }

    private static void buildVideo(final StringBuilder builder, final JSONObject format) throws JSONException {
        if (!isVideoFormat(format)) return;

        String _mime = format.getString("mimeType");
        String[] parts = _mime.split(";");
        String mime = parts[0];
        String[] _codec = parts[1].trim().split("=");
        String codec = _codec[1].substring(1, _codec[1].length() - 1);
        int height = format.getInt("height");
        int width = format.getInt("width");
        int fps = format.getInt("fps");
        int bitRate = format.getInt("bitrate");
        int itag = format.getInt("itag");

        builder.append("\t\t\t<Representation id=\"")
                .append(itag)
                .append("\" mimeType=\"")
                .append(mime)
                .append("\" codecs=\"")
                .append(codec)
                .append("\" width=\"")
                .append(width)
                .append("\" height=\"")
                .append(height)
                .append("\" frameRate=\"")
                .append(fps)
                .append("\" maxPlayoutRate=\"1\" bandwidth=\"")
                .append(bitRate)
                .append("\">\n");

        builder.append("\t\t\t\t<BaseURL>")
                .append(format.getString("url"))
                .append("</BaseURL>\n");

        JSONObject indexRange = format.getJSONObject("indexRange");
        int indexRangeStart = indexRange.getInt("start");
        int indexRangeEnd = indexRange.getInt("end");

        builder.append("\t\t\t\t<SegmentBase indexRange=\"")
                .append(indexRangeStart)
                .append("-")
                .append(indexRangeEnd)
                .append("\">\n");
        JSONObject initRange = format.getJSONObject("initRange");
        int initRangeStart = initRange.getInt("start");
        int initRangeEnd = initRange.getInt("end");


        builder.append("\t\t\t\t\t<Initialization range=\"")
                .append(initRangeStart)
                .append("-")
                .append(initRangeEnd)
                .append("\"/>\n");

        builder.append("\t\t\t\t</SegmentBase>\n");

        builder.append("\t\t\t</Representation>\n");
    }

    private static void buildAudio(StringBuilder builder, JSONObject format) throws JSONException {
        if (!isAudioFormat(format)) return;

        int itag = format.getInt("itag");
        String _mime = format.getString("mimeType");
        String[] parts = _mime.split(";");
        String mime = parts[0];
        String[] _codec = parts[1].trim().split("=");
        String codec = _codec[1].substring(1, _codec[1].length() - 1);
        int audioSamplingRate = format.getInt("audioSampleRate");
        int bitRate = format.getInt("bitrate");

        //if(mime.indexOf("mp4")!=-1) return;

        builder.append("\t\t\t<Representation id=\"")
                .append(itag)
                .append("\" mimeType=\"")
                .append(mime)
                .append("\" codecs=\"")
                .append(codec)
                .append("\" audioSamplingRate=\"")
                .append(audioSamplingRate)
                .append("\" bandwidth=\"")
                .append(bitRate)
                .append("\" maxPlayoutRate=\"1\">\n");

        builder.append("\t\t\t\t<AudioChannelConfiguration schemeIdUri=\"urn:mpeg:dash:23003:3:audio_channel_configuration:2011\" value=\"2\"/>\n");

        builder.append("\t\t\t\t<BaseURL>")
                .append(format.getString("url"))
                .append("</BaseURL>\n");

        JSONObject indexRange = format.getJSONObject("indexRange");
        int indexRangeStart = indexRange.getInt("start");
        int indexRangeEnd = indexRange.getInt("end");

        builder.append("\t\t\t\t<SegmentBase indexRange=\"")
                .append(indexRangeStart)
                .append("-")
                .append(indexRangeEnd)
                .append("\">\n");

        JSONObject initRange = format.getJSONObject("initRange");
        int initRangeStart = initRange.getInt("start");
        int initRangeEnd = initRange.getInt("end");

        builder.append("\t\t\t\t\t<Initialization range=\"")
                .append(initRangeStart)
                .append("-")
                .append(initRangeEnd)
                .append("\"/>\n");

        builder.append("\t\t\t\t</SegmentBase>\n");

        builder.append("\t\t\t</Representation>\n");
    }
}
