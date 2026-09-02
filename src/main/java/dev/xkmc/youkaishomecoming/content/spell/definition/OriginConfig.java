package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuHelper;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.Vec3;

/** Configurable origin (spawn position) for danmaku/laser actions. */
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
        CASTER_FACING("caster_facing"),
        TARGET_FACING("target_facing"),
        /** True world origin (0,0,0). ABSOLUTE remains a legacy caster-anchored mode. */
        WORLD("world");

        /** Modes offered for new editor content; ABSOLUTE stays decode-compatible only. */
		public static OriginMode[] editorValues() {
			return new OriginMode[]{CASTER, TARGET, CASTER_FACING, TARGET_FACING, WORLD};
        }

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

    public static OriginConfig caster() {
        return new OriginConfig(OriginMode.CASTER,
                NumberProvider.constant(0), NumberProvider.constant(0),
                NumberProvider.constant(0), NumberProvider.constant(0));
    }

    public Vec3 resolve(SpellContext ctx) {
        Vec3 base = switch (mode) {
            case CASTER -> ctx.holder().center();
            case TARGET -> {
                var target = ctx.holder().target();
                yield target != null ? target : ctx.holder().center();
            }
            case ABSOLUTE, CASTER_FACING -> ctx.holder().center();
            case TARGET_FACING -> {
                var target = ctx.holder().target();
                yield target != null ? target : ctx.holder().center();
            }
            case WORLD -> Vec3.ZERO;
        };

        Vec3 offset = new Vec3(offsetX.get(ctx), offsetY.get(ctx), offsetZ.get(ctx));
        double rot = rotation.get(ctx);
        if (rot != 0) {
            double rad = Math.toRadians(rot);
            double cos = Math.cos(rad), sin = Math.sin(rad);
            offset = new Vec3(offset.x * cos - offset.z * sin, offset.y,
                    offset.x * sin + offset.z * cos);
        }

        if (mode == OriginMode.CASTER_FACING || mode == OriginMode.TARGET_FACING) {
            Vec3 forward = resolveFacing(ctx);
            if (forward.lengthSqr() > 1e-8) {
                var orientation = DanmakuHelper.getOrientation(forward);
                offset = orientation.forward().scale(offset.z)
                        .add(orientation.side().scale(offset.x))
                        .add(orientation.normal().scale(offset.y));
            }
        }
        return base.add(offset);
    }

    private Vec3 resolveFacing(SpellContext ctx) {
        if (mode == OriginMode.CASTER_FACING) {
            return ctx.self().getLookAngle();
        }
        var target = ctx.holder().targetEntity();
        if (target != null) return target.getLookAngle();
        var targetPos = ctx.holder().target();
        if (targetPos != null) {
            Vec3 delta = targetPos.subtract(ctx.holder().center());
            if (delta.lengthSqr() > 1e-8) return delta.normalize();
        }
        return ctx.holder().forward();
    }

    /** Inverse of the origin offset transform, used by paused viewport dragging. */
    public static Vec3 worldDeltaToOffsetDelta(OriginMode mode, double rotation,
            Vec3 worldDelta, Vec3 casterFacing, Vec3 targetFacing) {
        Vec3 local = worldDelta;
        if (mode == OriginMode.CASTER_FACING || mode == OriginMode.TARGET_FACING) {
            Vec3 forward = mode == OriginMode.CASTER_FACING ? casterFacing : targetFacing;
            if (forward == null || forward.lengthSqr() < 1.0e-8) forward = new Vec3(0, 0, 1);
            var orientation = DanmakuHelper.getOrientation(forward.normalize());
            local = new Vec3(worldDelta.dot(orientation.side()),
                    worldDelta.dot(orientation.normal()), worldDelta.dot(orientation.forward()));
        }
        if (Math.abs(rotation) > 1.0e-8) {
            double radians = Math.toRadians(-rotation);
            double cos = Math.cos(radians), sin = Math.sin(radians);
            local = new Vec3(local.x * cos - local.z * sin, local.y,
                    local.x * sin + local.z * cos);
        }
        return local;
    }
}
