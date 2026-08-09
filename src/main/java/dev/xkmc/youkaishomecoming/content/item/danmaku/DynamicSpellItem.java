package dev.xkmc.youkaishomecoming.content.item.danmaku;

import dev.xkmc.l2library.util.raytrace.IGlowingTarget;
import dev.xkmc.l2library.util.raytrace.RayTraceUtil;
import dev.xkmc.youkaishomecoming.compat.kubejs.spell.DynamicSpellCastEventJS;
import dev.xkmc.youkaishomecoming.compat.kubejs.spell.DynamicSpellSingleUseEventJS;
import dev.xkmc.youkaishomecoming.compat.kubejs.spell.YHSpellKubeJSEvents;
import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuProxyEntity;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellHash;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationManager;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellValidator;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.item.SpellContainer;
import dev.xkmc.youkaishomecoming.content.spell.preview.OpenSpellPreviewToClient;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHLangData;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
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
	private static final String TAG_COLOR = "SpellColor";
	/** Mark on OP-given cards: a complete spell card that casts directly (no editor). */
	private static final String TAG_COMPLETE = "complete";
	/**
	 * Special-node quota: how many EXPERIMENTAL nodes (teleport, confine, erase,
	 * clear, flags, force/fire spell, on_damage / fire / laser hooks) the final
	 * certified card may carry. Derives from the boss's original definition;
	 * run_command stays operator-only and is never part of a quota.
	 */
	private static final String TAG_OP_QUOTA = "yh_op_quota";
	/** Sentinel: run until the spell naturally finishes (no fixed duration). */
	public static final int DURATION_NATURAL = -1;

	public DynamicSpellItem(Properties properties) {
		super(properties);
	}

	/** Spell color stored like dynamic-color danmaku (DanmakuColor.argb in NBT). */
	public static ItemStack withColor(ItemStack stack, dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuColor color) {
		stack.getOrCreateTag().putInt(TAG_COLOR, color.argb());
		return stack;
	}

	/** Random #RRGGBB spell color (used by certified rewards). */
	public static ItemStack withRandomColor(ItemStack stack, net.minecraft.util.RandomSource random) {
		int rgb = 0xFF000000 | (random.nextInt(256) << 16) | (random.nextInt(256) << 8) | random.nextInt(256);
		stack.getOrCreateTag().putInt(TAG_COLOR, rgb);
		return stack;
	}

	public static dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuColor getColor(ItemStack stack) {
		if (stack.hasTag() && stack.getTag().contains(TAG_COLOR)) {
			return new dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuColor(stack.getTag().getInt(TAG_COLOR));
		}
		return dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuColor.WHITE;
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

	/** Complete (OP-given) cards cast directly; only unfinished self-made cards open the editor. */
	public static boolean isComplete(ItemStack stack) {
		return stack.hasTag() && stack.getTag().getBoolean(TAG_COMPLETE);
	}

	public static void setComplete(ItemStack stack, boolean complete) {
		if (complete) {
			stack.getOrCreateTag().putBoolean(TAG_COMPLETE, true);
		} else if (stack.hasTag()) {
			stack.getTag().remove(TAG_COMPLETE);
		}
	}

	/**
	 * Special-node quota of a draft card: derived dynamically from the ORIGINAL
	 * (built-in default) definition bound by spell_id — the count of special
	 * nodes (teleport/confine/erase/clear/flag/force/fire + on_damage / fire /
	 * laser hooks) in the boss's own spell. Stable: player edits to the working
	 * definition never change the quota. run_command is never part of it.
	 */
	public static int getOpQuota(ItemStack stack) {
		ResourceLocation id = getSpellId(stack);
		if (id == null) return 0;
		SpellDefinition original = SpellRegistry.getDefault(id);
		if (original == null) return 0;
		return dev.xkmc.youkaishomecoming.content.spell.analysis.SpecialNodeCounter.count(original);
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

	/**
	 * Bind a spell id onto a BLANK card in place (the card the player was holding
	 * when they opened the editor). Idempotent: cards already bound to an id are
	 * never rebound — one spell card binds exactly one spell id.
	 */
	public static void bindSpellId(ItemStack stack, ResourceLocation spellId) {
		if (stack.hasTag() && stack.getTag().contains(TAG_SPELL_ID)) {
			return;
		}
		stack.getOrCreateTag().putString(TAG_SPELL_ID, spellId.toString());
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
		// State machine: blank card -> create a self-made spell in the editor;
		// unfinished card (bound, not certified, not complete) -> re-enter the
		// editor. Certified (complete) and OP-given (complete) cards cast.
		if (getSpellId(stack) == null) {
			if (!level.isClientSide && player instanceof ServerPlayer sp) {
				YoukaisHomecoming.HANDLER.toClientPlayer(OpenSpellPreviewToClient.draftEditor(), sp);
			}
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
		}
		if (!CertifiedSpellValidator.isCertified(stack) && !isComplete(stack)) {
			if (!level.isClientSide && player instanceof ServerPlayer sp) {
				SpellDefinition def = getSpellDefinition(stack);
				if (def == null) {
					sp.displayClientMessage(Component.literal("Unknown spell: " + getSpellId(stack)), false);
					return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
				}
				OpenSpellPreviewToClient.sendPreview(sp, def);
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
			// No-bomb/no-hit certification rule: casting a DIFFERENT spell during
			// the trial fails it (the certified spell itself is the allowed attack).
			var trial = dev.xkmc.youkaishomecoming.content.spell.certification.CertificationManager.INSTANCE.getActiveTrial(sp);
			if (trial != null && trial.isActive() && !trial.definitionHash().equals(SpellHash.canonicalHash(def))) {
				trial.onPlayerCastsOtherSpell();
			}
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
			if (!CertifiedSpellValidator.isCertified(stack)) {
				list.add(YHLangData.SPELL_UNFINISHED.get());
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
