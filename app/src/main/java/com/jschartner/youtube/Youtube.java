package com.jschartner.youtube;

import static js.Io.concat;

import android.media.MediaMetadata;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import js.Req;

public class Youtube {
    private static long engine = 0;

    private static String lastId = null;
    private static String lastResponse = null;
    private static JSONObject lastPlayerResponse = null;

    public static void resetCache() {
        lastId = null;
        lastResponse = null;
        lastPlayerResponse = null;
    }

    @Override
    protected void finalize() {
        close();
    }

    private static String getOrNull(String url) {
        final String[] _response = {""};

        Thread thread = new Thread(() -> {
		try {
		    final Req.Result result = Req.get(url);
		    _response[0] = result.ok ? Req.utf8(result.data) : null;
		} catch (Exception e) {
		    _response[0] = null;
		    e.printStackTrace();
		}
        });

        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
            _response[0] = null;
        }

        return _response[0];
    }

    public static JSONArray search() {
        final String api = "https://www.youtube.com";

        final String response = getOrNull(api);
        final JSONObject ytInitialData = getInitialPlayerData(response);
        if(ytInitialData == null) {
            return null;
        }

        JSONArray results = new JSONArray();

        try {
            JSONArray contents = ytInitialData
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
            return null;
        }


        return results;
    }

    public static JSONArray search(final String word) {
        if (word == null) return search();
        final String api = "https://www.youtube.com/results?search_query=";

        final String response = getOrNull(concat(api, Req.encode(word)));
        final JSONObject ytInitialData = getInitialPlayerData(response);
        if(ytInitialData == null) {
            return null;
        }

        JSONArray results = new JSONArray();

        try {
            JSONArray _contents = ytInitialData
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
            return null;
        }

        return results;
    }

    public static String getNextVideo(final String id) {
        final String api = "https://www.youtube.com/watch?v=";
        if(id == null) {
            return null;
        }

        final String response;
        if(id.equals(lastId)) {
            response = lastResponse;
        } else {
            response = getOrNull(concat(api, id));
            lastId = id;
            lastResponse = response;
            lastPlayerResponse = null;
        }

        final JSONObject ytInitialData = getInitialPlayerData2(response);

        try{
            return ytInitialData.getJSONObject("contents")
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

    public static JSONArray getRecommendedVideos(final String id) {
        final String api = "https://www.youtube.com/watch?v=";

        if(id == null) {
            return null;
        }

        final String response;
        if(id.equals(lastId)) {
            response = lastResponse;
        } else {
            response = getOrNull(concat(api, id));
            lastId = id;
            lastResponse = response;
            lastPlayerResponse = null;
        }

        final JSONObject ytInitialData = getInitialPlayerData2(response);
        final JSONArray result = new JSONArray();	

        try{
            //TODO: add autoplay
            //ytInitialData.getJSONObject("contents").getJSONObject("twoColumnWatchNextResults").getJSONObject("autoplay")

            final JSONArray results = ytInitialData
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

    private static void init() {
        if (engine == 0) {
            System.loadLibrary("duktape-lib");

            engine = initEngine();
        }
    }

    public static void close() {
        if (engine != 0) {
            closeEngine(engine);
            engine = 0;
        }
    }

    public static final MediaBrowserCompat.MediaItem toMediaItem(final String id) {
        if(id == null) {
            return null;
        }

        final JSONObject videoInfo = getInfo(id);
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

    private static final native long initEngine();

    private static final synchronized native String runScript(String code, long pointer);

    private static final native void closeEngine(long pointer);

    private static JSONObject getOb(final JSONObject json, final String key) {
        try {
            return json.getJSONObject(key);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static JSONArray getAr(final JSONObject json, final String key) {
        try {
            return json.getJSONArray(key);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static JSONArray concatJson(final JSONArray arr1, final JSONArray arr2) {
        JSONArray res = new JSONArray();

        for (int i = 0; i < arr1.length(); i++) {
            res.put(arr1.opt(i));
        }

        for (int i = 0; i < arr2.length(); i++) {
            res.put(arr2.opt(i));
        }

        return res;
    }

    private static List<String> match(final String response, final String pattern) {
        Matcher matcher =
	    Pattern.compile(pattern).matcher(response);

        List<String> matches = new ArrayList<>();
        while (matcher.find()) {
            for (int i = 0; i <= matcher.groupCount(); i++) {
                matches.add(matcher.group(i));
            }
        }

        return matches.size() > 0 ? matches : null;
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

    static class Pair<T, V> {
        final T first;
        final V second;

        public Pair(T first, V second) {
            this.first = first;
            this.second = second;
        }
    }

    private static JSONObject getInitialPlayerData2(final String response) {
	List<String> matches = match(response, "ytInitialData = (.*)\\}\\}\\};");

        if (matches == null) {
            return null;
        }

        JSONObject json;
        try {
            json = new JSONObject(concat(matches.get(1), "}}}"));
        } catch (Exception e) {
            json = null;
        }

	return json;
    }

    private static JSONObject getInitialPlayerData(final String response) {
        if(response == null) {
            return null;
        }

        int pos = response.indexOf("ytInitialData = ");
        if (pos == -1) {
            return null;
        }

        int _pos = pos + "ytInitialData = ".length();

        int __pos = response.indexOf("</script>", _pos);
        if(__pos < 0) {
            return null;
        }

        try {
            return new JSONObject(response.substring(_pos, __pos));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static Pair<JSONObject, String> getInitialPlayerResponse(final String id) {
        //GET RESPONSE
        if (id == null) {
            return null;
        }

        if (id.equals(lastId) && lastPlayerResponse != null) {
            return new Pair<>(lastPlayerResponse, lastResponse);
        }

        String response = requestApi(id);
        if (response == null) {
            return null;
        }

        List<String> matches = match(response, "ytInitialPlayerResponse = (.*)\\}\\}\\};");

        if (matches == null) {
            return null;
        }

        String match = matches.get(1);
        String playerResponse = concat(match, "}}}");

        JSONObject json;
        try {
            json = new JSONObject(playerResponse);
        } catch (Exception e) {
            json = null;
        }

        lastId = id;
        lastResponse = response;
        lastPlayerResponse = json;
        return new Pair<>(json, response);
    }

    private static String requestApi(final String id) {
        String api = "https://www.youtube.com/watch?v=";
        return getOrNull(concat(api, id));
    }

    private static Function<String, String> buildDecoder(final String response) {
        if (response == null) {
            return null;
        }

        List<String> jsFileUrlMatches = match(response, "\\/s\\/player\\/[A-Za-z0-9]+\\/[A-Za-z0-9_.]+\\/[A-Za-z0-9_]+\\/base\\.js");

        if (jsFileUrlMatches == null) {
            return null;
        }

        String jsFileContent = getOrNull("https://www.youtube.com" + jsFileUrlMatches.get(0));
        if (jsFileContent == null) {
            return null;
        }

        List<String> decodeFunctionMatches = match(jsFileContent, "function.*\\.split\\(\\\"\\\"\\).*\\.join\\(\\\"\\\"\\)\\}");

        if (decodeFunctionMatches == null) {
            return null;
        }

        String decodeFunction = decodeFunctionMatches.get(0);

        List<String> varNameMatches = match(decodeFunction, "\\.split\\(\\\"\\\"\\);([a-zA-Z0-9]+)\\.");

        if (varNameMatches == null) {
            return null;
        }

        List<String> varDeclaresMatches = match(jsFileContent, "(var " + varNameMatches.get(1) + "=\\{[\\s\\S]+\\}\\};)[a-zA-Z0-9]+\\.[a-zA-Z0-9]+\\.prototype");

        if (varDeclaresMatches == null) {
            return null;
        }

        if (engine == 0) init();
        return signatureCipher -> {

            Map<String, String> params = getQueryMap(signatureCipher);
            String url = Req.decode(params.get("url"));
            String signature = Req.decode(params.get("s"));

            String expr = concat("\"use-strict\";",
				 varDeclaresMatches.get(1),
				 "(", decodeFunction, ")(\"", signature, "\");");

            String val = runScript(expr, engine);
            String script = concat("encodeURIComponent(\"", val, "\")");

            return url + concat("&sig=", runScript(script, engine));
        };
    }

    private static boolean decryptFormats(final JSONArray formats, final String response) throws JSONException {
        if (formats == null) return false;
        Function<String, String> decoder = null;

        for (int i = 0; i < formats.length(); i++) {
            JSONObject format = formats.optJSONObject(i);
            if (format == null) continue;
            if (!format.has("signatureCipher")) continue;

            if (decoder == null) {
                decoder = buildDecoder(response);
                if (decoder == null) {
                    return false;
                }
            }

            String signatureCipher = format.optString("signatureCipher");
            String url;


            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                url = decoder.apply(signatureCipher);
            } else {
                return false;
            }

            format.put("url", url);
        }

        return true;
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

    private static String buildMpd(JSONArray formats, int lengthSeconds) throws JSONException {

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

    public static JSONArray getFormats(final String id) {
        Pair<JSONObject, String> result = getInitialPlayerResponse(id);
        if (result.first == null) return null;

        JSONObject streamingData = getOb(result.first, "streamingData");
        JSONArray formats = concatJson(getAr(streamingData, "formats"), getAr(streamingData, "adaptiveFormats"));

        //ENCRYPT
        try {
            if (!decryptFormats(formats, result.second)) {
                return null;
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }

        List<Integer> toBeRemoved = new ArrayList<>();

        for(int i=0;i<formats.length();i++) {
            final JSONObject format = formats.optJSONObject(i);
            if(format == null) continue;
            final String url = format.optString("url");
            if(url == null) continue;
            long len = -1;
            try {
                final Req.Result response = Utils.onThread(Req.builder(url, "HEAD"));
                len = response.ok ? response.len : -1;
            }
            catch(IOException e) {
                e.printStackTrace();
            }

            if(len == -1) {
                toBeRemoved.add(i);
            }

            try {
                format.put("lengthInBytes", len);
            } catch(JSONException e) {
                e.printStackTrace();
            }
        }

        for(int i=0;i<toBeRemoved.size();i++) {
            formats.remove(toBeRemoved.get(i));
        }

        return formats;
    }

    public static String getTitle(final String id) {
        final JSONObject info = getInfo(id);
        if(info == null) return null;
        final String title = info.optString("title");
        if(title == null || title.length() == 0) return null;
        return title
	    .replaceAll("[^a-zA-Z0-9-_\\.]", "_");
    }

    //[fileTitle, filePath]
    public static String[] getNames(final JSONObject format, final String title, final String prefix) {
        if(format == null) return null;
        if(title == null || title.length() == 0) return null;

        final String suffix = getSuffix(format);
        if(suffix == null) return null;
        return new String[]{concat(title, suffix), concat(prefix, "/", title, suffix)};
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

        return concat(video ? "_video" : "", ".",
		      video
		      ? (("webm".equals(mime)) ? "webm" : "mp4")
		      : (("webm".equals(mime)) ? "webm" : "m4a"));
    }

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

    public static JSONArray getAudioFormats(final String id) {
        JSONArray formats = getFormats(id);
        if (formats == null) return null;

        JSONArray result = new JSONArray();
        for (int i = 0; i < formats.length(); i++) {
            JSONObject format = formats.optJSONObject(i);
            if (isAudioFormat(format)) result.put(format);
        }
        return result;
    }

    public static JSONObject getInfo(final String id) {
        Pair<JSONObject, String> result = getInitialPlayerResponse(id);
        if (result.first == null) return null;

        try {
            return result.first.getJSONObject("videoDetails");
        } catch (JSONException e) {
            return null;
        }
    }

    public static String getM3u8(final String id) {
        Pair<JSONObject, String> result = getInitialPlayerResponse(id);
        JSONObject initialPlayerResponse = result.first;
        if (initialPlayerResponse == null) {
            return null;
        }

        JSONObject videoDetails = getOb(initialPlayerResponse, "videoDetails");
        if (videoDetails.has("isLive") && videoDetails.optBoolean("isLive")) {
            List<String> m3u8Link = match(result.second, "hlsManifestUrl\":\"(.*\\/file\\/index\\.m3u8)");

            if (m3u8Link != null) {
                return m3u8Link.get(1);
            }
        }

        return null;
    }

    public static String getDash(final String id) {
        Pair<JSONObject, String> result = getInitialPlayerResponse(id);
        JSONObject initialPlayerResponse = result.first;
        if (initialPlayerResponse == null) {
            return null;
        }

        JSONObject streamingData = getOb(initialPlayerResponse, "streamingData");

        if (streamingData.has("dashManifestUrl")) {
            Object dashLink = streamingData.opt("dashManifestUrl");
            if (dashLink != null && dashLink instanceof String) {
                return (String) dashLink;
            }
        }

        return null;
    }

    public static String buildDashSource(final String id) {
	/*
	  String id = getQueryMap(url.split("\\?")[1]).get("v");
	*/
        Pair<JSONObject, String> result = getInitialPlayerResponse(id);
        JSONObject initialPlayerResponse = result.first;
        if (initialPlayerResponse == null) {
            return null;
        }

        JSONObject streamingData = getOb(initialPlayerResponse, "streamingData");
        JSONArray formats = concatJson(getAr(streamingData, "formats"), getAr(streamingData, "adaptiveFormats"));
        JSONObject videoDetails = getOb(initialPlayerResponse, "videoDetails");

        if (formats.length() == 0) {
            return null;
        }

        if (!videoDetails.has("lengthSeconds")) {
            return null;
        }

        //ENCRYPT
        try {
            if (!decryptFormats(formats, result.second)) {
                return null;
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }

        String mpd;
        try {
            mpd = buildMpd(formats, videoDetails.getInt("lengthSeconds"));
        } catch (JSONException e) {
            e.printStackTrace();
            mpd = null;
        }

        return mpd;
    }

    public static Iterator<String> allSources(String id) {
        return new Iterator<String>() {
            int pos = 0;

            @Override
            public boolean hasNext() {
                return pos < 3;
            }

            @Override
            public String next() {
                String out = null;
                if (pos == 0) {
                    out = buildDashSource(id);
                } else if (pos == 1) {
                    out = getM3u8(id);
                } else if (pos == 2) {
                    out = getDash(id);
                }
                pos++;

                return out;
            }
        };
    }
}
