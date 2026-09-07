package dev.xkmc.youkaishomecoming.compat.stg.control;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntPredicate;

/** Keeps a toggle chord consumed until its primary key is released. */
@OnlyIn(Dist.CLIENT)
final class ToggleKeyChord {

	enum Result {
		IGNORED, CONSUMED, TRIGGERED
	}

	private int heldKey = GLFW.GLFW_KEY_UNKNOWN;

	Result handle(int key, int action, int toggleKey, boolean modifierDown) {
		if (key == GLFW.GLFW_KEY_UNKNOWN) return Result.IGNORED;
		if (key == heldKey) {
			if (action == GLFW.GLFW_RELEASE) reset();
			return Result.CONSUMED;
		}
		if (key != toggleKey || !modifierDown || action == GLFW.GLFW_RELEASE) return Result.IGNORED;
		heldKey = key;
		return action == GLFW.GLFW_PRESS ? Result.TRIGGERED : Result.CONSUMED;
	}

	void releaseIfUp(IntPredicate isDown) {
		if (heldKey != GLFW.GLFW_KEY_UNKNOWN && !isDown.test(heldKey)) reset();
	}

	void reset() {
		heldKey = GLFW.GLFW_KEY_UNKNOWN;
	}

	static void consumeMappings(InputConstants.Key key, KeyMapping[] mappings) {
		// Forge's key event runs after vanilla queues binding clicks. Drain both
		// clicks and held state so Shift+F does not also swap the player's hands.
		for (KeyMapping mapping : mappings) {
			if (!mapping.getKey().equals(key)) continue;
			mapping.setDown(false);
			while (mapping.consumeClick()) {
				// Discard clicks queued for this shortcut, including key repeats.
			}
		}
	}
}
