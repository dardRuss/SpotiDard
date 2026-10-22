package com.spotifyhud;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.awt.Desktop;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AuthServer {
	public static final String SCOPES = "user-read-playback-state user-modify-playback-state user-read-currently-playing";
	private static final String AUTHORIZE_URL = "https://accounts.spotify.com/authorize";
	private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";
	private static final Gson GSON = new Gson();
	public static final HttpClient CLIENT = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

	private static HttpServer server;
	private static String pendingCodeVerifier;
	private static volatile String capturedCode;
	private static String pendingState;

	public static synchronized void start() {
		if (server != null) {
			return;
		}
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", Config.INSTANCE.redirectPort), 0);
		} catch (IOException e) {
			SpotifyPlayerClient.LOGGER.error("Failed to bind callback port {}", Config.INSTANCE.redirectPort, e);
			return;
		}
		server.createContext("/callback", exchange -> {
			Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
			String code = params.get("code");
			String error = params.get("error");
			String state = params.get("state");
			byte[] body;
			if (error != null) {
				body = "<html><body><h2>Spotify error: ".concat(error).concat("</h2></body></html>").getBytes(StandardCharsets.UTF_8);
				capturedCode = null;
			} else if (code != null) {
				if (state == null || !state.equals(pendingState)) {
					body = "<h2>SpotyDard: state mismatch</h2>".getBytes(StandardCharsets.UTF_8);
				} else {
					capturedCode = code;
					body = "<h2>SpotyDard: OK</h2>".getBytes(StandardCharsets.UTF_8);
				}
			} else {
				body = "<h2>SpotyDard: no code</h2>".getBytes(StandardCharsets.UTF_8);
			}
			exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(2, r -> {
			Thread t = new Thread(r, "SpotifyPlayer-HTTP");
			t.setDaemon(true);
			return t;
		}));
		server.start();
		SpotifyPlayerClient.LOGGER.info("Spotify callback server on http://127.0.0.1:{}/callback", Config.INSTANCE.redirectPort);
	}

	public static synchronized void stop() {
		if (server != null) {
			try {
				server.stop(0);
			} catch (Throwable ignored) {
			}
			server = null;
		}
	}

	public static String redirectUri() {
		return "http://127.0.0.1:" + Config.INSTANCE.redirectPort + "/callback";
	}

	private static byte[] randomBytes() {
		byte[] bytes = new byte[48];
		new SecureRandom().nextBytes(bytes);
		return bytes;
	}

	public static boolean beginLogin() {
		if (Config.INSTANCE.clientId.isBlank()) {
			postMessage(Component.translatable("spotifyhud.msg.no_client_id"));
			return false;
		}
		start();
		byte[] raw = randomBytes();
		String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
		String digest = Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(verifier));
		pendingState = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes());
		pendingCodeVerifier = verifier;
		Config.INSTANCE.lastCodeVerifier = verifier;
		Config.save();
		String url = AUTHORIZE_URL + "?response_type=code"
				+ "&client_id=" + url(Config.INSTANCE.clientId)
				+ "&scope=" + url(SCOPES)
				+ "&redirect_uri=" + url(redirectUri())
				+ "&state=" + url(pendingState)
				+ "&code_challenge=" + url(digest)
				+ "&code_challenge_method=S256";
		postMessage(Component.translatable("spotifyhud.msg.opening_browser"));
		openBrowser(url);
		return true;
	}

	private static byte[] sha256(String input) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.US_ASCII));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static String url(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static void openBrowser(String url) {
		SpotifyPlayerClient.LOGGER.info("SpotyDard: authorize URL: {}", url);
		boolean opened = false;
		try {
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
				Desktop.getDesktop().browse(URI.create(url));
				opened = true;
			}
		} catch (Throwable ignored) {
		}
		if (!opened) {
			// URL содержит '&' — без кавычек cmd обрежет ссылку на первом же параметре
			String quoted = "\"" + url + "\"";
			try {
				ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "start", "", quoted);
				pb.redirectErrorStream(true);
				Process p = pb.start();
				p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
				if (p.exitValue() == 0) {
					opened = true;
				}
			} catch (Throwable ignored) {
			}
		}
		if (!opened) {
			postMessage(Component.translatable("spotifyhud.msg.browser_fail"));
		}
	}

	public static boolean hasLogin() {
		return Config.INSTANCE.refreshToken != null && !Config.INSTANCE.refreshToken.isBlank();
	}

	public static synchronized boolean awaitCapture(int seconds) throws IOException, InterruptedException {
		capturedCode = null;
		CountDownLatch latch = new CountDownLatch(1);
		latch.await(0, TimeUnit.MILLISECONDS);
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
		while (System.nanoTime() < deadline) {
			if (capturedCode != null) {
				return true;
			}
			Thread.sleep(200);
		}
		return false;
	}

	public static synchronized void completeLogin() {
		String code = capturedCode;
		if (code == null || pendingCodeVerifier == null) {
			return;
		}
		JsonObject json = tokenRequest(form(
				"grant_type", "authorization_code",
				"code", code,
				"redirect_uri", redirectUri(),
				"client_id", Config.INSTANCE.clientId,
				"code_verifier", pendingCodeVerifier));
		if (json == null) {
			postMessage(Component.translatable("spotifyhud.msg.token_fail"));
			return;
		}
		storeTokens(json);
		postMessage(Component.translatable("spotifyhud.msg.auth_success"));
		SpotifyApi.resetAndPollNow();
	}

	public static synchronized boolean refreshToken() {
		if (Config.INSTANCE.refreshToken == null || Config.INSTANCE.refreshToken.isBlank()) {
			return false;
		}
		JsonObject json = tokenRequest(form(
				"grant_type", "refresh_token",
				"refresh_token", Config.INSTANCE.refreshToken,
				"client_id", Config.INSTANCE.clientId));
		if (json == null) {
			return false;
		}
		storeTokens(json);
		return true;
	}

	public static void clearTokens() {
		Config.INSTANCE.accessToken = "";
		Config.INSTANCE.refreshToken = "";
		Config.INSTANCE.tokenExpiresAt = 0L;
		Config.save();
	}

	private static String form(String... pairs) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			if (i > 0) {
				sb.append('&');
			}
			sb.append(url(pairs[i])).append('=').append(url(pairs[i + 1]));
		}
		return sb.toString();
	}

	private static void storeTokens(JsonObject json) {
		Config.INSTANCE.accessToken = str(json, "access_token");
		String refresh = str(json, "refresh_token");
		if (refresh != null && !refresh.isBlank()) {
			Config.INSTANCE.refreshToken = refresh;
		}
		long expiresIn = 3600L;
		if (json.has("expires_in") && json.get("expires_in").isJsonPrimitive()) {
			expiresIn = json.get("expires_in").getAsLong();
		}
		Config.INSTANCE.tokenExpiresAt = System.currentTimeMillis() + Math.max(60L, expiresIn - 60L) * 1000L;
		Config.save();
	}

	private static JsonObject tokenRequest(String body) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
					.header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
					.build();
			HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				SpotifyPlayerClient.LOGGER.warn("Token exchange failed: {} {}", response.statusCode(), response.body());
				return null;
			}
			return GSON.fromJson(response.body(), JsonObject.class);
		} catch (Exception e) {
			SpotifyPlayerClient.LOGGER.error("Token request error", e);
			return null;
		}
	}

	private static String str(JsonObject json, String key) {
		if (json.has(key) && json.get(key).isJsonPrimitive()) {
			return json.get(key).getAsString();
		}
		return "";
	}

	private static Map<String, String> parseQuery(String raw) {
		Map<String, String> map = new HashMap<>();
		if (raw == null) {
			return map;
		}
		for (String pair : raw.split("&")) {
			int eq = pair.indexOf('=');
			if (eq < 0) {
				continue;
			}
			String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
			String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
			map.put(key, value);
		}
		return map;
	}

	private static void postMessage(Component component) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft != null) {
			minecraft.execute(() -> {
				if (minecraft.gui != null && minecraft.gui.hud != null) {
					minecraft.gui.hud.getChat().addClientSystemMessage(component);
				}
			});
		}
	}
}
