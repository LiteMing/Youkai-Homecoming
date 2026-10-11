package dev.xkmc.youkaishomecoming.content.entity.goal;

import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import static dev.xkmc.youkaishomecoming.content.entity.goal.CompanionFlightMath.*;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Youkai native 3D companion flight goal.
 */
public class YoukaiFlyCompanionGoal<T extends YoukaiEntity> extends Goal {

    public static final String TAG_ADVENTURE = "cchat_reimu_adventure";
    public static final String TAG_FLIGHT_COMPANION = "cchat_flight_companion";
    public static final String NBT_COMPANION_UUID = "cchat_companion_player";

    protected final T youkai;
    protected Player companionPlayer;
    protected int tickCount = 0;
    private CompanionFlightSettings settings;
    private String settingsJson;
    private Vec3 sampledTarget;
    private int lastSample = -1;
    private float sampledYaw, sampledPitch;

    public YoukaiFlyCompanionGoal(T youkai) {
        this.youkai = youkai;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (youkai.getTarget() != null && youkai.getTarget().isAlive()) {
            return false;
        }
        if (!isCompanionModeActive()) {
            return false;
        }
        companionPlayer = findCompanionPlayer();
        return companionPlayer != null && companionPlayer.isAlive() && !companionPlayer.isSpectator();
    }

    @Override
    public boolean canContinueToUse() {
        if (youkai.getTarget() != null && youkai.getTarget().isAlive()) {
            return false;
        }
        if (!isCompanionModeActive()) {
            return false;
        }
        var binding = youkai.getPersistentData();
        if (binding.contains(NBT_COMPANION_UUID) && (!binding.hasUUID(NBT_COMPANION_UUID)
                || companionPlayer == null || !binding.getUUID(NBT_COMPANION_UUID).equals(companionPlayer.getUUID()))) return false;
        return companionPlayer != null && companionPlayer.isAlive() && !companionPlayer.isSpectator()
                && youkai.level() == companionPlayer.level();
    }

    @Override
    public void start() {
        tickCount = 0;
        settings = null;
        sampledTarget = null;
        lastSample = -1;
        youkai.setFlying();
        youkai.getNavigation().stop();
    }

    @Override
    public void stop() {
        companionPlayer = null;
        sampledTarget = null;
        youkai.getPersistentData().putString("yh_companion_flight_status", "INACTIVE");
        youkai.setDeltaMovement(Vec3.ZERO);
        if (!isCompanionModeActive()) {
            youkai.setWalking();
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (companionPlayer == null) return;
        tickCount++;

        if (!youkai.isFlying()) {
            youkai.setFlying();
        }
        // This Goal owns movement. Prevent FlyingMoveControl from adding a
        // second acceleration or changing the configured facing after tick().
        youkai.getNavigation().stop();
        youkai.getMoveControl().setWantedPosition(youkai.getX(), youkai.getY(), youkai.getZ(), 0);
        youkai.setSpeed(0); youkai.setXxa(0); youkai.setYya(0); youkai.setZza(0);

        String json = youkai.getPersistentData().getCompound(CompanionFlightSettings.NBT_KEY).getString("settings");
        CompanionFlightSettings next = json.isBlank() ? CompanionFlightSettings.defaults()
                : settings != null && json.equals(settingsJson) ? settings : CompanionFlightSettings.read(youkai.getPersistentData());
        boolean changed = !next.equals(settings);
        settings = next;
        settingsJson = json;
        if (sampleNow(tickCount, lastSample, settings.updateIntervalTicks(), changed)) {
            float yaw = settings.targetMode() == TargetMode.LEGACY ? companionPlayer.getYRot() : companionPlayer.yBodyRot;
            Point target = target(settings.targetMode(), new Point(companionPlayer.getX(), companionPlayer.getY(), companionPlayer.getZ()),
                    yaw, new Point(settings.x(), settings.y(), settings.z()));
            sampledTarget = new Vec3(target.x(), target.y(), target.z());
            sampledYaw = settings.facingMode() == FacingMode.PLAYER_BODY ? companionPlayer.yBodyRot : companionPlayer.getYRot();
            sampledPitch = settings.facingMode() == FacingMode.PLAYER_VIEW ? companionPlayer.getXRot() : 0;
            lastSample = tickCount;
        }
        if (!validEndpoint(sampledTarget)) {
            youkai.getMoveControl().setWantedPosition(youkai.getX(), youkai.getY(), youkai.getZ(), 0);
            youkai.setDeltaMovement(Vec3.ZERO);
            youkai.getPersistentData().putString("yh_companion_flight_status", "BLOCKED_TARGET");
            return;
        }
        youkai.getPersistentData().putString("yh_companion_flight_status", "ACTIVE");
        if (settings.movementMode() == MovementMode.LOCK_TO_TARGET || youkai.position().distanceToSqr(sampledTarget) > 50 * 50) {
            youkai.getNavigation().stop();
            youkai.teleportTo(sampledTarget.x, sampledTarget.y, sampledTarget.z);
            youkai.getMoveControl().setWantedPosition(sampledTarget.x, sampledTarget.y, sampledTarget.z, 0);
            youkai.setDeltaMovement(Vec3.ZERO);
            youkai.hasImpulse = true;
            applyFacing();
            return;
        }
        double targetX = sampledTarget.x, targetY = sampledTarget.y, targetZ = sampledTarget.z;
        double dx = targetX - youkai.getX(), dy = targetY - youkai.getY(), dz = targetZ - youkai.getZ();
        double dist3D = Math.sqrt(dx * dx + dy * dy + dz * dz);

        Vec3 curVel = youkai.getDeltaMovement();

        if (dist3D > 0.8) {
            Point velocity = steer(new Point(dx, dy, dz), new Point(curVel.x, curVel.y, curVel.z), settings.maxSpeed());
            double nextVx = velocity.x(), nextVy = velocity.y(), nextVz = velocity.z();

            youkai.setDeltaMovement(new Vec3(nextVx, nextVy, nextVz));
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
            double bobVy = Math.sin(tickCount * 0.15) * 0.02;
            Vec3 velocity = new Vec3(curVel.x * 0.40, curVel.y * 0.40 + bobVy, curVel.z * 0.40);
            if (velocity.length() > settings.maxSpeed()) velocity = velocity.normalize().scale(settings.maxSpeed());
            youkai.setDeltaMovement(velocity);
            youkai.hasImpulse = true;
            if (settings.facingMode() == FacingMode.TRAVEL) youkai.getLookControl().setLookAt(companionPlayer, 15.0F, 15.0F);
        }
        applyFacing();
    }

    private boolean validEndpoint(Vec3 target) {
        var level = youkai.level();
        if (target == null || !Double.isFinite(target.x) || !Double.isFinite(target.y) || !Double.isFinite(target.z)
                || Math.abs(target.x) >= 30_000_000 || Math.abs(target.z) >= 30_000_000) return false;
        if (settings.targetMode() == TargetMode.ABSOLUTE && !settings.dimension().equals(level.dimension().location().toString())) return false;
        var box = youkai.getBoundingBox().move(target.subtract(youkai.position()));
        if (box.minY < level.getMinBuildHeight() || box.maxY > level.getMaxBuildHeight() || !level.getWorldBorder().isWithinBounds(box)) return false;
        // Do not force-load a chunk to follow a player or reach an absolute point.
        if (!level.hasChunkAt(BlockPos.containing(box.minX, box.minY, box.minZ))
                || !level.hasChunkAt(BlockPos.containing(box.maxX, box.maxY, box.maxZ))) return false;
        return level.noCollision(youkai, box);
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

    protected boolean isCompanionModeActive() {
        if (youkai.getTags().contains(TAG_ADVENTURE) || youkai.getTags().contains(TAG_FLIGHT_COMPANION)) {
            return true;
        }
        var data = youkai.getPersistentData();
        return data.contains(NBT_COMPANION_UUID) || "ADVENTURE".equals(data.getString("cchat_reimu_outer_mode"));
    }

    protected Player findCompanionPlayer() {
        var data = youkai.getPersistentData();
        if (data.hasUUID(NBT_COMPANION_UUID)) {
            UUID id = data.getUUID(NBT_COMPANION_UUID);
            Player p = youkai.level().getPlayerByUUID(id);
            return p != null && p.isAlive() && !p.isSpectator() ? p : null;
        }
        if (data.contains(NBT_COMPANION_UUID)) return null;
        return youkai.level().getNearestPlayer(youkai, 64.0);
    }
}
