package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.mover.DanmakuMover;
import dev.xkmc.youkaishomecoming.content.spell.mover.RectMover;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Codec-serializable wrapper that produces a DanmakuMover instance at runtime.
 * Bridges the Codec world (JSON/datapack) to the L2Serial world (DanmakuMover).
 */
public interface MoverConfig {

	Codec<MoverConfig> CODEC = new Codec<>() {
		@Override
		public <T> DataResult<Pair<MoverConfig, T>> decode(DynamicOps<T> ops, T input) {
			return MoverConfigs.DISPATCH_CODEC.decode(ops, input);
		}

		@Override
		public <T> DataResult<T> encode(MoverConfig input, DynamicOps<T> ops, T prefix) {
			return MoverConfigs.DISPATCH_CODEC.encode(input, ops, prefix);
		}
	};

	/**
	 * Create a DanmakuMover for a projectile starting at the given origin with the given velocity.
	 */
	DanmakuMover create(Vec3 origin, Vec3 velocity);

	/**
	 * Create a DanmakuMover with an additional baseDirection parameter.
	 * baseDirection is the overall aim direction (e.g., toward target), shared by all projectiles in a pattern.
	 * Used by movers that need to drift the origin along a common direction (e.g., formula mover's speed).
	 * Default implementation ignores baseDirection.
	 */
	default DanmakuMover create(Vec3 origin, Vec3 velocity, Vec3 baseDirection) {
		return create(origin, velocity);
	}

	/**
	 * Create a DanmakuMover with target and caster position context.
	 * Used by movers that need to reference entity positions in expressions (e.g., translate mover).
	 * Target/caster positions are snapshotted at creation time.
	 * Default implementation delegates to the 3-arg version (ignores target/caster).
	 */
	default DanmakuMover create(Vec3 origin, Vec3 velocity, Vec3 baseDirection, Vec3 targetPos, Vec3 casterPos) {
		return create(origin, velocity, baseDirection);
	}

}
