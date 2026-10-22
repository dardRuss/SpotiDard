package com.spotifyhud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

public class HudRenderer implements HudElement {
	public static final HudRenderer INSTANCE = new HudRenderer();

	private static final int PANEL_W = 224;
	private static final int PANEL_H = 238;
	private static final int COVER = 60;
	private static final int PAD = 6;
	private static final int SPOTIFY_GREEN = 0xFF1DB954;
	private static final int TEXT_WHITE = 0xFFFFFFFF;
	private static final int TEXT_GRAY = 0xFFB3B3B3;
	private static final int BG = 0x82141414;
	private static final int BAR_BG = 0xFF464646;

	@Override
	public void extractRenderState(GuiGraphicsExtractor gui, DeltaTracker deltaTracker) {
		Config cfg = Config.INSTANCE;
		if (!cfg.showPanel) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.gui == null || minecraft.gui.hud == null || minecraft.gui.hud.isHidden()) {
			return;
		}
		int screenW = gui.guiWidth();
		int screenH = gui.guiHeight();
		TrackState track = SpotifyApi.INSTANCE.currentOrNull();
		String error = SpotifyApi.INSTANCE.lastError;
		Object[] errorArgs = SpotifyApi.INSTANCE.lastErrorArgs;

		float scale = Math.max(0.5F, Math.min(3.0F, cfg.scale));
		int scaledW = Math.round(PANEL_W * scale);
		int scaledH = Math.round(PANEL_H * scale);
		int posX = Math.max(0, Math.min(100, cfg.posX));
		int posY = Math.max(0, Math.min(100, cfg.posY));
		int x = (screenW - scaledW) * posX / 100;
		int y = (screenH - scaledH) * posY / 100;
		gui.pose().pushMatrix();
		gui.pose().translate(x, y);
		gui.pose().scale(scale);

		if (cfg.showBackground) {
			gui.fill(0, 0, PANEL_W, PANEL_H, BG);
			gui.outline(0, 0, PANEL_W, PANEL_H, 0xFF000000);
		}
		gui.text(minecraft.font, Component.literal("SpotyDard"), PAD, 5, TEXT_GRAY);

		if (track == null) {
			gui.fill(PAD, 22, PANEL_W - PAD, 22 + COVER, 0xFF282828);
			gui.centeredText(minecraft.font, Component.translatable("spotifyhud.hud.no_data"), PANEL_W / 2, 22 + COVER / 2 - 4, TEXT_GRAY);
			if (!error.isEmpty()) {
				gui.fill(PAD, 92, PANEL_W - PAD, 132, 0x66222222);
				drawMultiline(gui, minecraft, SpotifyApi.errorComponent(error, errorArgs), PAD + 3, 97, PANEL_W - 2 * PAD - 6, 30);
			}
			gui.pose().popMatrix();
			return;
		}

		Identifier art = AlbumArtManager.currentTexture();
		gui.fill(PAD, 22, PAD + COVER, 22 + COVER, 0xFF282828);
		if (art != null) {
			gui.blit(RenderPipelines.GUI_TEXTURED, art, PAD, 22, 0.0F, 0.0F, COVER, COVER, COVER, COVER);
		}

		int textX = PAD + COVER + PAD;
		int textW = PANEL_W - PAD - textX;
		Minecraft mc = minecraft;
		String title = mc.font.plainSubstrByWidth(track.title.isEmpty() ? "-" : track.title, textW - 20);
		gui.text(mc.font, Component.literal(title), textX, 26, TEXT_WHITE);
		String artists = mc.font.plainSubstrByWidth(track.artists.isEmpty() ? "" : track.artists, textW - 20);
		gui.text(mc.font, Component.literal(artists), textX, 41, TEXT_GRAY);

		int iconX = textX;
		if (track.shuffle) {
			gui.pose().pushMatrix();
			gui.pose().translate(iconX - 1, 54);
			gui.pose().scale(1.3F, 1.3F);
			gui.text(mc.font, Component.literal("\u21C4"), 0, 2, SPOTIFY_GREEN);
			gui.pose().popMatrix();
			iconX += 18;
		}
		if (!track.repeat.equals("off")) {
			String repeatIcon = track.repeat.equals("track") ? "\u27F2\u2776" : "\u27F2";
			gui.text(mc.font, Component.literal(repeatIcon), iconX, 56, SPOTIFY_GREEN);
		}

		long progress = Math.min(track.currentProgressMs(), Math.max(1, track.durationMs));
		int barW = PANEL_W - 2 * PAD;
		int barY = PANEL_H - 52;
		drawLyrics(gui, mc, track, progress, barY - 8);

		gui.fill(PAD, barY, PAD + barW, barY + 5, BAR_BG);
		int played = (int) ((long) barW * progress / Math.max(1, track.durationMs));
		gui.fill(PAD, barY, PAD + played, barY + 5, SPOTIFY_GREEN);

		gui.text(mc.font, Component.literal(SpotifyApi.formatTime(progress)), PAD, barY + 9, TEXT_GRAY);
		String total = SpotifyApi.formatTime(track.durationMs);
		gui.text(mc.font, Component.literal(total), PANEL_W - PAD - mc.font.width(total), barY + 9, TEXT_GRAY);

		if (track.volumePercent >= 0) {
			String vol = "\u266B " + track.volumePercent + "%";
			int volW = mc.font.width(vol);
			gui.text(mc.font, Component.literal(vol), (PANEL_W - volW) / 2, barY + 9, TEXT_GRAY);
		}

		if (!error.isEmpty()) {
			int ey = 94;
			gui.fill(PAD, ey, PANEL_W - PAD, ey + 38, 0x66661FaA);
			gui.fill(PAD, ey, PANEL_W - PAD, ey + 2, 0xFFB34747);
			drawMultiline(gui, mc, SpotifyApi.errorComponent(error, errorArgs), PAD + 3, ey + 6, PANEL_W - 2 * PAD - 6, 30);
		}
		gui.pose().popMatrix();
	}

	private static void drawLyrics(GuiGraphicsExtractor gui, Minecraft mc, TrackState track, long progress, int areaBottom) {
		String line = LyricsManager.INSTANCE.currentLine(track, progress);
		if (line.isEmpty()) {
			return;
		}
		int top = 86;
		int bottom = areaBottom - 4;
		float lyScale = 1.3F;
		int wrapW = (int) ((PANEL_W - 2 * PAD - 4) / lyScale);
		var parts = mc.font.split(Component.literal(line), wrapW);
		int lineH = 12;
		int textH = Math.round(parts.size() * lineH * lyScale);
		int avail = bottom - top;
		if (textH > avail) {
			int skip = (textH - avail) / Math.round(lineH * lyScale);
			parts = parts.subList(skip, parts.size());
			textH = Math.round(parts.size() * lineH * lyScale);
		}
		int startY = Math.round(Math.max(0, (avail - textH) / 2) / lyScale);
		gui.pose().pushMatrix();
		gui.pose().translate(PANEL_W / 2.0F, top);
		gui.pose().scale(lyScale, lyScale);
		for (var f : parts) {
			int x = -mc.font.width(f) / 2;
			gui.text(mc.font, f, x, startY, TEXT_WHITE);
			startY += lineH;
		}
		gui.pose().popMatrix();
	}

	private static void drawMultiline(GuiGraphicsExtractor gui, Minecraft mc, Component line, int x, int y, int maxWidth, int maxLines) {
		var parts = mc.font.split(line, maxWidth);
		int shown = 0;
		for (var f : parts) {
			if (shown >= maxLines) {
				break;
			}
			gui.text(mc.font, f, x, y + shown * 10, TEXT_GRAY);
			shown++;
		}
	}
}
