package dev.xkmc.youkaishomecoming.content.capability;

import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.List;

public class PowerInfoOverlay implements IGuiOverlay {

	public static final GrazeCapability.InfoIcon RESOURCE_ICON = new GrazeCapability.InfoIcon(
			YoukaisHomecoming.loc("textures/gui/elements.png"),
			20, 20
	);
	public static final int ICON_SIZE = 10;
	private static final int BOMB_ICON_X = 0;
	private static final int BOMB_ICON_Y = 0;
	private static final int LIFE_ICON_X = 0;
	private static final int LIFE_ICON_Y = 10;

	@Override
	public void render(ForgeGui gui, GuiGraphics g, float pTick, int w, int h) {
		if (Minecraft.getInstance().screen != null) return;
		var pl = Minecraft.getInstance().player;
		if (pl == null) return;
		var graze = GrazeCapability.HOLDER.get(pl);
		List<GrazeCapability.InfoLine> info = graze.getInfoLines();
		if (info.isEmpty()) return;
		var font = gui.getFont();
		int lh = font.lineHeight + 2;
		int th = lh * info.size();
		int tw = 0;
		for (var e : info) {
			tw = Math.max(tw, font.width(e.text()));
		}
		tw += 14;

		int xa = YHModConfig.CLIENT.powerInfoXAnchor.get();
		int xo = YHModConfig.CLIENT.powerInfoXOffset.get();
		int ya = YHModConfig.CLIENT.powerInfoYAnchor.get();
		int yo = YHModConfig.CLIENT.powerInfoYOffset.get();

		int x = xo + (xa + 1) * (w - tw) / 2;
		int y = yo + (ya + 1) * (h - th) / 2;

		for (var e : info) {
			renderIcon(g, e.icon(), x, y, e.x(), e.y());
			g.drawString(font, e.text(), x + 14, y, 0xffffffff, false);
			y += lh;
		}


	}

	public static void renderBombIcon(GuiGraphics g, int x, int y) {
		renderIcon(g, RESOURCE_ICON, x, y, BOMB_ICON_X, BOMB_ICON_Y);
	}

	public static void renderLifeIcon(GuiGraphics g, int x, int y) {
		renderIcon(g, RESOURCE_ICON, x, y, LIFE_ICON_X, LIFE_ICON_Y);
	}

	public static void renderIcon(GuiGraphics g, GrazeCapability.InfoIcon icon, int x, int y, int u, int v) {
		g.blit(icon.loc(), x, y, u, v, ICON_SIZE, ICON_SIZE, icon.w(), icon.h());
	}

}
