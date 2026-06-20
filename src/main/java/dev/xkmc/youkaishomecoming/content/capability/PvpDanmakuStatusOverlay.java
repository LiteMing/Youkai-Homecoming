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
	private static final int BAR_WIDTH = 182;
	private static final int BAR_HEIGHT = 8;
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
				Util.getMillis()
		));
	}

	@Override
	public void render(ForgeGui gui, GuiGraphics g, float pTick, int width, int height) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen != null || mc.player == null) return;
		long now = Util.getMillis();
		STATUS.entrySet().removeIf(e -> now - e.getValue().time > EXPIRE_MS);
		if (STATUS.isEmpty()) return;

		var font = gui.getFont();
		int x = (width - BAR_WIDTH) / 2;
		int y = 12;
		for (Status status : STATUS.values()) {
			String name = status.name();
			if (!name.isEmpty()) {
				int nameX = x + (BAR_WIDTH - font.width(name)) / 2;
				g.drawString(font, name, nameX, y, 0xFFFFFFFF, true);
			}
			int barY = y + 10;
			renderBombBar(g, x, barY, status.bomb(), status.maxBomb());
			String life = "x" + resourceCount(status.life());
			g.drawString(font, life, x + BAR_WIDTH + 6, barY, 0xFFFFDD88, true);
			y += 24;
		}
	}

	private static void renderBombBar(GuiGraphics g, int x, int y, int bomb, int maxBomb) {
		int segments = Math.max(1, resourceCount(Math.max(SHARD, maxBomb)));
		g.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, 0xFF100B18);
		g.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xFF21162B);
		for (int i = 0; i < segments; i++) {
			int sx = x + i * BAR_WIDTH / segments;
			int ex = x + (i + 1) * BAR_WIDTH / segments;
			int innerX0 = sx + 1;
			int innerX1 = ex - 1;
			if (innerX1 <= innerX0) continue;
			double segMin = maxBomb * i / (double) segments;
			double segMax = maxBomb * (i + 1) / (double) segments;
			double fill = (bomb - segMin) / Math.max(1.0, segMax - segMin);
			fill = Math.max(0, Math.min(1, fill));
			if (fill > 0) {
				int fillX = innerX0 + (int) Math.round((innerX1 - innerX0) * fill);
				g.fill(innerX0, y + 1, fillX, y + BAR_HEIGHT - 1, 0xFF3B72D9);
				g.fill(innerX0, y + 1, fillX, y + 3, 0xFF86C5FF);
			}
			if (i > 0) {
				g.fill(sx, y, sx + 1, y + BAR_HEIGHT, 0xFF100B18);
			}
		}
	}

	private static int resourceCount(int value) {
		return Math.max(0, value / SHARD);
	}

	private record Status(String name, int life, int bomb, int maxLife, int maxBomb, long time) {
	}

}
