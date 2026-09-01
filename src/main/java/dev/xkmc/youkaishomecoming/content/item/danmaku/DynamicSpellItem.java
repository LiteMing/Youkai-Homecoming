package dev.xkmc.youkaishomecoming.content.item.danmaku;

import dev.xkmc.l2library.util.raytrace.IGlowingTarget;
import dev.xkmc.l2library.util.raytrace.RayTraceUtil;
import dev.xkmc.youkaishomecoming.compat.kubejs.spell.DynamicSpellCastEventJS;
import dev.xkmc.youkaishomecoming.compat.kubejs.spell.DynamicSpellSingleUseEventJS;
import dev.xkmc.youkaishomecoming.compat.kubejs.spell.YHSpellKubeJSEvents;
import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuProxyEntity;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationManager;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellValidator;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellCardType;
import dev.xkmc.youkaishomecoming.content.spell.analysis.NonSpellValidator;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysisException;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellHealthPlan;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellDraftBudget;
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
import java.util.Locale;

/**
 * A generic spell item that reads its SpellDefinition from NBT.
 * Used for datapack/KJS-defined spells that don't have dedicated Java item classes.
 */
public class DynamicSpellItem extends Item implements IGlowingTarget, ISpellItem {

	private static final String TAG_SPELL_ID = "spell_id";
	private static final String TAG_DURATION = "duration";
	private static final String TAG_SINGLE_USE = "single_use";
	private static final String TAG_COLOR = "SpellColor";
	private static final String TAG_RANK = "yh_spell_rank";
	private static final String TAG_CARD_TYPE = "yh_spell_card_type";
	private static final String TAG_EX_SPELL = "yh_ex_spell";
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

	@Override
	public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
		consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
			@Override
			public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
				return dev.xkmc.youkaishomecoming.client.render.SpellCardItemRenderer.INSTANCE;
			}
		});
	}

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
		ResourceLocation id = getSpellId(stack);
		return id == null ? null : SpellRegistry.get(id);
	}

	@Nullable
	public static ResourceLocation getSpellId(ItemStack stack) {
		if (!stack.hasTag()) return null;
		String id = stack.getTag().getString(TAG_SPELL_ID);
		if (id.isEmpty()) return null;
		return ResourceLocation.tryParse(id);
	}

	public static String playerSpellNamespace(Player player) {
		String name = player.getGameProfile().getName().toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9_.-]", "_");
		return name.isBlank() ? player.getStringUUID().toLowerCase(Locale.ROOT) : name;
	}

	public static dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCardRank getRank(ItemStack stack) {
		if (stack.hasTag() && stack.getTag().contains(TAG_RANK)) {
			return dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCardRank.byName(stack.getTag().getString(TAG_RANK));
		}
		return dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCardRank.LESSER_WISDOM;
	}

	public static void setRank(ItemStack stack, dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCardRank rank) {
		if (rank != null) {
			stack.getOrCreateTag().putString(TAG_RANK, rank.getSerializedName());
		} else if (stack.hasTag()) {
			stack.getTag().remove(TAG_RANK);
		}
	}

	public static boolean isSingleUse(ItemStack stack) {
		return stack.hasTag() && stack.getTag().getBoolean(TAG_SINGLE_USE);
	}

	/** Complete (OP-given) cards cast directly; only unfinished self-made cards open the editor. */
	public static boolean isComplete(ItemStack stack) {
		return stack.hasTag() && stack.getTag().getBoolean(TAG_COMPLETE);
	}

	@Override
	public boolean isCastReady(ItemStack stack) {
		SpellDefinition definition = getSpellDefinition(stack);
		return definition != null && isNonSpell(stack)
				|| CertifiedSpellValidator.isCertified(stack) || isComplete(stack);
	}

	public static SpellCardType getCardType(ItemStack stack) {
		if (stack.hasTag() && stack.getTag().contains(TAG_CARD_TYPE)) {
			return SpellCardType.byName(stack.getTag().getString(TAG_CARD_TYPE));
		}
		SpellDefinition definition = getSpellDefinition(stack);
		return definition == null || definition.itemForm.cardType() == null
				? SpellCardType.NORMAL : definition.itemForm.cardType();
	}

	public static boolean isNonSpell(ItemStack stack) {
		return getCardType(stack).isNonSpell();
	}

	public static boolean isExSpell(ItemStack stack) {
		if (stack.hasTag() && stack.getTag().contains(TAG_EX_SPELL)) return stack.getTag().getBoolean(TAG_EX_SPELL);
		SpellDefinition definition = getSpellDefinition(stack);
		return definition != null && definition.itemForm.exSpell();
	}

	public static void setExSpell(ItemStack stack, boolean enabled) {
		if (enabled) stack.getOrCreateTag().putBoolean(TAG_EX_SPELL, true);
		else if (stack.hasTag()) stack.getTag().remove(TAG_EX_SPELL);
	}

	/** Sets a pre-certification type projection supplied by an aura conversion. */
	public static void setCardType(ItemStack stack, SpellCardType type) {
		if (type == null || type == SpellCardType.NORMAL) {
			if (stack.hasTag()) stack.getTag().remove(TAG_CARD_TYPE);
		} else {
			stack.getOrCreateTag().putString(TAG_CARD_TYPE, type.getSerializedName());
		}
	}

	/** Applies one-use aura traits from an unfinished card to its authoritative definition. */
	public static SpellDefinition applyDraftTraits(ItemStack stack, SpellDefinition definition) {
		if (stack == null || definition == null || CertifiedSpellValidator.isCertified(stack)) return definition;
		SpellCardType type = getCardType(stack);
		boolean ex = isExSpell(stack);
		if (type == SpellCardType.NON_SPELL && ex) {
			throw new IllegalArgumentException("Non-spells cannot carry the EX spell-health trait");
		}
		return new SpellDefinition(definition.id, definition.display,
				definition.itemForm.withCardType(type).withExSpell(ex), definition.entryPhase,
				definition.phases, definition.difficulty, definition.customNames);
	}

	public static void setComplete(ItemStack stack, boolean complete) {
		if (complete) {
			stack.getOrCreateTag().putBoolean(TAG_COMPLETE, true);
		} else if (stack.hasTag()) {
			stack.getTag().remove(TAG_COMPLETE);
		}
	}

	/**
	 * Special-node quota of a (blank) spell card: how many policy-classified
	 * EXPERIMENTAL nodes the card may carry. Written onto the blank card by the
	 * boss-base crafting recipes; survives naming/saving. run_command is never
	 * part of a quota.
	 */
	public static int getOpQuota(ItemStack stack) {
		if (stack.hasTag() && stack.getTag().contains(TAG_OP_QUOTA)) {
			return Math.max(0, stack.getTag().getInt(TAG_OP_QUOTA));
		}
		return 0;
	}

	public static void setOpQuota(ItemStack stack, int quota) {
		if (quota > 0) {
			stack.getOrCreateTag().putInt(TAG_OP_QUOTA, quota);
		} else if (stack.hasTag()) {
			stack.getTag().remove(TAG_OP_QUOTA);
		}
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
		if (getSpellId(stack) == null) setSpellId(stack, spellId);
	}

	/** Budget frozen on this draft base. Missing data uses config defaults; the
	 * old aggregate quota remains readable for pre-0.22.9 cards. */
	public static SpellDraftBudget getDraftBudget(ItemStack stack) {
		return SpellDraftBudget.read(stack.getTag(), getOpQuota(stack));
	}

	public static void setDraftBudget(ItemStack stack, SpellDraftBudget budget) {
		budget.write(stack.getOrCreateTag());
		// New budgets use capability-specific grants. Retain no ambiguous aggregate
		// quota on freshly produced cards.
		setOpQuota(stack, budget.legacyExperimentalQuota());
	}

	/**
	 * Binds a newly created definition and upgrades the legacy unqualified-id
	 * format. A bare path was parsed as {@code minecraft:path}; it may be replaced
	 * only when that old id has no definition and the requested player id has the
	 * same path.
	 */
	public static boolean bindCreatedSpellId(ItemStack stack, ResourceLocation spellId) {
		ResourceLocation existing = getSpellId(stack);
		if (existing == null) {
			setSpellId(stack, spellId);
			return true;
		}
		if (isMissingLegacyBinding(existing, spellId)) {
			setSpellId(stack, spellId);
			return true;
		}
		return false;
	}

	@Nullable
	private static SpellDefinition resolveEditableDefinition(ItemStack stack, Player player) {
		ResourceLocation existing = getSpellId(stack);
		if (existing == null) return null;
		SpellDefinition exact = SpellRegistry.get(existing);
		if (exact != null) return exact;
		ResourceLocation playerId = new ResourceLocation(playerSpellNamespace(player), existing.getPath());
		if (!isMissingLegacyBinding(existing, playerId)) return null;
		SpellDefinition repaired = SpellRegistry.get(playerId);
		if (repaired == null || SpellRegistry.getOrigin(playerId) != SpellRegistry.Origin.CUSTOM) return null;
		setSpellId(stack, playerId);
		return repaired;
	}

	private static boolean isMissingLegacyBinding(ResourceLocation existing, ResourceLocation replacement) {
		return "minecraft".equals(existing.getNamespace())
				&& !"minecraft".equals(replacement.getNamespace())
				&& existing.getPath().equals(replacement.getPath())
				&& SpellRegistry.get(existing) == null;
	}

	private static void setSpellId(ItemStack stack, ResourceLocation spellId) {
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
		// Shift+right-click is the universal close action while a spell runtime is
		// active.  Keep this ahead of the draft editor branch so a non-spell can
		// always be used to leave combat without accidentally reopening its editor.
		if (player.isShiftKeyDown() && GrazeHelper.isPlayerSpellBeingCast(player)) {
			if (!level.isClientSide && player instanceof ServerPlayer sp) {
				GrazeHelper.tryForceCloseSpell(sp, stack);
			}
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
		}
		if (player.isShiftKeyDown() && isNonSpell(stack)) {
			if (!level.isClientSide && player instanceof ServerPlayer sp) {
				if (getSpellId(stack) == null) {
					YoukaisHomecoming.HANDLER.toClientPlayer(OpenSpellPreviewToClient.draftEditor(), sp);
				} else {
					SpellDefinition def = resolveEditableDefinition(stack, sp);
					if (def == null) {
						sp.displayClientMessage(Component.literal("Unknown spell: " + getSpellId(stack)), false);
					} else {
						OpenSpellPreviewToClient.sendPreview(sp, applyDraftTraits(stack, def));
					}
				}
			}
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
		}
		// An unfinished draft is an editor artifact, never a combat toggle.  Keep
		// Shift+right-click available for editing while allowing the branch above
		// to close an active spell first.
		boolean editableDraft = getSpellId(stack) == null
				|| (!isNonSpell(stack) && !CertifiedSpellValidator.isCertified(stack) && !isComplete(stack));
		if (player.isShiftKeyDown() && editableDraft) {
			if (!level.isClientSide && player instanceof ServerPlayer sp) {
				if (getSpellId(stack) == null) {
					YoukaisHomecoming.HANDLER.toClientPlayer(OpenSpellPreviewToClient.draftEditor(), sp);
				} else {
					SpellDefinition def = resolveEditableDefinition(stack, sp);
					if (def == null) {
						sp.displayClientMessage(Component.literal("Unknown spell: " + getSpellId(stack)), false);
					} else {
						OpenSpellPreviewToClient.sendPreview(sp, applyDraftTraits(stack, def));
					}
				}
			}
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
		}
		if (!isNonSpell(stack) && GrazeHelper.forbidSpellCardWithMessage(player))
			return InteractionResultHolder.fail(stack);
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
		if (!isNonSpell(stack) && !CertifiedSpellValidator.isCertified(stack) && !isComplete(stack)) {
			if (!level.isClientSide && player instanceof ServerPlayer sp) {
				SpellDefinition def = resolveEditableDefinition(stack, sp);
				if (def == null) {
					sp.displayClientMessage(Component.literal("Unknown spell: " + getSpellId(stack)), false);
					return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
				}
				OpenSpellPreviewToClient.sendPreview(sp, def);
			}
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
		}
		if (!castSpell(stack, player, !player.getAbilities().instabuild, true)) {
			return InteractionResultHolder.fail(stack);
		}
		return InteractionResultHolder.consume(stack);
	}

	@Override
	public boolean castSpell(ItemStack stack, Player player, boolean consume, boolean cooldown) {
		// Bomb and integration APIs call this method directly, bypassing use().
		// Keep draft validation at the authoritative cast boundary.
		if (!isCastReady(stack)) return false;
		SpellDefinition def = getSpellDefinition(stack);
		boolean certifiedStack = CertifiedSpellValidator.isCertified(stack);
		SpellHealthPlan certifiedPlan = null;
		if (player instanceof ServerPlayer sp0 && certifiedStack) {
			certifiedPlan = CertifiedSpellValidator.resolveCertifiedPlan(sp0, stack);
			SpellDefinition certified = certifiedPlan == null
					? CertifiedSpellValidator.resolveCertifiedDefinition(sp0, stack)
					: certifiedPlan.rootDefinition();
			if (certified == null) {
				sp0.displayClientMessage(YHLangData.CERT_CAST_REJECTED.get(), false);
				return false;
			}
			def = certified;
		} else if (def != null && !certifiedStack) {
			def = applyDraftTraits(stack, def);
		}
		SpellCardType cardType = def == null ? getCardType(stack) : def.itemForm.cardType();
		boolean nonSpell = cardType != null && cardType.isNonSpell();
		boolean lastSpell = cardType == SpellCardType.LAST_SPELL;
		if (player instanceof ServerPlayer sp0 && lastSpell) {
			var cap = GrazeCapability.HOLDER.get(sp0);
			if (!cap.canActivateLastSpell() || SpellContainer.hasActiveSpellCard(sp0)) {
				sp0.displayClientMessage(Component.translatable(cap.isInDanmakuCombat()
						? "youkaishomecoming.last_spell.unavailable"
						: "youkaishomecoming.last_spell.combat_only"), true);
				return false;
			}
		}
		if (player instanceof ServerPlayer sp0 && nonSpell) {
			var cap = GrazeCapability.HOLDER.get(sp0);
			String key = GrazeHelper.spellCardKey(stack);
			// A non-spell is a toggle. Releasing another non-spell first keeps the
			// single-active-runtime invariant without marking either card broken.
			if (cap.isNonSpellActive(key)) {
				SpellContainer.clearActiveNonSpell(sp0);
				cap.clearActiveNonSpellCard();
				return true;
			}
			if (cap.getActiveNonSpellCardKey() != null) {
				SpellContainer.clearActiveNonSpell(sp0);
				cap.clearActiveNonSpellCard();
			}
		} else if (GrazeHelper.forbidSpellCardWithMessage(player)) {
			return false;
		}
		if (nonSpell && def != null && dev.xkmc.youkaishomecoming.content.spell.analysis.SpellHealthPlan.hasHealthDeclaration(def)) {
			if (player instanceof ServerPlayer sp) sp.displayClientMessage(YHLangData.NON_SPELL_INVALID.get(), false);
			return false;
		}
		if (nonSpell && def != null) {
			try {
				NonSpellValidator.validate(def, getRank(stack),
						player instanceof ServerPlayer sp ? GrazeHelper.getEffectivePowerLevel(sp) : 0);
			} catch (NonSpellValidator.PresentationNodeException rejected) {
				if (player instanceof ServerPlayer sp) {
					sp.displayClientMessage(YHLangData.NON_SPELL_INVALID.get(), false);
				}
				return false;
			} catch (SpellAnalysisException rejected) {
				if (player instanceof ServerPlayer sp) {
					sp.displayClientMessage(YHLangData.NON_SPELL_REJECTED.get(rejected.getMessage()), false);
				}
				return false;
			} catch (RuntimeException unexpected) {
				YoukaisHomecoming.LOGGER.warn("Unexpected non-spell validation failure for {}", def.id, unexpected);
				if (player instanceof ServerPlayer sp) {
					sp.displayClientMessage(YHLangData.NON_SPELL_REJECTED_UNKNOWN.get(), false);
				}
				return false;
			}
		}
		String cardKey = GrazeHelper.spellCardKey(stack);
		if (def == null && !(certifiedStack && player instanceof ServerPlayer)) return false;
		ResourceLocation spellId = getSpellId(stack);
		if (spellId == null) return false;

		boolean singleUse = isSingleUse(stack);
		if (player instanceof ServerPlayer sp0
				&& GrazeCapability.HOLDER.get(sp0).isSpellCardUnavailable(cardKey)) {
			sp0.displayClientMessage(YHLangData.SPELL_BROKEN_UNAVAILABLE.get(), true);
			return false;
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
			boolean hasDurationOverride = stack.hasTag() && stack.getTag().contains(TAG_DURATION);
			SpellHealthPlan playerHealthPlan = certifiedPlan;
			boolean hasHealthDeclaration = SpellHealthPlan.hasHealthDeclaration(def);
			if (playerHealthPlan == null) {
				try {
					playerHealthPlan = SpellHealthPlan.analyzeIfPresent(def, SpellRegistry::get).orElse(null);
				} catch (IllegalArgumentException ignored) {
					// Invalid plans are rejected at certification/export boundaries; a
					// legacy OP card may still cast without a player spell bar.
				}
			}
			int duration = getStackDuration(stack);
			if (duration == DURATION_NATURAL && playerHealthPlan != null) {
				// Complete cards created before 0.22.6 did not carry a duration tag.
				duration = playerHealthPlan.totalDurationTicks();
			}
			if (duration == DURATION_NATURAL && !hasHealthDeclaration && def.itemForm.cooldown() > 0) {
				// item_form.cooldown acts as fixed duration when set, unless overridden by NBT
				duration = def.itemForm.cooldown();
			}
			if (CertifiedSpellValidator.isCertified(stack)) {
				// Certified cards always use the immutable reward duration.
				duration = CertifiedSpellValidator.getCertifiedCastDuration(stack);
			}
			if (consume && !nonSpell && !lastSpell) {
				boolean paid = CertifiedSpellValidator.isCertified(stack)
						? SpellItemCost.tryPayCertifiedUnits(sp,
								CertifiedSpellValidator.getCertifiedCost(stack), cardType)
						: SpellItemCost.tryPay(sp, duration, cardType);
				if (!paid) return false;
			}
			// A zero-tick health plan is the explicit no-timeout variant, not an
			// immediately expiring proxy.
			if (duration == 0 && hasHealthDeclaration) duration = DURATION_NATURAL;
			DanmakuProxyEntity proxy = new DanmakuProxyEntity(
					YHEntities.DANMAKU_PROXY.get(), sp.serverLevel());
			Integer durationOverride = !certifiedStack && hasDurationOverride && duration >= 0
					? duration : null;
			proxy.init(sp, def, duration, target, certifiedPlan, durationOverride, certifiedStack);
			sp.serverLevel().addFreshEntity(proxy);
			SpellContainer.trackProxy(sp, proxy, cardKey);
			if (lastSpell) {
				GrazeCapability.HOLDER.get(sp).activateLastSpell();
			}
			if (nonSpell) {
				GrazeCapability.HOLDER.get(sp).setActiveNonSpellCard(cardKey);
			} else {
				GrazeHelper.onPlayerSpellCast(sp);
			}
			// certified cards show the player-use spell bar: a fraction of the
			// certification HP (boss bar); misses shrink it instead of costing life
			if (playerHealthPlan != null && playerHealthPlan.totalHealth() > 0) {
				SpellContainer.startSpellBar(sp, playerHealthPlan.totalHealth(), cardKey,
						def.display.displayName());
			}
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
		boolean blankDraft = getSpellId(stack) == null;
		if (blankDraft) {
			list.add(YHLangData.SPELL_CREATE.get());
		}
		SpellDefinition def = getSpellDefinition(stack);
		SpellHealthPlan healthPlan = null;
		boolean hasHealthDeclaration = SpellHealthPlan.hasHealthDeclaration(def);
		if (def != null) {
			try {
				healthPlan = SpellHealthPlan.analyzeIfPresent(def, SpellRegistry::get).orElse(null);
			} catch (IllegalArgumentException ignored) {
			}
		}
		int castDuration = CertifiedSpellValidator.isCertified(stack)
				? CertifiedSpellValidator.getCertifiedCastDuration(stack) : getStackDuration(stack);
		if (castDuration == DURATION_NATURAL && healthPlan != null) {
			castDuration = healthPlan.totalDurationTicks();
		} else if (castDuration == DURATION_NATURAL && def != null && !hasHealthDeclaration) {
			castDuration = def.itemForm.duration();
		}
		if (!blankDraft) {
			SpellItemCost.appendCostTooltip(list, stack, castDuration);
		}
		SpellDraftBudget budget = getDraftBudget(stack);
		dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCardRank rank = getRank(stack);
		if (!isComplete(stack) && !CertifiedSpellValidator.isCertified(stack)) {
			list.add(Component.literal("§6" + rank.displayName() + " (Tier " + rank.tierNumber() + ")"));
			list.add(Component.translatable("youkaishomecoming.tooltip.spell_budget.nodes",
					budget.freeNodeCount()).withStyle(ChatFormatting.GRAY));
			list.add(Component.translatable("youkaishomecoming.tooltip.spell_budget.performance",
					budget.maxSpawnPerTick(), budget.maxPeakAlive(),
					budget.maxProjectileTicks(), budget.maxHookExecutions()).withStyle(ChatFormatting.DARK_GRAY));
			int grants = budget.teleportGrants() + budget.eraseEnemyDanmakuGrants()
					+ budget.clearScreenGrants() + budget.bossOnDamageGrants()
					+ budget.confinedTargetGrants() + budget.experimentalFireGrants();
			if (grants > 0 || budget.legacyExperimentalQuota() > 0) {
				list.add(Component.translatable("youkaishomecoming.tooltip.spell_budget.experimental",
						budget.teleportGrants(), budget.eraseEnemyDanmakuGrants(),
						budget.clearScreenGrants(), budget.bossOnDamageGrants(),
						budget.confinedTargetGrants(),
						budget.experimentalFireGrants(),
						budget.legacyExperimentalQuota()).withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		}
		if (isSingleUse(stack)) {
			list.add(YHLangData.SPELL_SINGLE_USE.get());
		}
		if (def != null) {
			list.add(def.display.displayName().copy().withStyle(ChatFormatting.GOLD));
			if (!def.display.description().isEmpty()) {
				list.add(def.display.displayDesc().copy().withStyle(ChatFormatting.GRAY));
			}
			if (!CertifiedSpellValidator.isCertified(stack) && !isComplete(stack)) {
				list.add(YHLangData.SPELL_UNFINISHED.get());
			}
			if (healthPlan != null) {
				list.add(YHLangData.SPELL_HP.get(healthPlan.totalHealth()));
			} else if (hasHealthDeclaration) {
				list.add(YHLangData.SPELL_HEALTH_DYNAMIC.get());
			}
			if (castDuration > 0) {
				list.add(YHLangData.SPELL_DURATION.get(castDuration).withStyle(ChatFormatting.DARK_GRAY));
			} else if (castDuration == 0 && hasHealthDeclaration) {
				list.add(YHLangData.SPELL_DURATION_INFINITE.get());
			} else if (castDuration == DURATION_NATURAL && hasHealthDeclaration) {
				list.add(YHLangData.SPELL_DURATION_RUNTIME.get());
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
		if (user instanceof Player player && level.isClientSide && sel
				&& !isNonSpell(stack)
				&& (CertifiedSpellValidator.isCertified(stack) || isComplete(stack))) {
			RayTraceUtil.clientUpdateTarget(player, GrazeHelper.SPELL_TARGET_RANGE);
		}
	}

	@Override
	public int getDistance(ItemStack itemStack) {
		return GrazeHelper.SPELL_TARGET_RANGE;
	}
}
