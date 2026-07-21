package dev.xkmc.youkaishomecoming.compat.kubejs.spell;

import dev.latvian.mods.kubejs.event.EventJS;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Fired after a single-use dynamic spell was successfully cast and the stack was consumed.
 */
public class DynamicSpellSingleUseEventJS extends EventJS {

	public final ServerPlayer player;
	public final ItemStack stack;
	public final ResourceLocation spellId;

	public DynamicSpellSingleUseEventJS(ServerPlayer player, ItemStack stack, ResourceLocation spellId) {
		this.player = player;
		this.stack = stack;
		this.spellId = spellId;
	}
}
