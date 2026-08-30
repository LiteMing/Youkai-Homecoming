package dev.xkmc.youkaishomecoming.content.client;

import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.IItemDecorator;

public class DanmakuItemDeco implements IItemDecorator {

	@Override
	public boolean render(GuiGraphics g, Font font, ItemStack stack, int x, int y) {
		if (DanmakuClientState.isLocalPlayerSuppressed()) {
			g.fill(x, y, x + 16, y + 16, 0x7fff0000);
			return true;
		}
		Player player = Minecraft.getInstance().player;
		if (player == null || GrazeHelper.findSpellCard(player) != stack) return false;

		boolean castable = DanmakuClientState.isSpellCardCastable(player, stack);
		int color = castable ? 0xff20d060 : 0xffff3333; // 可用为原版绿色，B/经验不足或被击破为红色边框
		g.fill(x, y, x + 16, y + 1, color);
		g.fill(x, y + 15, x + 16, y + 16, color);
		g.fill(x, y + 1, x + 1, y + 15, color);
		g.fill(x + 15, y + 1, x + 16, y + 15, color);
		return true;
	}

}
