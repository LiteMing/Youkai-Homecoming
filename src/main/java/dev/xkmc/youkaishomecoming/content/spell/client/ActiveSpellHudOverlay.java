package dev.xkmc.youkaishomecoming.content.spell.client;

import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDisplay;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellTitleStyle;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Server-owned active cards and their introductions share one layout/render pass. */
public final class ActiveSpellHudOverlay implements IGuiOverlay {

	private record Entry(ActiveSpellHudToClient.Entry source, SpellTitleStyle style) {}
	public record Row(String titleId, String text, float x, float y, float scale, int color, SpellTitleStyle style) {}

	private static List<Entry> entries = List.of();

	public static void update(ActiveSpellHudToClient packet) {
		List<Entry> next = new ArrayList<>();
		if (packet != null && packet.entries != null) {
			for (ActiveSpellHudToClient.Entry entry : packet.entries) {
				if (entry == null || entry.spellId == null || entry.spellId.isBlank()) continue;
				var copy = new ActiveSpellHudToClient.Entry(entry.hostId, entry.spellId, entry.displayName,
						entry.hostile, entry.own, entry.titleId, entry.presentation);
				next.add(new Entry(copy, SpellTitleStyle.fromTag(copy.presentation)));
			}
		}
		next.sort(Comparator.comparing((Entry e) -> e.source().hostile).reversed()
				.thenComparing(Comparator.comparing((Entry e) -> e.source().own).reversed())
				.thenComparingInt(e -> e.source().hostId));
		entries = List.copyOf(next);
		SpellTitleOverlay.synchronize(entries.stream().map(e -> e.source().titleId).toList());
	}

	static void clear() {
		entries = List.of();
	}

	@Override
	public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			clear();
			SpellTitleOverlay.clear();
			return;
		}
		if (mc.screen != null || mc.options.hideGui) return;
		List<Row> rows = YHModConfig.CLIENT.activeSpellHudEnabled.get()
				? layout(gui.getFont(), width, height, gui.rightHeight, entries) : List.of();
		for (Row row : rows) {
			if (!SpellTitleOverlay.isAnimating(row.titleId())) {
				SpellTitleRenderer.drawTitle(graphics, gui.getFont(), row.text(), row.x(), row.y(), row.scale(),
						row.color(), row.style(), 1);
			}
		}
		SpellTitleOverlay.renderIntroductions(graphics, gui.getFont(), partialTick, width, height, rows);
	}

	/** A local single-card projection; never changes the server-synchronized HUD entries. */
	public static Row previewRow(Font font, int width, int height, int rightHeight,
			String spellId, String name, SpellTitleStyle style) {
		var source = new ActiveSpellHudToClient.Entry(0, spellId, name, true, false);
		return layout(font, width, height, rightHeight, List.of(new Entry(source, style))).get(0);
	}

	private static List<Row> layout(Font font, int width, int height, int rightHeight, List<Entry> entries) {
		if (entries.isEmpty()) return List.of();
		float scale = YHModConfig.CLIENT.activeSpellHudScale.get().floatValue();
		int maxTextWidth = Math.max(1, (int) (width / (3.0f * scale)));
		List<String> lines = entries.stream().map(e -> format(e.source(), font, maxTextWidth)).toList();
		int panelWidth = lines.stream().mapToInt(font::width).max().orElse(0) + 7;
		boolean hostileGroup = entries.stream().anyMatch(e -> e.source().hostile);
		int groupGap = hostileGroup && entries.stream().anyMatch(e -> !e.source().hostile) ? 5 : 0;
		int lineStep = font.lineHeight + 3;
		int panelHeight = entries.size() * lineStep + 1 + groupGap;
		int originX = anchored(YHModConfig.CLIENT.activeSpellHudXAnchor.get(),
				YHModConfig.CLIENT.activeSpellHudXOffset.get(), width, Math.round(panelWidth * scale));
		int originY = anchored(YHModConfig.CLIENT.activeSpellHudYAnchor.get(),
				YHModConfig.CLIENT.activeSpellHudYOffset.get(), height, Math.round(panelHeight * scale));
		if (YHModConfig.CLIENT.activeSpellHudYAnchor.get() == -1) {
			originY = Math.max(originY, rightHeight + 4);
		}
		List<Row> rows = new ArrayList<>();
		int y = 2;
		for (int i = 0; i < entries.size(); i++) {
			Entry entry = entries.get(i);
			if (!entry.source().hostile && i > 0 && entries.get(i - 1).source().hostile) y += 5;
			String text = lines.get(i);
			int color = entry.source().hostile ? 0xFFFF8A8A : entry.source().own ? 0xFFFFD36A : 0xFF9EDCFF;
			rows.add(new Row(entry.source().titleId, text, originX + (panelWidth - 3 - font.width(text)) * scale,
					originY + y * scale, scale, color, entry.style()));
			y += lineStep;
		}
		return rows;
	}

	private static int anchored(int anchor, int offset, int viewport, int content) {
		return offset + (anchor + 1) * (viewport - content) / 2;
	}

	private static String format(ActiveSpellHudToClient.Entry entry, Font font, int maxWidth) {
		String name = entry.displayName.isBlank() ? entry.spellId : SpellDisplay.displayText(entry.displayName).getString();
		if (name.isBlank()) name = entry.spellId;
		return fit(font, name, maxWidth);
	}

	static String fit(Font font, String value, int maxWidth) {
		if (font.width(value) <= maxWidth) return value;
		int suffix = font.width("...");
		return font.plainSubstrByWidth(value, Math.max(0, maxWidth - suffix)) + "...";
	}
}
