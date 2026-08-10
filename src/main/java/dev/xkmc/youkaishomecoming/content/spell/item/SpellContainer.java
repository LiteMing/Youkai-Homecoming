package dev.xkmc.youkaishomecoming.content.spell.item;

import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.fastprojectileapi.render.virtual.DanmakuManager;
import dev.xkmc.l2library.capability.conditionals.*;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuProxyEntity;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
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

	@Nullable
	private net.minecraft.world.phys.Vec3 lockPos;

	// ------------------------------------------------------------ player-use spell bar

	/**
	 * Certified spell cards show a boss bar with the spell's break HP (a fraction
	 * of the certification HP, see certificationPlayerUseHpRatio). The bar is
	 * shown while the player releases the card; each miss shrinks it by 1 and a
	 * zero bar breaks (interrupts) the spell instead of costing life.
	 */
	public static void startSpellBar(ServerPlayer sp, int maxHp) {
		var data = ConditionalData.HOLDER.get(sp).getOrCreateData(PVD, PVD);
		data.endSpellBar(sp);
		data.spellBar = new net.minecraft.server.level.ServerBossEvent(
				net.minecraft.network.chat.Component.literal("Spell Card"),
				net.minecraft.world.BossEvent.BossBarColor.RED,
				net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
		data.spellBarValue = Math.max(1, maxHp);
		data.spellBarMax = data.spellBarValue;
		data.spellBar.setProgress(1.0f);
		data.spellBar.addPlayer(sp);
	}

	public static void removeSpellBar(ServerPlayer sp) {
		ConditionalData.HOLDER.get(sp).getOrCreateData(PVD, PVD).endSpellBar(sp);
	}

	/**
	 * A miss while the spell bar is up: shrink it by 1 and break (interrupt) the
	 * spell when it reaches zero. Returns true when the bar absorbed the hit.
	 */
	public static boolean consumeSpellBarHit(ServerPlayer sp) {
		var data = ConditionalData.HOLDER.get(sp).getOrCreateData(PVD, PVD);
		if (data.spellBar == null) {
			return false;
		}
		data.spellBarValue = Math.max(0, data.spellBarValue - 1);
		data.spellBar.setProgress(data.spellBarMax <= 0 ? 0
				: data.spellBarValue / (float) data.spellBarMax);
		if (data.spellBarValue <= 0) {
			data.endSpellBar(sp);
			breakActiveSpell(sp);
		}
		return true;
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
		DanmakuManager.flushErases();
	}

	@Nullable
	private net.minecraft.server.level.ServerBossEvent spellBar;
	private int spellBarValue;
	private int spellBarMax;

	private void endSpellBar(Player player) {
		if (spellBar != null && player instanceof ServerPlayer sp) {
			spellBar.removePlayer(sp);
		}
		spellBar = null;
		spellBarValue = 0;
		spellBarMax = 0;
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
		if (proxies.isEmpty() && spellBar != null) {
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
