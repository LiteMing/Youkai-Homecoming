package dev.xkmc.youkaishomecoming.content.capability;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.LinkedHashMap;
import java.util.Map;

public class PvpDanmakuStatusOverlay implements IGuiOverlay {

	private static final int SHARD = 5;
	private static final int MIN_ENTRY_WIDTH = 64;
	private static final int ICON_TEXT_GAP = 4;
	private static final int RESOURCE_GAP = 14;
	private static final int EXPIRE_MS = 1500;

	private static final Map<Integer, Status> STATUS = new LinkedHashMap<>();

	public static void update(PvpDanmakuStatusToClient packet) {
		if (!packet.active) {
			if (packet.entityId < 0) {
				STATUS.clear();
			} else {
				STATUS.remove(packet.entityId);
			}
			return;
		}
		STATUS.put(packet.entityId, new Status(
				packet.name == null ? "" : packet.name,
				Math.max(0, packet.life),
				Math.max(0, packet.bomb),
				Math.max(0, packet.maxLife),
				Math.max(0, packet.maxBomb),
				new SpellProgress(Math.max(0, packet.spellHealth), Math.max(0, packet.spellMaxHealth),
						Math.max(0, packet.spellElapsedTicks), Math.max(0, packet.spellDurationTicks),
						Util.getMillis()),
				Util.getMillis()
		));
	}

	public record SpellProgress(int health, int maxHealth, int elapsedTicks, int durationTicks, long receivedAt) {
		public boolean active() {
			return maxHealth > 0 || durationTicks > 0;
		}
	}

	@org.jetbrains.annotations.Nullable
	public static SpellProgress spellProgress(int entityId) {
		Status status = STATUS.get(entityId);
		return status == null || !status.spell().active() ? null : status.spell();
	}

	@Override
	public void render(ForgeGui gui, GuiGraphics g, float pTick, int width, int height) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen != null || mc.player == null) return;
		long now = Util.getMillis();
		STATUS.entrySet().removeIf(e -> now - e.getValue().time > EXPIRE_MS);
		if (STATUS.isEmpty()) return;

		var font = gui.getFont();
		int y = 12;
		for (Status status : STATUS.values()) {
			String name = status.name();
			String bomb = "x" + resourceCount(status.bomb());
			String life = "x" + resourceCount(status.life());
			int bombWidth = PowerInfoOverlay.ICON_SIZE + ICON_TEXT_GAP + font.width(bomb);
			int lifeWidth = PowerInfoOverlay.ICON_SIZE + ICON_TEXT_GAP + font.width(life);
			int rowWidth = bombWidth + RESOURCE_GAP + lifeWidth;
			int entryWidth = Math.max(MIN_ENTRY_WIDTH, Math.max(rowWidth, font.width(name)));
			int x = (width - entryWidth) / 2;
			if (!name.isEmpty()) {
				int nameX = x + (entryWidth - font.width(name)) / 2;
				g.drawString(font, name, nameX, y, 0xFFFFFFFF, true);
			}
			int rowX = x + (entryWidth - rowWidth) / 2;
			int rowY = y + 10;
			PowerInfoOverlay.renderBombIcon(g, rowX, rowY);
			g.drawString(font, bomb, rowX + PowerInfoOverlay.ICON_SIZE + ICON_TEXT_GAP, rowY, 0xFFFFFFFF, true);
			rowX += bombWidth + RESOURCE_GAP;
			PowerInfoOverlay.renderLifeIcon(g, rowX, rowY);
			g.drawString(font, life, rowX + PowerInfoOverlay.ICON_SIZE + ICON_TEXT_GAP, rowY, 0xFFFFFFFF, true);
			y += 24;
		}
	}

	private static int resourceCount(int value) {
		return Math.max(0, value / SHARD);
	}

	private record Status(String name, int life, int bomb, int maxLife, int maxBomb,
						  SpellProgress spell, long time) {
	}

}
