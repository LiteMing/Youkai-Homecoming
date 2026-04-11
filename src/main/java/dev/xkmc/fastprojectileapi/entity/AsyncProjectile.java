package dev.xkmc.fastprojectileapi.entity;

import dev.xkmc.fastprojectileapi.collision.UserMatrixCache;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class AsyncProjectile extends SimplifiedProjectile {

	protected final TickData tickData = new TickData();

	public TickData tickData() {
		return tickData;
	}

	protected AsyncProjectile(EntityType<?> pEntityType, Level pLevel) {
		super(pEntityType, pLevel);
	}

	@Override
	public void tick() {
		TickData data = tickData;
		data.reset();
		beginTick(data);
		if (data.stopTick) return;
		planMove(data);
		if (data.stopTick) return;
		trimMove(data);
		if (data.stopTick) return;
		planPreheatRange(data, null);
		if (data.stopTick) return;
		collectCollisionInput(data);
		if (data.stopTick) return;
		resolveCollision(data);
		if (data.stopTick) return;
		finishTick(data);
	}

	protected void beginTick(TickData data) {
		super.tick();
	}

	protected void planMove(TickData data) {
	}

	protected void trimMove(TickData data) {
	}

	protected void planPreheatRange(TickData data, UserMatrixCache cache) {
	}

	protected void collectCollisionInput(TickData data) {
	}

	protected void resolveCollision(TickData data) {
	}

	protected void finishTick(TickData data) {
	}

	public static class TickData {

		@Nullable
		public Vec3 moveSrc;
		@Nullable
		public Vec3 inputVelocity;
		@Nullable
		public ProjectileMovement plannedMovement;
		@Nullable
		public Vec3 moveDst;
		@Nullable
		public BlockHitResult blockHit;
		public final List<Entity> hitEntities = new ArrayList<>();
		public int candidateCount;
		public int grazeCount;
		public boolean removed;
		public boolean stopTick;

		public void reset() {
			moveSrc = null;
			inputVelocity = null;
			plannedMovement = null;
			moveDst = null;
			blockHit = null;
			hitEntities.clear();
			candidateCount = 0;
			grazeCount = 0;
			removed = false;
			stopTick = false;
		}

	}

}
