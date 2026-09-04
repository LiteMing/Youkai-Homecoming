package dev.xkmc.youkaishomecoming.compat.stg.control;

import com.mojang.blaze3d.platform.InputConstants;
import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
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

	private ClassicControlClient() {
	}

	public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
		event.register(FIRE_NON_SPELL);
		event.register(CAST_NEXT_SPELL);
	}

	public static void setEnabled(boolean value) {
		if (enabled && !value) {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.player != null) minecraft.player.setSprinting(false);
		}
		enabled = value;
		if (!value) nonSpellHeldSent = false;
	}

	public static void handleKey(InputEvent.Key event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.screen != null || event.getAction() != GLFW.GLFW_PRESS) return;
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
			enabled = false;
			nonSpellHeldSent = false;
			return;
		}
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
		event.getInput().shiftKeyDown = false;
		if (toggleDown && focusDown && ControlKey.TOGGLE.matches(minecraft.options.keyJump.getKey())) {
			event.getInput().jumping = false;
		}
		event.getEntity().setSprinting(!focused && Math.abs(forward) + Math.abs(left) > 0.01f);
	}

	public static boolean shouldRenderFocusHitbox() {
		Minecraft minecraft = Minecraft.getInstance();
		return enabled && minecraft.player != null && minecraft.screen == null
				&& !minecraft.options.getCameraType().isFirstPerson()
				&& ControlKey.FOCUS.isDown(minecraft) && !ControlKey.TOGGLE.isDown(minecraft);
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
		FORWARD(GLFW.GLFW_KEY_UP, () -> YHModConfig.CLIENT.classicControlForwardKey.get()),
		BACKWARD(GLFW.GLFW_KEY_DOWN, () -> YHModConfig.CLIENT.classicControlBackwardKey.get()),
		LEFT(GLFW.GLFW_KEY_LEFT, () -> YHModConfig.CLIENT.classicControlLeftKey.get()),
		RIGHT(GLFW.GLFW_KEY_RIGHT, () -> YHModConfig.CLIENT.classicControlRightKey.get()),
		FOCUS(GLFW.GLFW_KEY_LEFT_SHIFT, () -> YHModConfig.CLIENT.classicControlFocusKey.get()),
		TOGGLE(GLFW.GLFW_KEY_SPACE, () -> YHModConfig.CLIENT.classicControlToggleKey.get()),
		CLASSIC_NON_SPELL(GLFW.GLFW_KEY_Z, () -> YHModConfig.CLIENT.classicControlNonSpellKey.get()),
		CLASSIC_NEXT_SPELL(GLFW.GLFW_KEY_X, () -> YHModConfig.CLIENT.classicControlNextSpellKey.get());

		private final InputConstants.Key fallback;
		private final Supplier<String> configuredName;

		ControlKey(int fallback, Supplier<String> configuredName) {
			this.fallback = InputConstants.Type.KEYSYM.getOrCreate(fallback);
			this.configuredName = configuredName;
		}

		private InputConstants.Key key() {
			try {
				InputConstants.Key key = InputConstants.getKey(configuredName.get());
				return key.getType() == InputConstants.Type.KEYSYM && key.getValue() != GLFW.GLFW_KEY_UNKNOWN
						? key : fallback;
			} catch (IllegalArgumentException ignored) {
				return fallback;
			}
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
