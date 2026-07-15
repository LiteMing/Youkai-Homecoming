package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.datafixers.util.Pair;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;

public interface NumberProvider {

	// Lazy codec to avoid circular static init with NumberProviders
	Codec<NumberProvider> CODEC = new Codec<>() {
		@Override
		public <T> DataResult<Pair<NumberProvider, T>> decode(DynamicOps<T> ops, T input) {
			return NumberProviders.CODEC.decode(ops, input);
		}

		@Override
		public <T> DataResult<T> encode(NumberProvider input, DynamicOps<T> ops, T prefix) {
			return NumberProviders.CODEC.encode(input, ops, prefix);
		}
	};

	double get(SpellContext ctx);

	/**
	 * Wrap a constant value as a NumberProvider.
	 */
	static NumberProvider constant(double value) {
		return new NumberProviders.Constant(value);
	}

}
