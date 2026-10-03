package gen;

import static dev.xkmc.youkaishomecoming.content.entity.goal.CompanionFlightMath.*;

/** Standalone deterministic geometry and velocity checks; no game bootstrap. */
public final class CompanionFlightMathTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
    private static void near(Point a, Point b) { check(Math.abs(a.x()-b.x()) < 1e-9 && Math.abs(a.y()-b.y()) < 1e-9 && Math.abs(a.z()-b.z()) < 1e-9); }
    public static void main(String[] args) {
        Point player = new Point(100, 80, -20);
        near(target(TargetMode.PLAYER_LOCAL, player, 0, new Point(2, -10, 3)), new Point(98, 70, -17));
        near(target(TargetMode.PLAYER_LOCAL, player, 90, new Point(2, -10, 3)), new Point(97, 70, -22));
        near(target(TargetMode.PLAYER_LOCAL, player, -90, new Point(2, -10, 3)), new Point(103, 70, -18));
        near(target(TargetMode.PLAYER_LOCAL, player, 180, new Point(2, -10, 3)), new Point(102, 70, -23));
        for (int yaw = -180; yaw <= 180; yaw += 15) {
            near(target(TargetMode.PLAYER_LOCAL, player, yaw, new Point(0, -10, 0)), new Point(100, 70, -20));
            near(target(TargetMode.PLAYER_WORLD, player, yaw, new Point(3, 2, 1)), new Point(103, 82, -19));
            near(target(TargetMode.ABSOLUTE, player, yaw, new Point(-10, 50, 30)), new Point(-10, 50, 30));
        }
        near(target(TargetMode.LEGACY, player, 0, new Point(0,0,0)), new Point(100 + Math.sqrt(2)*1.2, 81.6, -20 + Math.sqrt(2)*1.2));
        check(sampleNow(0, -1, 20, false)); check(!sampleNow(19, 0, 20, false));
        check(sampleNow(20, 0, 20, false)); check(sampleNow(1, 0, 20, true)); check(sampleNow(1, 0, 1, false));
        for (double speed : new double[]{.01,.1,.3,.85,2,4}) for (Point delta : new Point[]{new Point(100,20,-30),new Point(0,.01,0),new Point(-100,0,0)}) {
            Point v = steer(delta, new Point(10,-5,8), speed);
            check(Math.sqrt(v.x()*v.x()+v.y()*v.y()+v.z()*v.z()) <= speed + 1e-9);
        }
        near(steer(new Point(0,0,0), new Point(1,2,3), .85), new Point(0,0,0));
        System.out.println("CompanionFlightMathTest: " + checks + " checks passed");
    }
}
