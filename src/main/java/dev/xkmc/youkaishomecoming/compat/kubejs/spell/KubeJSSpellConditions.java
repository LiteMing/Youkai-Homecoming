package dev.xkmc.youkaishomecoming.compat.kubejs.spell;

import com.mojang.serialization.Codec;
import dev.xkmc.youkaishomecoming.content.spell.condition.SpellCondition;
import dev.xkmc.youkaishomecoming.content.spell.condition.SpellConditions;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;

import java.util.function.Predicate;

public class KubeJSSpellConditions {

	public static void register() {
		SpellConditions.register("js_callback", JSCondition.CODEC, JSCondition.class);
	}

	public static class JSCondition implements SpellCondition {

		public static final Codec<JSCondition> CODEC = Codec.unit(JSCondition::new);

		private final Predicate<SpellContext> callback;

		JSCondition() {
			this.callback = ctx -> false;
		}

		public JSCondition(Predicate<SpellContext> callback) {
			this.callback = callback;
		}

		@Override
		public boolean test(SpellContext ctx) {
			return callback.test(ctx);
		}
	}
}
