package gen;

import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuHitBox;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.ThreatSemantic;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.SelfBoxModel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Fast focused checks for live danmaku hit-box scaling. */
public class PilotHitBoxTest {

	private static int passed;
	private static int failed;

	public static void main(String[] args) {
		testScale();
		testPoseDependentBox();
		System.out.println("PilotHitBoxTest: " + passed + " passed, " + failed + " failed");
		if (failed > 0) throw new RuntimeException(failed + " hit-box tests failed");
	}

	private static void testScale() {
		AABB base = new AABB(-0.3, 0, -0.3, 0.3, 1.8, 0.3);
		AABB normal = DanmakuHitBox.scaled(base, new Vec3(0, 1.62, 0), 1);
		approx("scale one uses average edge", normal.getXsize(), 1.0);
		approx("scale one is cubic", normal.getYsize(), normal.getXsize());
		approx("scale one keeps depth cubic", normal.getZsize(), normal.getXsize());
		approx("scale one follows eye Y", normal.getCenter().y, 1.62);
		AABB half = DanmakuHitBox.scaled(base, new Vec3(0, 1.62, 0), 0.5);
		approx("half scale edge", half.getXsize(), 0.5);
		approx("half scale is cubic", half.getYsize(), half.getXsize());
		approx("half scale follows eye Y", half.getCenter().y, 1.62);
		AABB point = DanmakuHitBox.scaled(base, new Vec3(0, 1.62, 0), 0);
		approx("zero scale keeps one pixel X", point.getXsize(), 1d / 16);
		approx("zero scale keeps one pixel Y", point.getYsize(), 1d / 16);
	}

	private static void testPoseDependentBox() {
		// Representative swimming/gliding pose: the live box is shorter than standing.
		AABB pose = new AABB(-0.3, 0, -0.3, 0.3, 0.6, 0.3);
		SelfBoxModel model = SelfBoxModel.playerDanmaku(
				pose, Vec3.ZERO, new Vec3(0, 0.4, 0), 1);
		AABB danmaku = model.hitBoxAt(Vec3.ZERO, ThreatSemantic.DANMAKU);
		AABB vanilla = model.hitBoxAt(Vec3.ZERO, ThreatSemantic.VANILLA);
		approx("live vanilla width", vanilla.getXsize(), pose.getXsize());
		approx("live vanilla height", vanilla.getYsize(), pose.getYsize());
		approx("scale one keeps live danmaku height", danmaku.getYsize(), pose.getYsize());
	}

	private static void approx(String name, double actual, double expected) {
		if (Math.abs(actual - expected) < 1e-9) passed++;
		else {
			failed++;
			System.out.println(name + ": actual=" + actual + ", expected=" + expected);
		}
	}
}
