package dev.xkmc.youkaishomecoming.content.entity.danmaku;

import dev.xkmc.youkaishomecoming.content.spell.SpellTestBootstrap;
import dev.xkmc.youkaishomecoming.content.spell.shooter.ShooterEntity;
import net.minecraft.world.entity.Entity;
import sun.misc.Unsafe;

/** Exercises the production quota and shooter forwarding without allocating a Minecraft world. */
public final class NonSpellSpawnBudgetTest {
	private static int checks;

	public static void main(String[] args) throws Exception {
		if (SpellTestBootstrap.enter(NonSpellSpawnBudgetTest.class, args)) return;
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
		var root = allocate(RecordingProxy.class);
		var child = allocate(ShooterEntity.class);
		child.bindNonSpellHost(root);
		root.time = 100;
		root.limit = 2;
		root.shoot(null);
		child.shoot(null);
		child.shoot(null);
		root.shoot(null);
		check("root and child output share two slots", root.accepted == 2 && root.attempts == 4);
		root.time++;
		child.shoot(null);
		root.shoot(null);
		child.shoot(null);
		check("child ticking first cannot refill the root allowance", root.accepted == 4 && root.attempts == 7);
		root.limit = 1;
		root.shoot(null);
		check("Power reduction in the same tick cannot reset usage", root.accepted == 4);
		root.limit = 3;
		child.shoot(null);
		child.shoot(null);
		check("Power increase grants only the additional capacity", root.accepted == 5);
		root.time++;
		root.limit = 1;
		child.shoot(null);
		root.shoot(null);
		check("next tick uses the current Power cap", root.accepted == 6);
		root.time++;
		root.limit = 0;
		root.shoot(null);
		check("zero allowance rejects output", root.accepted == 6);
		System.out.println("NonSpellSpawnBudgetTest: " + checks + " checks passed");
	}

	private static final class RecordingProxy extends DanmakuProxyEntity {
		long time;
		int limit;
		int accepted;
		int attempts;
		private RecordingProxy() { super(null, null); }
		@Override public void shoot(Entity entity) {
			attempts++;
			if (reserveNonSpellSpawn(time, limit)) accepted++;
		}
	}

	private static <T> T allocate(Class<T> type) throws Exception {
		var field = Unsafe.class.getDeclaredField("theUnsafe");
		field.setAccessible(true);
		return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
	}

	private static void check(String label, boolean pass) {
		if (!pass) throw new AssertionError(label);
		checks++;
	}
}
