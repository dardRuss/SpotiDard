package com.spotifyhud;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpotifyPlayerClient implements ClientModInitializer {
	public static final String MOD_ID = "spotifyhud";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		Config.load();
		Keybinds.init();
		SpotifyApi.INSTANCE.start();
		AuthServer.start();
		InventoryControls.init();
		HudElementRegistry.attachElementAfter(
				VanillaHudElements.HOTBAR,
				Identifier.fromNamespaceAndPath(MOD_ID, "player_hud"),
				HudRenderer.INSTANCE
		);
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> tick());
		ClientTickEvents.START_CLIENT_TICK.register(minecraft -> {
			if (!keysRestored) {
				keysRestored = true;
				Keybinds.restoreStoredKeys();
			}
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> AuthServer.stop());
		LOGGER.info("Spotify Player initialized");
	}

	private static boolean keysRestored = false;
	private static final boolean[] downState = new boolean[8];

	private static void tick() {
		poll(0, Keybinds.all()[0], () -> SpotifyApi.INSTANCE.togglePlay());
		poll(1, Keybinds.all()[1], () -> SpotifyApi.INSTANCE.next());
		poll(2, Keybinds.all()[2], () -> SpotifyApi.INSTANCE.previous());
		poll(3, Keybinds.all()[3], () -> SpotifyApi.INSTANCE.toggleShuffle());
		poll(4, Keybinds.all()[4], () -> SpotifyApi.INSTANCE.cycleRepeat());
		poll(5, Keybinds.all()[5], () -> SpotifyApi.INSTANCE.adjustVolume(Config.INSTANCE.volumeStep));
		poll(6, Keybinds.all()[6], () -> SpotifyApi.INSTANCE.adjustVolume(-Config.INSTANCE.volumeStep));
		poll(7, Keybinds.all()[7], () -> {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.gui.screen() == null) {
				minecraft.gui.setScreen(new SpotifyConfigScreen(null));
			}
		});
	}

	// Coalesces vanilla click counting with our own isDown() edge detection:
	// a single press triggers the action once, even if one of the two paths is
	// unavailable (e.g. scoreboard/screen clearing click counts).
	private static void poll(int index, KeyMapping mapping, Runnable action) {
		boolean clicked = false;
		while (mapping.consumeClick()) {
			clicked = true;
		}
		boolean nowDown = !mapping.isUnbound() && mapping.isDown();
		boolean edge = nowDown && !downState[index];
		downState[index] = nowDown;
		if (clicked || edge) {
			action.run();
		}
	}
}
