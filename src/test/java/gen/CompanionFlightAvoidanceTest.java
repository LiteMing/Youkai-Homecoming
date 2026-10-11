package gen;

import dev.xkmc.youkaishomecoming.content.entity.goal.CompanionFlightAvoidance;
import dev.xkmc.youkaishomecoming.content.entity.goal.CompanionFlightSlots;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static dev.xkmc.youkaishomecoming.content.entity.goal.CompanionFlightMath.*;

/** Production local controller against deterministic solid boxes; no Minecraft bootstrap. */
public final class CompanionFlightAvoidanceTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static double length(Point p) { return Math.sqrt(p.x() * p.x() + p.y() * p.y() + p.z() * p.z()); }
    private static Point add(Point a, Point b) { return new Point(a.x() + b.x(), a.y() + b.y(), a.z() + b.z()); }
    private static Point subtract(Point a, Point b) { return new Point(a.x() - b.x(), a.y() - b.y(), a.z() - b.z()); }
    private static void near(Point a, Point b) { check(length(subtract(a, b)) < 1e-8, a + " != " + b); }
    private static final CompanionFlightAvoidance.Parameters PARAMETERS =
            new CompanionFlightAvoidance.Parameters(1.5, .4, 4, 1.5, 20);

    private record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        boolean intersects(Box other) {
            return minX < other.maxX && maxX > other.minX && minY < other.maxY
                    && maxY > other.minY && minZ < other.maxZ && maxZ > other.minZ;
        }
        Box move(Point p) { return new Box(minX + p.x(), minY + p.y(), minZ + p.z(), maxX + p.x(), maxY + p.y(), maxZ + p.z()); }
        Box sweep(Point p) {
            return new Box(minX + Math.min(0, p.x()), minY + Math.min(0, p.y()), minZ + Math.min(0, p.z()),
                    maxX + Math.max(0, p.x()), maxY + Math.max(0, p.y()), maxZ + Math.max(0, p.z()));
        }
    }
    private static final class World {
        final List<Box> blocks = new ArrayList<>();
        Point position = new Point(0, 0, 0), velocity = new Point(0, 0, 0);
        int probes;
        Box box(Point p) { return new Box(p.x() - .3, p.y(), p.z() - .3, p.x() + .3, p.y() + 1.8, p.z() + .3); }
        boolean clear(Box box) { return blocks.stream().noneMatch(box::intersects); }
        CompanionFlightAvoidance.Budget budget() {
            probes = 0;
            return new CompanionFlightAvoidance.Budget(p -> { probes++; return clear(box(p)); },
                    p -> { probes++; return clear(box(position).sweep(p)); });
        }
        void step(CompanionFlightAvoidance controller, Point requested, Point player, int tick) {
            var budget = budget();
            Point target = controller.resolveTarget(requested, player, add(player, new Point(0, 1.8, 0)), tick, PARAMETERS, budget);
            if (target == null) velocity = new Point(0, 0, 0);
            else {
                Point delta = subtract(target, position);
                Point desired = length(delta) > .8 ? steer(delta, velocity, .85) : new Point(0, 0, 0);
                velocity = controller.avoid(desired, velocity, PARAMETERS, budget, .85);
                // Conservative stand-in for vanilla movement clipping. Never cross a solid.
                if (clear(box(position).sweep(velocity))) position = add(position, velocity);
                else velocity = new Point(0, 0, 0);
            }
            check(probes <= 2, "collision budget exceeded on tick " + tick);
            check(length(velocity) <= .85 + 1e-8, "velocity cap exceeded");
            check(clear(box(position)), "entity entered a solid");
        }
    }

    private static void formation() {
        var slots = new CompanionFlightSlots();
        UUID player = UUID.randomUUID(), otherPlayer = UUID.randomUUID();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), third = UUID.randomUUID();
        check(slots.claim(player, first, 0) == 0, "single companion must retain legacy position");
        check(slots.claim(player, second, 0) == 1, "second companion overlaps first");
        check(slots.claim(otherPlayer, third, 0) == 0, "player groups share slots");
        for (int tick = 1; tick <= 20; tick++) {
            // Alternate entity update order to catch expiry and reassignment errors.
            if (tick % 2 == 0) check(slots.claim(player, first, tick) == 0, "first slot changed");
            check(slots.claim(player, second, tick) == 1, "second slot changed");
            if (tick % 2 != 0) check(slots.claim(player, first, tick) == 0, "first slot changed");
        }
        slots.release(player, first);
        check(slots.claim(player, second, 21) == 1, "survivor moved into vacant slot");
        check(slots.claim(player, third, 21) == 0, "vacant slot was not reused");
        check(slots.claim(player, second, 22) == 1, "survivor changed position");
        check(slots.claim(player, second, 23) == 1, "survivor was expired");
        check(slots.claim(player, first, 23) == 0, "unloaded member did not expire");
        slots.release(player, first);
        slots.release(player, second);
        check(slots.claim(player, third, 24) == 0, "empty group not reset");
        // Separate level registries do not share leases, even for the same UUIDs.
        check(new CompanionFlightSlots().claim(player, second, 24) == 0, "dimension isolation failed");
        Point origin = new Point(0, 0, 0);
        for (int yaw = -180; yaw <= 180; yaw += 15) {
            near(slotTarget(origin, yaw, 0, 2.4), target(TargetMode.LEGACY, origin, yaw, origin));
            List<Point> targets = new ArrayList<>();
            for (int slot = 0; slot < 32; slot++) {
                Point point = slotTarget(origin, yaw, slot, 2.4);
                for (Point other : targets) check(length(subtract(point, other)) >= 2.4 - 1e-8, "formation targets overlap");
                targets.add(point);
            }
        }
        for (TargetMode mode : TargetMode.values()) for (MovementMode movement : MovementMode.values()) {
            check(dailyFollow(mode, movement) == (mode == TargetMode.LEGACY && movement == MovementMode.STEER),
                    "explicit targets or locking entered the daily controller");
        }
    }

    private static void obstacles() {
        Point target = new Point(0, 0, 7), player = new Point(0, 0, 5);
        World open = new World();
        var controller = new CompanionFlightAvoidance();
        for (int tick = 1; tick <= 30; tick++) open.step(controller, target, player, tick);
        check(length(subtract(open.position, target)) < .8, "open-field tracking failed");

        World lowWall = new World();
        lowWall.blocks.add(new Box(-2, -1, 2, 2, 1.2, 2.4));
        controller = new CompanionFlightAvoidance();
        double peak = 0;
        for (int tick = 1; tick <= 160; tick++) {
            lowWall.step(controller, target, player, tick);
            peak = Math.max(peak, lowWall.position.y());
        }
        check(peak >= 1.2, "low wall did not trigger lifting");
        check(length(subtract(lowWall.position, target)) < .8, "failed to pass low wall and return to target height");

        World ceiling = new World();
        ceiling.blocks.add(new Box(-20, 2.0, -20, 20, 3, 20));
        ceiling.blocks.add(new Box(-.7, -1, 2, .7, 2, 2.4));
        controller = new CompanionFlightAvoidance();
        double sideways = 0;
        for (int tick = 1; tick <= 180; tick++) {
            ceiling.step(controller, target, player, tick);
            sideways = Math.max(sideways, Math.abs(ceiling.position.x()));
        }
        check(sideways > 1, "ceiling did not cause lateral avoidance");
        check(length(subtract(ceiling.position, target)) < .8, "failed to pass wall under ceiling");

        World trapped = new World();
        trapped.blocks.add(new Box(-20, -1, .4, 20, 4, .8));
        trapped.blocks.add(new Box(-20, 2, -20, 20, 4, 20));
        trapped.blocks.add(new Box(-1, -1, -20, -.4, 4, 20));
        trapped.blocks.add(new Box(.4, -1, -20, 1, 4, 20));
        controller = new CompanionFlightAvoidance();
        for (int tick = 1; tick <= 60; tick++) trapped.step(controller, target, player, tick);
        check(trapped.position.z() < .4, "trapped controller bypassed wall");

        // A shifted endpoint can miss an intervening thin wall; the production probe uses a sweep.
        World thinWall = new World();
        thinWall.blocks.add(new Box(-1, -1, .8, 1, 3, 1.2));
        Point displacement = new Point(0, 0, 2);
        check(thinWall.clear(thinWall.box(thinWall.position).move(displacement)), "test endpoint unexpectedly intersects wall");
        check(!thinWall.budget().sweep(displacement), "sweep missed intermediate wall");
        var budget = new CompanionFlightAvoidance.Budget(p -> true, p -> {
            check(length(p) >= 4 - 1e-8, "lookahead shorter than high-speed tick displacement");
            return true;
        });
        controller.reset();
        controller.avoid(new Point(0, 0, 4), new Point(0, 0, 0), PARAMETERS, budget, 4);
        // Budget exhaustion cannot call the world a third time.
        final int[] calls = {0};
        budget = new CompanionFlightAvoidance.Budget(p -> { calls[0]++; return true; }, p -> { calls[0]++; return true; });
        budget.endpoint(target); budget.sweep(displacement); budget.endpoint(player); budget.sweep(displacement);
        check(calls[0] == 2, "hard probe limit failed");

        controller.reset();
        Point actual = new Point(0, 0, 0);
        for (int tick = 0; tick < 7; tick++) {
            budget = new CompanionFlightAvoidance.Budget(p -> true, p -> p.x() > 0);
            budget.endpoint(target);
            Point result = controller.avoid(new Point(0, 0, .85), actual, PARAMETERS, budget, .85);
            if (tick < 3) near(result, new Point(0, 0, 0));
            else check(result.x() > 0 && result.z() == 0, "right detour was not retained after up and left failed");
        }
        for (double maxSpeed : new double[]{.01, .1, .3, .85, 4}) {
            controller.reset();
            budget = new CompanionFlightAvoidance.Budget(p -> true, p -> false);
            controller.avoid(new Point(0, 0, maxSpeed), new Point(0, 0, 0), PARAMETERS, budget, maxSpeed);
            budget = new CompanionFlightAvoidance.Budget(p -> true, p -> true);
            Point lift = controller.avoid(new Point(0, 0, maxSpeed), new Point(0, 0, 0), PARAMETERS, budget, maxSpeed);
            check(lift.y() > 0 && length(lift) <= maxSpeed + 1e-8, "detour ignored a small speed cap");
        }
    }

    private static void fallback() {
        near(retract(new Point(0, 0, 4), new Point(0, 0, 0), 1.5), new Point(0, 0, 2.5));
        near(retract(new Point(0, 0, .2), new Point(0, 0, 0), 1.5), new Point(0, 0, 0));
        near(retract(new Point(0, 0, 0), new Point(0, 0, 0), 1.5), new Point(0, 0, 0));
        World world = new World();
        Point player = new Point(0, 0, 4), target = new Point(0, 1.6, 6.4);
        world.blocks.add(new Box(-1, 0, 6.2, 1, 5, 7));
        var controller = new CompanionFlightAvoidance();
        Point fallback = controller.resolveTarget(target, player, add(player, new Point(0, 1.8, 0)), 1, PARAMETERS, world.budget());
        check(fallback != null && !fallback.equals(target) && world.probes == 2, "blocked target did not retract within budget");
        Point next = controller.resolveTarget(target, player, add(player, new Point(0, 1.8, 0)), 2, PARAMETERS, world.budget());
        near(next, fallback);
        check(world.probes == 1, "fallback consumes both probes on every tick");
        world.blocks.clear();
        next = controller.resolveTarget(target, player, add(player, new Point(0, 1.8, 0)), 21, PARAMETERS, world.budget());
        near(next, target);
        check(world.probes == 1, "recovery did not return to original target");
        // Headroom is always checked; it is not assumed safe.
        controller.reset();
        var budget = new CompanionFlightAvoidance.Budget(p -> false, p -> true);
        check(controller.resolveTarget(target, player, add(player, new Point(0, 1.8, 0)), 1, PARAMETERS, budget) == null, "unsafe fallback accepted");
        budget = new CompanionFlightAvoidance.Budget(p -> false, p -> true);
        check(controller.resolveTarget(target, player, add(player, new Point(0, 1.8, 0)), 2, PARAMETERS, budget) == null, "unsafe head target accepted");
        controller.reset();
        Point overhead = add(player, new Point(0, 1.8, 0));
        budget = new CompanionFlightAvoidance.Budget(p -> p.equals(overhead), p -> true);
        check(controller.resolveTarget(target, player, overhead, 1, PARAMETERS, budget) == null, "third endpoint probe executed");
        budget = new CompanionFlightAvoidance.Budget(p -> p.equals(overhead), p -> true);
        near(controller.resolveTarget(target, player, overhead, 2, PARAMETERS, budget), overhead);
        check(budget.remaining() == 1, "safe overhead fallback did not leave a motion probe");
    }

    public static void main(String[] args) {
        formation(); obstacles(); fallback();
        System.out.println("CompanionFlightAvoidanceTest: " + checks + " checks passed (simulated collision world, not in-game performance)");
    }
}
