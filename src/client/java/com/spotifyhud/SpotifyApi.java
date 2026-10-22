package com.spotifyhud;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class SpotifyApi {
	public static final SpotifyApi INSTANCE = new SpotifyApi();
	private static final Gson GSON = new Gson();
	private static final String API = "https://api.spotify.com/v1/me/player";
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(8))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	private final Object lock = new Object();
	private volatile TrackState current;
	public volatile String lastError = "";
	public volatile Object[] lastErrorArgs = new Object[0];
	private long lastErrorAt = 0L;
	private volatile boolean running;
	private volatile boolean immediatePoll;
	private Thread pollThread;
	private Thread actionThread;

	public interface ApiCall {
		int call() throws Exception;
	}

	public TrackState currentOrNull() {
		return current;
	}

	public void start() {
		synchronized (lock) {
			if (running) {
				return;
			}
			running = true;
			pollThread = new Thread(this::pollLoop, "SpotifyPlayer-Poll");
			pollThread.setDaemon(true);
			actionThread = new Thread(this::actionLoop, "SpotifyPlayer-Actions");
			actionThread.setDaemon(true);
			pollThread.start();
			actionThread.start();
		}
	}

	public void stop() {
		running = false;
		if (pollThread != null) {
			pollThread.interrupt();
		}
		if (actionThread != null) {
			actionThread.interrupt();
		}
	}

	public void pollNow() {
		immediatePoll = true;
	}

	public static void resetAndPollNow() {
		INSTANCE.current = null;
		INSTANCE.pollNow();
	}

	public static String formatTime(long ms) {
		long totalSeconds = Math.max(0, ms) / 1000;
		return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
	}

	private void info(String message) {
		lastError = message;
		lastErrorArgs = new Object[0];
		lastErrorAt = System.currentTimeMillis();
	}

	private void info(String key, Object... args) {
		lastError = key;
		lastErrorArgs = args;
		lastErrorAt = System.currentTimeMillis();
	}

	public static Component errorComponent(String key, Object[] args) {
		return key.startsWith("spotifyhud.") ? Component.translatable(key, args) : Component.literal(key);
	}

	private void pollLoop() {
		while (running) {
			int intervalMs = Math.max(1000, Config.INSTANCE.pollIntervalMs);
			boolean immediate = immediatePoll;
			immediatePoll = false;
			pollOnce();
			long sleep = immediate ? 500 : intervalMs;
			try {
				Thread.sleep(sleep);
			} catch (InterruptedException e) {
				return;
			}
		}
	}

	private void pollOnce() {
		if (Config.INSTANCE.refreshToken.isBlank() || Config.INSTANCE.clientId.isBlank()) {
			current = null;
			info("");
			return;
		}
		try {
			if (Config.INSTANCE.tokenExpiresAt <= System.currentTimeMillis() || Config.INSTANCE.accessToken.isBlank()) {
				if (!AuthServer.refreshToken()) {
					AuthServer.clearTokens();
					info("spotifyhud.api.session_expired");
					current = null;
					return;
				}
			}
			HttpResponse<byte[]> response = HTTP.send(authedGet(API + "?additional_types=track"), HttpResponse.BodyHandlers.ofByteArray());
			handlePlayerResponse(response.statusCode(), response.body() == null ? new byte[0] : response.body());
		} catch (InterruptedException e) {
			return;
		} catch (IOException e) {
			if (lastErrorAt + 30000L < System.currentTimeMillis()) {
				info("spotifyhud.api.connection_fail");
			}
		} catch (Exception e) {
			info("spotifyhud.api.api_error", e.getMessage());
		}
	}

	private void handlePlayerResponse(int status, byte[] body) throws Exception {
		if (status == 204 || body.length == 0) {
			current = null;
			info("");
			return;
		}
		if (status == 401) {
			if (AuthServer.refreshToken()) {
				return;
			}
			AuthServer.clearTokens();
			info("spotifyhud.api.session_expired");
			current = null;
			return;
		}
		if (status == 403) {
			info("spotifyhud.api.premium_scope");
			return;
		}
		if (status == 429) {
			info("spotifyhud.api.rate_limited");
			return;
		}
		if (status != 200) {
			info("spotifyhud.api.http_error", status);
			return;
		}
		JsonObject json = GSON.fromJson(new String(body, StandardCharsets.UTF_8), JsonObject.class);
		if (json == null) {
			return;
		}
		TrackState state = new TrackState();
		state.progressMs = Number(json, "progress_ms", 0L);
		state.playing = bool(json, "is_playing", false);
		state.shuffle = bool(json, "shuffle_state", false);
		state.repeat = string(json, "repeat_state", "off");
		state.deviceId = string(json, "device", "__id", "");
		JsonObject device = obj(json, "device");
		if (device != null) {
			state.volumePercent = Number(device, "volume_percent", -1L);
			if (state.deviceId.isEmpty()) {
				state.deviceId = string(device, "id", "");
			}
		}
		JsonObject item = obj(json, "item");
		if (item == null) {
			current = null;
			info("");
			return;
		}
		state.title = string(item, "name", "");
		state.trackId = string(item, "id", "");
		state.durationMs = Number(item, "duration_ms", 0L);
		JsonArray artists = arr(item, "artists");
		StringBuilder names = new StringBuilder();
		if (artists != null) {
			for (int i = 0; i < artists.size(); i++) {
				JsonObject a = artists.get(i).getAsJsonObject();
				if (names.length() > 0) {
					names.append(", ");
				}
				names.append(string(a, "name", "?"));
			}
		}
		state.artists = names.toString();
		JsonObject album = obj(item, "album");
		if (album != null) {
			state.albumName = string(album, "name", "");
			JsonArray images = arr(album, "images");
			String bestUrl = "";
			int bestWidth = Integer.MAX_VALUE;
			if (images != null) {
				for (int i = 0; i < images.size(); i++) {
					JsonObject img = images.get(i).getAsJsonObject();
					int w = (int) Number(img, "width", 640L);
					if (w >= 64 && w < bestWidth) {
						bestWidth = w;
						bestUrl = string(img, "url", "");
					}
				}
			}
			state.coverUrl = bestUrl;
		}
		state.capturedAtMillis = System.currentTimeMillis();
		info("");
		current = state;
		AlbumArtManager.ensureLoaded(state.coverUrl);
		LyricsManager.INSTANCE.ensureLoaded(state);
	}

	private static HttpRequest authedGet(String url) {
		return HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(10))
				.header("Authorization", "Bearer " + Config.INSTANCE.accessToken)
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.build();
	}

	private static HttpRequest authedPut(String url, String body) {
		return HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(10))
				.header("Authorization", "Bearer " + Config.INSTANCE.accessToken)
				.header("Content-Type", "application/json")
				.PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
				.build();
	}

	private static HttpRequest authedPost(String url) {
		return HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(10))
				.header("Authorization", "Bearer " + Config.INSTANCE.accessToken)
				.POST(HttpRequest.BodyPublishers.noBody())
				.build();
	}

	private int send(HttpRequest request) throws Exception {
		HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
		return response.statusCode();
	}

	public void togglePlay() {
		TrackState s = current;
		submitAction(() -> {
			if (s == null) {
				info("spotifyhud.api.not_playing");
				return 0;
			}
			String path = s.playing ? "/pause" : "/play";
			String qs = s.deviceId.isEmpty() ? "" : "?device_id=" + url(s.deviceId);
			return send(authedPut(API + path + qs, ""));
		});
	}

	public void next() {
		submitAction(() -> send(authedPost(API + "/next")));
	}

	public void previous() {
		submitAction(() -> send(authedPost(API + "/previous")));
	}

	public void seek(long ms) {
		submitAction(() -> send(authedPut(API + "/seek?position_ms=" + Math.max(0, ms), "")));
	}

	public void setVolume(long percent) {
		long clamped = Math.max(0, Math.min(100, percent));
		submitAction(() -> send(authedPut(API + "/volume?volume_percent=" + clamped, "")));
	}

	public void adjustVolume(int delta) {
		TrackState s = current;
		long base = (s != null && s.volumePercent >= 0) ? s.volumePercent : 50L;
		base = Math.max(0, Math.min(100, base + delta));
		if (s != null) {
			s.volumePercent = base;
			s.capturedAtMillis = System.currentTimeMillis();
		}
		setVolume(base);
	}

	public void toggleShuffle() {
		TrackState s = current;
		boolean target = !(s != null && s.shuffle);
		submitAction(() -> send(authedPut(API + "/shuffle?state=" + target, "")));
	}

	public void cycleRepeat() {
		TrackState s = current;
		String cur = s != null ? s.repeat : "off";
		String next = switch (cur) {
			case "off" -> "track";
			case "track" -> "context";
			default -> "off";
		};
		submitAction(() -> send(authedPut(API + "/repeat?state=" + next, "")));
	}

	private void submitAction(ApiCall call) {
		synchronized (actionQueueLock) {
			actionQueue[0] = call;
			actionQueueFilled = true;
			actionQueueLock.notifyAll();
		}
	}

	private final Object actionQueueLock = new Object();
	private final ApiCall[] actionQueue = new ApiCall[8];
	private volatile boolean actionQueueFilled;

	private void actionLoop() {
		while (running) {
			ApiCall call;
			synchronized (actionQueueLock) {
				while (!actionQueueFilled && running) {
					try {
						actionQueueLock.wait(500L);
					} catch (InterruptedException e) {
						return;
					}
				}
				if (!running) {
					return;
				}
				call = actionQueue[0];
				actionQueue[0] = null;
				actionQueueFilled = false;
			}
			if (call != null) {
				int code = -1;
				try {
					code = call.call();
				} catch (InterruptedException e) {
					return;
				} catch (Exception e) {
					info("spotifyhud.api.action_error", e.getMessage());
				}
				if (code == 401) {
					try {
						if (AuthServer.refreshToken()) {
							try {
								code = call.call();
							} catch (Exception e) {
								info("spotifyhud.api.action_error", e.getMessage());
							}
						} else {
							info("spotifyhud.api.session_expired");
						}
					} catch (Exception e) {
						info("spotifyhud.api.token_refresh_error");
					}
				}
				if (code == 403) {
					info("spotifyhud.api.premium_required");
				} else if (code == -1) {
					info("spotifyhud.api.network_unavailable");
				} else if (code >= 400) {
					info("spotifyhud.api.action_rejected", code);
				} else if (code == 204 || code == 200) {
					info("");
				}
				immediatePoll = true;
			}
		}
	}

	private boolean infoExpired() {
		return System.currentTimeMillis() - lastErrorAt > 5000L;
	}

	private static long Number(JsonObject json, String key, long def) {
		try {
			if (json != null && json.has(key) && json.get(key).isJsonPrimitive() && !json.get(key).isJsonNull()) {
				return json.get(key).getAsLong();
			}
		} catch (Exception ignored) {
		}
		return def;
	}

	private static boolean bool(JsonObject json, String key, boolean def) {
		try {
			if (json != null && json.has(key) && json.get(key).isJsonPrimitive() && !json.get(key).isJsonNull()) {
				return json.get(key).getAsBoolean();
			}
		} catch (Exception ignored) {
		}
		return def;
	}

	private static String string(JsonObject json, String key, String def) {
		try {
			if (json != null && json.has(key) && json.get(key).isJsonPrimitive() && !json.get(key).isJsonNull()) {
				return json.get(key).getAsString();
			}
		} catch (Exception ignored) {
		}
		return def;
	}

	private static String string(JsonObject parent, String key, String nested, String def) {
		JsonObject o = obj(parent, key);
		if (o == null) {
			return def;
		}
		return string(o, nested, def);
	}

	private static JsonObject obj(JsonObject json, String key) {
		try {
			if (json != null && json.has(key) && json.get(key).isJsonObject() && !json.get(key).isJsonNull()) {
				return json.getAsJsonObject(key);
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private static JsonArray arr(JsonObject json, String key) {
		try {
			if (json != null && json.has(key) && json.get(key).isJsonArray()) {
				return json.getAsJsonArray(key);
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private static String url(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}
}
