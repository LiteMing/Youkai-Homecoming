package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Confines the target entity within a maximum distance from the caster.
 * If the target exceeds {@code maxDistance}, it is teleported to the boundary
 * and given an inward push of {@code pushSpeed}.
 * <p>
 * Replicates legacy KoishiSpell's player confinement behavior:
 * moveTo(boundary) + setDeltaMovement(inward) + hurtMarked=true.
 * <p>
 * JSON: {"type": "confine_target", "max_distance": 32, "push_speed": 1.0}
 */
public record ConfineTargetAction(double maxDistance, double pushSpeed) implements SpellAction {

	public static final Codec<ConfineTargetAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.DOUBLE.fieldOf("max_distance").forGetter(ConfineTargetAction::maxDistance),
			Codec.DOUBLE.optionalFieldOf("push_speed", 1.0).forGetter(ConfineTargetAction::pushSpeed)
	).apply(i, ConfineTargetAction::new));

	@Override
	public void execute(SpellContext ctx) {
		if (!(ctx.self() instanceof Mob mob)) return;
		var tar = mob.getTarget();
		if (tar == null) return;
		Vec3 diff = tar.position().subtract(ctx.holder().center());
		if (diff.length() > maxDistance) {
			tar.moveTo(diff.normalize().scale(maxDistance).add(ctx.holder().center()));
			tar.setDeltaMovement(diff.normalize().scale(-pushSpeed));
			tar.hasImpulse = true;
			if (tar instanceof Player)
				tar.hurtMarked = true;
		}
	}
}
