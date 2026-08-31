package dev.xkmc.youkaishomecoming.content.spell.item;

import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * An ItemSpell that drives a SpellRuntime instead of using hand-coded Tickers.
 * Used by DynamicSpellItem to let players cast any SpellDefinition.
 */
public class RuntimeItemSpell extends ItemSpell {

	private SpellDefinition definition;
	private final int maxDuration;
	private SpellRuntime runtime;
	private int tickCount;
	/** Runtimes replaced mid-cast but still owning held projectile callbacks. */
	private transient final List<SpellRuntime> delayedRuntimes = new ArrayList<>();

	public RuntimeItemSpell(SpellDefinition definition, int maxDuration) {
		this.definition = definition;
		this.maxDuration = maxDuration;
	}

	@Override
	public void start(Player player, @Nullable LivingEntity target) {
		super.start(player, target);
		runtime = new SpellRuntime(definition);
		runtime.reset();
		if (maxDuration >= 0) {
			runtime.setDurationOverride(maxDuration);
		}
		tickCount = 0;
		delayedRuntimes.clear();
	}

	@Override
	public boolean tick(Player player) {
		if (runtime == null) return true;
		// Drive normal ticker logic for cache cleanup
		cache.removeIf(e -> !e.isValid());

		if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)) return true;
		var target = getTarget(sp.serverLevel());
		updateTargetPosition(player, target);
		holder = new PlayerHolder(player, dir, this, target);

		// Keep the runtime alive long enough to release held projectiles, while
		// stopping the normal cast loop at maxDuration so no extra waves spawn.
		SpellRuntime active = runtime;
		if (tickCount < maxDuration) {
			active.tick(holder);
		} else {
			active.tickDelayed(holder);
		}
		if (runtime != active && active.hasPendingHoldActions()) {
			retainDelayedRuntime(active);
		}
		// A release callback may switch spells, which can add a runtime to the
		// retained list. Iterate a snapshot so that callback-side changes are safe.
		var delayedSnapshot = new ArrayList<>(delayedRuntimes);
		delayedRuntimes.removeAll(delayedSnapshot);
		for (SpellRuntime delayed : delayedSnapshot) {
			delayed.tickDelayed(holder);
			if (delayed.hasPendingHoldActions()) {
				retainDelayedRuntime(delayed);
			}
		}
		tickCount++;

		return tickCount >= maxDuration && !runtime.hasPendingHoldActions() && delayedRuntimes.isEmpty();
	}

	public void clearDanmaku() {
		for (var danmaku : cache) {
			danmaku.markErased(false);
		}
		cache.clear();
	}

	public void switchSpell(SpellDefinition definition, boolean clearScreen) {
		switchSpell(definition, new SpellRuntime(definition), clearScreen);
	}

	public void switchSpell(SpellDefinition definition, SpellRuntime nextRuntime, boolean clearScreen) {
		retainDelayedRuntime(runtime);
		this.definition = definition;
		if (clearScreen) {
			clearDanmaku();
		}
		runtime = nextRuntime;
	}

	private void retainDelayedRuntime(@Nullable SpellRuntime candidate) {
		if (candidate != null && candidate.hasPendingHoldActions()
				&& !delayedRuntimes.contains(candidate)) {
			delayedRuntimes.add(candidate);
		}
	}
}
