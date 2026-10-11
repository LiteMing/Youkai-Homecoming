package dev.xkmc.youkaishomecoming.content.entity.goal;

import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import static dev.xkmc.youkaishomecoming.content.entity.goal.CompanionFlightMath.*;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Youkai native 3D companion flight goal.
 */
public class YoukaiFlyCompanionGoal<T extends YoukaiEntity> extends Goal {

    private static final Map<Level, CompanionFlightSlots> SLOTS = new WeakHashMap<>();

    protected final T youkai;
    protected Player companionPlayer;
    protected int tickCount = 0;
    private CompanionFlightSettings settings;
    private String settingsJson;
    private Vec3 sampledTarget;
    private int lastSample = -1;
    private float sampledYaw, sampledPitch;
    private Point sampledPlayer, sampledOverhead;
    private final CompanionFlightPlanner planner = new CompanionFlightPlanner();
    private CompanionFlightSlots slotRegistry;
    private UUID slotPlayer;
    private int slot = -1;

    public YoukaiFlyCompanionGoal(T youkai) {
        this.youkai = youkai;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (youkai.getTarget() != null && youkai.getTarget().isAlive()) {
            return false;
        }
        var binding = CompanionFlightSettings.configured(youkai.getPersistentData());
        if (binding.isEmpty()) return false;
        companionPlayer = findCompanionPlayer(binding.get().playerUuid());
        return validCompanionPlayer(companionPlayer);
    }

    @Override
    public boolean canContinueToUse() {
        if (youkai.getTarget() != null && youkai.getTarget().isAlive()) {
            return false;
        }
        var binding = CompanionFlightSettings.configured(youkai.getPersistentData());
        return binding.isPresent() && companionPlayer != null
                && binding.get().playerUuid().equals(companionPlayer.getUUID())
                && validCompanionPlayer(companionPlayer);
    }

    @Override
    public void start() {
        tickCount = 0;
        settings = null;
        sampledTarget = null;
        lastSample = -1;
        planner.reset();
        youkai.setFlying();
        youkai.getNavigation().stop();
    }

    @Override
    public void stop() {
        releaseSlot();
        companionPlayer = null;
        sampledTarget = null;
        youkai.getPersistentData().putString("yh_companion_flight_status", "INACTIVE");
        youkai.setDeltaMovement(Vec3.ZERO);
        youkai.setWalking();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (companionPlayer == null) return;
        tickCount++;

        // This Goal owns movement. Prevent FlyingMoveControl from adding a
        // second acceleration or changing the configured facing after tick().
        youkai.getNavigation().stop();
        youkai.getMoveControl().setWantedPosition(youkai.getX(), youkai.getY(), youkai.getZ(), 0);
        youkai.setSpeed(0); youkai.setXxa(0); youkai.setYya(0); youkai.setZza(0);

        var binding = CompanionFlightSettings.configured(youkai.getPersistentData());
        if (binding.isEmpty()) return;
        String json = binding.get().settings().json();
        CompanionFlightSettings next = settings != null && json.equals(settingsJson) ? settings : binding.get().settings();
        boolean changed = !next.equals(settings);
        settings = next;
        settingsJson = json;
        var config = dev.xkmc.youkaishomecoming.init.data.YHModConfig.COMMON;
        boolean daily = dailyFollow(settings.targetMode(), settings.movementMode());
        int previousSlot = slot;
        if (daily && config.companionAutomaticSlots.get()) {
            if (slotRegistry == null) {
                slotRegistry = SLOTS.computeIfAbsent(youkai.level(), unused -> new CompanionFlightSlots());
                slotPlayer = companionPlayer.getUUID();
            }
            slot = slotRegistry.claim(slotPlayer, youkai.getUUID(), youkai.level().getGameTime());
        } else releaseSlot();
        changed |= slot != previousSlot;
        if (changed) planner.reset();
        if (sampleNow(tickCount, lastSample, settings.updateIntervalTicks(), changed)) {
            float yaw = settings.targetMode() == TargetMode.LEGACY ? companionPlayer.getYRot() : companionPlayer.yBodyRot;
            sampledPlayer = new Point(companionPlayer.getX(), companionPlayer.getY(), companionPlayer.getZ());
            sampledOverhead = new Point(sampledPlayer.x(), sampledPlayer.y() + companionPlayer.getBbHeight(), sampledPlayer.z());
            Point target = slot >= 0 ? slotTarget(sampledPlayer, yaw, slot, config.companionSlotSpacing.get())
                    : target(settings.targetMode(), sampledPlayer, yaw, new Point(settings.x(), settings.y(), settings.z()));
            sampledTarget = new Vec3(target.x(), target.y(), target.z());
            sampledYaw = settings.facingMode() == FacingMode.PLAYER_BODY ? companionPlayer.yBodyRot : companionPlayer.getYRot();
            sampledPitch = settings.facingMode() == FacingMode.PLAYER_VIEW ? companionPlayer.getXRot() : 0;
            lastSample = tickCount;
        }
        Point requested = new Point(sampledTarget.x, sampledTarget.y, sampledTarget.z);
        if (!validTarget(requested)) {
            planner.reset();
            youkai.setDeltaMovement(Vec3.ZERO);
            youkai.getPersistentData().putString("yh_companion_flight_status", "BLOCKED_TARGET");
            return;
        }
        if ((!daily || settings.movementMode() == MovementMode.LOCK_TO_TARGET) && !validEndpoint(requested)) {
            planner.reset();
            youkai.setDeltaMovement(Vec3.ZERO);
            youkai.getPersistentData().putString("yh_companion_flight_status", "BLOCKED_TARGET");
            return;
        }
        youkai.getPersistentData().putString("yh_companion_flight_status", "ACTIVE");
        Vec3 effectiveTarget = sampledTarget;
        boolean lockToTarget = settings.movementMode() == MovementMode.LOCK_TO_TARGET;
        boolean farCatchUp = youkai.position().distanceToSqr(effectiveTarget) > 50 * 50
                && (!daily || validEndpoint(requested));
        if (lockToTarget || farCatchUp) {
            youkai.getNavigation().stop();
            youkai.teleportTo(effectiveTarget.x, effectiveTarget.y, effectiveTarget.z);
            youkai.getMoveControl().setWantedPosition(effectiveTarget.x, effectiveTarget.y, effectiveTarget.z, 0);
            youkai.setDeltaMovement(Vec3.ZERO);
            youkai.hasImpulse = true;
            applyFacing();
            return;
        }
        double targetX = effectiveTarget.x, targetY = effectiveTarget.y, targetZ = effectiveTarget.z;
        double dx = targetX - youkai.getX(), dy = targetY - youkai.getY(), dz = targetZ - youkai.getZ();
        double dist3D = Math.sqrt(dx * dx + dy * dy + dz * dz);

        Vec3 curVel = youkai.getDeltaMovement();

        Vec3 velocity;
        if (dist3D > 0.8) {
            Point steered = steer(new Point(dx, dy, dz), new Point(curVel.x, curVel.y, curVel.z), settings.maxSpeed());
            velocity = new Vec3(steered.x(), steered.y(), steered.z());
        } else {
            double bobVy = Math.sin(tickCount * 0.15) * 0.02;
            velocity = new Vec3(curVel.x * 0.40, curVel.y * 0.40 + bobVy, curVel.z * 0.40);
            if (velocity.length() > settings.maxSpeed()) velocity = velocity.normalize().scale(settings.maxSpeed());
        }
        boolean routeActive = daily && planner.hasRoute();
        boolean directBlocked = daily && dist3D > 0.8 && !routeActive && !validSweep(velocity);
        if (daily && (routeActive || directBlocked)) {
            int plannerHorizon = config.companionPlannerHorizon.get();
            int plannerReplanTicks = config.companionPlannerReplanTicks.get();
            if (planner.shouldPlan(requested, tickCount, plannerReplanTicks)) {
                planner.plan(youkai.fltNav, youkai, requested, tickCount, plannerReplanTicks,
                        config.companionPlannerSearchNodes.get(), plannerHorizon);
            }
            Vec3 waypoint = planner.waypoint(youkai);
            if (waypoint != null) {
                double wx = waypoint.x - youkai.getX(), wy = waypoint.y - youkai.getY(), wz = waypoint.z - youkai.getZ();
                Point routeVelocity = steer(new Point(wx, wy, wz), new Point(curVel.x, curVel.y, curVel.z), settings.maxSpeed());
                velocity = new Vec3(routeVelocity.x(), routeVelocity.y(), routeVelocity.z());
                if (!validSweep(velocity)) {
                    planner.invalidate(tickCount);
                    velocity = curVel.lengthSqr() > 1.0E-6 ? curVel.scale(0.35) : Vec3.ZERO;
                }
            } else {
                velocity = curVel.lengthSqr() > 1.0E-6 ? curVel.scale(0.35) : Vec3.ZERO;
                youkai.getPersistentData().putString("yh_companion_flight_status", "BLOCKED_TARGET");
            }
        }

        if (dist3D > 0.8) {
            double nextVx = velocity.x, nextVz = velocity.z;
            youkai.setDeltaMovement(velocity);
            youkai.hasImpulse = true;

            float moveYaw = (float) (-Math.toDegrees(Math.atan2(nextVx, nextVz)));
            if (settings.facingMode() == FacingMode.TRAVEL && Float.isFinite(moveYaw)) {
                youkai.setYRot(moveYaw);
                youkai.setYHeadRot(moveYaw);
                youkai.setYBodyRot(moveYaw);
                youkai.getLookControl().setLookAt(youkai.getX() + nextVx * 16,
                        youkai.getEyeY(), youkai.getZ() + nextVz * 16, 360, 360);
            }
        } else {
            youkai.setDeltaMovement(velocity);
            youkai.hasImpulse = true;
            if (settings.facingMode() == FacingMode.TRAVEL) youkai.getLookControl().setLookAt(companionPlayer, 15.0F, 15.0F);
        }
        applyFacing();
    }

    private boolean validTarget(Point target) {
        var level = youkai.level();
        if (!Double.isFinite(target.x()) || !Double.isFinite(target.y()) || !Double.isFinite(target.z())
                || Math.abs(target.x()) >= 30_000_000 || Math.abs(target.z()) >= 30_000_000) return false;
        if (settings.targetMode() == TargetMode.ABSOLUTE
                && !settings.dimension().equals(level.dimension().location().toString())) return false;
        var box = youkai.getBoundingBox().move(new Vec3(target.x(), target.y(), target.z()).subtract(youkai.position()));
        if (box.minY < level.getMinBuildHeight() || box.maxY > level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(box)) return false;
        return level.hasChunkAt(BlockPos.containing(box.minX, box.minY, box.minZ))
                && level.hasChunkAt(BlockPos.containing(box.maxX, box.maxY, box.maxZ));
    }

    private boolean validEndpoint(Point target) {
        var level = youkai.level();
        var box = youkai.getBoundingBox().move(new Vec3(target.x(), target.y(), target.z()).subtract(youkai.position()));
        return level.noCollision(youkai, box);
    }

    private boolean validSweep(Vec3 displacement) {
        var level = youkai.level();
        var box = youkai.getBoundingBox().expandTowards(displacement.x(), displacement.y(), displacement.z());
        if (box.minY < level.getMinBuildHeight() || box.maxY > level.getMaxBuildHeight() || !level.getWorldBorder().isWithinBounds(box)) return false;
        return level.noCollision(youkai, box);
    }

    private void releaseSlot() {
        if (slotRegistry != null) slotRegistry.release(slotPlayer, youkai.getUUID());
        slotRegistry = null;
        slotPlayer = null;
        slot = -1;
    }

    private void applyFacing() {
        if (settings.facingMode() == FacingMode.TRAVEL) return;
        float yaw = settings.facingMode() == FacingMode.KEEP ? youkai.getYRot() : sampledYaw;
        float pitch = settings.facingMode() == FacingMode.KEEP ? youkai.getXRot() : sampledPitch;
        youkai.setYRot(yaw); youkai.setYHeadRot(yaw); youkai.setYBodyRot(yaw); youkai.setXRot(pitch);
        Vec3 direction = Vec3.directionFromRotation(pitch, yaw).scale(16);
        youkai.getLookControl().setLookAt(youkai.getX() + direction.x, youkai.getEyeY() + direction.y,
                youkai.getZ() + direction.z, 360, 360);
    }

    private boolean validCompanionPlayer(Player player) {
        return player != null && player.isAlive() && !player.isSpectator() && youkai.level() == player.level();
    }

    protected Player findCompanionPlayer(UUID id) {
        return youkai.level().getPlayerByUUID(id);
    }
}
