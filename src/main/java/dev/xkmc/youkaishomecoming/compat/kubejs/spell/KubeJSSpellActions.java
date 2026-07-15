package dev.xkmc.youkaishomecoming.compat.kubejs.spell;

import com.mojang.serialization.Codec;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;

import java.util.function.Consumer;

public class KubeJSSpellActions {

	public static void register() {
		SpellActions.register("js_callback", JSAction.CODEC, JSAction.class);
	}

	public static class JSAction implements SpellAction {

		public static final Codec<JSAction> CODEC = Codec.unit(JSAction::new);

		private final Consumer<SpellContext> callback;

		JSAction() {
			this.callback = ctx -> {};
		}

		public JSAction(Consumer<SpellContext> callback) {
			this.callback = callback;
		}

		@Override
		public void execute(SpellContext ctx) {
			callback.accept(ctx);
		}
	}
}
