package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.datafixers.util.Pair;
import dev.xkmc.youkaishomecoming.content.spell.mover.DanmakuMover;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import net.minecraft.world.phys.Vec3;

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
	 * Context-aware mover creation hook.
	 * Movers that need runtime target data can override this while older movers keep the simple path.
	 */
	default DanmakuMover create(SpellContext ctx, Vec3 origin, Vec3 velocity) {
		return create(origin, velocity);
	}

}
