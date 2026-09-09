package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.compat.ysm.YsmRenderConfig;
import dev.xkmc.youkaishomecoming.content.spell.definition.AimMode;
import dev.xkmc.youkaishomecoming.content.spell.definition.GroupRotation;
import dev.xkmc.youkaishomecoming.content.spell.definition.MoverConfig;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.definition.OriginConfig;
import dev.xkmc.youkaishomecoming.content.spell.definition.PatternType;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.shooter.ShooterData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Spawns ShooterEntity instances with the same pattern emitter model used by danmaku actions.
 * Each shooter executes its own data-driven tick actions from its current position.
 *
 * <p>The initial YSM override is one shared {@link YsmRenderConfig}. It is the
 * same model/texture/animation-hint route used by spell-side YSM context hints;
 * the /yhysm editor remains the owner of profiles, presets and parameters.
 */
public record SpawnShooterAction(
        int health,
        float damage,
        int lifetime,
        ResourceLocation circle,
        OriginConfig origin,
        NumberProvider count,
        NumberProvider speed,
        NumberProvider angleOffset,
        NumberProvider spread,
        NumberProvider elevation,
        PatternType pattern,
        AimMode aimMode,
        Optional<NumberProvider> outerCount,
        Optional<NumberProvider> tiltAngle,
        Optional<GroupRotation> groupRotation,
        Optional<MoverConfig> mover,
        YsmRenderConfig ysm,
        boolean targetable,
        List<SpellAction> body,
        boolean randomAxis
) implements SpellAction {

    private static final com.mojang.serialization.MapCodec<SpawnShooterAction> BASE_MAP = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("health", 40).forGetter(SpawnShooterAction::health),
            Codec.FLOAT.optionalFieldOf("damage", 4f).forGetter(SpawnShooterAction::damage),
            Codec.INT.optionalFieldOf("lifetime", 100).forGetter(SpawnShooterAction::lifetime),
            OriginConfig.CODEC.optionalFieldOf("origin", OriginConfig.caster()).forGetter(SpawnShooterAction::origin),
            NumberProvider.CODEC.fieldOf("count").forGetter(SpawnShooterAction::count),
            NumberProvider.CODEC.fieldOf("speed").forGetter(SpawnShooterAction::speed),
            NumberProvider.CODEC.optionalFieldOf("angle_offset", NumberProvider.constant(0)).forGetter(SpawnShooterAction::angleOffset),
            NumberProvider.CODEC.optionalFieldOf("spread", NumberProvider.constant(360)).forGetter(SpawnShooterAction::spread),
            NumberProvider.CODEC.optionalFieldOf("elevation", NumberProvider.constant(0)).forGetter(SpawnShooterAction::elevation),
            PatternType.CODEC.optionalFieldOf("pattern", PatternType.RING).forGetter(SpawnShooterAction::pattern),
            AimMode.CODEC.optionalFieldOf("aim_mode", new AimMode.AimModes.Target()).forGetter(SpawnShooterAction::aimMode),
            NumberProvider.CODEC.optionalFieldOf("outer_count").forGetter(SpawnShooterAction::outerCount),
            NumberProvider.CODEC.optionalFieldOf("tilt_angle").forGetter(SpawnShooterAction::tiltAngle)
    ).apply(i, SpawnShooterAction::new));

    public static final Codec<SpawnShooterAction> CODEC = RecordCodecBuilder.create(i -> i.group(
            BASE_MAP.forGetter(ssa -> ssa),
            ResourceLocation.CODEC.optionalFieldOf("circle", ShooterData.DEFAULT_CIRCLE).forGetter(SpawnShooterAction::circle),
            GroupRotation.CODEC.optionalFieldOf("group_rotation").forGetter(SpawnShooterAction::groupRotation),
            MoverConfig.CODEC.optionalFieldOf("mover").forGetter(SpawnShooterAction::mover),
			YsmRenderConfig.CODEC.optionalFieldOf("ysm").forGetter(ssa ->
					ssa.ysm().enabled() ? Optional.of(ssa.ysm()) : Optional.empty()),
			// Read the pre-0.29 flat fields, but never emit them from new saves.
			Codec.STRING.optionalFieldOf("ysm_model", "").forGetter(ssa -> ""),
			Codec.STRING.optionalFieldOf("ysm_texture", "").forGetter(ssa -> ""),
			Codec.STRING.optionalFieldOf("ysm_animation", "").forGetter(ssa -> ""),
			Codec.INT.optionalFieldOf("ysm_duration", 0).forGetter(ssa -> 0),
			Codec.STRING.optionalFieldOf("ysm_clear_target", "changed").forGetter(ssa -> "changed"),
			Codec.BOOL.optionalFieldOf("targetable", true).forGetter(SpawnShooterAction::targetable),
			SpellAction.CODEC.listOf().fieldOf("body").forGetter(SpawnShooterAction::body),
			Codec.BOOL.optionalFieldOf("random_axis", true).forGetter(SpawnShooterAction::randomAxis)
	).apply(i, (base, circle, groupRotation, mover, nestedYsm, legacyModel, legacyTexture, legacyHint,
			legacyDuration, legacyClearTarget, targetable, body, randomAxis) -> new SpawnShooterAction(
			base.health, base.damage, base.lifetime, circle, base.origin,
			base.count, base.speed, base.angleOffset, base.spread, base.elevation,
			base.pattern, base.aimMode, base.outerCount, base.tiltAngle, groupRotation, mover,
			nestedYsm.orElseGet(() -> new YsmRenderConfig(legacyModel, legacyTexture, legacyHint,
					legacyDuration, legacyClearTarget)), targetable, body, randomAxis
	)));

    public SpawnShooterAction {
        if (circle == null) circle = ShooterData.DEFAULT_CIRCLE;
        ysm = ysm == null ? YsmRenderConfig.EMPTY : ysm;
        body = List.copyOf(body);
    }

    public SpawnShooterAction(
            int health, float damage, int lifetime, OriginConfig origin,
            NumberProvider count, NumberProvider speed, NumberProvider angleOffset,
            NumberProvider spread, NumberProvider elevation, PatternType pattern,
            AimMode aimMode, Optional<NumberProvider> outerCount, Optional<NumberProvider> tiltAngle) {
        this(health, damage, lifetime, ShooterData.DEFAULT_CIRCLE, origin, count, speed, angleOffset,
                spread, elevation, pattern, aimMode, outerCount, tiltAngle, Optional.empty(), Optional.empty(),
                YsmRenderConfig.EMPTY, true, List.of(), true);
    }

    public SpawnShooterAction(
            int health, float damage, int lifetime, OriginConfig origin,
            NumberProvider count, NumberProvider speed, NumberProvider angleOffset,
            NumberProvider spread, NumberProvider elevation, PatternType pattern,
            AimMode aimMode, Optional<NumberProvider> outerCount, Optional<NumberProvider> tiltAngle,
            Optional<GroupRotation> groupRotation, Optional<MoverConfig> mover, List<SpellAction> body) {
        this(health, damage, lifetime, ShooterData.DEFAULT_CIRCLE, origin, count, speed, angleOffset,
                spread, elevation, pattern, aimMode, outerCount, tiltAngle, groupRotation, mover,
                YsmRenderConfig.EMPTY, true, body, true);
    }

    /** Backwards-compatible Java constructor for callers that still pass the five legacy fields. */
    public SpawnShooterAction(
            int health, float damage, int lifetime, ResourceLocation circle, OriginConfig origin,
            NumberProvider count, NumberProvider speed, NumberProvider angleOffset, NumberProvider spread,
            NumberProvider elevation, PatternType pattern, AimMode aimMode, Optional<NumberProvider> outerCount,
            Optional<NumberProvider> tiltAngle, Optional<GroupRotation> groupRotation, Optional<MoverConfig> mover,
            String ysmModel, String ysmTexture, String ysmAnimation, int ysmDuration, String ysmClearTarget,
            boolean targetable, List<SpellAction> body) {
        this(health, damage, lifetime, circle, origin, count, speed, angleOffset, spread, elevation,
                pattern, aimMode, outerCount, tiltAngle, groupRotation, mover,
                new YsmRenderConfig(ysmModel, ysmTexture, ysmAnimation, ysmDuration, ysmClearTarget),
                targetable, body, true);
    }

    /* Legacy accessors keep editor and external Java callers source-compatible while the
     * serialized/configuration representation is now the shared YsmRenderConfig. */
    public String ysmModel() { return ysm.model(); }
    public String ysmTexture() { return ysm.texture(); }
    public String ysmAnimation() { return ysm.hint(); }
    public int ysmDuration() { return ysm.duration(); }
    public String ysmClearTarget() { return ysm.clearTarget(); }

    public SpawnShooterAction withTargetable(boolean v) {
        return all(health, damage, lifetime, circle, origin, count, speed, angleOffset, spread, elevation,
                pattern, aimMode, outerCount, tiltAngle, groupRotation, mover, ysm, v, body);
    }

    public SpawnShooterAction withRandomAxis(boolean v) {
        return new SpawnShooterAction(health, damage, lifetime, circle, origin, count, speed, angleOffset,
                spread, elevation, pattern, aimMode, outerCount, tiltAngle, groupRotation, mover,
                ysm, targetable, body, v);
    }

    public SpawnShooterAction withBody(List<SpellAction> value) { return all(health, damage, lifetime, circle, origin, count, speed, angleOffset, spread, elevation, pattern, aimMode, outerCount, tiltAngle, groupRotation, mover, ysm, targetable, value); }
    public SpawnShooterAction withHealth(int value) { return all(value, damage, lifetime, circle, origin, count, speed, angleOffset, spread, elevation, pattern, aimMode, outerCount, tiltAngle, groupRotation, mover, ysm, targetable, body); }
    public SpawnShooterAction withDamage(float value) { return all(health, value, lifetime, circle, origin, count, speed, angleOffset, spread, elevation, pattern, aimMode, outerCount, tiltAngle, groupRotation, mover, ysm, targetable, body); }
    public SpawnShooterAction withLifetime(int value) { return all(health, damage, value, circle, origin, count, speed, angleOffset, spread, elevation, pattern, aimMode, outerCount, tiltAngle, groupRotation, mover, ysm, targetable, body); }
    public SpawnShooterAction withCircle(ResourceLocation value) { return all(health, damage, lifetime, value, origin, count, speed, angleOffset, spread, elevation, pattern, aimMode, outerCount, tiltAngle, groupRotation, mover, ysm, targetable, body); }
    public SpawnShooterAction withOrigin(OriginConfig value) { return all(health, damage, lifetime, circle, value, count, speed, angleOffset, spread, elevation, pattern, aimMode, outerCount, tiltAngle, groupRotation, mover, ysm, targetable, body); }
    public SpawnShooterAction withCount(NumberProvider value) { return all(health, damage, lifetime, circle, origin, value, speed, angleOffset, spread, elevation, pattern, aimMode, outerCount, tiltAngle, groupRotation, mover, ysm, targetable, body); }
    public SpawnShooterAction withSpeed(NumberProvider value) { return all(health, damage, lifetime, circle, origin, count, value, angleOffset, spread, elevation, pattern, aimMode, outerCount, tiltAngle, groupRotation, mover, ysm, targetable, body); }
    public SpawnShooterAction withAngleOffset(NumberProvider value) { return all(health, damage, lifetime, circle, origin, count, speed, value, spread, elevation, pattern, aimMode, outerCount, tiltAngle, groupRotation, mover, ysm, targetable, body); }
    public SpawnShooterAction withSpread(NumberProvider value) { return all(health, damage, lifetime, circle, origin, count, speed, angleOffset, value, elevation, pattern, aimMode, outerCount, tiltAngle, groupRotation, mover, ysm, targetable, body); }
    public SpawnShooterAction withElevation(NumberProvider value) { return all(health, damage, lifetime, circle, origin, count, speed, angleOffset, spread, value, pattern, aimMode, outerCount, tiltAngle, groupRotation, mover, ysm, targetable, body); }
    public SpawnShooterAction withPattern(PatternType value) { return all(health, damage, lifetime, circle, origin, count, speed, angleOffset, spread, elevation, value, aimMode, outerCount, tiltAngle, groupRotation, mover, ysm, targetable, body); }
    public SpawnShooterAction withAimMode(AimMode value) { return all(health, damage, lifetime, circle, origin, count, speed, angleOffset, spread, elevation, pattern, value, outerCount, tiltAngle, groupRotation, mover, ysm, targetable, body); }
    public SpawnShooterAction withOuterCount(Optional<NumberProvider> value) { return all(health, damage, lifetime, circle, origin, count, speed, angleOffset, spread, elevation, pattern, aimMode, value, tiltAngle, groupRotation, mover, ysm, targetable, body); }
    public SpawnShooterAction withTiltAngle(Optional<NumberProvider> value) { return all(health, damage, lifetime, circle, origin, count, speed, angleOffset, spread, elevation, pattern, aimMode, outerCount, value, groupRotation, mover, ysm, targetable, body); }
    public SpawnShooterAction withGroupRotation(Optional<GroupRotation> value) { return all(health, damage, lifetime, circle, origin, count, speed, angleOffset, spread, elevation, pattern, aimMode, outerCount, tiltAngle, value, mover, ysm, targetable, body); }
    public SpawnShooterAction withMover(Optional<MoverConfig> value) { return all(health, damage, lifetime, circle, origin, count, speed, angleOffset, spread, elevation, pattern, aimMode, outerCount, tiltAngle, groupRotation, value, ysm, targetable, body); }

    public SpawnShooterAction withYsm(YsmRenderConfig value) { return all(health, damage, lifetime, circle, origin, count, speed, angleOffset, spread, elevation, pattern, aimMode, outerCount, tiltAngle, groupRotation, mover, value == null ? YsmRenderConfig.EMPTY : value, targetable, body); }
    public SpawnShooterAction withYsmModel(String value) { return withYsm(ysm.withModel(value)); }
    public SpawnShooterAction withYsmTexture(String value) { return withYsm(ysm.withTexture(value)); }
    public SpawnShooterAction withYsmAnimation(String value) { return withYsm(ysm.withHint(value)); }
    public SpawnShooterAction withYsmDuration(int value) { return withYsm(ysm.withDuration(value)); }
    public SpawnShooterAction withYsmClearTarget(String value) { return withYsm(ysm.withClearTarget(value)); }

    private SpawnShooterAction all(int health, float damage, int lifetime, ResourceLocation circle,
            OriginConfig origin, NumberProvider count, NumberProvider speed, NumberProvider angleOffset,
            NumberProvider spread, NumberProvider elevation, PatternType pattern, AimMode aimMode,
            Optional<NumberProvider> outerCount, Optional<NumberProvider> tiltAngle,
            Optional<GroupRotation> groupRotation, Optional<MoverConfig> mover, YsmRenderConfig ysm,
            boolean targetable, List<SpellAction> body) {
        return new SpawnShooterAction(health, damage, lifetime, circle, origin, count, speed, angleOffset,
                spread, elevation, pattern, aimMode, outerCount, tiltAngle, groupRotation, mover,
                ysm, targetable, body, randomAxis);
    }

    @Override
    public void execute(SpellContext ctx) {
        Vec3 spawnPos = origin.resolve(ctx);
        var settings = new PatternEmitter.Settings(count, speed, angleOffset, spread, elevation, pattern,
                aimMode, origin.rotation(), outerCount, tiltAngle, groupRotation, randomAxis);
        PatternEmitter.emit(ctx, spawnPos, settings, (vel, baseDir, spawnIndex, resolvedSpread) -> spawnOne(ctx, spawnPos, vel, baseDir));
    }

    private void spawnOne(SpellContext ctx, Vec3 spawnPos, Vec3 vel, Vec3 baseDir) {
        var holder = ctx.holder();
        var shooterSpell = new DataDrivenShooterSpell(body, ctx.runtime());
        var data = new ShooterData(health, damage, Math.max(1, lifetime), circle, targetable);
        var entity = holder.prepareShooter(data, shooterSpell);
        entity.inheritDamageFrom(holder);
        entity.setPos(spawnPos);
        if (vel.lengthSqr() > 1e-8) entity.setDeltaMovement(vel);
        if (mover.isPresent()) {
            Vec3 casterPos = holder.self() != null ? holder.self().position() : spawnPos;
            MoverConfig moverConfig = mover.get();
            Vec3 targetPos = moverConfig.resolveTargetPos(ctx, spawnPos);
            entity.mover = moverConfig.create(ctx, spawnPos, vel, baseDir, targetPos, casterPos);
        }
        ysm.apply(entity);
        holder.shoot(entity);
    }
}
