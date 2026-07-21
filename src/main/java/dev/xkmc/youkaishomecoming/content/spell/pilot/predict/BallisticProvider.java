package dev.xkmc.youkaishomecoming.content.spell.pilot.predict;

import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.ThreatFilters;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * T2 ballistic prediction for exact vanilla EntityType whitelist only.
 * Do not match by AbstractArrow/ThrownItemProjectile base class — mod subclasses
 * may override physics; whitelist misses fall through to T3.
 * <p>
 * Constants from vanilla 1.20.1 tick order: move with current vel, then drag, then gravity.
 * Arrow/trident: drag 0.99, gravity 0.05 ({@code AbstractArrow#tick}).
 * Throwable (snowball/egg/pearl/potion): drag 0.99, gravity 0.03 ({@code ThrowableItemProjectile#tick}).
 * Fireball/skull: near-straight with mild drag, zero gravity.
 * <p>
 * Table is lazy so offline unit tests can load this class without Minecraft bootstrap.
 */
public class BallisticProvider implements ThreatProvider {

	private volatile Map<EntityType<?>, BallisticParams> table;

	private Map<EntityType<?>, BallisticParams> table() {
		Map<EntityType<?>, BallisticParams> local = table;
		if (local == null) {
			synchronized (this) {
				local = table;
				if (local == null) {
					local = buildTable();
					table = local;
				}
			}
		}
		return local;
	}

	private static Map<EntityType<?>, BallisticParams> buildTable() {
		return Map.ofEntries(
				Map.entry(EntityType.ARROW, new BallisticParams(0.99, 0.05, true)),
				Map.entry(EntityType.SPECTRAL_ARROW, new BallisticParams(0.99, 0.05, true)),
				Map.entry(EntityType.SNOWBALL, new BallisticParams(0.99, 0.03, true)),
				Map.entry(EntityType.EGG, new BallisticParams(0.99, 0.03, true)),
				Map.entry(EntityType.ENDER_PEARL, new BallisticParams(0.99, 0.03, true)),
				Map.entry(EntityType.LLAMA_SPIT, new BallisticParams(0.99, 0.06, true)),
				Map.entry(EntityType.SMALL_FIREBALL, new BallisticParams(0.95, 0.0, false)),
				Map.entry(EntityType.FIREBALL, new BallisticParams(0.95, 0.0, false)),
				Map.entry(EntityType.DRAGON_FIREBALL, new BallisticParams(0.95, 0.0, false)),
				Map.entry(EntityType.WITHER_SKULL, new BallisticParams(0.95, 0.0, false)),
				Map.entry(EntityType.TRIDENT, new BallisticParams(0.99, 0.05, true)),
				Map.entry(EntityType.POTION, new BallisticParams(0.99, 0.03, true)),
				Map.entry(EntityType.EXPERIENCE_BOTTLE, new BallisticParams(0.99, 0.03, true))
		);
	}

	@Override
	public boolean supports(Entity entity) {
		return entity != null && table().containsKey(entity.getType());
	}

	@Override
	@Nullable
	public Threat capture(Entity entity, int horizon) {
		if (entity == null || horizon <= 0) return null;
		BallisticParams params = table().get(entity.getType());
		if (params == null) return null;

		Vec3 pos = entity.position();
		Vec3 vel = entity.getDeltaMovement();
		// MLM-style: no meaningful motion → no ballistic threat (stuck arrows)
		if (vel.lengthSqr() < ThreatFilters.MIN_PROJECTILE_SPEED_SQR) return null;

		float hitRadius = (float) (entity.getBbWidth() / 2);
		float damage = 0;

		if (entity instanceof AbstractArrow arrow) {
			damage = (float) arrow.getBaseDamage();
			vel = arrow.getDeltaMovement();
		} else if (entity instanceof Fireball) {
			damage = 1;
		} else if (entity instanceof ThrownTrident trident) {
			damage = (float) trident.getBaseDamage();
			vel = trident.getDeltaMovement();
		}

		// Vanilla tick order: integrate position with current vel, then apply drag + gravity
		ThreatFrame[] frames = new ThreatFrame[horizon];
		for (int i = 0; i < horizon; i++) {
			if (i > 0) {
				pos = pos.add(vel);
				vel = vel.scale(params.drag());
				if (params.hasGravity()) {
					vel = vel.add(0, -params.gravity(), 0);
				}
			}
			frames[i] = new ThreatFrame(pos, null, hitRadius, 0f, true);
		}

		return new Threat(entity.getId(), frames, ThreatSemantic.VANILLA, entity, damage);
	}

	/** Simulate ballistic path without an entity (unit tests / offline). */
	public static Vec3[] simulate(Vec3 startPos, Vec3 startVel, double drag, double gravity, int horizon) {
		Vec3[] out = new Vec3[horizon];
		Vec3 pos = startPos;
		Vec3 vel = startVel;
		for (int i = 0; i < horizon; i++) {
			if (i > 0) {
				pos = pos.add(vel);
				vel = vel.scale(drag);
				if (gravity != 0) {
					vel = vel.add(0, -gravity, 0);
				}
			}
			out[i] = pos;
		}
		return out;
	}

	private record BallisticParams(double drag, double gravity, boolean hasGravity) {
	}
}
