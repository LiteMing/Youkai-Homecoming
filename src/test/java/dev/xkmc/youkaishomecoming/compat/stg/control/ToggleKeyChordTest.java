package dev.xkmc.youkaishomecoming.compat.stg.control;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import static dev.xkmc.youkaishomecoming.compat.stg.control.ToggleKeyChord.Result.*;

/** Narrow input regressions; does not start Minecraft or connect to a server. */
public final class ToggleKeyChordTest {

	private static int checks;

	public static void main(String[] args) {
		testKeyEdges();
		testRebindingAndFocus();
		testConflictingBindings();
		System.out.println("ToggleKeyChordTest: all " + checks + " checks passed");
	}

	private static void testKeyEdges() {
		ToggleKeyChord chord = new ToggleKeyChord();
		int key = GLFW.GLFW_KEY_F;
		check("plain F is left to vanilla", chord.handle(key, GLFW.GLFW_PRESS, key, false) == IGNORED);
		check("a different key is not a toggle", chord.handle(GLFW.GLFW_KEY_G, GLFW.GLFW_PRESS, key, true) == IGNORED);
		check("unknown key is ignored", chord.handle(GLFW.GLFW_KEY_UNKNOWN, GLFW.GLFW_PRESS, key, true) == IGNORED);
		check("release alone does not toggle", chord.handle(key, GLFW.GLFW_RELEASE, key, true) == IGNORED);
		check("Shift+F press toggles", chord.handle(key, GLFW.GLFW_PRESS, key, true) == TRIGGERED);
		check("holding F does not toggle again", chord.handle(key, GLFW.GLFW_REPEAT, key, true) == CONSUMED);
		check("a duplicate press does not toggle again", chord.handle(key, GLFW.GLFW_PRESS, key, true) == CONSUMED);
		check("modifier release is independent", chord.handle(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_RELEASE, key, false) == IGNORED);
		check("repeat after releasing Shift stays consumed", chord.handle(key, GLFW.GLFW_REPEAT, key, false) == CONSUMED);
		check("unrelated keys still work while held", chord.handle(GLFW.GLFW_KEY_G, GLFW.GLFW_PRESS, key, false) == IGNORED);
		check("release ends consumption", chord.handle(key, GLFW.GLFW_RELEASE, key, false) == CONSUMED);
		check("plain F works after the chord", chord.handle(key, GLFW.GLFW_PRESS, key, false) == IGNORED);
		check("next chord press can exit combat", chord.handle(key, GLFW.GLFW_PRESS, key, true) == TRIGGERED);
	}

	private static void testRebindingAndFocus() {
		ToggleKeyChord chord = new ToggleKeyChord();
		int oldKey = GLFW.GLFW_KEY_F;
		int newKey = GLFW.GLFW_KEY_G;
		check("original binding starts the chord", chord.handle(oldKey, GLFW.GLFW_PRESS, oldKey, true) == TRIGGERED);
		check("a held key is consumed after rebinding", chord.handle(oldKey, GLFW.GLFW_REPEAT, newKey, false) == CONSUMED);
		check("the old key can still be released", chord.handle(oldKey, GLFW.GLFW_RELEASE, newKey, false) == CONSUMED);
		check("old binding no longer toggles", chord.handle(oldKey, GLFW.GLFW_PRESS, newKey, true) == IGNORED);
		check("configured binding toggles", chord.handle(newKey, GLFW.GLFW_PRESS, newKey, true) == TRIGGERED);
		chord.releaseIfUp(key -> true);
		check("physical hold preserves the latch", chord.handle(newKey, GLFW.GLFW_REPEAT, newKey, true) == CONSUMED);
		chord.releaseIfUp(key -> false);
		check("a missed key-up does not stick", chord.handle(newKey, GLFW.GLFW_PRESS, newKey, true) == TRIGGERED);
		chord.reset();
		check("focus reset allows a fresh press", chord.handle(newKey, GLFW.GLFW_PRESS, newKey, true) == TRIGGERED);
		chord.reset();
		check("returning with a held key does not toggle", chord.handle(newKey, GLFW.GLFW_REPEAT, newKey, true) == CONSUMED);
		check("repeat keeps the key consumed", chord.handle(newKey, GLFW.GLFW_REPEAT, newKey, false) == CONSUMED);
		chord.handle(newKey, GLFW.GLFW_RELEASE, newKey, false);
		check("press after returning and releasing toggles", chord.handle(newKey, GLFW.GLFW_PRESS, newKey, true) == TRIGGERED);
	}

	private static void testConflictingBindings() {
		KeyMapping swap = new KeyMapping("yh.test.swap", GLFW.GLFW_KEY_F, KeyMapping.CATEGORY_INVENTORY);
		KeyMapping shared = new KeyMapping("yh.test.shared", GLFW.GLFW_KEY_F, KeyMapping.CATEGORY_MISC);
		KeyMapping other = new KeyMapping("yh.test.other", GLFW.GLFW_KEY_G, KeyMapping.CATEGORY_MISC);
		InputConstants.Key f = swap.getKey();
		InputConstants.Key g = other.getKey();
		try {
			KeyMapping.set(f, true);
			KeyMapping.click(f);
			KeyMapping.click(f);
			KeyMapping.set(g, true);
			KeyMapping.click(g);
			ToggleKeyChord.consumeMappings(f, new KeyMapping[]{swap, shared, other});
			check("offhand swap clicks are drained", !swap.consumeClick());
			check("all bindings sharing the shortcut are drained", !shared.consumeClick());
			check("consumed binding is no longer held", !swap.isDown() && !shared.isDown());
			check("other key's click survives", other.consumeClick());
			check("other key's held state survives", other.isDown());
			KeyMapping.click(f);
			check("plain F can swap hands afterward", swap.consumeClick());
			check("other F bindings resume afterward", shared.consumeClick());
		} finally {
			KeyMapping.releaseAll();
		}
	}

	private static void check(String label, boolean condition) {
		if (!condition) throw new AssertionError(label);
		checks++;
	}
}
