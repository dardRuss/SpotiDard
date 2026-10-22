package com.spotifyhud;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class InventoryControls {
	private static final int BTN_W = 20;
	private static final int BTN_H = 18;
	private static final int GAP = 2;
	private static final int SLIDER_W = 72;
	private static final int GROUP_GAP = 6;
	private static final int HANDLE_W = 14;

	private static boolean dragging = false;
	private static double grabX;
	private static double grabY;
	private static int rowStartX;
	private static int rowStartY;
	private static int totalW;
	private static int handleX;
	private static int handleY;

	private record RowItem(AbstractWidget widget, int offsetX) {
	}

	private static List<RowItem> row;

	public static void init() {
		ScreenEvents.AFTER_INIT.register((minecraft, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof InventoryScreen) || !Config.INSTANCE.showInventoryButtons) {
				return;
			}
			var widgets = Screens.getWidgets(screen);
			List<RowItem> rowItems = new ArrayList<>();

			int buttonsRowW = HANDLE_W + GAP + BTN_W * 6 + GAP * 5;
			totalW = buttonsRowW + GROUP_GAP + SLIDER_W;

			int x0 = stored2(Config.INSTANCE.invControlsX, screen.width - totalW, (screen.width - totalW) / 2);
			int y0 = stored2(Config.INSTANCE.invControlsY, screen.height - BTN_H, 6);
			rowStartX = x0;
			rowStartY = y0;
			handleX = x0;
			handleY = y0;

			int cursor = x0;

			AbstractWidget handle = Button.builder(Component.literal("\u2B71"), b -> {
					})
					.bounds(cursor, y0, HANDLE_W, BTN_H)
					.build();
			rowItems.add(new RowItem(handle, cursor - x0));
			cursor += HANDLE_W + GAP;

			Button prev = Button.builder(Component.literal("\u23EE"), b -> SpotifyApi.INSTANCE.previous())
					.bounds(cursor, y0, BTN_W, BTN_H)
					.build();
			rowItems.add(new RowItem(prev, cursor - x0));
			cursor += BTN_W + GAP;

			final Button[] playRef = new Button[1];
			Button play = Button.builder(Component.literal(""), b -> {
						SpotifyApi.INSTANCE.togglePlay();
						if (playRef[0] != null) {
							playRef[0].setMessage(Component.literal(playLabel()));
						}
					})
					.bounds(cursor, y0, BTN_W, BTN_H)
					.build();
			playRef[0] = play;
			play.setMessage(Component.literal(playLabel()));
			rowItems.add(new RowItem(play, cursor - x0));
			cursor += BTN_W + GAP;

			Button next = Button.builder(Component.literal("\u23ED"), b -> SpotifyApi.INSTANCE.next())
					.bounds(cursor, y0, BTN_W, BTN_H)
					.build();
			rowItems.add(new RowItem(next, cursor - x0));
			cursor += BTN_W + GAP;

			Button shuffle = Button.builder(Component.literal("\u21C4"), b -> SpotifyApi.INSTANCE.toggleShuffle())
					.tooltip(Tooltip.create(Component.translatable("spotifyhud.inv.tooltip_shuffle")))
					.bounds(cursor, y0, BTN_W, BTN_H)
					.build();
			rowItems.add(new RowItem(shuffle, cursor - x0));
			cursor += BTN_W + GAP;

			Button repeat = Button.builder(Component.literal("\u27F2"), b -> SpotifyApi.INSTANCE.cycleRepeat())
					.tooltip(Tooltip.create(Component.translatable("spotifyhud.inv.tooltip_repeat")))
					.bounds(cursor, y0, BTN_W, BTN_H)
					.build();
			rowItems.add(new RowItem(repeat, cursor - x0));
			cursor += BTN_W + GAP;

			final Button[] visRef = new Button[1];
			Button visibility = Button.builder(Component.literal(visibilityGlyph()), b -> {
						Config.INSTANCE.showPanel = !Config.INSTANCE.showPanel;
						if (visRef[0] != null) {
							visRef[0].setMessage(Component.literal(visibilityGlyph()));
							visRef[0].setTooltip(Tooltip.create(Component.translatable(Config.INSTANCE.showPanel ? "spotifyhud.inv.tooltip_hide_player" : "spotifyhud.inv.tooltip_show_player")));
						}
						Config.save();
					})
					.tooltip(Tooltip.create(Component.translatable(Config.INSTANCE.showPanel ? "spotifyhud.inv.tooltip_hide_player" : "spotifyhud.inv.tooltip_show_player")))
					.bounds(cursor, y0, BTN_W, BTN_H)
					.build();
			visRef[0] = visibility;
			rowItems.add(new RowItem(visibility, cursor - x0));
			cursor += BTN_W + GAP;

			AbstractSliderButton volume = new AbstractSliderButton(cursor + GROUP_GAP, y0, SLIDER_W, BTN_H,
					volumeLabel(), initialVolume()) {
				@Override
				protected void updateMessage() {
					setMessage(volumeLabel());
				}

				@Override
				protected void applyValue() {
					SpotifyApi.INSTANCE.setVolume(Math.round(value * 100.0));
				}
			};
			rowItems.add(new RowItem(volume, cursor + GROUP_GAP - x0));

			row = rowItems;
			for (RowItem item : rowItems) {
				widgets.add(item.widget());
			}

			ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
				if (insideHandle(event.x(), event.y())) {
					dragging = true;
					grabX = event.x() - rowStartX;
					grabY = event.y() - rowStartY;
					return false;
				}
				return true;
			});
			ScreenMouseEvents.allowMouseDrag(screen).register((s, event, dragX, dragY) -> {
				if (dragging) {
					moveRowTo(event.x(), event.y(), screen);
					return false;
				}
				return true;
			});
			ScreenMouseEvents.allowMouseRelease(screen).register((s, event) -> {
				if (dragging) {
					dragging = false;
					Config.INSTANCE.invControlsX = rowStartX;
					Config.INSTANCE.invControlsY = rowStartY;
					Config.save();
					return false;
				}
				return true;
			});
		});
	}

	private static boolean insideHandle(double mx, double my) {
		return mx >= handleX && mx <= handleX + HANDLE_W && my >= handleY && my <= handleY + BTN_H;
	}

	private static void moveRowTo(double mx, double my, net.minecraft.client.gui.screens.Screen screen) {
		int maxX = Math.max(0, screen.width - totalW);
		int maxY = Math.max(0, screen.height - BTN_H);
		rowStartX = Math.max(0, Math.min((int) (mx - grabX), maxX));
		rowStartY = Math.max(0, Math.min((int) (my - grabY), maxY));
		handleX = rowStartX;
		handleY = rowStartY;
		if (row != null) {
			for (RowItem item : row) {
				item.widget().setX(rowStartX + item.offsetX());
				item.widget().setY(rowStartY);
			}
		}
	}

	private static int stored2(int stored, int max, int def) {
		if (stored < 0) {
			return Math.max(0, def);
		}
		return Math.max(0, Math.min(stored, Math.max(0, max)));
	}

	private static String visibilityGlyph() {
		return Config.INSTANCE.showPanel ? "\u25CF" : "\u25CB";
	}

	private static double initialVolume() {
		TrackState track = SpotifyApi.INSTANCE.currentOrNull();
		if (track == null || track.volumePercent < 0) {
			return 0.5;
		}
		return track.volumePercent / 100.0;
	}

	private static Component volumeLabel() {
		TrackState track = SpotifyApi.INSTANCE.currentOrNull();
		long vol = track != null && track.volumePercent >= 0 ? track.volumePercent : 50L;
		return Component.translatable("spotifyhud.inv.volume", vol);
	}

	private static String playLabel() {
		TrackState track = SpotifyApi.INSTANCE.currentOrNull();
		boolean playing = track != null && track.playing;
		return playing ? "\u23F8" : "\u25B6";
	}
}
