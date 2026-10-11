package dev.xkmc.youkaishomecoming.content.entity.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Set;

import static dev.xkmc.youkaishomecoming.content.entity.goal.CompanionFlightMath.Point;

/**
 * A cached, bounded flight route used only after direct steering is blocked.
 * Vanilla's FlyNodeEvaluator supplies the local 3D search; this class limits
 * its target horizon, visited-node budget, and recomputation cadence.
 */
public final class CompanionFlightPlanner {
    private Path path;
    private BlockPos requestedGoal;
    private long lastPlanTick = Long.MIN_VALUE;

    public void reset() {
        path = null;
        requestedGoal = null;
        lastPlanTick = Long.MIN_VALUE;
    }

    public boolean hasRoute() {
        return path != null && !path.isDone();
    }

    public boolean shouldPlan(Point target, long tick, int replanTicks) {
        BlockPos goal = BlockPos.containing(target.x(), target.y(), target.z());
        if (lastPlanTick == Long.MIN_VALUE) return true;
        boolean targetMoved = requestedGoal == null || requestedGoal.distSqr(goal) > 36;
        boolean retryDue = tick - lastPlanTick >= replanTicks;
        return targetMoved || retryDue && !hasRoute();
    }

    /**
     * Plans toward a short horizon instead of asking navigation to solve the
     * entire player distance. A small target set lets a route finish in the
     * nearest open block when the exact requested point is inside a wall.
     */
    public boolean plan(PathNavigation navigation, Mob mob, Point target,
                       long tick, int replanTicks, int searchNodes, int horizon) {
        lastPlanTick = tick;
        requestedGoal = BlockPos.containing(target.x(), target.y(), target.z());
        BlockPos plannedGoal = requestedGoal;
        path = null;
        navigation.setCanFloat(true);

        Vec3 delta = new Vec3(target.x() - mob.getX(), target.y() - mob.getY(), target.z() - mob.getZ());
        double distance = delta.length();
        if (distance > horizon && distance > 1.0E-6) {
            Vec3 local = mob.position().add(delta.scale(horizon / distance));
            plannedGoal = BlockPos.containing(local);
        }

        Set<BlockPos> candidates = new LinkedHashSet<>();
        addCandidates(candidates, plannedGoal);
        // The public argument is the acceptable target distance, not the
        // visited-node budget. Keep it small and apply the budget only for
        // this synchronous search, restoring the shared navigation afterwards.
        double followRange = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        float baselineNodes = (float) Math.max(16.0, Math.max(followRange, 16.0) * 16.0);
        float multiplier = Math.max(0.05F, Math.min(1.0F, searchNodes / baselineNodes));
        navigation.setMaxVisitedNodesMultiplier(multiplier);
        try {
            path = navigation.createPath(candidates, 1);
        } finally {
            navigation.resetMaxVisitedNodesMultiplier();
        }
        if (path == null || path.getNodeCount() == 0) {
            path = null;
            return false;
        }
        return hasRoute();
    }

    public void invalidate(long tick) {
        path = null;
        lastPlanTick = Math.min(lastPlanTick, tick - 2);
    }

    /** Returns the next route node and advances nodes already reached. */
    public Vec3 waypoint(Mob mob) {
        if (!hasRoute()) return null;
        while (hasRoute()) {
            Vec3 waypoint = path.getNextEntityPos(mob);
            if (mob.position().distanceToSqr(waypoint) <= 0.64) {
                path.advance();
                continue;
            }
            return waypoint;
        }
        return null;
    }

    private static void addCandidates(Set<BlockPos> candidates, BlockPos center) {
        // Keep this set small; FlyNodeEvaluator performs the actual volume test.
        candidates.add(center);
        candidates.add(center.above());
        candidates.add(center.below());
        candidates.add(center.east());
        candidates.add(center.west());
        candidates.add(center.north());
        candidates.add(center.south());
    }
}
