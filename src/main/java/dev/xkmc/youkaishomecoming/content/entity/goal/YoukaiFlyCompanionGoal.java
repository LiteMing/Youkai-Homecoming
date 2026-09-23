package dev.xkmc.youkaishomecoming.content.entity.goal;

import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

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
        return companionPlayer != null && companionPlayer.isAlive() && !companionPlayer.isSpectator()
                && youkai.level() == companionPlayer.level();
    }

    @Override
    public void start() {
        tickCount = 0;
        youkai.setFlying();
    }

    @Override
    public void stop() {
        companionPlayer = null;
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

        double pX = companionPlayer.getX();
        double pY = companionPlayer.getY();
        double pZ = companionPlayer.getZ();

        double curX = youkai.getX();
        double curY = youkai.getY();
        double curZ = youkai.getZ();

        double distSq = youkai.distanceToSqr(companionPlayer);

        if (distSq > 50.0 * 50.0) {
            youkai.teleportTo(pX + 1.5, pY + 1.5, pZ + 1.5);
            youkai.setDeltaMovement(Vec3.ZERO);
            return;
        }

        float playerYaw = companionPlayer.getYRot();
        double offsetRad = Math.toRadians(playerYaw + 135.0);
        double orbitDist = 2.4;

        double targetX = pX + Math.sin(offsetRad) * orbitDist;
        double targetY = pY + 1.6;
        double targetZ = pZ - Math.cos(offsetRad) * orbitDist;

        double dx = targetX - curX;
        double dy = targetY - curY;
        double dz = targetZ - curZ;
        double dist3D = Math.sqrt(dx * dx + dy * dy + dz * dz);

        double speedMod = dist3D > 8.0 ? 1.5 : (dist3D > 4.0 ? 1.25 : 1.0);
        youkai.getMoveControl().setWantedPosition(targetX, targetY, targetZ, speedMod);

        Vec3 curVel = youkai.getDeltaMovement();

        if (dist3D > 0.8) {
            double targetSpeed = Math.min(0.85, Math.max(0.30, dist3D * 0.20));
            double desiredVx = (dx / dist3D) * targetSpeed;
            double desiredVy = (dy / dist3D) * targetSpeed;
            double desiredVz = (dz / dist3D) * targetSpeed;

            double nextVx = curVel.x * 0.30 + desiredVx * 0.70;
            double nextVy = curVel.y * 0.30 + desiredVy * 0.70;
            double nextVz = curVel.z * 0.30 + desiredVz * 0.70;

            double nextSpeed = Math.sqrt(nextVx * nextVx + nextVy * nextVy + nextVz * nextVz);
            if (nextSpeed > targetSpeed) {
                double scale = targetSpeed / nextSpeed;
                nextVx *= scale;
                nextVy *= scale;
                nextVz *= scale;
            }

            youkai.setDeltaMovement(new Vec3(nextVx, nextVy, nextVz));
            youkai.hasImpulse = true;

            float moveYaw = (float) (-Math.toDegrees(Math.atan2(nextVx, nextVz)));
            if (Float.isFinite(moveYaw)) {
                youkai.setYRot(moveYaw);
                youkai.setYHeadRot(moveYaw);
                youkai.setYBodyRot(moveYaw);
            }
        } else {
            double bobVy = Math.sin(tickCount * 0.15) * 0.02;
            youkai.setDeltaMovement(new Vec3(curVel.x * 0.40, curVel.y * 0.40 + bobVy, curVel.z * 0.40));
            youkai.hasImpulse = true;
            youkai.getLookControl().setLookAt(companionPlayer, 15.0F, 15.0F);
        }
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
            if (p != null && p.isAlive()) return p;
        }
        return youkai.level().getNearestPlayer(youkai, 64.0);
    }
}
