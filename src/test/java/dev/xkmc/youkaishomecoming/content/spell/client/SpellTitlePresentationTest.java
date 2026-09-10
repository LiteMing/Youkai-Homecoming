package dev.xkmc.youkaishomecoming.content.spell.client;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellTitleStyle;
import dev.xkmc.youkaishomecoming.content.spell.preview.SpellTitlePreviewTimeline;
import net.minecraft.nbt.CompoundTag;

import java.util.List;

/** Headless contracts for title style serialization and cue/HUD handoff. */
public final class SpellTitlePresentationTest {
	private static int checks;

	public static void main(String[] args) {
		var defaults = SpellTitleStyle.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow(false, s -> {});
		check("missing style fields use viewer defaults", defaults.equals(SpellTitleStyle.DEFAULT));
		var json = JsonParser.parseString("""
				{"background":"youkaishomecoming:textures/item/spell/spell_remilia.png",
				 "background_scale":1.5,"background_x":-40,"background_y":18,
				 "gradient_start":"#CC123456","gradient_end":"#00123456"}
				""");
		var style = SpellTitleStyle.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(false, s -> {});
		check("style JSON round trip retains all fields",
				SpellTitleStyle.CODEC.encodeStart(JsonOps.INSTANCE, style).getOrThrow(false, s -> {}).equals(json));
		check("alpha survives style NBT", SpellTitleStyle.fromTag(style.toTag()).equals(style));
		check("transparent gradient survives decoding", style.gradientEnd().orElseThrow() == 0x00123456);
		var copy = new ActiveSpellHudToClient.Entry(4, "yh_test:card", "same name", true, false, "a", style.toTag());
		check("HUD snapshot retains introduction style", SpellTitleStyle.fromTag(copy.presentation).equals(style));

		SpellTitleOverlay.clear();
		SpellTitleOverlay.show("a", "same name", "", 20, style.toTag());
		SpellTitleOverlay.show("b", "same name", "", 20, new CompoundTag());
		SpellTitleOverlay.synchronize(List.of());
		check("cue tolerates a late first snapshot", SpellTitleOverlay.isAnimating("a"));
		SpellTitleOverlay.synchronize(List.of("a", "b"));
		SpellTitleOverlay.synchronize(List.of("b"));
		check("ended card cancels only its own introduction", !SpellTitleOverlay.isAnimating("a") && SpellTitleOverlay.isAnimating("b"));
		for (int i = 0; i < 19; i++) SpellTitleOverlay.advance();
		check("HUD row remains hidden through the last moving frame", SpellTitleOverlay.isAnimating("b"));
		SpellTitleOverlay.advance();
		check("duration completion releases the HUD row", !SpellTitleOverlay.isAnimating("b"));
		SpellTitleOverlay.show("c", "same name", "", 100, new CompoundTag());
		SpellTitleOverlay.clear();
		check("world cleanup releases introductions", !SpellTitleOverlay.isAnimating("c"));
		checkPreviewPlayback();
		System.out.println("SpellTitlePresentationTest: all " + checks + " checks passed");
	}

	private static void checkPreviewPlayback() {
		var preview = new SpellTitlePreviewTimeline();
		preview.setDuration(100);
		preview.seek(0.4f);
		check("layout inspection starts paused", !preview.isPlaying());
		close("selected frame is held", preview.progress(0.75f), 0.4f);
		preview.tick();
		close("paused preview does not advance", preview.progress(0), 0.4f);
		preview.setDuration(200);
		close("editing duration preserves the inspected frame", preview.progress(0), 0.4f);
		preview.togglePlaying();
		preview.tick();
		close("playback uses the updated duration", preview.progress(0), 0.405f);
		close("playing frames interpolate", preview.progress(0.5f), 0.4075f);
		preview.togglePlaying();
		close("pause ignores partial ticks", preview.progress(0.9f), 0.405f);
		preview.seek(1);
		check("docked frame remains visible without playback", preview.progress(0.5f) == 1 && !preview.isPlaying());
		preview.togglePlaying();
		check("play from the docked frame restarts the introduction", preview.progress(0) == 0 && preview.isPlaying());
		preview.setDuration(-1);
		check("preview uses the same minimum duration as real titles", preview.duration() == 20);
		for (int i = 0; i < 20; i++) preview.tick();
		check("playback holds its final frame", preview.progress(0) == 1 && !preview.isPlaying());
		preview.restart();
		check("replay starts a fresh timeline", preview.progress(0) == 0 && preview.isPlaying());
		preview.seek(2);
		check("dragging beyond the end clamps and pauses", preview.progress(0) == 1 && !preview.isPlaying());
		preview.seek(-1);
		check("dragging before the start clamps", preview.progress(0) == 0);
		preview.seek(Float.NaN);
		check("invalid seek cannot poison later rendering", preview.progress(0) == 0);
	}

	private static void close(String label, float actual, float expected) {
		check(label, Math.abs(actual - expected) < 1e-6f);
	}

	private static void check(String label, boolean condition) {
		if (!condition) throw new AssertionError(label);
		checks++;
	}
}
