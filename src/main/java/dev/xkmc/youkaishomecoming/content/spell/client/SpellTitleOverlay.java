package dev.xkmc.youkaishomecoming.content.spell.client;

import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDisplay;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellTitleStyle;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Transient introduction cues; only an authoritative HUD row can keep a title visible. */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = YoukaisHomecoming.MODID)
public final class SpellTitleOverlay {

	private static final Map<String, Entry> ACTIVE = new LinkedHashMap<>();

	private SpellTitleOverlay() {}

	public static void show(String titleId, String name, String description, int duration, CompoundTag presentation) {
		if (titleId == null || titleId.isBlank() || ACTIVE.containsKey(titleId)) return;
		SpellTitleStyle style = SpellTitleStyle.fromTag(presentation);
		ACTIVE.put(titleId, new Entry(localize(name), localize(description), Math.max(20, duration), style));
	}

	static boolean isAnimating(String titleId) {
		return ACTIVE.containsKey(titleId);
	}

	static void synchronize(List<String> activeIds) {
		ACTIVE.entrySet().removeIf(cue -> {
			if (activeIds.contains(cue.getKey())) {
				cue.getValue().seenActive = true;
				return false;
			}
			// Allow the first snapshot to arrive after the cue, but cancel an ended card.
			return cue.getValue().seenActive;
		});
	}

	static void clear() {
		ACTIVE.clear();
	}

	static void advance() {
		ACTIVE.values().removeIf(entry -> ++entry.age >= entry.duration);
	}

	@SubscribeEvent
	public static void tick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			clear();
			ActiveSpellHudOverlay.clear();
		} else if (!mc.isPaused()) {
			advance();
		}
	}

	@SubscribeEvent
	public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
		clear();
		ActiveSpellHudOverlay.clear();
	}

	@SubscribeEvent
	public static void unload(LevelEvent.Unload event) {
		if (event.getLevel().isClientSide()) {
			clear();
			ActiveSpellHudOverlay.clear();
		}
	}

	static void renderIntroductions(GuiGraphics g, Font font, float partialTick, int width, int height,
			List<ActiveSpellHudOverlay.Row> rows) {
		int index = 0;
		for (var cue : ACTIVE.entrySet()) {
			Entry entry = cue.getValue();
			var destination = rows.stream().filter(row -> row.titleId().equals(cue.getKey())).findFirst().orElse(null);
			float progress = Mth.clamp((entry.age + partialTick) / entry.duration, 0, 1);
			entry.renderer.render(g, font, entry.title, entry.description, entry.style,
					progress, width, height, index++, destination);
		}
	}

	private static String localize(String value) {
		return value == null || value.isBlank() ? "" : SpellDisplay.displayText(value).getString();
	}

	private static final class Entry {
		final String title, description;
		final int duration;
		final SpellTitleStyle style;
		int age;
		boolean seenActive;
		final SpellTitleRenderer renderer = new SpellTitleRenderer();

		Entry(String title, String description, int duration, SpellTitleStyle style) {
			this.title = title;
			this.description = description;
			this.duration = duration;
			this.style = style;
		}
	}
}
