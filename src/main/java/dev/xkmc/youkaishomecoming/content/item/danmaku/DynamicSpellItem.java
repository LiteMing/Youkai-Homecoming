package dev.xkmc.youkaishomecoming.content.item.danmaku;

import dev.xkmc.l2library.util.raytrace.IGlowingTarget;
import dev.xkmc.l2library.util.raytrace.RayTraceUtil;
import dev.xkmc.youkaishomecoming.compat.kubejs.spell.DynamicSpellCastEventJS;
import dev.xkmc.youkaishomecoming.compat.kubejs.spell.DynamicSpellSingleUseEventJS;
import dev.xkmc.youkaishomecoming.compat.kubejs.spell.YHSpellKubeJSEvents;
import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuProxyEntity;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.item.SpellContainer;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.init.data.YHLangData;
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
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A generic spell item that reads its SpellDefinition from NBT.
 * Used for datapack/KJS-defined spells that don't have dedicated Java item classes.
 */
public class DynamicSpellItem extends Item implements IGlowingTarget, ISpellItem {

	private static final String TAG_SPELL_ID = "spell_id";
	private static final String TAG_DURATION = "duration";
	private static final String TAG_SINGLE_USE = "single_use";
	/** Sentinel: run until the spell naturally finishes (no fixed duration). */
	public static final int DURATION_NATURAL = -1;

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

	@Nullable
	public static ResourceLocation getSpellId(ItemStack stack) {
		if (!stack.hasTag()) return null;
		String id = stack.getTag().getString(TAG_SPELL_ID);
		if (id.isEmpty()) return null;
		return new ResourceLocation(id);
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

	public static ItemStack createStack(Item item, ResourceLocation spellId) {
		return createStack(item, spellId, false);
	}

	public static ItemStack createStack(Item item, ResourceLocation spellId, boolean singleUse) {
		ItemStack stack = new ItemStack(item);
		stack.getOrCreateTag().putString(TAG_SPELL_ID, spellId.toString());
		setSingleUse(stack, singleUse);
		return stack;
	}

	/** Create a stack with an explicit fixed duration (for /yhspell give). */
	public static ItemStack createStackWithDuration(Item item, ResourceLocation spellId, int duration) {
		return createStackWithDuration(item, spellId, duration, false);
	}

	public static ItemStack createStackWithDuration(Item item, ResourceLocation spellId, int duration, boolean singleUse) {
		ItemStack stack = createStack(item, spellId, singleUse);
		stack.getOrCreateTag().putInt(TAG_DURATION, duration);
		return stack;
	}

	/** Read duration from NBT: -1 if not set (natural end mode). */
	public static int getStackDuration(ItemStack stack) {
		if (stack.hasTag() && stack.getTag().contains(TAG_DURATION)) {
			return stack.getTag().getInt(TAG_DURATION);
		}
		return DURATION_NATURAL;
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
		ResourceLocation spellId = getSpellId(stack);
		if (spellId == null) return false;

		boolean singleUse = isSingleUse(stack);

		// Certified items (design doc §15, §22): resolve the immutable definition from
		// world certificate storage, verify hash and capability policy; tampered NBT,
		// overwritten storage or revoked capabilities reject the cast.
		if (player instanceof ServerPlayer sp0 && CertifiedSpellValidator.isCertified(stack)) {
			SpellDefinition certified = CertifiedSpellValidator.resolveCertifiedDefinition(sp0, stack);
			if (certified == null) {
				sp0.displayClientMessage(YHLangData.CERT_CAST_REJECTED.get(), false);
				return false;
			}
			def = certified;
		}

		if (player instanceof ServerPlayer sp && ModList.get().isLoaded("kubejs")
				&& YHSpellKubeJSEvents.DYNAMIC_SPELL_CAST.hasListeners()) {
			var castEvent = new DynamicSpellCastEventJS(sp, stack, spellId, singleUse, consume, cooldown);
			if (YHSpellKubeJSEvents.DYNAMIC_SPELL_CAST.post(castEvent).interruptFalse()) {
				return false;
			}
		}

		LivingEntity target = GrazeHelper.resolveSpellTarget(player);

		if (player instanceof ServerPlayer sp) {
			if (consume && !SpellItemCost.tryPay(sp)) {
				return false;
			}
			int duration = getStackDuration(stack);
			if (duration == DURATION_NATURAL && def.itemForm.cooldown() > 0) {
				// item_form.cooldown acts as fixed duration when set, unless overridden by NBT
				duration = def.itemForm.cooldown();
			}
			if (CertifiedSpellValidator.isCertified(stack)) {
				// certified duration is the hard maximum (§2.4)
				int certifiedDuration = CertifiedSpellValidator.getCertifiedDuration(stack);
				if (duration == DURATION_NATURAL || duration > certifiedDuration) {
					duration = certifiedDuration;
				}
			}
			DanmakuProxyEntity proxy = new DanmakuProxyEntity(
					YHEntities.DANMAKU_PROXY.get(), sp.serverLevel());
			proxy.init(sp, def, duration, target);
			sp.serverLevel().addFreshEntity(proxy);
			SpellContainer.trackProxy(sp, proxy);
			if (cooldown) {
				int cd = def.itemForm.cooldown() > 0 ? def.itemForm.cooldown() : YHModConfig.COMMON.playerSpellCooldown.get();
				sp.getCooldowns().addCooldown(this, cd);
			}
			if (singleUse) {
				ItemStack consumed = stack.copy();
				consumed.setCount(1);
				stack.shrink(1);
				if (ModList.get().isLoaded("kubejs") && YHSpellKubeJSEvents.DYNAMIC_SPELL_SINGLE_USE.hasListeners()) {
					YHSpellKubeJSEvents.DYNAMIC_SPELL_SINGLE_USE.post(
							new DynamicSpellSingleUseEventJS(sp, consumed, spellId));
				}
			}
		}
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> list, TooltipFlag flag) {
		if (GrazeHelper.isManualCombatMode()) {
			list.add(YHLangData.STG_TOGGLE_TIP.get());
		}
		SpellItemCost.appendCostTooltip(list);
		if (isSingleUse(stack)) {
			list.add(YHLangData.SPELL_SINGLE_USE.get());
		}
		SpellDefinition def = getSpellDefinition(stack);
		if (def != null) {
			list.add(def.display.displayName().copy().withStyle(ChatFormatting.GOLD));
			if (!def.display.description().isEmpty()) {
				list.add(def.display.displayDesc().copy().withStyle(ChatFormatting.GRAY));
			}
			int dur = getStackDuration(stack);
			if (dur > 0) {
				list.add(Component.literal("Duration: " + dur + "t").withStyle(ChatFormatting.DARK_GRAY));
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
			RayTraceUtil.clientUpdateTarget(player, GrazeHelper.SPELL_TARGET_RANGE);
		}
	}

	@Override
	public int getDistance(ItemStack itemStack) {
		return GrazeHelper.SPELL_TARGET_RANGE;
	}
}
