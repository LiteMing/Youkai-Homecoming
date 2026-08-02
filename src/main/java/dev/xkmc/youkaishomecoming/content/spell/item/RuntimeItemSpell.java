package dev.xkmc.youkaishomecoming.content.spell.item;

import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * An ItemSpell that drives a SpellRuntime instead of using hand-coded Tickers.
 * Used by DynamicSpellItem to let players cast any SpellDefinition.
 */
public class RuntimeItemSpell extends ItemSpell {

	private SpellDefinition definition;
	private final int maxDuration;
	private SpellRuntime runtime;
	private int tickCount;

	public RuntimeItemSpell(SpellDefinition definition, int maxDuration) {
		this.definition = definition;
		this.maxDuration = maxDuration;
	}

	@Override
	public void start(Player player, @Nullable LivingEntity target) {
		super.start(player, target);
		runtime = new SpellRuntime(definition);
		runtime.reset();
		tickCount = 0;
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

		runtime.tick(holder);
		tickCount++;

		return tickCount >= maxDuration;
	}

	public void clearDanmaku() {
		for (var danmaku : cache) {
			danmaku.markErased(false);
		}
		cache.clear();
	}

	public void switchSpell(SpellDefinition definition, boolean clearScreen) {
		this.definition = definition;
		if (clearScreen) {
			clearDanmaku();
		}
		runtime = new SpellRuntime(definition);
	}
}
