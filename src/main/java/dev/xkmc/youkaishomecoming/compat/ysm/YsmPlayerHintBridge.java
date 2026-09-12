package dev.xkmc.youkaishomecoming.compat.ysm;

import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/** Optional native player cap adapter. Never changes the player's model or capability selection. */
public final class YsmPlayerHintBridge {

	private static volatile Access access;
	private static volatile boolean unavailable;

	private YsmPlayerHintBridge() { }

	@Nullable
	public static Object playHint(Object event) {
		if (unavailable) return null;
		try {
			Access methods = access;
			if (methods == null) access = methods = new Access();
			Object animatable = methods.animatable.invoke(event);
			if (!(methods.entity.invoke(animatable) instanceof Player player)
					|| YSMClientCompat.isBeatenProjection(player)
					|| Boolean.TRUE.equals(methods.switching.invoke(animatable))) return null;
			String hint = YsmSpellHintClient.animationOverride(player);
			if (hint.isBlank()) return null;
			String model = String.valueOf(methods.model.invoke(animatable));
			String normalized = YsmAnimationHints.normalize(hint, key -> YSMCompatConfig.expressionToken(model, key));
			Access current = methods;
			String clip = YsmAnimationHints.resolve(normalized, candidate -> current.hasAnimation(animatable, candidate));
			return clip == null ? null : methods.playLoop.invoke(null, event, clip);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
			unavailable = true;
			return null;
		}
	}

	/** Kept free of external types so a missing or incompatible OYSM silently falls back. */
	static final class Access {
		private static final String ROOT = "com.elfmcys.yesstevemodel.";
		private final Method animatable, entity, model, switching, animation, playLoop;

		Access() throws ReflectiveOperationException {
			this(YsmPlayerHintBridge.class.getClassLoader());
		}

		Access(ClassLoader loader) throws ReflectiveOperationException {
			Class<?> event = Class.forName(ROOT + "geckolib3.core.event.predicate.AnimationEvent", false, loader);
			Class<?> player = Class.forName(ROOT + "client.entity.CustomPlayerEntity", false, loader);
			animatable = event.getMethod("getAnimatable");
			entity = player.getMethod("getEntity");
			model = player.getMethod("getModelId");
			switching = player.getMethod("isModelSwitching");
			animation = player.getMethod("getAnimation", String.class);
			playLoop = Class.forName(ROOT + "client.animation.IAnimationPredicate", false, loader)
					.getMethod("playLoopAnimation", event, String.class);
		}

		private boolean hasAnimation(Object entity, String clip) {
			try {
				return animation.invoke(entity, clip) != null;
			} catch (ReflectiveOperationException ignored) {
				return false;
			}
		}
	}
}
