package com.spotifyhud;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

public class AlbumArtManager {
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(8))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
	private static final Object LOCK = new Object();
	private static volatile String loadingUrl;
	private static volatile String loadedHash;
	private static volatile Identifier loadedId;

	public static Identifier currentTexture() {
		return loadedId;
	}

	public static void ensureLoaded(String coverUrl) {
		if (coverUrl == null || coverUrl.isBlank()) {
			return;
		}
		String hash = hash(coverUrl);
		if (hash.equals(loadedHash)) {
			return;
		}
		if (loadingUrl != null && loadingUrl.equals(coverUrl)) {
			return;
		}
		synchronized (LOCK) {
			if (hash.equals(loadedHash) || (loadingUrl != null && loadingUrl.equals(coverUrl))) {
				return;
			}
			loadingUrl = coverUrl;
		}
		Thread worker = new Thread(() -> {
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(coverUrl))
						.timeout(Duration.ofSeconds(10))
						.header("User-Agent", "MinecraftSpotifyHud/1.0")
						.GET()
						.build();
				HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
				if (response.statusCode() != 200 || response.body() == null) {
					synchronized (LOCK) {
						loadingUrl = null;
					}
					return;
				}
				byte[] png = toPng(response.body());
				if (png == null) {
					synchronized (LOCK) {
						loadingUrl = null;
					}
					return;
				}
				NativeImage image = NativeImage.read(new ByteArrayInputStream(png));
				Identifier id = Identifier.fromNamespaceAndPath("spotifyhud", "albumart/" + hash(coverUrl));
				Minecraft minecraft = Minecraft.getInstance();
				if (minecraft == null) {
					image.close();
					return;
				}
				minecraft.execute(() -> {
					try {
						DynamicTexture texture = new DynamicTexture(() -> "Spotify album art", image);
						minecraft.getTextureManager().register(id, texture);
						apply(id, hash);
					} catch (Exception e) {
						image.close();
					}
				});
			} catch (Exception ignored) {
				synchronized (LOCK) {
					loadingUrl = null;
				}
			}
		}, "SpotifyPlayer-Art");
		worker.setDaemon(true);
		worker.start();
	}

	private static void apply(Identifier id, String hash) {
		Identifier old = loadedId;
		loadedId = id;
		loadedHash = hash;
		loadingUrl = null;
		if (old != null && !old.equals(id)) {
			Minecraft.getInstance().getTextureManager().release(old);
		}
	}

	private static String hash(String input) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : digest) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (Exception e) {
			return "art";
		}
	}

	private static byte[] toPng(byte[] raw) {
		try {
			BufferedImage source = ImageIO.read(new ByteArrayInputStream(raw));
			if (source == null) {
				return null;
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(source, "png", out);
			return out.toByteArray();
		} catch (Exception e) {
			return null;
		}
	}
}
