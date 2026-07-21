package dev.xkmc.youkaishomecoming.compat.kubejs.spell;

import dev.latvian.mods.kubejs.event.EventJS;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Fired before a dynamic spell is cast. Cancel to block the cast (e.g. insufficient cost).
 */
public class DynamicSpellCastEventJS extends EventJS {

	public final ServerPlayer player;
	public final ItemStack stack;
	public final ResourceLocation spellId;
	public final boolean singleUse;
	public final boolean consume;
	public final boolean cooldown;

	public DynamicSpellCastEventJS(ServerPlayer player, ItemStack stack, ResourceLocation spellId,
								   boolean singleUse, boolean consume, boolean cooldown) {
		this.player = player;
		this.stack = stack;
		this.spellId = spellId;
		this.singleUse = singleUse;
		this.consume = consume;
		this.cooldown = cooldown;
	}
}
