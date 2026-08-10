package dev.xkmc.youkaishomecoming.content.item.danmaku;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public interface ISpellItem {

	/** Whether this stack is eligible for automatic/API spell-card selection. */
	default boolean isCastReady(ItemStack stack) {
		return true;
	}

	boolean castSpell(ItemStack stack, Player player, boolean consume, boolean cooldown);

}
