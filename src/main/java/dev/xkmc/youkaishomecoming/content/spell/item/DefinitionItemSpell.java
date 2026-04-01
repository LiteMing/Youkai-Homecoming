package dev.xkmc.youkaishomecoming.content.spell.item;

import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public class DefinitionItemSpell extends ItemSpell {

	private final SpellRuntime runtime;
	private final int duration;

	public DefinitionItemSpell(SpellDefinition definition, int duration) {
		this.runtime = new SpellRuntime(definition);
		this.duration = Math.max(1, duration);
	}

	@Override
	public void start(Player player, @Nullable LivingEntity target) {
		super.start(player, target);
		runtime.reset();
	}

	@Override
	public boolean tick(Player player) {
		if (!(player instanceof ServerPlayer sp)) return true;
		var target = getTarget(sp.serverLevel());
		if (target != null) {
			targetPos = target.position().add(0, target.getBbHeight() / 2, 0);
		}
		holder = new PlayerHolder(player, dir, this, target);
		runtime.tick(holder);
		cache.removeIf(e -> !e.isValid());
		return runtime.getTotalTick() >= duration;
	}
}
