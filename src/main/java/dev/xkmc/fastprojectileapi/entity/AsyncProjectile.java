package dev.xkmc.fastprojectileapi.entity;

import dev.xkmc.fastprojectileapi.collision.LaserHitHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

public abstract class AsyncProjectile extends SimplifiedProjectile {

	protected final TickData tickData = new TickData();

	protected AsyncProjectile(EntityType<?> pEntityType, Level pLevel) {
		super(pEntityType, pLevel);
	}

	@Override
	public void tick() {
		super.tick();
		TickData data = tickData;
		data.reset(this);
		beginTick(data);
		if (data.stopTick) return;
		planMove(data);
		if (data.stopTick) return;
		planPreheatRange(data);
		if (data.stopTick) return;
		collectCollisionInput(data);
		if (data.stopTick) return;
		resolveCollision(data);
		if (data.stopTick) return;
		finishTick(data);
	}

	protected void beginTick(TickData data) {
	}

	protected void planMove(TickData data) {
	}

	protected void planPreheatRange(TickData data) {
	}

	protected void collectCollisionInput(TickData data) {
	}

	protected void resolveCollision(TickData data) {
	}

	protected void finishTick(TickData data) {
	}

	public static class TickData {

		@Nullable
		public AsyncProjectile projectile;
		@Nullable
		public ProjectileMovement plannedMovement;
		@Nullable
		public HitResult projectileHit;
		@Nullable
		public LaserHitHelper.LaserHitResult laserHit;
		public boolean stopTick;

		public void reset(AsyncProjectile projectile) {
			this.projectile = projectile;
			plannedMovement = null;
			projectileHit = null;
			laserHit = null;
			stopTick = false;
		}

	}

}
