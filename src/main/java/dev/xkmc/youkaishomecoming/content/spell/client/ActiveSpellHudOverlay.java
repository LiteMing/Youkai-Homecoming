package dev.xkmc.youkaishomecoming.content.spell.client;

import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Compact right-top list for active spell cards. */
public final class ActiveSpellHudOverlay implements IGuiOverlay {

	private static List<ActiveSpellHudToClient.Entry> entries = List.of();

	public static void update(ActiveSpellHudToClient packet) {
		List<ActiveSpellHudToClient.Entry> next = new ArrayList<>();
		if (packet != null && packet.entries != null) {
			for (ActiveSpellHudToClient.Entry entry : packet.entries) {
				if (entry == null || entry.spellId == null || entry.spellId.isBlank()) continue;
				ActiveSpellHudToClient.Entry copy = new ActiveSpellHudToClient.Entry(
						entry.hostId, entry.spellId, entry.displayName, entry.hostile, entry.own);
				next.add(copy);
			}
		}
		next.sort(Comparator.comparing((ActiveSpellHudToClient.Entry e) -> e.hostile).reversed()
				.thenComparing(Comparator.comparing((ActiveSpellHudToClient.Entry e) -> e.own).reversed())
				.thenComparingInt(e -> e.hostId));
		entries = List.copyOf(next);
	}

	@Override
	public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
		if (!YHModConfig.CLIENT.activeSpellHudEnabled.get()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen != null) return;
		if (mc.player == null) {
			entries = List.of();
			return;
		}
		if (entries.isEmpty()) return;

		Font font = gui.getFont();
		float scale = YHModConfig.CLIENT.activeSpellHudScale.get().floatValue();
		int maxTextWidth = Math.max(40, (int) (width / (3.0f * scale)));
		List<String> lines = entries.stream().map(e -> format(e, font, maxTextWidth)).toList();
		int panelWidth = lines.stream().mapToInt(font::width).max().orElse(0) + 7;
		boolean hostileGroup = false;
		for (ActiveSpellHudToClient.Entry entry : entries) {
			if (entry.hostile) {
				hostileGroup = true;
			}
		}
		int groupGap = hostileGroup && entries.stream().anyMatch(e -> !e.hostile) ? 5 : 0;
		int lineStep = font.lineHeight + 3;
		int panelHeight = entries.size() * lineStep + 1 + groupGap;
		int renderedWidth = Math.round(panelWidth * scale);
		int renderedHeight = Math.round(panelHeight * scale);
		int originX = anchored(YHModConfig.CLIENT.activeSpellHudXAnchor.get(),
				YHModConfig.CLIENT.activeSpellHudXOffset.get(), width, renderedWidth);
		int originY = anchored(YHModConfig.CLIENT.activeSpellHudYAnchor.get(),
				YHModConfig.CLIENT.activeSpellHudYOffset.get(), height, renderedHeight);
		if (YHModConfig.CLIENT.activeSpellHudYAnchor.get() == -1) {
			originY = Math.max(originY, gui.rightHeight + 4);
		}

		graphics.pose().pushPose();
		graphics.pose().translate(originX, originY, 0);
		graphics.pose().scale(scale, scale, scale);
		int right = panelWidth - 3;
		int y = 2;
		int index = 0;
		for (ActiveSpellHudToClient.Entry entry : entries) {
			if (!entry.hostile && hostileGroup && index > 0) {
				// Keep the friendly group visually below all hostile cards.
				if (entries.get(index - 1).hostile) y += 5;
			}
			String text = lines.get(index);
			int textWidth = font.width(text);
			int x = right - textWidth;
			int color = entry.hostile ? 0xFFFF8A8A : (entry.own ? 0xFFFFD36A : 0xFF9EDCFF);
			graphics.fill(x - 4, y - 2, right + 3, y + font.lineHeight + 2,
					(entry.hostile ? 0x88300000 : 0x88304050));
			graphics.drawString(font, text, x, y, color, true);
			y += font.lineHeight + 3;
			index++;
		}
		graphics.pose().popPose();
	}

	private static int anchored(int anchor, int offset, int viewport, int content) {
		return offset + (anchor + 1) * (viewport - content) / 2;
	}

	private static String format(ActiveSpellHudToClient.Entry entry, Font font, int maxWidth) {
		String id = entry.spellId == null ? "" : entry.spellId.trim();
		String rawName = entry.displayName == null ? "" : entry.displayName.trim();
		String name = rawName.isEmpty() ? id
				: Component.translatableWithFallback(rawName, rawName).getString();
		if (name.isBlank()) name = id;
		if (font.width(name) <= maxWidth) return name;
		if (!id.isBlank() && !id.equals(name)) {
			String compact = name + " [" + id + "]";
			if (font.width(compact) <= maxWidth) return compact;
		}
		return trim(font, name, maxWidth);
	}

	private static String trim(Font font, String value, int maxWidth) {
		if (font.width(value) <= maxWidth) return value;
		int suffix = font.width("...");
		return font.plainSubstrByWidth(value, Math.max(1, maxWidth - suffix)) + "...";
	}
}
