package dev.xkmc.youkaishomecoming.content.spell.item;

import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.fastprojectileapi.render.virtual.DanmakuManager;
import dev.xkmc.l2library.capability.conditionals.*;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuProxyEntity;
import dev.xkmc.youkaishomecoming.content.spell.SpellProgressColor;
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

	public static void trackProxy(ServerPlayer sp, DanmakuProxyEntity proxy) {
		var data = ConditionalData.HOLDER.get(sp).getOrCreateData(PVD, PVD);
		data.proxies.add(proxy);
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

	public record ActiveSpellStatus(int health, int maxHealth, int elapsedTicks, int durationTicks) {
		public static final ActiveSpellStatus NONE = new ActiveSpellStatus(0, 0, 0, 0);

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
		return new ActiveSpellStatus(Math.max(0, data.spellBarValue), Math.max(0, data.spellBarMax),
				proxy.spellElapsedTicks(), proxy.spellDurationTicks());
	}

	@Nullable
	private net.minecraft.world.phys.Vec3 lockPos;

	// ------------------------------------------------------------ player-use spell bar

	/**
	 * Certified spell cards keep a server-side break HP value (a fraction of the
	 * certification HP). The value is projected onto the player's spell circle;
	 * each miss shrinks it by 1 and a zero bar breaks the spell and costs one LIFE.
	 */
	public static void startSpellBar(ServerPlayer sp, int maxHp, @Nullable String cardKey, Component spellName) {
		var data = ConditionalData.HOLDER.get(sp).getOrCreateData(PVD, PVD);
		data.endSpellBar(sp);
		data.spellBarValue = Math.max(1, maxHp);
		data.spellBarMax = data.spellBarValue;
		data.spellBarCardKey = cardKey;
		data.spellBarName = spellName.copy();
		data.syncPlayerSpellStatus(sp);
	}

	public static void removeSpellBar(ServerPlayer sp) {
		ConditionalData.HOLDER.get(sp).getOrCreateData(PVD, PVD).endSpellBar(sp);
	}

	/**
	 * A miss while the spell bar is up: shrink it by 1 and break (interrupt) the
	 * spell when it reaches zero. Returns the authoritative STG hit result, or
	 * null when no spell bar was active.
	 */
	@Nullable
	public static GrazeCapability.HitType consumeSpellBarHit(ServerPlayer sp, @Nullable LivingEntity source) {
		var data = ConditionalData.HOLDER.get(sp).getOrCreateData(PVD, PVD);
		if (data.spellBarMax <= 0) {
			return null;
		}
		data.spellBarValue = Math.max(0, data.spellBarValue - 1);
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
		for (var proxy : data.proxies) {
			if (!proxy.isRemoved()) {
				proxy.eraseAllDanmaku(null);
				proxy.cleanup();
			}
		}
		data.proxies.clear();
		data.syncPlayerSpellStatus(sp);
		DanmakuManager.flushErases();
	}

	private int spellBarValue;
	private int spellBarMax;
	@Nullable
	private String spellBarCardKey;
	@Nullable
	private Component spellBarName;
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

	private void syncPlayerSpellStatus(ServerPlayer sp) {
		var proxy = proxies.stream().filter(e -> !e.isRemoved()).findFirst().orElse(null);
		var cap = GrazeCapability.HOLDER.get(sp);
		cap.setPlayerSpellStatus(new GrazeCapability.SpellProgressStatus(
				spellBarValue, spellBarMax,
				proxy == null ? 0 : proxy.spellElapsedTicks(),
				proxy == null ? 0 : proxy.spellDurationTicks()));
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
		if (ownSpellBossEvent != null) ownSpellBossEvent.removeAllPlayers();
		if (opponentSpellBossEvent != null) opponentSpellBossEvent.removeAllPlayers();
	}

	public static void castSpell(ServerPlayer sp, Supplier<? extends ItemSpell> sup, @Nullable LivingEntity target) {
		ItemSpell spell = sup.get();
		spell.start(sp, target);
		ConditionalData.HOLDER.get(sp).getOrCreateData(PVD, PVD).spells.add(spell);
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
		for (var proxy : proxies) {
			if (!proxy.isRemoved()) proxy.cleanup();
		}
		spells.clear();
		proxies.clear();
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
		if (player instanceof ServerPlayer sp && spellBarMax > 0 && (player.tickCount & 3) == 0) {
			syncPlayerSpellStatus(sp);
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
