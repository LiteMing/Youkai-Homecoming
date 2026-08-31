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
		if (player == null || !GrazeHelper.isSpellStack(stack)) return false;

		// Green marks exactly the next card that can be released. Every other
		// cast-ready card that is broken or cannot pay is red, so it is visibly
		// skipped without hiding a later usable card.
		boolean castable = DanmakuClientState.isSpellCardCastable(player, stack);
		boolean selected = DanmakuClientState.findNextCastableSpellCard(player) == stack;
		if (!selected && castable) return false;
		int color = selected ? 0xff20d060 : 0xffff3333;
		g.fill(x, y, x + 16, y + 1, color);
		g.fill(x, y + 15, x + 16, y + 16, color);
		g.fill(x, y + 1, x + 1, y + 15, color);
		g.fill(x + 15, y + 1, x + 16, y + 15, color);
		return true;
	}

}
