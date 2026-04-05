package dev.xkmc.youkaishomecoming.content.item.danmaku;

import dev.xkmc.l2library.util.raytrace.IGlowingTarget;
import dev.xkmc.l2library.util.raytrace.RayTraceUtil;
import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuProxyEntity;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.item.SpellContainer;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import dev.xkmc.youkaishomecoming.init.registrate.YHEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A generic spell item that reads its SpellDefinition from NBT.
 * Used for datapack/KJS-defined spells that don't have dedicated Java item classes.
 */
public class DynamicSpellItem extends Item implements IGlowingTarget, ISpellItem {

	private static final String TAG_SPELL_ID = "spell_id";
	private static final int DEFAULT_DURATION = 200; // 10 seconds

	public DynamicSpellItem(Properties properties) {
		super(properties);
	}

	@Nullable
	public static SpellDefinition getSpellDefinition(ItemStack stack) {
		if (!stack.hasTag()) return null;
		String id = stack.getTag().getString(TAG_SPELL_ID);
		if (id.isEmpty()) return null;
		return SpellRegistry.get(new ResourceLocation(id));
	}

	public static ItemStack createStack(Item item, ResourceLocation spellId) {
		ItemStack stack = new ItemStack(item);
		stack.getOrCreateTag().putString(TAG_SPELL_ID, spellId.toString());
		return stack;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (GrazeHelper.forbidDanmaku(player))
			return InteractionResultHolder.fail(stack);
		if (!castSpell(stack, player, !player.getAbilities().instabuild, true)) {
			return InteractionResultHolder.fail(stack);
		}
		return InteractionResultHolder.consume(stack);
	}

	@Override
	public boolean castSpell(ItemStack stack, Player player, boolean consume, boolean cooldown) {
		SpellDefinition def = getSpellDefinition(stack);
		if (def == null) return false;

		LivingEntity target = RayTraceUtil.serverGetTarget(player);
		if (target != null) GrazeHelper.addSession(player, target);
		if (target == null && def.itemForm.requiresTarget()) {
			target = GrazeHelper.getTarget(player);
			if (target == null) return false;
		}

		if (player instanceof ServerPlayer sp) {
			int duration = def.itemForm.cooldown() > 0 ? def.itemForm.cooldown() : DEFAULT_DURATION;
			DanmakuProxyEntity proxy = new DanmakuProxyEntity(
					YHEntities.DANMAKU_PROXY.get(), sp.serverLevel());
			proxy.init(sp, def, duration, target);
			sp.serverLevel().addFreshEntity(proxy);
			SpellContainer.trackProxy(sp, proxy);
			if (cooldown) {
				int cd = def.itemForm.cooldown() > 0 ? def.itemForm.cooldown() : YHModConfig.COMMON.playerSpellCooldown.get();
				sp.getCooldowns().addCooldown(this, cd);
			}
		}
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> list, TooltipFlag flag) {
		SpellDefinition def = getSpellDefinition(stack);
		if (def != null) {
			list.add(def.display.displayName().copy().withStyle(ChatFormatting.GOLD));
			if (!def.display.description().isEmpty()) {
				list.add(def.display.displayDesc().copy().withStyle(ChatFormatting.GRAY));
			}
		} else {
			String id = stack.hasTag() ? stack.getTag().getString(TAG_SPELL_ID) : "";
			if (!id.isEmpty()) {
				list.add(Component.literal("Unknown spell: " + id).withStyle(ChatFormatting.RED));
			}
		}
	}

	@Override
	public Component getName(ItemStack stack) {
		SpellDefinition def = getSpellDefinition(stack);
		if (def != null) {
			return def.display.displayName();
		}
		return super.getName(stack);
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity user, int slot, boolean sel) {
		if (user instanceof Player player && level.isClientSide && sel) {
			RayTraceUtil.clientUpdateTarget(player, 64);
		}
	}

	@Override
	public int getDistance(ItemStack itemStack) {
		return 64;
	}
}
