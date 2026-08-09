package dev.xkmc.youkaishomecoming.content.item.danmaku;

import dev.xkmc.l2library.util.raytrace.IGlowingTarget;
import dev.xkmc.l2library.util.raytrace.RayTraceUtil;
import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import dev.xkmc.youkaishomecoming.content.spell.item.ItemSpell;
import dev.xkmc.youkaishomecoming.content.spell.item.SpellContainer;
import dev.xkmc.youkaishomecoming.init.data.YHLangData;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class SpellItem extends ProjectileWeaponItem implements IGlowingTarget, ISpellItem {

	public static final List<SpellItem> LIST = new ArrayList<>();

	private static final String TAG_SINGLE_USE = "single_use";

	private final Supplier<ItemSpell> spell;
	private final boolean requireTarget;
	private final Supplier<Item> pred;

	public SpellItem(Properties prop, Supplier<ItemSpell> spell, boolean requireTarget, Supplier<Item> pred) {
		super(prop);
		this.spell = spell;
		this.requireTarget = requireTarget;
		this.pred = pred;
		synchronized (LIST) {
			LIST.add(this);
		}
	}

	public static boolean isSingleUse(ItemStack stack) {
		return stack.hasTag() && stack.getTag().getBoolean(TAG_SINGLE_USE);
	}

	public static void setSingleUse(ItemStack stack, boolean singleUse) {
		if (singleUse) {
			stack.getOrCreateTag().putBoolean(TAG_SINGLE_USE, true);
		} else if (stack.hasTag()) {
			stack.getTag().remove(TAG_SINGLE_USE);
		}
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.isShiftKeyDown() && GrazeHelper.isManualCombatMode()) {
			if (!level.isClientSide) {
				GrazeHelper.tryToggleManualCombat(player);
			}
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
		}
		if (GrazeHelper.forbidSpellCardWithMessage(player))
			return InteractionResultHolder.fail(stack);
		boolean consume = !player.getAbilities().instabuild && !(player instanceof FakePlayer);
		if (!castSpell(stack, player, consume, true)) {
			return InteractionResultHolder.fail(stack);
		}
		return InteractionResultHolder.consume(stack);
	}

	@Override
	public boolean castSpell(ItemStack stack, Player player, boolean consume, boolean cooldown) {
		if (GrazeHelper.forbidSpellCardWithMessage(player)) return false;
		LivingEntity target = GrazeHelper.resolveSpellTarget(player);
		if (player instanceof ServerPlayer sp) {
			if (consume && !SpellItemCost.tryPay(sp, 0)) {
				return false;
			}
			SpellContainer.castSpell(sp, spell, target);
			if (cooldown) {
				int cd = YHModConfig.COMMON.playerSpellCooldown.get();
				sp.getCooldowns().addCooldown(this, cd);
			}
			if (isSingleUse(stack)) {
				stack.shrink(1);
			}
		}
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> list, TooltipFlag flag) {
		if (GrazeHelper.isManualCombatMode()) {
			list.add(YHLangData.STG_TOGGLE_TIP.get());
		}
		SpellItemCost.appendCostTooltip(list, 0);
		if (isSingleUse(stack)) {
			list.add(YHLangData.SPELL_SINGLE_USE.get());
		}
		if (requireTarget) {
			list.add(YHLangData.SPELL_TARGET.get());
		}
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity user, int slot, boolean sel) {
		if (user instanceof Player player && level.isClientSide && sel) {
			RayTraceUtil.clientUpdateTarget(player, GrazeHelper.SPELL_TARGET_RANGE);
		}
	}

	@Override
	public Predicate<ItemStack> getAllSupportedProjectiles() {
		return e -> e.is(pred.get());
	}

	@Override
	public int getDefaultProjectileRange() {
		return 40;
	}

	@Override
	public int getDistance(ItemStack itemStack) {
		return GrazeHelper.SPELL_TARGET_RANGE;
	}

}
