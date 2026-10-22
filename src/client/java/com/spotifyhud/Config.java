package com.spotifyhud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public String clientId = "";
	public int redirectPort = 5580;
	public int posX = 100;
	public int posY = 100;
	public float scale = 1.0F;
	public int volumeStep = 5;
	public int pollIntervalMs = 3000;
	public boolean showBackground = true;
	public boolean showPanel = true;
	public boolean showLyrics = true;
	public boolean showInventoryButtons = true;
	public int invControlsX = -1;
	public int invControlsY = -1;

	public String accessToken = "";
	public String refreshToken = "";
	public long tokenExpiresAt = 0L;

	public String lastCodeVerifier = "";

	public String[] boundKeys;

	public static Config INSTANCE = new Config();

	public static Path getPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("spotifyplayer.json");
	}

	public static void load() {
		Path path = getPath();
		if (Files.exists(path)) {
			try {
				INSTANCE = GSON.fromJson(Files.readString(path), Config.class);
				if (INSTANCE == null) {
					INSTANCE = new Config();
				}
			} catch (Exception e) {
				SpotifyPlayerClient.LOGGER.error("Failed to read spotifyplayer config", e);
				INSTANCE = new Config();
			}
		}
	}

	public static void save() {
		Path path = getPath();
		try {
			Files.createDirectories(path.getParent());
			Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(INSTANCE));
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			SpotifyPlayerClient.LOGGER.error("Failed to save spotifyplayer config", e);
		}
	}
}
