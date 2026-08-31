package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuHelper;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.Vec3;

/**
 * Configurable origin (spawn position) for danmaku/laser actions.
 * Replaces the previous static Optional<Vec3> originOffset.
 */
public record OriginConfig(
		OriginMode mode,
		NumberProvider offsetX,
		NumberProvider offsetY,
		NumberProvider offsetZ,
		NumberProvider rotation
) {

	public enum OriginMode implements StringRepresentable {
		CASTER("caster"),
		TARGET("target"),
		ABSOLUTE("absolute"),
		CASTER_FACING("caster_facing");

		private final String name;

		OriginMode(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	public static final Codec<OriginConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
			StringRepresentable.fromEnum(OriginMode::values)
					.optionalFieldOf("mode", OriginMode.CASTER).forGetter(OriginConfig::mode),
			NumberProvider.CODEC.optionalFieldOf("offset_x", NumberProvider.constant(0)).forGetter(OriginConfig::offsetX),
			NumberProvider.CODEC.optionalFieldOf("offset_y", NumberProvider.constant(0)).forGetter(OriginConfig::offsetY),
			NumberProvider.CODEC.optionalFieldOf("offset_z", NumberProvider.constant(0)).forGetter(OriginConfig::offsetZ),
			NumberProvider.CODEC.optionalFieldOf("rotation", NumberProvider.constant(0)).forGetter(OriginConfig::rotation)
	).apply(i, OriginConfig::new));

	/**
	 * Default config: caster center with zero offsets.
	 */
	public static OriginConfig caster() {
		return new OriginConfig(OriginMode.CASTER,
				NumberProvider.constant(0), NumberProvider.constant(0),
				NumberProvider.constant(0), NumberProvider.constant(0));
	}

	/**
	 * Resolve the origin position for this tick.
	 */
	public Vec3 resolve(SpellContext ctx) {
		Vec3 base = switch (mode) {
			case CASTER -> ctx.holder().center();
			case TARGET -> {
				var t = ctx.holder().target();
				yield t != null ? t : ctx.holder().center();
			}
			// Absolute offsets are anchored to the live spell caster. The editor's
			// preview world is centered at (0, 0, 0), while real players can be
			// anywhere; anchoring here keeps both paths in the same spell-local frame.
			case ABSOLUTE -> ctx.holder().center();
			case CASTER_FACING -> ctx.holder().center();
		};

		Vec3 offset = new Vec3(offsetX.get(ctx), offsetY.get(ctx), offsetZ.get(ctx));

		// Apply Y-axis rotation to offset vector
		double rot = rotation.get(ctx);
		if (rot != 0) {
			double rad = Math.toRadians(rot);
			double cos = Math.cos(rad), sin = Math.sin(rad);
			offset = new Vec3(offset.x * cos - offset.z * sin, offset.y, offset.x * sin + offset.z * cos);
		}

		// For CASTER_FACING mode, rotate offset into caster's local coordinate frame
		if (mode == OriginMode.CASTER_FACING) {
			Vec3 fwd = ctx.holder().forward();
			if (fwd.lengthSqr() > 1e-8) {
				var ori = DanmakuHelper.getOrientation(fwd);
				offset = ori.forward().scale(offset.z)
						.add(ori.side().scale(offset.x))
						.add(ori.normal().scale(offset.y));
			}
		}

		return base.add(offset);
	}

}
