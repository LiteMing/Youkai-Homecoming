package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.datafixers.util.Pair;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;

public interface SpellAction {

	// Lazy codec to avoid circular static init with SpellActions
	Codec<SpellAction> CODEC = new Codec<>() {
		@Override
		public <T> DataResult<Pair<SpellAction, T>> decode(DynamicOps<T> ops, T input) {
			return SpellActions.DISPATCH_CODEC.decode(ops, input);
		}

		@Override
		public <T> DataResult<T> encode(SpellAction input, DynamicOps<T> ops, T prefix) {
			return SpellActions.DISPATCH_CODEC.encode(input, ops, prefix);
		}
	};

	void execute(SpellContext ctx);

}
