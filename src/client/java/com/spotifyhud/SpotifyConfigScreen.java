package com.spotifyhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;

public class SpotifyConfigScreen extends Screen {
	private static final int TOP_COLOR = 0xFF1DB954;
	private static final int GRAY_COLOR = 0xFFAAAAAA;

	private final Screen parent;

	private EditBox clientId;
	private EditBox port;
	private Button visibilityButton;
	private Button loginButton;

	public SpotifyConfigScreen(Screen parent) {
		super(Component.translatable("spotifyhud.config.title"));
		this.parent = parent;
	}

	private Config config() {
		return Config.INSTANCE;
	}

	@Override
	protected void init() {
		int panelW = 320;
		int x0 = width / 2 - panelW / 2;
		int halfW = (panelW - 10) / 2;
		int label = 18;

		clientId = new EditBox(font, x0, 30, panelW, label, Component.translatable("spotifyhud.config.client_id"));
		clientId.setMaxLength(64);
		clientId.setValue(config().clientId == null ? "" : config().clientId);
		clientId.setHint(Component.translatable("spotifyhud.config.hint_client_id"));
		clientId.setResponder(s -> config().clientId = s.trim());
		addRenderableWidget(clientId);

		port = new EditBox(font, x0 + 34, 54, 50, label, Component.translatable("spotifyhud.config.port"));
		port.setMaxLength(5);
		port.setValue(String.valueOf(config().redirectPort));
		port.setHint(Component.literal("5580"));
		port.setResponder(s -> {
			try {
				int p = Integer.parseInt(s);
				if (p >= 1024 && p <= 65535) {
					config().redirectPort = p;
				}
			} catch (NumberFormatException ignored) {
			}
		});
		addRenderableWidget(port);

		loginButton = Button.builder(Component.translatable("spotifyhud.config.login_button"), b -> login())
				.bounds(x0 + 94, 54, panelW - 94, label)
				.build();
		addRenderableWidget(loginButton);

		visibilityButton = Button.builder(visibilityLabel(), b -> {
			Config.INSTANCE.showPanel = !Config.INSTANCE.showPanel;
			visibilityButton.setMessage(visibilityLabel());
		}).bounds(x0, 90, panelW, label).build();
		addRenderableWidget(visibilityButton);

		addRenderableWidget(slider(x0, 114, halfW, label,
				config().posX / 100.0,
				v -> config().posX = (int) Math.round(v * 100.0),
				v -> Component.translatable("spotifyhud.config.pos_x", (int) Math.round(v * 100.0))));
		addRenderableWidget(slider(x0 + halfW + 10, 114, halfW, label,
				config().posY / 100.0,
				v -> config().posY = (int) Math.round(v * 100.0),
				v -> Component.translatable("spotifyhud.config.pos_y", (int) Math.round(v * 100.0))));

		addRenderableWidget(slider(x0, 138, panelW, label,
				(config().scale - 0.5F) / 1.5F,
				v -> config().scale = (float) (0.5 + v * 1.5),
				v -> Component.translatable("spotifyhud.config.scale", String.format("%.2f", 0.5 + v * 1.5))));

		addRenderableWidget(slider(x0, 162, panelW, label,
				(config().volumeStep - 1) / 24.0,
				v -> config().volumeStep = 1 + (int) Math.round(v * 24.0),
				v -> Component.translatable("spotifyhud.config.volume_step", 1 + (int) Math.round(v * 24.0))));

		addRenderableWidget(Checkbox.builder(Component.translatable("spotifyhud.config.show_lyrics"), font)
				.pos(x0, 186)
				.maxWidth(halfW)
				.selected(config().showLyrics)
				.onValueChange((cb, v) -> config().showLyrics = v)
				.build());
		addRenderableWidget(Checkbox.builder(Component.translatable("spotifyhud.config.inv_buttons"), font)
				.pos(x0 + halfW + 10, 186)
				.maxWidth(halfW)
				.selected(config().showInventoryButtons)
				.onValueChange((cb, v) -> config().showInventoryButtons = v)
				.build());

		addRenderableWidget(Button.builder(Component.translatable("spotifyhud.config.keybinds"), b -> openKeyBinds())
				.bounds(x0, 210, panelW, label)
				.build());

		addRenderableWidget(Button.builder(Component.translatable("spotifyhud.config.done"), b -> onClose())
				.bounds(x0, 234, panelW, label)
				.build());

		Keybinds.storeBoundKeys();
		Config.save();
	}

	private void openKeyBinds() {
		Config.save();
		Minecraft mc = Minecraft.getInstance();
		mc.setScreenAndShow(new KeyBindsScreen(this, mc.options));
	}

	private Component visibilityLabel() {
		return Component.translatable(Config.INSTANCE.showPanel ? "spotifyhud.config.player_on" : "spotifyhud.config.player_off");
	}

	private AbstractWidget slider(int x, int y, int w, int h, double initial, java.util.function.DoubleConsumer store, java.util.function.DoubleFunction<Component> label) {
		return new AbstractSliderButton(x, y, w, h, label.apply(initial), initial) {
			@Override
			protected void updateMessage() {
				setMessage(label.apply(value));
			}

			@Override
			protected void applyValue() {
				store.accept(value);
			}
		};
	}

	private void login() {
		if (config().clientId == null || config().clientId.isBlank()) {
			showInfo(Component.translatable("spotifyhud.config.login_first"));
			return;
		}
		Config.save();
		boolean started = AuthServer.beginLogin();
		if (started) {
			Thread waiter = new Thread(() -> {
				try {
					boolean got = AuthServer.awaitCapture(90);
					if (got) {
						AuthServer.completeLogin();
					} else {
						showInfo(Component.translatable("spotifyhud.config.login_no_code"));
					}
				} catch (Exception e) {
					showInfo(Component.translatable("spotifyhud.config.login_error"));
				}
			}, "SpotifyPlayer-LoginWait");
			waiter.setDaemon(true);
			waiter.start();
		}
	}

	private void showInfo(Component message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc != null) {
			mc.execute(() -> {
				if (mc.gui != null && mc.gui.hud != null) {
					mc.gui.hud.getChat().addClientSystemMessage(message);
				}
			});
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		int panelW = 320;
		int x0 = width / 2 - panelW / 2;
		String title = getTitle().getString();
		graphics.text(font, getTitle(), (width - font.width(title)) / 2, 7, TOP_COLOR);
		graphics.text(font, Component.translatable("spotifyhud.config.client_id"), x0, 20, GRAY_COLOR);
		graphics.text(font, Component.translatable("spotifyhud.config.port"), x0, 59, GRAY_COLOR);
		boolean authorizedNow = AuthServer.hasLogin();
		graphics.text(font, Component.translatable(authorizedNow ? "spotifyhud.config.status_authorized" : "spotifyhud.config.status_not_authorized"), x0, 78, authorizedNow ? TOP_COLOR : GRAY_COLOR);
	}

	@Override
	public void onClose() {
		Config.save();
		if (parent != null) {
			Minecraft.getInstance().setScreenAndShow(parent);
		} else {
			super.onClose();
		}
	}

	@Override
	public void removed() {
		Config.save();
		super.removed();
	}
}
