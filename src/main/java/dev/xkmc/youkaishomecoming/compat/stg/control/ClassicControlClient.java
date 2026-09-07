package dev.xkmc.youkaishomecoming.compat.stg.control;

import com.mojang.blaze3d.platform.InputConstants;
import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import dev.xkmc.youkaishomecoming.compat.stg.event.ClassicControlModeEvent;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHLangData;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.glfw.GLFW;

import java.util.function.Supplier;

/** Client input projection for the temporary classic planar controls mode. */
@OnlyIn(Dist.CLIENT)
public final class ClassicControlClient {

	private static final float VANILLA_SPRINT_SPEED_MULTIPLIER = 1.3f;
	private static final String KEY_CATEGORY = "key.categories.youkaishomecoming";
	private static final KeyMapping FIRE_NON_SPELL = new KeyMapping(
			"key.youkaishomecoming.fire_non_spell",
			KeyConflictContext.IN_GAME,
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_UNKNOWN,
			KEY_CATEGORY
	);
	private static final KeyMapping CAST_NEXT_SPELL = new KeyMapping(
			"key.youkaishomecoming.cast_next_spell",
			KeyConflictContext.IN_GAME,
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_UNKNOWN,
			KEY_CATEGORY
	);
	private static boolean enabled;
	private static boolean nonSpellHeldSent;
	private static final ToggleKeyChord COMBAT_TOGGLE = new ToggleKeyChord();

	private ClassicControlClient() {
	}

	public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
		event.register(FIRE_NON_SPELL);
		event.register(CAST_NEXT_SPELL);
	}

	public static void setEnabled(boolean value, int notice) {
		boolean previous = enabled;
		if (enabled && !value) {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.player != null) minecraft.player.setSprinting(false);
		}
		enabled = value;
		if (!value) nonSpellHeldSent = false;
		if (previous != value) {
			MinecraftForge.EVENT_BUS.post(new ClassicControlModeEvent(previous, value));
		}
		showNotice(notice);
	}

	private static void showNotice(int notice) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) return;
		Component message = switch (notice) {
			case ClassicControlSyncToClient.NOTICE_ENABLED -> YHLangData.CLASSIC_CONTROL_ENABLED.get(
					ControlKey.FORWARD.displayName(), ControlKey.BACKWARD.displayName(),
					ControlKey.LEFT.displayName(), ControlKey.RIGHT.displayName(),
					ControlKey.ASCEND.displayName(), ControlKey.DESCEND.displayName());
			case ClassicControlSyncToClient.NOTICE_DISABLED -> YHLangData.CLASSIC_CONTROL_DISABLED.get(
					ControlKey.FOCUS.displayName(), ControlKey.TOGGLE.displayName());
			case ClassicControlSyncToClient.NOTICE_AVAILABLE -> YHLangData.CLASSIC_CONTROL_AVAILABLE.get(
					ControlKey.FOCUS.displayName(), ControlKey.TOGGLE.displayName());
			default -> null;
		};
		if (message != null) minecraft.player.displayClientMessage(message, true);
	}

	public static void handleKey(InputEvent.Key event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.screen != null || !minecraft.isWindowActive()) {
			COMBAT_TOGGLE.reset();
			return;
		}
		ToggleKeyChord.Result combatToggle = COMBAT_TOGGLE.handle(event.getKey(), event.getAction(),
				ControlKey.COMBAT_TOGGLE.key().getValue(), ControlKey.COMBAT_MODIFIER.isDown(minecraft));
		if (combatToggle != ToggleKeyChord.Result.IGNORED) {
			ToggleKeyChord.consumeMappings(InputConstants.getKey(event.getKey(), event.getScanCode()),
					minecraft.options.keyMappings);
			if (combatToggle == ToggleKeyChord.Result.TRIGGERED) send(ClassicControlRequestToServer.TOGGLE_COMBAT);
			return;
		}
		if (event.getAction() != GLFW.GLFW_PRESS) return;
		if (ControlKey.TOGGLE.matches(event) && ControlKey.FOCUS.isDown(minecraft)) {
			send(ClassicControlRequestToServer.TOGGLE_MODE);
			return;
		}
		boolean standardCastSent = false;
		while (CAST_NEXT_SPELL.consumeClick()) {
			send(ClassicControlRequestToServer.CAST_NEXT_SPELL);
			standardCastSent = true;
		}
		if (enabled && !standardCastSent && ControlKey.CLASSIC_NEXT_SPELL.matches(event)) {
			send(ClassicControlRequestToServer.CAST_NEXT_SPELL);
		}
	}

	public static void tick() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null) {
			COMBAT_TOGGLE.reset();
			setEnabled(false, ClassicControlSyncToClient.NOTICE_NONE);
			return;
		}
		if (minecraft.screen != null || !minecraft.isWindowActive()) COMBAT_TOGGLE.reset();
		else COMBAT_TOGGLE.releaseIfUp(key -> InputConstants.isKeyDown(minecraft.getWindow().getWindow(), key));
		boolean held = minecraft.screen == null
				&& GrazeCapability.HOLDER.get(minecraft.player).isInDanmakuCombat()
				&& (FIRE_NON_SPELL.isDown()
				|| enabled && ControlKey.CLASSIC_NON_SPELL.isDown(minecraft));
		if (held == nonSpellHeldSent) return;
		nonSpellHeldSent = held;
		send(held ? ClassicControlRequestToServer.NON_SPELL_ON
				: ClassicControlRequestToServer.NON_SPELL_OFF);
	}

	public static void applyMovement(MovementInputUpdateEvent event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!enabled || minecraft.player == null || event.getEntity() != minecraft.player
				|| minecraft.screen != null) return;
		boolean toggleDown = ControlKey.TOGGLE.isDown(minecraft);
		boolean focusDown = ControlKey.FOCUS.isDown(minecraft);
		boolean focused = focusDown && !toggleDown;
		float existingForward = event.getInput().forwardImpulse;
		float existingLeft = event.getInput().leftImpulse;
		if (focused && event.getInput().shiftKeyDown) {
			// KeyboardInput has already applied vanilla's sneak factor before Forge
			// publishes this event. Undo it so the configured value is the true
			// high-to-low speed ratio.
			existingForward /= 0.3f;
			existingLeft /= 0.3f;
		}
		float forward = clampInput(existingForward
				+ axis(minecraft, ControlKey.FORWARD, ControlKey.BACKWARD));
		float left = clampInput(existingLeft
				+ axis(minecraft, ControlKey.LEFT, ControlKey.RIGHT));
		float speedScale = focused
				? VANILLA_SPRINT_SPEED_MULTIPLIER * YHModConfig.CLIENT.classicControlLowSpeedMultiplier.get().floatValue()
				: 1;
		event.getInput().forwardImpulse = forward * speedScale;
		event.getInput().leftImpulse = left * speedScale;
		event.getInput().shiftKeyDown = ControlKey.DESCEND.isDown(minecraft);
		if (toggleDown && focusDown && ControlKey.TOGGLE.matches(minecraft.options.keyJump.getKey())) {
			event.getInput().jumping = false;
		}
		event.getInput().jumping |= ControlKey.ASCEND.isDown(minecraft);
		event.getEntity().setSprinting(!focused && Math.abs(forward) + Math.abs(left) > 0.01f);
	}

	public static boolean shouldRenderFocusHitbox() {
		Minecraft minecraft = Minecraft.getInstance();
		// Camera mods can detach the actual view while leaving CameraType at first person.
		return enabled && minecraft.player != null && minecraft.screen == null
				&& minecraft.gameRenderer.getMainCamera().isDetached()
				&& ControlKey.FOCUS.isDown(minecraft) && !ControlKey.TOGGLE.isDown(minecraft);
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static Component combatToggleHint() {
		return YHLangData.STG_TOGGLE_KEY_TIP.get(
				ControlKey.COMBAT_MODIFIER.displayName(), ControlKey.COMBAT_TOGGLE.displayName());
	}

	private static float axis(Minecraft minecraft, ControlKey positiveKey, ControlKey negativeKey) {
		return (positiveKey.isDown(minecraft) ? 1 : 0) - (negativeKey.isDown(minecraft) ? 1 : 0);
	}

	private static float clampInput(float value) {
		return Math.max(-1, Math.min(1, value));
	}

	private static void send(int action) {
		YoukaisHomecoming.HANDLER.toServer(new ClassicControlRequestToServer(action));
	}

	private enum ControlKey {
		FORWARD(() -> YHModConfig.CLIENT.classicControlForwardKey),
		BACKWARD(() -> YHModConfig.CLIENT.classicControlBackwardKey),
		LEFT(() -> YHModConfig.CLIENT.classicControlLeftKey),
		RIGHT(() -> YHModConfig.CLIENT.classicControlRightKey),
		ASCEND(() -> YHModConfig.CLIENT.classicControlAscendKey),
		DESCEND(() -> YHModConfig.CLIENT.classicControlDescendKey),
		FOCUS(() -> YHModConfig.CLIENT.classicControlFocusKey),
		TOGGLE(() -> YHModConfig.CLIENT.classicControlToggleKey),
		CLASSIC_NON_SPELL(() -> YHModConfig.CLIENT.classicControlNonSpellKey),
		CLASSIC_NEXT_SPELL(() -> YHModConfig.CLIENT.classicControlNextSpellKey),
		COMBAT_MODIFIER(() -> YHModConfig.CLIENT.danmakuCombatModifierKey),
		COMBAT_TOGGLE(() -> YHModConfig.CLIENT.danmakuCombatToggleKey);

		private final Supplier<ForgeConfigSpec.ConfigValue<String>> configuredKey;

		ControlKey(Supplier<ForgeConfigSpec.ConfigValue<String>> configuredKey) {
			this.configuredKey = configuredKey;
		}

		private InputConstants.Key key() {
			var config = configuredKey.get();
			try {
				InputConstants.Key key = InputConstants.getKey(config.get());
				if (key.getType() == InputConstants.Type.KEYSYM && key.getValue() != GLFW.GLFW_KEY_UNKNOWN) return key;
			} catch (IllegalArgumentException ignored) {
			}
			return InputConstants.getKey(config.getDefault());
		}

		private Component displayName() {
			return key().getDisplayName();
		}

		private boolean isDown(Minecraft minecraft) {
			return InputConstants.isKeyDown(minecraft.getWindow().getWindow(), key().getValue());
		}

		private boolean matches(InputEvent.Key event) {
			return event.getKey() == key().getValue();
		}

		private boolean matches(InputConstants.Key other) {
			return key().equals(other);
		}
	}
}
