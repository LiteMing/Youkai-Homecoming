package dev.xkmc.youkaishomecoming.content.spell.item;

import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.fastprojectileapi.render.virtual.DanmakuManager;
import dev.xkmc.l2library.capability.conditionals.*;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuProxyEntity;
import dev.xkmc.youkaishomecoming.content.spell.SpellProgressColor;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellProgressSnapshot;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@SerialClass
public class SpellContainer extends ConditionalToken {

	private static final TokenKey<SpellContainer> SPELL = TokenKey.of(YoukaisHomecoming.loc("spellcards"));

	private static final Provider PVD = new Provider();

	public static void clear(ServerPlayer sp) {
		var data = ConditionalData.HOLDER.get(sp).getOrCreateData(PVD, PVD);
		data.clearSpellState(sp);
		erase(data.combatItemCache);
		erase(data.ambientItemCache);
		DanmakuManager.flushErases();
	}

	/** Clear spell output and item projectiles fired during the current STG combat only. */
	public static void clearCombat(ServerPlayer sp) {
		var data = ConditionalData.HOLDER.get(sp).getOrCreateData(PVD, PVD);
		data.clearSpellState(sp);
		erase(data.combatItemCache);
		DanmakuManager.flushErases();
	}

	/** Remove loose item projectiles from before STG combat without touching the new combat state. */
	public static void clearOutsideCombat(ServerPlayer sp) {
		var data = ConditionalData.HOLDER.get(sp).getOrCreateData(PVD, PVD);
		erase(data.ambientItemCache);
		DanmakuManager.flushErases();
	}

	/** Track a loose item projectile in the combat domain it was fired from. */
	public static void track(ServerPlayer sp, SimplifiedProjectile e, boolean combatScoped) {
		var data = ConditionalData.HOLDER.get(sp).getOrCreateData(PVD, PVD);
		(combatScoped ? data.combatItemCache : data.ambientItemCache).add(e);
	}

	public static void trackProxy(ServerPlayer sp, DanmakuProxyEntity proxy, @Nullable String cardKey) {
		var data = ConditionalData.HOLDER.get(sp).getOrCreateData(PVD, PVD);
		data.proxies.add(proxy);
		data.activeSpellCardKey = cardKey;
	}

	public static boolean forceCloseActiveSpell(ServerPlayer sp, @Nullable String fallbackCardKey) {
		var data = ConditionalData.HOLDER.get(sp).getOrCreateData(PVD, PVD);
		if (data.spells.isEmpty() && data.proxies.stream().noneMatch(proxy -> !proxy.isRemoved())) {
			return false;
		}
		String cardKey = data.activeSpellCardKey == null ? fallbackCardKey : data.activeSpellCardKey;
		boolean inCombat = GrazeCapability.HOLDER.get(sp).isInDanmakuCombat();
		data.clearSpellState(sp);
		DanmakuManager.flushErases();
		if (inCombat) {
			GrazeCapability.HOLDER.get(sp).disableSpellCardForCombat(cardKey);
		}
		return true;
	}

	/** True while the player is releasing a spell card (an active proxy exists). */
	public static boolean hasActiveProxy(Player player) {
		var data = ConditionalData.HOLDER.get(player).getOrCreateData(PVD, PVD);
		return data.proxies.stream().anyMatch(proxy -> !proxy.isRemoved());
	}

	/** True while either a legacy or data-driven player spell is being released. */
	public static boolean hasActiveSpell(Player player) {
		var data = ConditionalData.HOLDER.get(player).getOrCreateData(PVD, PVD);
		return !data.spells.isEmpty() ||
				data.proxies.stream().anyMatch(proxy -> !proxy.isRemoved());
	}

	/** Clear all player-owned spell output when a beaten state starts. */
	public static void clearForBeaten(ServerPlayer sp) {
		var data = ConditionalData.HOLDER.get(sp).getOrCreateData(PVD, PVD);
		boolean active = !data.spells.isEmpty() || !data.cache.isEmpty() ||
				!data.combatItemCache.isEmpty() || !data.ambientItemCache.isEmpty() ||
				data.proxies.stream().anyMatch(proxy -> !proxy.isRemoved());
		if (active) {
			clear(sp);
		}
	}

	/** True when an active data-driven spell currently owns the player's movement. */
	public static boolean restrictsManualMovement(Player player) {
		var data = ConditionalData.HOLDER.get(player).getOrCreateData(PVD, PVD);
		return data.proxies.stream().anyMatch(proxy -> !proxy.isRemoved() && proxy.restrictsManualMovement());
	}

	public record ActiveSpellStatus(int health, int maxHealth, int elapsedTicks, int durationTicks,
			int completedHealth, int[] healthSegments) {
		public static final ActiveSpellStatus NONE = new ActiveSpellStatus(0, 0, 0, 0, 0, new int[0]);

		public ActiveSpellStatus {
			healthSegments = healthSegments == null ? new int[0] : healthSegments.clone();
		}

		@Override
		public int[] healthSegments() {
			return healthSegments.clone();
		}

		public boolean active() {
			return maxHealth > 0 || durationTicks > 0;
		}
	}

	/** Server-authoritative snapshot used by the PVP spell-circle projection. */
	public static ActiveSpellStatus activeSpellStatus(Player player) {
		var data = ConditionalData.HOLDER.get(player).getOrCreateData(PVD, PVD);
		DanmakuProxyEntity proxy = data.proxies.stream()
				.filter(e -> !e.isRemoved()).findFirst().orElse(null);
		if (proxy == null) return ActiveSpellStatus.NONE;
		int displayedHealth = data.displayedSpellBarValue();
		SpellProgressSnapshot progress = SpellProgressSnapshot.fromTotalRemaining(
				proxy.getSpellRuntime(), displayedHealth);
		if (!progress.active()) {
			return new ActiveSpellStatus(displayedHealth, Math.max(0, data.spellBarMax),
					proxy.spellElapsedTicks(), proxy.spellDurationTicks(), 0,
					data.spellBarMax > 0 ? new int[]{data.spellBarMax} : new int[0]);
		}
		return new ActiveSpellStatus(progress.health(), progress.segmentMaxHealth(),
				progress.elapsedTicks(), progress.durationTicks(), progress.completedHealth(),
				progress.healthSegments());
	}

	/** True while a set_spell_health plan is protecting the active player spell. */
	public static boolean hasActiveSpellBar(Player player) {
		var data = ConditionalData.HOLDER.get(player).getOrCreateData(PVD, PVD);
		return data.spellBarMax > 0 && data.proxies.stream().anyMatch(proxy -> !proxy.isRemoved());
	}

	@Nullable
	private net.minecraft.world.phys.Vec3 lockPos;

	// ------------------------------------------------------------ player-use spell bar

	/**
	 * Complete spell cards keep a server-side break HP value from their declared
	 * health plan. The precise value stays server-side and is projected onto the
	 * player's spell circle; incoming danmaku damage shrinks it, and reaching zero
	 * breaks the spell and costs one LIFE.
	 */
	public static void startSpellBar(ServerPlayer sp, int maxHp, @Nullable String cardKey, Component spellName) {
		var data = ConditionalData.HOLDER.get(sp).getOrCreateData(PVD, PVD);
		data.endSpellBar(sp);
		data.spellBarMax = Math.max(1, maxHp);
		data.spellBarValue = data.spellBarMax;
		data.spellBarCardKey = cardKey;
		data.spellBarName = spellName.copy();
		data.syncPlayerSpellStatus(sp);
	}

	/** Initializes dynamic health and reconciles the total shield after a runtime phase switch. */
	public static void syncRuntimeSpellBar(ServerPlayer sp, DanmakuProxyEntity proxy) {
		var data = ConditionalData.HOLDER.get(sp).getOrCreateData(PVD, PVD);
		if (!data.proxies.contains(proxy)) return;
		var runtime = proxy.getSpellRuntime();
		if (runtime == null || runtime.getSpellHealthTotal() <= 0) {
			if (data.spellBarMax > 0) data.endSpellBar(sp);
			return;
		}
		if (data.spellBarMax <= 0) {
			data.spellBarMax = runtime.getSpellHealthTotal();
			data.spellBarValue = data.spellBarMax;
			data.spellBarCardKey = data.activeSpellCardKey;
			data.spellBarName = runtime.getDefinition().display.displayName().copy();
		} else {
			SpellProgressSnapshot phaseStart = SpellProgressSnapshot.fromRuntime(
					runtime, runtime.getSpellMaxHealth());
			data.spellBarValue = Math.min(data.spellBarValue, phaseStart.totalRemainingHealth());
		}
		data.syncPlayerSpellStatus(sp);
	}

	/** Refreshes the player and opponent Bossbars from the proxy's current tick. */
	public static void refreshSpellBossBar(ServerPlayer sp, DanmakuProxyEntity proxy) {
		var data = ConditionalData.HOLDER.get(sp).getOrCreateData(PVD, PVD);
		if (data.spellBarMax > 0 && data.proxies.contains(proxy) && !proxy.isRemoved()) {
			data.syncPlayerSpellStatus(sp);
		}
	}

	/** Removes a proxy-owned Bossbar even when the capability token is discarded first. */
	public static void onProxyRemoved(ServerPlayer sp, DanmakuProxyEntity proxy) {
		var data = ConditionalData.HOLDER.get(sp).getOrCreateData(PVD, PVD);
		if (!data.proxies.remove(proxy)) return;
		if (data.proxies.stream().noneMatch(e -> !e.isRemoved())) {
			data.activeSpellCardKey = null;
			data.endSpellBar(sp);
			data.lockPos = null;
		} else if (data.spellBarMax > 0) {
			data.syncPlayerSpellStatus(sp);
		}
	}

	public static void removeSpellBar(ServerPlayer sp) {
		ConditionalData.HOLDER.get(sp).getOrCreateData(PVD, PVD).endSpellBar(sp);
	}

	/**
	 * A miss while the spell bar is up: apply its actual danmaku damage and break
	 * (interrupt) the spell when the current health chain reaches zero. Damage is
	 * capped at the current segment so it cannot spill into a newly entered phase.
	 * Returns the authoritative STG hit result, or null when no spell bar was active.
	 */
	@Nullable
	public static GrazeCapability.HitType consumeSpellBarHit(ServerPlayer sp, @Nullable LivingEntity source,
			float damage) {
		var data = ConditionalData.HOLDER.get(sp).getOrCreateData(PVD, PVD);
		if (data.spellBarMax <= 0) {
			return null;
		}
		DanmakuProxyEntity proxy = data.proxies.stream()
				.filter(e -> !e.isRemoved()).findFirst().orElse(null);
		SpellProgressSnapshot before = proxy == null ? SpellProgressSnapshot.NONE
				: SpellProgressSnapshot.fromTotalRemaining(
						proxy.getSpellRuntime(), data.displayedSpellBarValue());
		float appliedDamage = Float.isFinite(damage) ? Math.max(0, damage) : 0;
		float segmentHealth = data.spellBarValue;
		if (before.active()) {
			int futureHealth = before.totalRemainingHealth() - before.health();
			segmentHealth = Math.max(0, Math.min(before.health(), data.spellBarValue - futureHealth));
			appliedDamage = Math.min(appliedDamage, segmentHealth);
		}
		data.spellBarValue = Math.max(0, data.spellBarValue - appliedDamage);
		if (before.active() && segmentHealth > 0 && appliedDamage >= segmentHealth && proxy != null
				&& proxy.getSpellRuntime() != null) {
			proxy.getSpellRuntime().triggerSpellHealthBreak(proxy);
		}
		if (data.spellBarValue <= 0) {
			String cardKey = data.spellBarCardKey;
			data.endSpellBar(sp);
			breakActiveSpell(sp);
			return GrazeCapability.HOLDER.get(sp).breakSpellCardForCombat(cardKey, source);
		}
		data.syncPlayerSpellStatus(sp);
		return GrazeCapability.HitType.LIFE;
	}

	/** Interrupt the currently released spell card (erases its danmaku). */
	public static void breakActiveSpell(ServerPlayer sp) {
		var data = ConditionalData.HOLDER.get(sp).getOrCreateData(PVD, PVD);
		for (var proxy : List.copyOf(data.proxies)) {
			if (!proxy.isRemoved()) {
				proxy.eraseAllDanmaku(null);
				proxy.cleanup();
			}
		}
		data.proxies.clear();
		data.syncPlayerSpellStatus(sp);
		DanmakuManager.flushErases();
	}

	private float spellBarValue;
	private int spellBarMax;
	@Nullable
	private String spellBarCardKey;
	@Nullable
	private Component spellBarName;
	@Nullable
	private String activeSpellCardKey;
	@Nullable
	private ServerBossEvent ownSpellBossEvent;
	@Nullable
	private ServerBossEvent opponentSpellBossEvent;

	private void endSpellBar(Player player) {
		spellBarValue = 0;
		spellBarMax = 0;
		spellBarCardKey = null;
		spellBarName = null;
		if (player instanceof ServerPlayer sp) {
			syncPlayerSpellStatus(sp);
		} else {
			clearSpellBossBars();
		}
	}

	private int displayedSpellBarValue() {
		if (!Float.isFinite(spellBarValue) || spellBarValue <= 0) return 0;
		return (int) Math.min(Integer.MAX_VALUE, Math.ceil(spellBarValue));
	}

	private void syncPlayerSpellStatus(ServerPlayer sp) {
		var proxy = proxies.stream().filter(e -> !e.isRemoved()).findFirst().orElse(null);
		var cap = GrazeCapability.HOLDER.get(sp);
		ActiveSpellStatus status = activeSpellStatus(sp);
		cap.setPlayerSpellStatus(new GrazeCapability.SpellProgressStatus(
				status.health(), status.maxHealth(), status.elapsedTicks(), status.durationTicks(),
				status.completedHealth(), status.healthSegments()));
		syncSpellBossBars(sp, proxy);
		cap.sync();
	}

	private void syncSpellBossBars(ServerPlayer sp, @Nullable DanmakuProxyEntity proxy) {
		if (spellBarMax <= 0 || proxy == null) {
			clearSpellBossBars();
			return;
		}
		if (ownSpellBossEvent == null) {
			ownSpellBossEvent = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.BLUE,
					BossEvent.BossBarOverlay.PROGRESS);
		}
		if (opponentSpellBossEvent == null) {
			opponentSpellBossEvent = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.RED,
					BossEvent.BossBarOverlay.PROGRESS);
		}

		float progress = Math.max(0, Math.min(1, spellBarValue / (float) spellBarMax));
		Component name = spellBossBarName(sp, proxy);
		ownSpellBossEvent.setProgress(progress);
		opponentSpellBossEvent.setProgress(progress);
		ownSpellBossEvent.setName(name);
		opponentSpellBossEvent.setName(name);
		ownSpellBossEvent.setColor(SpellProgressColor.bossBarColor(sp, BossEvent.BossBarColor.BLUE));
		opponentSpellBossEvent.setColor(SpellProgressColor.bossBarColor(sp, BossEvent.BossBarColor.RED));
		ownSpellBossEvent.setVisible(true);
		opponentSpellBossEvent.setVisible(true);
		ownSpellBossEvent.addPlayer(sp);

		Set<ServerPlayer> opponents = new HashSet<>();
		for (LivingEntity entity : GrazeCapability.HOLDER.get(sp).snapshotOpponents().entities()) {
			if (entity instanceof ServerPlayer opponent && opponent != sp) {
				opponents.add(opponent);
			}
		}
		for (ServerPlayer viewer : List.copyOf(opponentSpellBossEvent.getPlayers())) {
			if (!opponents.contains(viewer)) opponentSpellBossEvent.removePlayer(viewer);
		}
		for (ServerPlayer opponent : opponents) {
			opponentSpellBossEvent.addPlayer(opponent);
		}
	}

	private Component spellBossBarName(ServerPlayer sp, DanmakuProxyEntity proxy) {
		Component name = Component.empty().append(sp.getDisplayName());
		if (spellBarName != null) {
			name = Component.empty().append(name).append(Component.literal(" - ")).append(spellBarName);
		}
		int duration = proxy.spellDurationTicks();
		if (duration <= 0) return name;
		int remainingTicks = Math.max(0, duration - proxy.spellElapsedTicks());
		int remainingSeconds = (remainingTicks + 19) / 20;
		return Component.empty().append(name).append(Component.literal(
				"  " + remainingSeconds + "s").withStyle(ChatFormatting.AQUA));
	}

	private void clearSpellBossBars() {
		if (ownSpellBossEvent != null) ownSpellBossEvent.setVisible(false);
		if (opponentSpellBossEvent != null) opponentSpellBossEvent.setVisible(false);
		if (ownSpellBossEvent != null) ownSpellBossEvent.removeAllPlayers();
		if (opponentSpellBossEvent != null) opponentSpellBossEvent.removeAllPlayers();
	}

	public static void castSpell(ServerPlayer sp, Supplier<? extends ItemSpell> sup,
			@Nullable LivingEntity target, @Nullable String cardKey) {
		ItemSpell spell = sup.get();
		spell.start(sp, target);
		SpellContainer data = ConditionalData.HOLDER.get(sp).getOrCreateData(PVD, PVD);
		data.spells.add(spell);
		data.activeSpellCardKey = cardKey;
	}

	@SerialClass.SerialField
	private final List<ItemSpell> spells = new LinkedList<>();

	private final List<SimplifiedProjectile> cache = new LinkedList<>();
	private final List<SimplifiedProjectile> combatItemCache = new LinkedList<>();
	private final List<SimplifiedProjectile> ambientItemCache = new LinkedList<>();

	private final List<DanmakuProxyEntity> proxies = new ArrayList<>();

	private void clearSpellState(ServerPlayer sp) {
		for (var spell : spells) {
			erase(spell.cache);
		}
		erase(cache);
		for (var proxy : List.copyOf(proxies)) {
			if (!proxy.isRemoved()) proxy.cleanup();
		}
		spells.clear();
		proxies.clear();
		activeSpellCardKey = null;
		endSpellBar(sp);
	}

	private static void erase(List<SimplifiedProjectile> projectiles) {
		for (var projectile : projectiles) {
			projectile.markErased(true);
		}
		projectiles.clear();
	}

	@Override
	public boolean tick(Player player) {
		var itr = spells.iterator();
		while (itr.hasNext()) {
			var spell = itr.next();
			boolean remove = spell.tick(player);
			if (remove) {
				itr.remove();
				cache.addAll(spell.cache);
			}
		}
		cache.removeIf(e -> !e.isValid());
		combatItemCache.removeIf(e -> !e.isValid());
		ambientItemCache.removeIf(e -> !e.isValid());
		proxies.removeIf(DanmakuProxyEntity::isRemoved);
		if (spells.isEmpty() && proxies.isEmpty()) {
			activeSpellCardKey = null;
		}
		if (proxies.isEmpty() && spellBarMax > 0) {
			// spell ended naturally: drop the bar
			endSpellBar(player);
			lockPos = null;
		}
		return spells.isEmpty() && cache.isEmpty() && combatItemCache.isEmpty() &&
				ambientItemCache.isEmpty() && proxies.isEmpty();
	}

	private record Provider() implements TokenProvider<SpellContainer, Provider>, Context {

		@Override
		public SpellContainer getData(Provider provider) {
			return new SpellContainer();
		}

		@Override
		public TokenKey<SpellContainer> getKey() {
			return SPELL;
		}
	}

}
