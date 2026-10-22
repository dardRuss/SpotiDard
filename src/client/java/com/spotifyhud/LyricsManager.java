package com.spotifyhud;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LyricsManager {
	public static final LyricsManager INSTANCE = new LyricsManager();

	private static class CachedLyrics {
		final long[] times;
		final String[] lines;

		CachedLyrics(long[] times, String[] lines) {
			this.times = times;
			this.lines = lines;
		}
	}

	private static final Logger LOGGER = LoggerFactory.getLogger("spotifyhud");
	private static final Gson GSON = new Gson();
	private static final String API_GET = "https://lrclib.net/api/get?";
	private static final String API_SEARCH = "https://lrclib.net/api/search?q=";
	private static final String USER_AGENT = "SpotifyPlayer/1.0 (Minecraft mod)";
	private static final Pattern LRC_TAG = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2}(?:\\.\\d{1,3})?)\\]");

	private final ConcurrentHashMap<String, CachedLyrics> cache = new ConcurrentHashMap<>();
	private volatile String pendingKey = "";

	public void ensureLoaded(TrackState track) {
		String key = !track.trackId.isEmpty() ? track.trackId : track.title + "|" + track.artists;
		if (key.equals(pendingKey) || cache.containsKey(key)) {
			return;
		}
		pendingKey = key;
		String fetchKey = key;
		String title = track.title;
		String artist = firstArtist(track.artists);
		String album = track.albumName;
		long durationSec = track.durationMs / 1000L;
		Thread t = new Thread(() -> {
			try {
				CachedLyrics lyrics = fetch(title, artist, album, durationSec);
				if (lyrics != null) {
					shrinkCache();
					cache.put(fetchKey, lyrics);
				}
				if (pendingKey.equals(fetchKey)) {
					pendingKey = "";
				}
			} catch (Throwable e) {
				LOGGER.warn("Failed to load lyrics for {}", title, e);
				if (pendingKey.equals(fetchKey)) {
					pendingKey = "";
				}
			}
		}, "SpotifyPlayer-Lyrics");
		t.setDaemon(true);
		t.start();
	}

	public String currentLine(TrackState track, long progressMs) {
		if (track == null || !Config.INSTANCE.showLyrics) {
			return "";
		}
		String key = !track.trackId.isEmpty() ? track.trackId : track.title + "|" + track.artists;
		CachedLyrics lyrics = cache.get(key);
		if (lyrics == null || lyrics.times.length == 0) {
			return "";
		}
		long[] times = lyrics.times;
		if (times[0] > progressMs) {
			return "";
		}
		int idx = 0;
		while (idx + 1 < times.length && times[idx + 1] <= progressMs) {
			idx++;
		}
		return lyrics.lines[idx];
	}

	private static String firstArtist(String artists) {
		if (artists == null || artists.isEmpty()) {
			return "";
		}
		int idx = artists.indexOf(',');
		return idx > 0 ? artists.substring(0, idx).trim() : artists.trim();
	}

	private CachedLyrics fetch(String title, String artist, String album, long durationSec) {
		JsonObject found = null;
		if (!title.isEmpty()) {
			StringBuilder url = new StringBuilder(API_GET);
			url.append("track_name=").append(enc(title));
			if (!artist.isEmpty()) {
				url.append("&artist_name=").append(enc(artist));
			}
			if (!album.isEmpty()) {
				url.append("&album_name=").append(enc(album));
			}
			if (durationSec > 0) {
				url.append("&duration=").append(durationSec);
			}
			found = requestJson(url.toString());
		}
		if (found == null && !title.isEmpty()) {
			JsonArray results = requestArray(API_SEARCH + enc(artist + " " + title));
			if (results != null) {
				for (int i = 0; i < results.size(); i++) {
					JsonElement el = results.get(i);
					if (!el.isJsonObject()) {
						continue;
					}
					JsonObject obj = el.getAsJsonObject();
					String text = stringOf(obj, "syncedLyrics");
					if (text == null || text.isEmpty()) {
						continue;
					}
					long dur = numberLong(obj, "duration", 0L);
					if (durationSec > 0 && dur > 0 && Math.abs(dur - durationSec) > 3) {
						continue;
					}
					found = obj;
					break;
				}
			}
		}
		if (found == null) {
			return null;
		}
		String synced = stringOf(found, "syncedLyrics");
		if (synced == null || synced.isEmpty()) {
			return null;
		}
		return parseLrc(synced);
	}

	private CachedLyrics parseLrc(String synced) {
		String[] rawLines = synced.split("\\r?\\n");
		java.util.ArrayList<Long> timesList = new java.util.ArrayList<>();
		java.util.ArrayList<String> linesList = new java.util.ArrayList<>();
		for (String raw : rawLines) {
			String rest = raw;
			java.util.ArrayList<Long> stamps = new java.util.ArrayList<>();
			while (rest.startsWith("[")) {
				Matcher m = LRC_TAG.matcher(rest);
				if (!m.lookingAt()) {
					break;
				}
				stamps.add(tagMs(m));
				rest = rest.substring(m.end());
			}
			if (stamps.isEmpty()) {
				continue;
			}
			String text = rest.trim();
			if (text.isEmpty()) {
				continue;
			}
			for (long ms : stamps) {
				timesList.add(ms);
				linesList.add(text);
			}
		}
		if (timesList.isEmpty()) {
			return null;
		}
		int n = timesList.size();
		Long[] timesObj = timesList.toArray(new Long[0]);
		for (int i = 0; i < n; i++) {
			for (int j = i + 1; j < n; j++) {
				if (timesObj[j] < timesObj[i]) {
					Long t = timesObj[i];
					timesObj[i] = timesObj[j];
					timesObj[j] = t;
					String l = linesList.get(i);
					linesList.set(i, linesList.get(j));
					linesList.set(j, l);
				}
			}
		}
		long[] times = new long[n];
		String[] lines = new String[n];
		for (int i = 0; i < n; i++) {
			times[i] = timesObj[i];
			lines[i] = linesList.get(i);
		}
		for (int i = 1; i < times.length; i++) {
			if (times[i] <= times[i - 1]) {
				times[i] = times[i - 1] + 1;
			}
		}
		return new CachedLyrics(times, lines);
	}

	private static long tagMs(Matcher m) {
		return Long.parseLong(m.group(1)) * 60_000L + Math.round(Double.parseDouble(m.group(2)) * 1000.0);
	}

	private void shrinkCache() {
		if (cache.size() < 24) {
			return;
		}
		for (String k : cache.keySet()) {
			if (cache.size() <= 12) {
				break;
			}
			if (!k.equals(pendingKey)) {
				cache.remove(k);
			}
		}
	}

	private static String enc(String s) {
		return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
	}

	private static JsonObject requestJson(String url) {
		String body = request(url);
		if (body == null || body.isEmpty()) {
			return null;
		}
		try {
			return GSON.fromJson(body, JsonObject.class);
		} catch (Exception ignored) {
			return null;
		}
	}

	private static JsonArray requestArray(String url) {
		String body = request(url);
		if (body == null || body.isEmpty()) {
			return null;
		}
		try {
			return GSON.fromJson(body, JsonArray.class);
		} catch (Exception ignored) {
			return null;
		}
	}

	private static String request(String url) {
		try {
			HttpURLConnection conn = (HttpURLConnection) new java.net.URI(url).toURL().openConnection();
			conn.setConnectTimeout(5000);
			conn.setReadTimeout(8000);
			conn.setRequestProperty("User-Agent", USER_AGENT);
			conn.setRequestProperty("Accept", "application/json");
			if (conn.getResponseCode() != 200) {
				return null;
			}
			try (InputStream in = conn.getInputStream()) {
				return new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}
		} catch (Exception ignored) {
			return null;
		}
	}

	private static String stringOf(JsonObject obj, String key) {
		JsonElement el = obj.get(key);
		return el != null && el.isJsonPrimitive() ? el.getAsString() : "";
	}

	private static long numberLong(JsonObject obj, String key, long def) {
		JsonElement el = obj.get(key);
		if (el == null || !el.isJsonPrimitive()) {
			return def;
		}
		try {
			return Long.parseLong(el.getAsString().replace(".0", ""));
		} catch (NumberFormatException e) {
			return def;
		}
	}
}
