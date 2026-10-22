package com.spotifyhud;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

public class Keybinds {
	public static KeyMapping togglePlay;
	public static KeyMapping next;
	public static KeyMapping previous;
	public static KeyMapping shuffle;
	public static KeyMapping repeat;
	public static KeyMapping volumeUp;
	public static KeyMapping volumeDown;
	public static KeyMapping openConfig;

	private static final int GLFW_KEY_P = 80;
	private static final int GLFW_KEY_N = 78;
	private static final int GLFW_KEY_B = 66;
	private static final int GLFW_KEY_I = 73;
	private static final int GLFW_KEY_O = 79;
	private static final int GLFW_KEY_U = 85;
	private static final int GLFW_KEY_UP = 265;
	private static final int GLFW_KEY_DOWN = 264;

	public static void init() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("spotifyhud", "spotify"));
		togglePlay = register(new KeyMapping("key.spotifyhud.pause", InputConstants.Type.KEYBOARD, GLFW_KEY_P, category));
		next = register(new KeyMapping("key.spotifyhud.next", InputConstants.Type.KEYBOARD, GLFW_KEY_N, category));
		previous = register(new KeyMapping("key.spotifyhud.previous", InputConstants.Type.KEYBOARD, GLFW_KEY_B, category));
		shuffle = register(new KeyMapping("key.spotifyhud.shuffle", InputConstants.Type.KEYBOARD, GLFW_KEY_I, category));
		repeat = register(new KeyMapping("key.spotifyhud.repeat", InputConstants.Type.KEYBOARD, GLFW_KEY_O, category));
		volumeUp = register(new KeyMapping("key.spotifyhud.volume_up", InputConstants.Type.KEYBOARD, GLFW_KEY_UP, category));
		volumeDown = register(new KeyMapping("key.spotifyhud.volume_down", InputConstants.Type.KEYBOARD, GLFW_KEY_DOWN, category));
		openConfig = register(new KeyMapping("key.spotifyhud.config", InputConstants.Type.KEYBOARD, GLFW_KEY_U, category));
	}

	private static final KeyMapping[] ALL = new KeyMapping[8];

	private static KeyMapping register(KeyMapping mapping) {
		KeyMapping registered = KeyMappingHelper.registerKeyMapping(mapping);
		for (int i = 0; i < ALL.length; i++) {
			if (ALL[i] == null) {
				ALL[i] = registered;
				break;
			}
		}
		return registered;
	}

	public static KeyMapping[] all() {
		return ALL;
	}

	public static void storeBoundKeys() {
		String[] stored = new String[ALL.length];
		for (int i = 0; i < ALL.length; i++) {
			stored[i] = ALL[i].saveString();
		}
		Config.INSTANCE.boundKeys = stored;
	}

	public static void restoreStoredKeys() {
		String[] stored = Config.INSTANCE.boundKeys;
		if (stored == null || stored.length < ALL.length) {
			return;
		}
		for (int i = 0; i < ALL.length; i++) {
			String s = stored[i];
			if (s == null || s.isBlank()) {
				continue;
			}
			try {
				InputConstants.Key desired = InputConstants.getKey(s);
				if (desired == null) {
					continue;
				}
				if (!s.equals(ALL[i].saveString())) {
					ALL[i].setKey(desired);
					KeyMapping.resetMapping();
				}
			} catch (Exception e) {
				SpotifyPlayerClient.LOGGER.warn("Failed to restore hotkey: {}", s, e);
			}
		}
	}
}
