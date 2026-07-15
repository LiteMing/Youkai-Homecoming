package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.preview.PreviewCardHolder;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Teleports the caster to a random position near the target.
 * Tries up to {@code attempts} times with Gaussian direction sampling.
 * {@code upwardBias} forces the Y component to be positive (abs(gaussian)), biasing upward.
 * <p>
 * Replicates legacy YukariSpell/ReimuSpell teleportRandom behavior:
 * random Gaussian direction * scaled distance, with collision check on each attempt.
 * <p>
 * JSON: {"type": "teleport_random", "max_distance": 32, "min_distance_factor": 0.8,
 *        "distance_variance": 0.4, "attempts": 16, "upward_bias": true, "play_sound": true}
 */
public record TeleportRandomAction(
		double maxDistance,
		double minDistanceFactor,
		double distanceVariance,
		int attempts,
		boolean upwardBias,
		boolean playSound
) implements SpellAction {

	public static final Codec<TeleportRandomAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.DOUBLE.optionalFieldOf("max_distance", 32.0).forGetter(TeleportRandomAction::maxDistance),
			Codec.DOUBLE.optionalFieldOf("min_distance_factor", 0.8).forGetter(TeleportRandomAction::minDistanceFactor),
			Codec.DOUBLE.optionalFieldOf("distance_variance", 0.4).forGetter(TeleportRandomAction::distanceVariance),
			Codec.INT.optionalFieldOf("attempts", 16).forGetter(TeleportRandomAction::attempts),
			Codec.BOOL.optionalFieldOf("upward_bias", true).forGetter(TeleportRandomAction::upwardBias),
			Codec.BOOL.optionalFieldOf("play_sound", true).forGetter(TeleportRandomAction::playSound)
	).apply(i, TeleportRandomAction::new));

	@Override
	public void execute(SpellContext ctx) {
		LivingEntity mob = ctx.self();
		Vec3 target = ctx.holder().target();
		if (target == null) return;

		if (ctx.holder() instanceof PreviewCardHolder) {
			// In preview mode: teleport to a random offset without collision check
			var r = mob.getRandom();
			double dy = upwardBias ? Math.abs(r.nextGaussian()) : r.nextGaussian();
			Vec3 dir = new Vec3(r.nextGaussian(), dy, r.nextGaussian()).normalize();
			double dist = Math.min(maxDistance, mob.position().distanceTo(target) * (minDistanceFactor + r.nextDouble() * distanceVariance));
			mob.setPos(target.add(dir.scale(dist)));
			return;
		}

		var r = mob.getRandom();
		double baseDist = mob.position().distanceTo(target);
		for (int i = 0; i < attempts; i++) {
			double dy = upwardBias ? Math.abs(r.nextGaussian()) : r.nextGaussian();
			Vec3 dir = new Vec3(r.nextGaussian(), dy, r.nextGaussian());
			if (dir.lengthSqr() < 0.01) continue;
			dir = dir.normalize();
			double dist = Math.min(maxDistance, baseDist * (minDistanceFactor + r.nextDouble() * distanceVariance));
			Vec3 pos = target.add(dir.scale(dist));
			if (TeleportAction.teleport(mob, pos, playSound)) {
				return;
			}
		}
	}
}
