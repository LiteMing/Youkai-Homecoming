package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuHelper;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Configurable base direction for danmaku/laser firing.
 * Replaces the previous boolean aimAtTarget.
 */
public interface AimMode {

	Codec<AimMode> CODEC = AimModes.CODEC;

	/**
	 * Get the base firing direction for this context.
	 */
	Vec3 getBaseDirection(SpellContext ctx);

	/**
	 * Get the base firing direction, taking the resolved origin position into account.
	 * <p>
	 * Most aim modes ignore the origin position (they use the caster/target direction).
	 * {@code DirectionToTarget} overrides this to compute the direction from the
	 * actual emission point to the target, which is critical when origin has offsets.
	 */
	default Vec3 getBaseDirection(SpellContext ctx, Vec3 originPos) {
		return getBaseDirection(ctx);
	}

	/**
	 * Backwards-compatible factory: converts old boolean aimAtTarget to AimMode.
	 */
	static AimMode fromLegacy(boolean aimAtTarget) {
		return aimAtTarget ? new AimModes.Target() : new AimModes.FixedDirection(new Vec3(0, 0, 1));
	}

	class AimModes {

		private static final Map<String, Codec<? extends AimMode>> REGISTRY = new HashMap<>();
		private static final Map<Class<?>, String> CLASS_TO_TYPE = new HashMap<>();

		static {
			register("target", Target.CODEC, Target.class);
			register("fixed", FixedDirection.CODEC, FixedDirection.class);
			register("caster_facing", CasterFacing.CODEC, CasterFacing.class);
			register("angle_offset", AngleOffset.CODEC, AngleOffset.class);
			register("variable_angle", VariableAngle.CODEC, VariableAngle.class);
			register("direction_to_target", DirectionToTarget.CODEC, DirectionToTarget.class);
			register("random_angle", RandomAngle.CODEC, RandomAngle.class);
		}

		public static void register(String id, Codec<? extends AimMode> codec, Class<? extends AimMode> clazz) {
			REGISTRY.put(id, codec);
			CLASS_TO_TYPE.put(clazz, id);
		}

		private static String getType(AimMode mode) {
			String type = CLASS_TO_TYPE.get(mode.getClass());
			if (type != null) return type;
			throw new IllegalStateException("Unknown AimMode type: " + mode.getClass());
		}

		public static String getTypeId(AimMode mode) {
			return CLASS_TO_TYPE.get(mode.getClass());
		}

		@SuppressWarnings("unchecked")
		private static final Codec<AimMode> DISPATCH_CODEC = Codec.STRING.fieldOf("type")
				.codec()
				.dispatch(
						AimModes::getType,
						id -> {
							var codec = REGISTRY.get(id);
							if (codec == null) throw new IllegalStateException("Unknown AimMode: " + id);
							return (Codec<AimMode>) (Codec<?>) codec;
						}
				);

		/**
		 * Main codec: accepts string shorthand ("target") or typed object.
		 * Also accepts boolean for backwards compatibility.
		 */
		static final Codec<AimMode> CODEC = new Codec<>() {
			@Override
			public <T> DataResult<Pair<AimMode, T>> decode(DynamicOps<T> ops, T input) {
				// Try boolean first (backwards compat with aim_at_target)
				var boolResult = ops.getBooleanValue(input);
				if (boolResult.result().isPresent()) {
					boolean val = boolResult.result().get();
					return DataResult.success(Pair.of(AimMode.fromLegacy(val), ops.empty()));
				}
				// Try bare string shorthand
				var strResult = ops.getStringValue(input);
				if (strResult.result().isPresent()) {
					String s = strResult.result().get();
					return switch (s) {
						case "target" -> DataResult.success(Pair.of((AimMode) new Target(), ops.empty()));
						case "caster_facing" -> DataResult.success(Pair.of((AimMode) new CasterFacing(), ops.empty()));
						case "direction_to_target" -> DataResult.success(Pair.of((AimMode) new DirectionToTarget(), ops.empty()));
						default -> DataResult.error(() -> "Unknown AimMode shorthand: " + s);
					};
				}
				return DISPATCH_CODEC.decode(ops, input);
			}

			@Override
			public <T> DataResult<T> encode(AimMode input, DynamicOps<T> ops, T prefix) {
				// Encode simple modes as string shorthand
				if (input instanceof Target) return DataResult.success(ops.createString("target"));
				if (input instanceof CasterFacing) return DataResult.success(ops.createString("caster_facing"));
				if (input instanceof DirectionToTarget) return DataResult.success(ops.createString("direction_to_target"));
				return DISPATCH_CODEC.encode(input, ops, prefix);
			}
		};

		/**
		 * Aim at the current target (= old aimAtTarget=true).
		 */
		public record Target() implements AimMode {
			public static final Codec<Target> CODEC = Codec.unit(Target::new);

			@Override
			public Vec3 getBaseDirection(SpellContext ctx) {
				return ctx.holder().forward();
			}
		}

		/**
		 * Fixed world direction (= old aimAtTarget=false with default (0,0,1)).
		 */
		public record FixedDirection(Vec3 direction) implements AimMode {
			public static final Codec<FixedDirection> CODEC = SpellCodecs.VEC3_CODEC
					.fieldOf("direction").codec()
					.xmap(FixedDirection::new, FixedDirection::direction);

			@Override
			public Vec3 getBaseDirection(SpellContext ctx) {
				return direction.normalize();
			}
		}

		/**
		 * Caster's look angle (entity facing direction).
		 */
		public record CasterFacing() implements AimMode {
			public static final Codec<CasterFacing> CODEC = Codec.unit(CasterFacing::new);

			@Override
			public Vec3 getBaseDirection(SpellContext ctx) {
				return ctx.self().getLookAngle();
			}
		}

		/**
		 * Forward direction rotated by a dynamic angle (degrees).
		 * Useful for rotating patterns: set angle = AddVariable per tick.
		 */
		public record AngleOffset(NumberProvider angle) implements AimMode {
			public static final Codec<AngleOffset> CODEC = NumberProvider.CODEC
					.fieldOf("angle").codec()
					.xmap(AngleOffset::new, AngleOffset::angle);

			@Override
			public Vec3 getBaseDirection(SpellContext ctx) {
				Vec3 fwd = ctx.holder().forward();
				double deg = angle.get(ctx);
				if (Math.abs(deg) < 1e-6) return fwd;
				var ori = DanmakuHelper.getOrientation(fwd);
				return ori.rotateDegrees(deg);
			}
		}

		/**
		 * Direction from holder.center() toward holder.target().
		 * In onExpiry context (TrailCardHolder), center() is the danmaku's expiry position.
		 * <p>
		 * When called with an explicit originPos (from FireDanmakuAction/FireLaserAction),
		 * computes the direction from the actual emission point to the target. This is
		 * critical for offset origins (e.g. ring emission points) where the emission point
		 * differs significantly from the caster center.
		 */
		public record DirectionToTarget() implements AimMode {
			public static final Codec<DirectionToTarget> CODEC = Codec.unit(DirectionToTarget::new);

			@Override
			public Vec3 getBaseDirection(SpellContext ctx) {
				return directionFrom(ctx, ctx.holder().center());
			}

			@Override
			public Vec3 getBaseDirection(SpellContext ctx, Vec3 originPos) {
				return directionFrom(ctx, originPos);
			}

			private Vec3 directionFrom(SpellContext ctx, Vec3 from) {
				Vec3 target = ctx.holder().target();
				if (target == null) return ctx.holder().forward();
				Vec3 dir = target.subtract(from);
				if (dir.lengthSqr() < 1e-6) return ctx.holder().forward();
				return dir.normalize();
			}
		}

		/**
		 * Random angle in [0, spread) degrees, re-evaluated each call.
		 * Useful for per-tick random laser rotation or random spread patterns.
		 */
		public record RandomAngle(NumberProvider spread) implements AimMode {
			public static final Codec<RandomAngle> CODEC = NumberProvider.CODEC
					.fieldOf("spread").codec()
					.xmap(RandomAngle::new, RandomAngle::spread);

			@Override
			public Vec3 getBaseDirection(SpellContext ctx) {
				double deg = ctx.holder().random().nextDouble() * spread.get(ctx);
				Vec3 fwd = ctx.holder().forward();
				var ori = DanmakuHelper.getOrientation(fwd);
				return ori.rotateDegrees(deg);
			}
		}

		/**
		 * Reads angle from a runtime variable (degrees).
		 */
		public record VariableAngle(String key) implements AimMode {
			public static final Codec<VariableAngle> CODEC = Codec.STRING
					.fieldOf("key").codec()
					.xmap(VariableAngle::new, VariableAngle::key);

			@Override
			public Vec3 getBaseDirection(SpellContext ctx) {
				double deg = ctx.getVariable(key);
				Vec3 fwd = ctx.holder().forward();
				if (Math.abs(deg) < 1e-6) return fwd;
				var ori = DanmakuHelper.getOrientation(fwd);
				return ori.rotateDegrees(deg);
			}
		}
	}

}
