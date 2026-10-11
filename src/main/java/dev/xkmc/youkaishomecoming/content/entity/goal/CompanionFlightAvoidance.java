package dev.xkmc.youkaishomecoming.content.entity.goal;

import java.util.function.Predicate;
import static dev.xkmc.youkaishomecoming.content.entity.goal.CompanionFlightMath.*;

/** Bounded local steering, not pathfinding. Probes are supplied by the server world. */
public final class CompanionFlightAvoidance {
    public record Parameters(double lookahead, double detourSpeed, int holdTicks,
                             double retractDistance, int recoveryTicks) {}

    /** Endpoint, fallback and swept movement checks share the same hard limit. */
    public static final class Budget {
        private final Predicate<Point> endpoint, sweep;
        private int probes;
        public Budget(Predicate<Point> endpoint, Predicate<Point> sweep) {
            this.endpoint = endpoint;
            this.sweep = sweep;
        }
        public int remaining() { return 2 - probes; }
        public boolean endpoint(Point point) { return probe(endpoint, point); }
        public boolean sweep(Point point) { return probe(sweep, point); }
        private boolean probe(Predicate<Point> predicate, Point point) {
            if (remaining() == 0) return false;
            probes++;
            return predicate.test(point);
        }
    }

    private int fallback, recoveryTick;
    private int direction, heldTicks;
    private int preferredDirection = 1;

    public void reset() {
        fallback = recoveryTick = direction = heldTicks = 0;
        preferredDirection = 1;
    }

    /** A fallback remains relative to the player; periodically retry the requested slot. */
    public Point resolveTarget(Point requested, Point player, Point overhead, int tick,
                               Parameters parameters, Budget budget) {
        if (fallback == 0 || tick >= recoveryTick) {
            if (budget.endpoint(requested)) {
                fallback = 0;
                return requested;
            }
            if (fallback == 0) fallback = 1;
            recoveryTick = tick + parameters.recoveryTicks();
        }
        while (budget.remaining() > 0) {
            Point candidate = fallback == 1 ? retract(requested, player, parameters.retractDistance()) : overhead;
            if (budget.endpoint(candidate)) return candidate;
            fallback = fallback == 1 ? 2 : 1;
        }
        return null;
    }

    public Point avoid(Point desired, Point actual, Parameters parameters, Budget budget, double maxSpeed) {
        if (length(desired) < 1e-9) return desired;
        if (budget.remaining() == 0) return brake(actual, maxSpeed);
        Point candidate = direction == 0 ? desired : detour(desired, direction, Math.min(maxSpeed, parameters.detourSpeed()));
        double distance = Math.max(parameters.lookahead(), length(candidate));
        Point lookahead = scale(candidate, distance / length(candidate));
        if (budget.sweep(lookahead)) {
            if (direction != 0) {
                preferredDirection = direction;
                if (++heldTicks >= parameters.holdTicks()) direction = heldTicks = 0;
            }
            return candidate;
        }
        // Test only one alternative on a later tick; retain a successful side across retries.
        direction = direction == 0 ? preferredDirection : direction == 3 ? 1 : direction + 1;
        heldTicks = 0;
        // Vanilla movement still clips this residual velocity and permits tangential sliding.
        return brake(actual, maxSpeed);
    }

    private static Point detour(Point desired, int direction, double speed) {
        if (direction == 1) return new Point(0, speed, 0);
        double horizontal = Math.hypot(desired.x(), desired.z());
        if (horizontal < 1e-9) return new Point(direction == 2 ? speed : -speed, 0, 0);
        double sign = direction == 2 ? 1 : -1;
        return new Point(-desired.z() / horizontal * speed * sign, 0, desired.x() / horizontal * speed * sign);
    }

    private static Point brake(Point velocity, double maxSpeed) {
        double magnitude = length(velocity);
        return scale(velocity, magnitude == 0 ? 0 : Math.min(0.4, maxSpeed / magnitude));
    }
    private static Point scale(Point point, double scale) { return new Point(point.x() * scale, point.y() * scale, point.z() * scale); }
    private static double length(Point point) { return Math.sqrt(point.x() * point.x() + point.y() * point.y() + point.z() * point.z()); }
}
