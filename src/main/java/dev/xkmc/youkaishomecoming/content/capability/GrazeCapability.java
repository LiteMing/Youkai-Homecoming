package dev.xkmc.youkaishomecoming.content.capability;

import dev.xkmc.fastprojectileapi.collision.EntityStorageHelper;
import dev.xkmc.l2library.capability.player.PlayerCapabilityHolder;
import dev.xkmc.l2library.capability.player.PlayerCapabilityNetworkHandler;
import dev.xkmc.l2library.capability.player.PlayerCapabilityTemplate;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.compat.stg.StgCombatMode;
import dev.xkmc.youkaishomecoming.compat.stg.event.StgBombEvent;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuProxyEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.EntitySpellProxyEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.spell.item.SpellContainer;
import dev.xkmc.youkaishomecoming.events.DanmakuLastHitEvent;
import dev.xkmc.youkaishomecoming.events.EffectEventHandlers;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import dev.xkmc.youkaishomecoming.init.data.YHTagGen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
			power = 0;
			hidden = 0;
			step = 0;
			invul = 0;
			life = 0;
			bomb = 0;
			weak = 0;
			forcedDanmakuCombat = false;
			playerOpponents.clear();
			dirty = true;
		}
	}

	public void initStatus() {
		int initResource = GrazeHelper.getInitialResource(player) * SHARD;
		int initPower = GrazeHelper.getInitialPower(player) * MAX_GRAZE;
		life = Math.max(initResource, life);
		bomb = Math.max(initResource, bomb);
		power = Math.max(initPower, power);
	}

	@Override
	public void tick() {
		boolean full = EffectEventHandlers.canDanmakuCombat(player);
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
			if (!full) {
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
					if (playerOpponents.removeIf(id -> shouldRemovePlayerOpponent(sl, id))) {
						dirty = true;
					}
				}
			}
			if (dirty)
				sync();
			syncPvpOpponentStatus(sl);
		}
		dirty = false;
		if (player.level().isClientSide) {
			GrazeHelper.globalInvulTime = invul;
			GrazeHelper.globalForbidTime = Math.max(invul, weak);
		}
	}

	public boolean graze() {
		if (invul > 0) return false;
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
		if (!EffectEventHandlers.canDanmakuCombat(player)) return HitType.NONE;
		if (!sessions.containsKey(e.getUUID()) && !forcedDanmakuCombat) return HitType.ERASE;
		return performDanmakuHit(e);
	}

	public HitType performDanmakuHit(@Nullable LivingEntity source) {
		if (!EffectEventHandlers.canDanmakuCombat(player)) return HitType.NONE;
		if (invul > 0) return HitType.INVUL;
		int erased = eraseActiveDanmakuForHit(source);
		if (getStgCombatMode().autoBombOnHit() && useBomb()) {
			if (player instanceof ServerPlayer sp) {
				MinecraftForge.EVENT_BUS.post(new StgBombEvent.Auto(sp, source, erased));
			}
			return HitType.BOMB;
		}
		int maxLoss = (int) (YHModConfig.COMMON.maxPowerLossOnMiss.get() * MAX_GRAZE);
		int loss = Math.min(power / 2, maxLoss);
		power -= loss;
		dirty = true;
		invul = YHModConfig.COMMON.missInvulTime.get();
		if (player instanceof ServerPlayer sp) {
			YoukaisHomecoming.HANDLER.toClientPlayer(new GrazeHelper.GrazeToClient().set(1), sp);
			SpellContainer.clear(sp);
		}
		if (life < SHARD) {
			if (source instanceof YoukaiEntity e && MinecraftForge.EVENT_BUS.post(new DanmakuLastHitEvent(player, e))) {
				dirty = true;
				return HitType.LIFE;
			}
			exitDanmakuCombatOnLastHit();
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
	public boolean useBomb() {
		if (bomb < SHARD) return false;
		bomb -= SHARD;
		invul = YHModConfig.COMMON.bombInvulTime.get();
		dirty = true;
		return true;
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
			boolean holding = player.getMainHandItem().is(YHTagGen.DANMAKU_SHOOTER) ||
					player.getOffhandItem().is(YHTagGen.DANMAKU_SHOOTER);
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

	public void setForcedDanmakuCombat(boolean enabled) {
		if (forcedDanmakuCombat == enabled) return;
		if (enabled && !isInDanmakuCombat()) {
			initStatus();
		}
		forcedDanmakuCombat = enabled;
		dirty = true;
	}

	public void initSession(YoukaiEntity youkai) {
		if (sessions.containsKey(youkai.getUUID())) return;
		if (!isInDanmakuCombat()) initStatus();
		sessions.put(youkai.getUUID(), new CombatSession().init(youkai));
		youkai.targets.add(player);
		dirty = true;
	}

	public void addPlayerOpponent(Player target) {
		if (target == player || target.level() != player.level()) return;
		if (!isInDanmakuCombat()) initStatus();
		if (playerOpponents.add(target.getUUID())) {
			pvpStatusSyncCooldown = 0;
			dirty = true;
		}
	}

	public void stopSession(UUID uuid) {
		if (!sessions.containsKey(uuid)) return;
		sessions.remove(uuid);
		// Only clear spell proxies when in a full danmaku combat session (has youkai/fairy effect).
		// Normal players using dynamic spell without effects should keep their spell proxies running.
		if (sessions.isEmpty() && player instanceof ServerPlayer sp
				&& EffectEventHandlers.isFullCharacter(player)) {
			SpellContainer.clear(sp);
		}
		dirty = true;
	}

	public boolean shouldHurt(LivingEntity le) {
		if (le instanceof YoukaiEntity youkai) {
			if (weak > 0) return false;
			if (sessions.containsKey(youkai.getUUID())) return true;
			if (youkai.targets.contains(player)) return true;
			if (sessions.isEmpty() && canStartDanmakuSession()) {
				initSession(youkai);
				return true;
			}
			if (!EffectEventHandlers.canDanmakuCombat(player)) return true;
			return false;
		}
		if (!EffectEventHandlers.canDanmakuCombat(player)) return true;
		if (le instanceof Player target) {
			return forcedDanmakuCombat || playerOpponents.contains(target.getUUID()) ||
					EffectEventHandlers.isFullCharacter(player);
		}
		return sessions.isEmpty() || le instanceof Mob mob && mob.getTarget() == player;
	}

	public boolean shouldAbsorbDanmakuFrom(@Nullable LivingEntity source) {
		if (!EffectEventHandlers.canDanmakuCombat(player)) return false;
		if (forcedDanmakuCombat) return true;
		if (source instanceof YoukaiEntity youkai) {
			return sessions.containsKey(youkai.getUUID());
		}
		if (source instanceof Player player) {
			return playerOpponents.contains(player.getUUID()) || EffectEventHandlers.isFullCharacter(this.player);
		}
		return EffectEventHandlers.isFullCharacter(player);
	}

	public Optional<LivingEntity> findAny(Player player) {
		return sessions.values().stream().findAny().map(e -> e.getTarget(player));
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
		return invul > 0;
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
			pvpStatusSyncCooldown = 0;
			if (pvpStatusVisible) {
				YoukaisHomecoming.HANDLER.toClientPlayer(PvpDanmakuStatusToClient.clearAll(), sp);
				pvpStatusVisible = false;
			}
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
			return forcedDanmakuCombat || playerOpponents.contains(target.getUUID()) ||
					EffectEventHandlers.isFullCharacter(player);
		}
		return true;
	}

	private boolean shouldEraseEntityProxyHost(EntitySpellProxyEntity proxy) {
		if (proxy.isOwnedBy(player)) return false;
		LivingEntity owner = proxy.owner();
		if (owner == null) return false;
		if (owner.isAlliedTo(player)) return false;
		if (owner instanceof Player target) {
			return forcedDanmakuCombat || playerOpponents.contains(target.getUUID()) ||
					EffectEventHandlers.isFullCharacter(player);
		}
		if (owner instanceof YoukaiEntity youkai) {
			return sessions.containsKey(youkai.getUUID()) || youkai.targets.contains(player);
		}
		if (proxy.targetEntity() == player) return true;
		return forcedDanmakuCombat || owner instanceof Mob mob && mob.getTarget() == player ||
				EffectEventHandlers.isFullCharacter(player);
	}

	private static AABB hostSearchArea(Vec3 center, double radius) {
		double range = Math.max(ACTIVE_DANMAKU_HOST_SEARCH_RANGE, radius);
		return AABB.ofSize(center, range * 2, range * 2, range * 2);
	}

	private boolean canStartDanmakuSession() {
		return forcedDanmakuCombat || EffectEventHandlers.isFullCharacter(player);
	}

	private boolean shouldRemovePlayerOpponent(ServerLevel sl, UUID id) {
		var entity = sl.getEntity(id);
		return !(entity instanceof ServerPlayer target) || !target.isAlive() || target.level() != player.level();
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

	private void exitDanmakuCombatOnLastHit() {
		life = 0;
		bomb = 0;
		for (var s : sessions.values()) {
			s.resetTarget(player);
		}
		sessions.clear();
		playerOpponents.clear();
		forcedDanmakuCombat = false;
		weak = WEAK;
		if (player instanceof ServerPlayer sp) {
			SpellContainer.clear(sp);
			sync();
		}
		dirty = true;
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
