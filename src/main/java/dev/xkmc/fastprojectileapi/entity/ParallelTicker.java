package dev.xkmc.fastprojectileapi.entity;

import java.util.ArrayList;
import java.util.function.BooleanSupplier;

public class ParallelTicker {

	public static void tickAll(Iterable<SimplifiedProjectile> all, BooleanSupplier shouldStop) {
		ArrayList<AsyncProjectile> active = new ArrayList<>();
		for (var e : all) {
			if (e.isAddedToWorld() && !e.isRemoved()) continue;
			if (!e.isValid()) continue;
			e.setOldPosAndRot();
			++e.tickCount;
			if (e instanceof AsyncProjectile async) {
				async.tickData.reset();
				active.add(async);
			} else {
				e.tick();
			}
			if (shouldStop.getAsBoolean()) return;
		}
		runStage(active, shouldStop, (e, data) -> e.beginTick(data));
		runStage(active, shouldStop, (e, data) -> e.planMove(data));
		runStage(active, shouldStop, (e, data) -> e.planPreheatRange(data));
		runStage(active, shouldStop, (e, data) -> e.collectCollisionInput(data));
		runStage(active, shouldStop, (e, data) -> e.resolveCollision(data));
		runStage(active, shouldStop, (e, data) -> e.finishTick(data));
	}

	private static void runStage(ArrayList<AsyncProjectile> active, BooleanSupplier shouldStop, Stage stage) {
		for (var e : active) {
			if (shouldStop.getAsBoolean()) return;
			var data = e.tickData;
			if (data.stopTick) continue;
			stage.run(e, data);
		}
	}

	@FunctionalInterface
	private interface Stage {

		void run(AsyncProjectile projectile, AsyncProjectile.TickData data);

	}

}
