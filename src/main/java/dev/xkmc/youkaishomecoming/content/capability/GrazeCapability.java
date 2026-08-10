package dev.xkmc.youkaishomecoming.content.capability;

import dev.xkmc.fastprojectileapi.collision.EntityStorageHelper;
import dev.xkmc.l2library.capability.player.PlayerCapabilityHolder;
import dev.xkmc.l2library.capability.player.PlayerCapabilityNetworkHandler;
import dev.xkmc.l2library.capability.player.PlayerCapabilityTemplate;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.compat.stg.StgCombatMode;
import dev.xkmc.youkaishomecoming.compat.stg.YHStgApi;
import dev.xkmc.youkaishomecoming.compat.stg.event.StgBombEvent;
import dev.xkmc.youkaishomecoming.compat.stg.event.StgCombatEvent;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuProxyEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.EntitySpellProxyEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.spell.item.SpellContainer;
import dev.xkmc.youkaishomecoming.events.DanmakuLastHitEvent;
import dev.xkmc.youkaishomecoming.events.EffectEventHandlers;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import dev.xkmc.youkaishomecoming.init.data.YHLangData;
import dev.xkmc.youkaishomecoming.init.registrate.YHEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@SerialClass
public class GrazeCapability extends PlayerCapabilityTemplate<GrazeCapability> {

	public static final Capability<GrazeCapability> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {
	});

	public static final PlayerCapabilityHolder<GrazeCapability> HOLDER = new PlayerCapabilityHolder<>(
			YoukaisHomecoming.loc("graze"), CAPABILITY,
			GrazeCapability.class, GrazeCapability::new, PlayerCapabilityNetworkHandler::new
	);

	private static final int MAX_GRAZE = 100, SHARD = 5, CYCLE = 3;
	private static final int WEAK = 60, GRAZE_CACHE = 10;
	private static final double ACTIVE_DANMAKU_HOST_SEARCH_RANGE = 128.0;

	@SerialClass.SerialField
	private int power, hidden, step, bomb, life, invul, weak;
	@SerialClass.SerialField
	private Map<UUID, CombatSession> sessions = new LinkedHashMap<>();
	@SerialClass.SerialField
	private Set<UUID> playerOpponents = new LinkedHashSet<>();
	@SerialClass.SerialField
	private boolean forcedDanmakuCombat = false;
	/** Debug/admin forced combat may ignore the spell-card inventory requirement. */
	@SerialClass.SerialField
	private boolean combatAdminBypass = false;
	@SerialClass.SerialField
	private boolean statusInitialized = false;
	/** True after resources have been seeded at least once; persists across combat sessions. */
	@SerialClass.SerialField
	private boolean resourcesPrimed = false;
	/** Synced client projection used by inventory overlays. */
	@SerialClass.SerialField
	private boolean playerSpellActive = false;
	/** Synced client projection: the active spell currently owns player movement. */
	@SerialClass.SerialField
	private boolean spellMovementRestricted = false;
	/** STG spell casts own invulnerability until their active caster ends. */
	@SerialClass.SerialField
	private boolean spellInvulnerable = false;
	@SerialClass.SerialField
	private String stgCombatMode = StgCombatMode.NOVICE_AUTO_BOMB.name();

	private boolean dirty = false;
	private int tempGraze = 0;
	private int lastGraze = 0;
	private int pvpStatusSyncCooldown = 0;
	private boolean pvpStatusVisible = false;

	@Override
	public void onClone(boolean isWasDeath) {
		if (isWasDeath) {
			hidden = 0;
			step = 0;
			invul = 0;
			weak = 0;
			playerSpellActive = false;
			spellMovementRestricted = false;
			spellInvulnerable = false;
			forcedDanmakuCombat = false;
			combatAdminBypass = false;
			statusInitialized = false;
			playerOpponents.clear();
			// Respawn restores default STG resources (not zero)
			applyDefaultResources(true);
			resourcesPrimed = true;
			dirty = true;
		}
	}

	/**
	 * Marks combat status active. Seeds default resources only on first prime;
	 * never tops up depleted values on re-entry (that was abusable).
	 */
	public void initStatus() {
		if (!resourcesPrimed) {
			applyDefaultResources(true);
			resourcesPrimed = true;
		}
		statusInitialized = true;
		dirty = true;
	}

	/** Force life/bomb/power to config defaults (respawn / battle defeat). */
	public void resetResourcesToDefault() {
		applyDefaultResources(true);
		resourcesPrimed = true;
		dirty = true;
	}

	/**
	 * Raise life/bomb/power up to defaults without lowering above-default values.
	 * Used after sleeping in a bed.
	 */
	public void topUpResourcesToDefault() {
		applyDefaultResources(false);
		resourcesPrimed = true;
		dirty = true;
	}

	private void applyDefaultResources(boolean forceExact) {
		int initResource = GrazeHelper.getInitialResource(player) * SHARD;
		int initPower = GrazeHelper.getInitialPower(player) * MAX_GRAZE;
		if (forceExact) {
			life = initResource;
			bomb = initResource;
			power = initPower;
		} else {
			life = Math.max(initResource, life);
			bomb = Math.max(initResource, bomb);
			power = Math.max(initPower, power);
		}
	}

	@Override
	public void tick() {
		if (player.level() instanceof ServerLevel && GrazeHelper.isManualCombatMode()
				&& forcedDanmakuCombat && !combatAdminBypass
				&& sessions.isEmpty() && playerOpponents.isEmpty()
				&& !SpellContainer.hasActiveSpell(player) && !GrazeHelper.hasSpellCard(player)) {
			// Do not strand a manual-mode player after their last card ends. Active
			// boss/PvP opponents keep combat alive so a cardless hit still costs life.
			clearCombatState(true);
		}
		if (player.level() instanceof ServerLevel) {
			boolean activeSpell = SpellContainer.hasActiveSpell(player);
			if (playerSpellActive != activeSpell) {
				playerSpellActive = activeSpell;
				dirty = true;
			}
			boolean movementRestricted = SpellContainer.restrictsManualMovement(player);
			if (player instanceof ServerPlayer sp) {
				var trial = dev.xkmc.youkaishomecoming.content.spell.certification.CertificationManager.INSTANCE
						.getActiveTrial(sp);
				movementRestricted |= trial != null && trial.restrictsAuthorMovement();
			}
			if (spellMovementRestricted != movementRestricted) {
				spellMovementRestricted = movementRestricted;
				dirty = true;
			}
			if (spellInvulnerable && !activeSpell) {
				spellInvulnerable = false;
				dirty = true;
			}
		}
		boolean activeCombat = isInDanmakuCombat();
		if (tempGraze > 0) {
			tempGraze--;
			double val = GrazeHelper.getGrazeEffectiveness(player);
			int count = (int) val;
			if (player.getRandom().nextFloat() < val - count) count++;
			for (int i = 0; i < count; i++)
				consumeGraze();
			dirty = true;
		}
		if (invul > 0) invul--;
		if (weak > 0) weak--;
		int maxPower = GrazeHelper.getMaxPower(player) * MAX_GRAZE;
		int maxResource = GrazeHelper.getMaxResource(player) * SHARD;
		if (power > maxPower) power = maxPower;
		if (life > maxResource) life = maxResource;
		if (bomb > maxResource) bomb = maxResource;
		if (player.level() instanceof ServerLevel sl) {
			if (!activeCombat) {
				if (!sessions.isEmpty()) {
					sessions.clear();
					dirty = true;
				}
			} else {
				for (var ent : new ArrayList<>(sessions.entrySet())) {
					if (ent.getValue().youkai == null) dirty = true;
					if (ent.getValue().shouldRemove(sl, player)) {
						sessions.remove(ent.getKey());
						dirty = true;
					}
				}
				if (!playerOpponents.isEmpty()) {
					for (UUID id : List.copyOf(playerOpponents)) {
						if (shouldRemovePlayerOpponent(sl, id)) {
							removePlayerOpponent(id, false, false);
						}
					}
				}
			}
			settleCombatIfIdle();
			if (dirty)
				sync();
			syncPvpOpponentStatus(sl);
		}
		dirty = false;
		if (player.level().isClientSide) {
			GrazeHelper.globalForbidTime = playerSpellActive
					? Math.max(1, Math.max(invul, weak)) : Math.max(invul, weak);
		}
	}

	public boolean graze() {
		if (isInvul()) return false;
		if (!EffectEventHandlers.canDanmakuCombat(player)) return false;
		if (tempGraze < GRAZE_CACHE)
			tempGraze++;
		boolean ans = player.tickCount != lastGraze;
		lastGraze = player.tickCount;
		return ans;
	}

	private void consumeGraze() {
		if (power < GrazeHelper.getMaxPower(player) * MAX_GRAZE) {
			power++;
			return;
		}
		if (sessions.isEmpty()) return;
		hidden++;
		if (hidden < MAX_GRAZE) return;
		hidden -= MAX_GRAZE;
		completePointCycle();
	}

	private void completePointCycle() {
		step++;
		int max = GrazeHelper.getMaxResource(player) * SHARD;
		if (step == CYCLE) {
			if (life < max) {
				life++;
				step = 0;
			} else if (bomb < max) {
				bomb++;
				step--;
			} else {
				step--;
			}
		} else {
			if (bomb < max) bomb++;
			else if (life < max) step++;
		}
	}

	public HitType performErase(YoukaiEntity e) {
		if (!prepareDanmakuHitContext(e)) return HitType.ERASE;
		return performDanmakuHit(e);
	}

	public HitType performDanmakuHit(@Nullable LivingEntity source) {
		if (!hasInitializedCombatContext(source)) return HitType.NONE;
		if (isInvul()) return HitType.INVUL;
		int erased = eraseActiveDanmakuForHit(source);
		// a certified spell card's break-HP bar absorbs misses: shrink it by 1,
		// break (interrupt) the spell at zero — no power/life loss for this hit
		if (player instanceof ServerPlayer sp
				&& dev.xkmc.youkaishomecoming.content.spell.item.SpellContainer.consumeSpellBarHit(sp)) {
			invul = YHModConfig.COMMON.missInvulTime.get();
			dirty = true;
			return HitType.LIFE;
		}
		// Auto-bomb is still a bomb use for certification purposes. Fail the
		// trial, but leave resources untouched and absorb this contact so the
		// player is not double-punished by a normal miss in the same tick.
		if (player instanceof ServerPlayer sp) {
			var trial = dev.xkmc.youkaishomecoming.content.spell.certification.CertificationManager.INSTANCE
					.getActiveTrial(sp);
			if (trial != null && trial.isActive()) {
				trial.onPlayerBomb();
				invul = YHModConfig.COMMON.missInvulTime.get();
				dirty = true;
				return HitType.INVUL;
			}
		}
		if (getStgCombatMode().autoBombOnHit() && player instanceof ServerPlayer sp
				&& GrazeHelper.tryCastBombSpell(sp)) {
			MinecraftForge.EVENT_BUS.post(new StgBombEvent.Auto(sp, source, erased));
			return HitType.BOMB;
		}
		int maxLoss = (int) (YHModConfig.COMMON.maxPowerLossOnMiss.get() * MAX_GRAZE);
		int loss = Math.min(power / 2, maxLoss);
		power -= loss;
		dirty = true;
		invul = YHModConfig.COMMON.missInvulTime.get();
		// legacy behaviour: a miss also leaves the player weak for the same duration —
		// enemy danmaku fades out, own danmaku deals no damage and firing is disabled.
		// (danmaku losing effect on a miss matches the source-game convention)
		weak = Math.max(weak, invul);
		if (player instanceof ServerPlayer sp) {
			YoukaisHomecoming.HANDLER.toClientPlayer(new GrazeHelper.GrazeToClient().set(1), sp);
			SpellContainer.clear(sp);
		}
		if (life < SHARD) {
			if (source != null && MinecraftForge.EVENT_BUS.post(new DanmakuLastHitEvent(player, source))) {
				dirty = true;
				return HitType.LIFE;
			}
			exitDanmakuCombatOnLastHit(source);
			return HitType.LAST;
		}
		life -= SHARD;
		restoreInitialBomb();
		return HitType.LIFE;
	}
	public void setWeak(int duration) {
		weak = duration;
		dirty = true;
	}

	public void startPlayerSpell() {
		playerSpellActive = true;
		if (isInDanmakuCombat()) {
			spellInvulnerable = true;
		}
		dirty = true;
		sync();
	}

	public int eraseActiveDanmaku(double radius, boolean sessionsOnly) {
		return eraseEnemyDanmakuInRadius(player.position(), radius, sessionsOnly);
	}

	public int eraseEnemyDanmakuInRadius(Vec3 center, double radius, boolean sessionsOnly) {
		double range = Math.max(0, radius);
		Set<UUID> erasedYoukai = new HashSet<>();
		Set<UUID> erasedProxy = new HashSet<>();
		int erased = 0;
		for (var s : sessions.values()) {
			erased += eraseSessionDanmaku(s, center, range, erasedYoukai);
		}
		if (!sessionsOnly && range > 0 && player.level() instanceof ServerLevel sl) {
			erased += eraseEnemyDanmakuHosts(sl, hostSearchArea(player.position(), range), center, range,
					erasedYoukai, erasedProxy);
			erased += eraseEnemyDanmakuHosts(sl, hostSearchArea(center, range), center, range,
					erasedYoukai, erasedProxy);
		}
		return erased;
	}

	public float powerBonus() {
		if (!EffectEventHandlers.canDanmakuCombat(player)) return 0;
		int support = power / MAX_GRAZE;
		return support * YHModConfig.COMMON.danmakuPowerBonus.get().floatValue();
	}

	public List<InfoLine> getInfoLines() {
		if (!EffectEventHandlers.canDanmakuCombat(player)) return List.of();
		var icon = new InfoIcon(
				YoukaisHomecoming.loc("textures/gui/elements.png"),
				20, 20
		);
		if (sessions.isEmpty()) {
			if (forcedDanmakuCombat || !playerOpponents.isEmpty()) {
				return fullInfo(icon);
			}
			boolean holding = YHStgApi.shouldShowPower(player);
			boolean bypass = player.getAbilities().instabuild && player.isShiftKeyDown();
			if (!holding) return List.of();
			if (!bypass) {
				return List.of(new InfoLine("%.2f".formatted(power * 0.01), icon, 10, 10));
			}

		}
		return fullInfo(icon);
	}

	private List<InfoLine> fullInfo(InfoIcon icon) {
		return List.of(
				new InfoLine("%.1f".formatted(life * 1d / SHARD), icon, 0, 10),
				new InfoLine("%.1f".formatted(bomb * 1d / SHARD), icon, 0, 0),
				new InfoLine("%.2f".formatted(power * 1d / MAX_GRAZE), icon, 10, 10),
				new InfoLine("%.2f".formatted(hidden * 1d / MAX_GRAZE), icon, 10, 0)
		);
	}

	public boolean isInSession(UUID uuid) {
		return sessions.containsKey(uuid);
	}

	/**
	 * Whether this player is in any active combat session.
	 */
	public boolean isInSession() {
		return !sessions.isEmpty();
	}

	public boolean isInDanmakuCombat() {
		return forcedDanmakuCombat || !sessions.isEmpty() || !playerOpponents.isEmpty();
	}

	public boolean isForcedDanmakuCombat() {
		return forcedDanmakuCombat;
	}

	private void settleCombatIfIdle() {
		if (isInDanmakuCombat()) return;
		boolean changed = false;
		if (statusInitialized) {
			statusInitialized = false;
			changed = true;
		}
		// Keep life/bomb/power across sessions so above-default gains persist
		// and depleted values are not free-refilled on the next initStatus().
		if (hidden != 0) {
			hidden = 0;
			changed = true;
		}
		if (step != 0) {
			step = 0;
			changed = true;
		}
		if (changed) dirty = true;
	}

	public boolean hasPlayerOpponent(UUID id) {
		return playerOpponents.contains(id);
	}

	public void setForcedDanmakuCombat(boolean enabled) {
		setForcedDanmakuCombat(enabled, false);
	}

	public void setForcedDanmakuCombat(boolean enabled, boolean adminBypass) {
		if (enabled) {
			if (!isInDanmakuCombat()) {
				initStatus();
			}
			forcedDanmakuCombat = true;
			combatAdminBypass = adminBypass;
		} else if (forcedDanmakuCombat) {
			forcedDanmakuCombat = false;
			combatAdminBypass = false;
			settleCombatIfIdle();
		}
		dirty = true;
	}

	public void clearPlayerOpponents() {
		if (player.level() instanceof ServerLevel sl) {
			clearPlayerOpponents(sl, true);
			return;
		}
		if (playerOpponents.isEmpty()) return;
		playerOpponents.clear();
		resetPvpOpponentStatus();
		dirty = true;
		settleCombatIfIdle();
	}

	public void initSession(YoukaiEntity youkai) {
		if (!statusInitialized) initStatus();
		if (sessions.containsKey(youkai.getUUID())) return;
		if (!hasActiveSession(youkai)) {
			youkai.resetCombatProgressForDanmakuSession();
		}
		sessions.put(youkai.getUUID(), new CombatSession().init(youkai));
		youkai.targets.add(player);
		dirty = true;
		if (player instanceof ServerPlayer sp) {
			OpponentSnapshot snap = snapshotOpponents(sp);
			MinecraftForge.EVENT_BUS.post(new StgCombatEvent.SessionStart(sp, youkai, snap.ids(), snap.entities()));
		}
	}

	private static boolean hasActiveSession(YoukaiEntity youkai) {
		for (LivingEntity target : youkai.targets.getTargets()) {
			if (target instanceof Player player && HOLDER.get(player).isInSession(youkai.getUUID())) {
				return true;
			}
		}
		return false;
	}

	public void addPlayerOpponent(Player target) {
		if (target == player || target.level() != player.level()) return;
		if (!statusInitialized) initStatus();
		if (playerOpponents.add(target.getUUID())) {
			pvpStatusSyncCooldown = 0;
			dirty = true;
		}
	}

	public void stopSession(UUID uuid) {
		stopSession(uuid, StgCombatEvent.SessionEndReason.CLEARED);
	}

	/**
	 * Ends one Youkai session and fires {@link StgCombatEvent.SessionEnd}.
	 * Prefer {@link StgCombatEvent.SessionEndReason#VICTORY} when the Youkai combat progress was cleared.
	 */
	public void stopSession(UUID uuid, StgCombatEvent.SessionEndReason reason) {
		CombatSession session = sessions.remove(uuid);
		if (session == null) return;
		LivingEntity opponent = session.getTarget(player);
		session.resetTarget(player);
		if (player instanceof ServerPlayer sp) {
			OpponentSnapshot snap = snapshotOpponents(sp);
			MinecraftForge.EVENT_BUS.post(new StgCombatEvent.SessionEnd(
					sp, uuid, opponent, reason == null ? StgCombatEvent.SessionEndReason.CLEARED : reason,
					snap.ids(), snap.entities()));
		}
		settleCombatIfIdle();
		dirty = true;
	}

	public boolean shouldHurt(LivingEntity le) {
		if (le instanceof YoukaiEntity youkai) {
			if (weak > 0) return false;
			if (sessions.containsKey(youkai.getUUID())) return true;
			if (GrazeHelper.isManualCombatMode()) {
				// Manual mode: only open/continue sessions while already in STG combat
				if (!isInDanmakuCombat()) return true;
				initSession(youkai);
				return true;
			}
			if (youkai.targets.contains(player) || sessions.isEmpty()) {
				initSession(youkai);
				return true;
			}
			if (!EffectEventHandlers.canDanmakuCombat(player)) return true;
			return false;
		}
		if (!EffectEventHandlers.canDanmakuCombat(player)) return true;
		if (le instanceof Player target) {
			return forcedDanmakuCombat || playerOpponents.contains(target.getUUID());
		}
		return sessions.isEmpty() || le instanceof Mob mob && mob.getTarget() == player;
	}

	public boolean shouldAbsorbDanmakuFrom(@Nullable LivingEntity source) {
		if (!isInDanmakuCombat()) return false;
		if (forcedDanmakuCombat) return true;
		if (source instanceof YoukaiEntity youkai) {
			return sessions.containsKey(youkai.getUUID());
		}
		if (source instanceof Player player) {
			return playerOpponents.contains(player.getUUID());
		}
		return false;
	}

	/**
	 * Establishes the explicit STG context for a legitimate first hostile hit.
	 */
	public boolean prepareDanmakuHitContext(@Nullable LivingEntity source) {
		if (forcedDanmakuCombat) {
			if (!statusInitialized) initStatus();
			return true;
		}
		if (source instanceof YoukaiEntity youkai) {
			if (sessions.containsKey(youkai.getUUID())) {
				if (!statusInitialized) initStatus();
				return true;
			}
			// Manual mode: never auto-enter from enemy danmaku alone
			if (GrazeHelper.isManualCombatMode()) return false;
			if (!youkai.targets.contains(player)) return false;
			initSession(youkai);
			return true;
		}
		if (source instanceof Player attacker) {
			if (attacker.level() != player.level() || attacker.isAlliedTo(player)) return false;
			if (playerOpponents.contains(attacker.getUUID())) {
				if (!statusInitialized) initStatus();
				return true;
			}
			// Manual mode: PvP absorb only after explicit spell-target duel setup
			if (GrazeHelper.isManualCombatMode()) return false;
			addPlayerOpponent(attacker);
			HOLDER.get(attacker).addPlayerOpponent(player);
			return true;
		}
		return false;
	}

	private boolean hasInitializedCombatContext(@Nullable LivingEntity source) {
		return statusInitialized && shouldAbsorbDanmakuFrom(source);
	}

	public Optional<LivingEntity> findAny(Player player) {
		for (CombatSession session : sessions.values()) {
			LivingEntity target = session.getTarget(player);
			if (target != null && target.isAlive() && target.level() == player.level()) {
				return Optional.of(target);
			}
		}
		if (player.level() instanceof ServerLevel level) {
			for (UUID id : playerOpponents) {
				if (level.getEntity(id) instanceof LivingEntity target && target.isAlive()) {
					return Optional.of(target);
				}
			}
		}
		return Optional.empty();
	}

	public void remove(UUID uuid) {
		sessions.remove(uuid);
	}

	public void setLife(int i) {
		life = i;
		dirty = true;
	}

	public void setBomb(int i) {
		bomb = i;
		dirty = true;
	}

	public void setPower(int i) {
		power = i;
		dirty = true;
	}

	public void setPoints(int i) {
		hidden = Math.max(0, Math.min(MAX_GRAZE - 1, i));
		dirty = true;
	}

	public void addPoints(int amount) {
		if (amount == 0) {
			return;
		}
		if (amount < 0) {
			hidden = Math.max(0, hidden + amount);
			dirty = true;
			return;
		}
		long total = hidden + (long) amount;
		hidden = (int) (total % MAX_GRAZE);
		long cycles = total / MAX_GRAZE;
		while (cycles > 0) {
			completePointCycle();
			cycles--;
			int max = GrazeHelper.getMaxResource(player) * SHARD;
			if (life >= max && bomb >= max && step == CYCLE - 1) {
				break;
			}
		}
		dirty = true;
	}

	public StgCombatMode getStgCombatMode() {
		return StgCombatMode.fromSerialized(stgCombatMode);
	}

	public void setStgCombatMode(StgCombatMode mode) {
		stgCombatMode = Objects.requireNonNull(mode, "mode").name();
		dirty = true;
	}

	public boolean isInvul() {
		return invul > 0 || spellInvulnerable;
	}

	public boolean isPlayerSpellActive() {
		return playerSpellActive;
	}

	public boolean isSpellMovementRestricted() {
		return spellMovementRestricted;
	}

	public boolean isWeak() {
		return weak > 0;
	}

	public int getLife() {
		return life;
	}

	public int getBomb() {
		return bomb;
	}

	public int getPower() {
		return power;
	}

	public int getPoints() {
		return hidden;
	}

	public void sync() {
		if (player instanceof ServerPlayer sp)
			HOLDER.network.toClientSyncAll(sp);
	}

	private void syncPvpOpponentStatus(ServerLevel sl) {
		if (!(player instanceof ServerPlayer sp)) return;
		if (playerOpponents.isEmpty()) {
			resetPvpOpponentStatus();
			return;
		}
		if (!dirty && pvpStatusVisible && --pvpStatusSyncCooldown > 0) return;
		pvpStatusSyncCooldown = 5;
		pvpStatusVisible = true;
		for (UUID id : playerOpponents) {
			if (!(sl.getEntity(id) instanceof ServerPlayer target)) continue;
			var cap = HOLDER.get(target);
			int maxResource = GrazeHelper.getMaxResource(target) * SHARD;
			YoukaisHomecoming.HANDLER.toClientPlayer(PvpDanmakuStatusToClient.status(
					target.getId(),
					target.getGameProfile().getName(),
					cap.getLife(),
					cap.getBomb(),
					maxResource,
					maxResource
			), sp);
		}
	}

	private int eraseSessionDanmaku(CombatSession session, Vec3 center, double radius, Set<UUID> erasedYoukai) {
		if (!(session.getTarget(player) instanceof YoukaiEntity youkai)) return 0;
		if (!erasedYoukai.add(youkai.getUUID())) return 0;
		return eraseDanmaku(youkai, center, radius);
	}

	private int eraseDanmaku(YoukaiEntity youkai, Vec3 center, double radius) {
		if (radius > 0) {
			return youkai.eraseDanmakuInRadius(center, radius, player);
		}
		return youkai.eraseAllDanmakuAndCount(player);
	}

	private int eraseEnemyDanmakuHosts(ServerLevel sl, AABB area, Vec3 center, double radius,
									  Set<UUID> erasedYoukai, Set<UUID> erasedProxy) {
		int erased = 0;
		for (var youkai : sl.getEntitiesOfClass(YoukaiEntity.class, area)) {
			if (!erasedYoukai.add(youkai.getUUID())) continue;
			if (!shouldEraseYoukaiHost(youkai)) continue;
			erased += youkai.eraseDanmakuInRadius(center, radius, player);
		}
		for (var proxy : sl.getEntitiesOfClass(DanmakuProxyEntity.class, area)) {
			if (!erasedProxy.add(proxy.getUUID())) continue;
			if (!shouldEraseProxyHost(proxy)) continue;
			erased += proxy.eraseDanmakuInRadius(center, radius, player);
		}
		for (var proxy : sl.getEntitiesOfClass(EntitySpellProxyEntity.class, area)) {
			if (!erasedProxy.add(proxy.getUUID())) continue;
			if (!shouldEraseEntityProxyHost(proxy)) continue;
			erased += proxy.eraseDanmakuInRadius(center, radius, player);
		}
		return erased;
	}

	private boolean shouldEraseYoukaiHost(YoukaiEntity youkai) {
		if (youkai.isAlliedTo(player)) return false;
		return sessions.containsKey(youkai.getUUID()) || youkai.targets.contains(player);
	}

	private boolean shouldEraseProxyHost(DanmakuProxyEntity proxy) {
		if (proxy.isOwnedBy(player)) return false;
		LivingEntity owner = proxy.owner();
		if (owner == null) return false;
		if (owner.isAlliedTo(player)) return false;
		if (owner instanceof Player target) {
			return forcedDanmakuCombat || playerOpponents.contains(target.getUUID());
		}
		return true;
	}

	private boolean shouldEraseEntityProxyHost(EntitySpellProxyEntity proxy) {
		if (proxy.isOwnedBy(player)) return false;
		LivingEntity owner = proxy.owner();
		if (owner == null) return false;
		if (owner.isAlliedTo(player)) return false;
		if (owner instanceof Player target) {
			return forcedDanmakuCombat || playerOpponents.contains(target.getUUID());
		}
		if (owner instanceof YoukaiEntity youkai) {
			return sessions.containsKey(youkai.getUUID()) || youkai.targets.contains(player);
		}
		if (proxy.targetEntity() == player) return true;
		return forcedDanmakuCombat || owner instanceof Mob mob && mob.getTarget() == player;
	}

	private static AABB hostSearchArea(Vec3 center, double radius) {
		double range = Math.max(ACTIVE_DANMAKU_HOST_SEARCH_RANGE, radius);
		return AABB.ofSize(center, range * 2, range * 2, range * 2);
	}

	private boolean shouldRemovePlayerOpponent(ServerLevel sl, UUID id) {
		var entity = sl.getEntity(id);
		return !(entity instanceof ServerPlayer target) || !target.isAlive() || target.level() != player.level() ||
				!HOLDER.get(target).hasPlayerOpponent(player.getUUID());
	}

	private void clearPlayerOpponents(ServerLevel sl, boolean reciprocal) {
		if (playerOpponents.isEmpty()) return;
		List<UUID> ids = List.copyOf(playerOpponents);
		playerOpponents.clear();
		resetPvpOpponentStatus();
		dirty = true;
		if (reciprocal) {
			for (UUID id : ids) {
				if (sl.getEntity(id) instanceof ServerPlayer target) {
					HOLDER.get(target).removePlayerOpponent(player.getUUID(), false, true);
				}
			}
		}
		settleCombatIfIdle();
	}

	private boolean removePlayerOpponent(UUID id, boolean reciprocal, boolean syncAfter) {
		if (!playerOpponents.remove(id)) return false;
		resetPvpOpponentStatus();
		dirty = true;
		if (reciprocal && player.level() instanceof ServerLevel sl && sl.getEntity(id) instanceof ServerPlayer target) {
			HOLDER.get(target).removePlayerOpponent(player.getUUID(), false, true);
		}
		settleCombatIfIdle();
		if (syncAfter) sync();
		return true;
	}

	private void resetPvpOpponentStatus() {
		pvpStatusSyncCooldown = 0;
		if (pvpStatusVisible && player instanceof ServerPlayer sp) {
			YoukaisHomecoming.HANDLER.toClientPlayer(PvpDanmakuStatusToClient.clearAll(), sp);
		}
		pvpStatusVisible = false;
	}

	private int eraseActiveDanmakuForHit(@Nullable LivingEntity source) {
		int erased = eraseActiveDanmaku(0, true);
		if (source instanceof ServerPlayer sp) {
			SpellContainer.clear(sp);
		}
		return erased;
	}

	private void restoreInitialBomb() {
		bomb = GrazeHelper.getInitialResource(player) * SHARD;
		dirty = true;
	}

	/**
	 * Public defeat entry (used by the certification No-Hit failure): run the
	 * full danmaku battle defeat flow — clear sessions, reset resources, apply
	 * weak/beaten, fire {@link StgCombatEvent.Defeat}.
	 */
	public void defeat(@Nullable LivingEntity fatalSource) {
		exitDanmakuCombatOnLastHit(fatalSource);
	}

	/**
	 * Full STG defeat: clear sessions, reset resources, apply weak/beaten, fire {@link StgCombatEvent.Defeat}.
	 */
	private void exitDanmakuCombatOnLastHit(@Nullable LivingEntity fatalSource) {
		// Snapshot opponents before clearing so external mods can settle dialogue/score.
		OpponentSnapshot snap = player instanceof ServerPlayer sp
				? snapshotOpponents(sp)
				: new OpponentSnapshot(List.of(), List.of());
		eraseDefeatDanmaku();
		// Defeat restores default resources (same as respawn)
		resetResourcesToDefault();
		for (var s : sessions.values()) {
			s.resetTarget(player);
		}
		sessions.clear();
		clearPlayerOpponents();
		forcedDanmakuCombat = false;
		combatAdminBypass = false;
		statusInitialized = false;
		playerSpellActive = false;
		spellMovementRestricted = false;
		spellInvulnerable = false;
		hidden = 0;
		step = 0;
		weak = WEAK;
		if (player instanceof ServerPlayer sp) {
			SpellContainer.clear(sp);
			sp.displayClientMessage(YHLangData.STG_DEFEAT.get(), true);
			sp.playNotifySound(SoundEvents.PLAYER_DEATH, SoundSource.PLAYERS, 1.0f, 0.8f);
			int duration = YHModConfig.COMMON.beatenDurationTicks.get();
			if (duration > 0 && !sp.hasEffect(YHEffects.BEATEN.get())) {
				sp.addEffect(new MobEffectInstance(YHEffects.BEATEN.get(), duration, 0));
			}
			MinecraftForge.EVENT_BUS.post(new StgCombatEvent.Defeat(sp, fatalSource, snap.ids(), snap.entities()));
			sync();
		}
		dirty = true;
	}

	private void eraseDefeatDanmaku() {
		// Session hosts own the normal boss projectiles. Clear them before removing session metadata.
		eraseActiveDanmaku(0, true);
		if (!(player.level() instanceof ServerLevel level)) return;

		for (UUID opponentId : playerOpponents) {
			if (level.getEntity(opponentId) instanceof ServerPlayer opponent) {
				SpellContainer.clear(opponent);
			}
		}

		Set<UUID> sessionIds = Set.copyOf(sessions.keySet());
		Set<UUID> cleanedProxies = new HashSet<>();
		cleanupDefeatSpellProxies(level, hostSearchArea(player.position(), ACTIVE_DANMAKU_HOST_SEARCH_RANGE),
				sessionIds, cleanedProxies);
		for (CombatSession session : sessions.values()) {
			LivingEntity opponent = session.getTarget(player);
			if (opponent != null) {
				cleanupDefeatSpellProxies(level,
						hostSearchArea(opponent.position(), ACTIVE_DANMAKU_HOST_SEARCH_RANGE),
						sessionIds, cleanedProxies);
			}
		}
	}

	private void cleanupDefeatSpellProxies(ServerLevel level, AABB area, Set<UUID> sessionIds,
										 Set<UUID> cleanedProxies) {
		for (EntitySpellProxyEntity proxy : level.getEntitiesOfClass(EntitySpellProxyEntity.class, area)) {
			if (!cleanedProxies.add(proxy.getUUID())) continue;
			LivingEntity owner = proxy.owner();
			boolean sessionOwner = owner != null && sessionIds.contains(owner.getUUID());
			boolean targetsDefeatedPlayer = proxy.targetEntity() == player;
			if ((sessionOwner || targetsDefeatedPlayer) && (owner == null || !owner.isAlliedTo(player))) {
				proxy.cleanup();
			}
		}
	}

	/** Snapshot of active Youkai sessions + player opponents for public STG events. */
	public OpponentSnapshot snapshotOpponents() {
		if (player instanceof ServerPlayer sp) return snapshotOpponents(sp);
		return new OpponentSnapshot(List.of(), List.of());
	}

	private OpponentSnapshot snapshotOpponents(ServerPlayer sp) {
		LinkedHashSet<UUID> ids = new LinkedHashSet<>();
		ArrayList<LivingEntity> entities = new ArrayList<>();
		for (var entry : sessions.entrySet()) {
			ids.add(entry.getKey());
			LivingEntity le = entry.getValue().getTarget(player);
			if (le != null && !entities.contains(le)) entities.add(le);
		}
		if (sp.level() instanceof ServerLevel sl) {
			for (UUID id : playerOpponents) {
				ids.add(id);
				if (sl.getEntity(id) instanceof LivingEntity le && !entities.contains(le)) {
					entities.add(le);
				}
			}
		} else {
			ids.addAll(playerOpponents);
		}
		return new OpponentSnapshot(List.copyOf(ids), List.copyOf(entities));
	}

	public record OpponentSnapshot(List<UUID> ids, List<LivingEntity> entities) {
	}

	/**
	 * Clear active combat sessions / opponents.
	 * Does not wipe life/bomb/power — those persist until death, defeat, or sleep top-up.
	 */
	public void clearCombatState(boolean eraseDanmaku) {
		if (eraseDanmaku) {
			eraseActiveDanmaku(0, true);
			if (player.level() instanceof ServerLevel level) {
				for (UUID opponentId : playerOpponents) {
					if (level.getEntity(opponentId) instanceof ServerPlayer opponent) {
						SpellContainer.clear(opponent);
					}
				}
			}
		}
		for (var session : sessions.values()) {
			session.resetTarget(player);
		}
		sessions.clear();
		clearPlayerOpponents();
		forcedDanmakuCombat = false;
		combatAdminBypass = false;
		statusInitialized = false;
		playerSpellActive = false;
		spellMovementRestricted = false;
		spellInvulnerable = false;
		hidden = 0;
		step = 0;
		dirty = true;
		if (player instanceof ServerPlayer sp) {
			SpellContainer.clear(sp);
			sync();
		}
	}

	public static void register() {
	}

	@SerialClass
	public static class CombatSession {

		@SerialClass.SerialField
		private UUID uuid;
		@SerialClass.SerialField
		private int uid;

		private YoukaiEntity youkai;

		public CombatSession init(YoukaiEntity e) {
			uuid = e.getUUID();
			uid = e.getId();
			youkai = e;
			return this;
		}

		public boolean shouldRemove(ServerLevel sl, Player player) {
			if (youkai == null) {
				if (sl.getEntity(uuid) instanceof YoukaiEntity e) {
					youkai = e;
					uid = youkai.getId();
				} else return true;
			}
			if (!youkai.isAlive() || !EntityStorageHelper.isPresent(youkai))
				return true;
			return !youkai.targets.contains(player);
		}

		@Nullable
		public LivingEntity getTarget(Player player) {
			if (youkai != null) return youkai;
			return player.level().getEntity(uid) instanceof LivingEntity le ? le : null;
		}

		protected void resetTarget(Player player) {
			if (getTarget(player) instanceof YoukaiEntity e) {
				e.resetTarget(player);
			}
		}

		protected void eraseDanmaku(Player player) {
			if (getTarget(player) instanceof YoukaiEntity e) {
				e.eraseAllDanmaku(player);
			}
		}

	}

	public record InfoLine(String text, InfoIcon icon, int x, int y) {

	}

	public record InfoIcon(ResourceLocation loc, int w, int h) {

	}

	public enum HitType {
		NONE, INVUL, BOMB, LIFE, ERASE, LAST;

		public boolean skipDamage() {
			return this == BOMB || this == LIFE || this == INVUL || this == LAST;
		}

		public boolean erase() {
			return this == BOMB || this == LIFE || this == ERASE || this == LAST;
		}

	}

}
