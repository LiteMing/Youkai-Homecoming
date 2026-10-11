package dev.xkmc.youkaishomecoming.content.entity.goal;

import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import static dev.xkmc.youkaishomecoming.content.entity.goal.CompanionFlightMath.*;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Youkai native 3D companion flight goal.
 */
public class YoukaiFlyCompanionGoal<T extends YoukaiEntity> extends Goal {

    public static final String TAG_ADVENTURE = "cchat_reimu_adventure";
    public static final String TAG_FLIGHT_COMPANION = "cchat_flight_companion";
    public static final String NBT_COMPANION_UUID = "cchat_companion_player";
    // Values contain UUIDs only: unloaded levels and entities are not retained.
    private static final Map<Level, CompanionFlightSlots> SLOTS = new WeakHashMap<>();

    protected final T youkai;
    protected Player companionPlayer;
    protected int tickCount = 0;
    private CompanionFlightSettings settings;
    private String settingsJson;
    private Vec3 sampledTarget;
    private Point sampledPlayer, sampledOverhead;
    private int lastSample = -1;
    private float sampledYaw, sampledPitch;
    private final CompanionFlightAvoidance avoidance = new CompanionFlightAvoidance();
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
        avoidance.reset();
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
        var config = YHModConfig.COMMON;
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
        if (changed) avoidance.reset();
        if (sampleNow(tickCount, lastSample, settings.updateIntervalTicks(), changed)) {
            float yaw = settings.targetMode() == TargetMode.LEGACY ? companionPlayer.getYRot() : companionPlayer.yBodyRot;
            sampledPlayer = point(companionPlayer.position());
            sampledOverhead = new Point(sampledPlayer.x(), sampledPlayer.y() + companionPlayer.getBbHeight(), sampledPlayer.z());
            Point target = slot >= 0 ? slotTarget(sampledPlayer, yaw, slot, config.companionSlotSpacing.get())
                    : target(settings.targetMode(), sampledPlayer, yaw, new Point(settings.x(), settings.y(), settings.z()));
            sampledTarget = vector(target);
            sampledYaw = settings.facingMode() == FacingMode.PLAYER_BODY ? companionPlayer.yBodyRot : companionPlayer.getYRot();
            sampledPitch = settings.facingMode() == FacingMode.PLAYER_VIEW ? companionPlayer.getXRot() : 0;
            lastSample = tickCount;
        }
        var budget = new CompanionFlightAvoidance.Budget(p -> validEndpoint(vector(p)), p -> validSweep(vector(p)));
        var parameters = new CompanionFlightAvoidance.Parameters(config.companionLookahead.get(), config.companionDetourSpeed.get(),
                config.companionDetourHoldTicks.get(), config.companionTargetRetraction.get(), config.companionTargetRecoveryTicks.get());
        boolean localAvoidance = daily && config.companionLocalAvoidance.get();
        Point resolved = localAvoidance ? avoidance.resolveTarget(point(sampledTarget), sampledPlayer, sampledOverhead,
                tickCount, parameters, budget) : budget.endpoint(point(sampledTarget)) ? point(sampledTarget) : null;
        if (resolved == null) {
            youkai.getMoveControl().setWantedPosition(youkai.getX(), youkai.getY(), youkai.getZ(), 0);
            youkai.setDeltaMovement(Vec3.ZERO);
            youkai.getPersistentData().putString("yh_companion_flight_status", "BLOCKED_TARGET");
            return;
        }
        Vec3 effectiveTarget = vector(resolved);
        youkai.getPersistentData().putString("yh_companion_flight_status", "ACTIVE");
        if (settings.movementMode() == MovementMode.LOCK_TO_TARGET || youkai.position().distanceToSqr(effectiveTarget) > 50 * 50) {
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
            velocity = vector(steer(new Point(dx, dy, dz), point(curVel), settings.maxSpeed()));
        } else {
            double bobVy = Math.sin(tickCount * 0.15) * 0.02;
            velocity = new Vec3(curVel.x * 0.40, curVel.y * 0.40 + bobVy, curVel.z * 0.40);
            if (velocity.length() > settings.maxSpeed()) velocity = velocity.normalize().scale(settings.maxSpeed());
        }
        if (localAvoidance) {
            // Do not probe past a nearby destination, especially during the idle bob.
            parameters = new CompanionFlightAvoidance.Parameters(dist3D > 0.8 ? Math.min(parameters.lookahead(), dist3D) : 0,
                    parameters.detourSpeed(), parameters.holdTicks(), parameters.retractDistance(), parameters.recoveryTicks());
            velocity = vector(avoidance.avoid(point(velocity), point(curVel), parameters, budget, settings.maxSpeed()));
        }
        youkai.setDeltaMovement(velocity);
        youkai.hasImpulse = true;
        if (dist3D > 0.8 && (!daily || velocity.horizontalDistanceSqr() > 1e-9)) {
            float moveYaw = (float) (-Math.toDegrees(Math.atan2(velocity.x, velocity.z)));
            if (settings.facingMode() == FacingMode.TRAVEL && Float.isFinite(moveYaw)) {
                youkai.setYRot(moveYaw);
                youkai.setYHeadRot(moveYaw);
                youkai.setYBodyRot(moveYaw);
                youkai.getLookControl().setLookAt(youkai.getX() + velocity.x * 16,
                        youkai.getEyeY(), youkai.getZ() + velocity.z * 16, 360, 360);
            }
        } else if (dist3D <= 0.8) {
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
        return validBox(box) && level.noCollision(youkai, box);
    }

    private boolean validSweep(Vec3 displacement) {
        // Check the whole short motion interval, not only a shifted endpoint box.
        AABB box = youkai.getBoundingBox().expandTowards(displacement);
        return validBox(box) && youkai.level().noCollision(youkai, box);
    }

    private boolean validBox(AABB box) {
        var level = youkai.level();
        if (box.minY < level.getMinBuildHeight() || box.maxY > level.getMaxBuildHeight() || !level.getWorldBorder().isWithinBounds(box)) return false;
        // Do not force-load a chunk to follow a player or reach an absolute point.
        for (int x = Mth.floor(box.minX) >> 4; x <= (Mth.floor(box.maxX) >> 4); x++) {
            for (int z = Mth.floor(box.minZ) >> 4; z <= (Mth.floor(box.maxZ) >> 4); z++) {
                if (!level.hasChunkAt(new BlockPos(x << 4, Mth.floor(box.minY), z << 4))) return false;
            }
        }
        return true;
    }

    private static Point point(Vec3 vector) { return new Point(vector.x, vector.y, vector.z); }
    private static Vec3 vector(Point point) { return new Vec3(point.x(), point.y(), point.z()); }

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
