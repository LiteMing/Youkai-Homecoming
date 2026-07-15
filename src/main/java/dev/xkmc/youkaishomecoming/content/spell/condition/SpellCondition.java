package dev.xkmc.youkaishomecoming.content.spell.condition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.datafixers.util.Pair;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;

public interface SpellCondition {

	// Lazy codec to avoid circular static init with SpellConditions
	Codec<SpellCondition> CODEC = new Codec<>() {
		@Override
		public <T> DataResult<Pair<SpellCondition, T>> decode(DynamicOps<T> ops, T input) {
			return SpellConditions.DISPATCH_CODEC.decode(ops, input);
		}

		@Override
		public <T> DataResult<T> encode(SpellCondition input, DynamicOps<T> ops, T prefix) {
			return SpellConditions.DISPATCH_CODEC.encode(input, ops, prefix);
		}
	};

	boolean test(SpellContext ctx);

}
