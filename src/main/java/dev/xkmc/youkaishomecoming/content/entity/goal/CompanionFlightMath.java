package dev.xkmc.youkaishomecoming.content.entity.goal;

/** Deterministic target geometry shared by the Goal and narrow JVM checks. */
public final class CompanionFlightMath {
    public enum TargetMode { LEGACY, PLAYER_LOCAL, PLAYER_WORLD, ABSOLUTE }
    public enum MovementMode { STEER, LOCK_TO_TARGET }
    public enum FacingMode { TRAVEL, PLAYER_BODY, PLAYER_VIEW, KEEP }
    public record Point(double x, double y, double z) {}
    private CompanionFlightMath() {}

    /** Local X is right, Y is up, Z is forward; yaw zero faces world +Z. */
    public static Point target(TargetMode mode, Point player, float yaw, Point configured) {
        if (mode == TargetMode.ABSOLUTE) return configured;
        if (mode == TargetMode.LEGACY) {
            double radians = Math.toRadians(yaw + 135.0);
            return new Point(player.x + Math.sin(radians) * 2.4, player.y + 1.6,
                    player.z - Math.cos(radians) * 2.4);
        }
        if (mode == TargetMode.PLAYER_WORLD)
            return new Point(player.x + configured.x, player.y + configured.y, player.z + configured.z);
        double radians = Math.toRadians(yaw), sin = Math.sin(radians), cos = Math.cos(radians);
        // Minecraft yaw zero faces +Z; the player's right is then world -X.
        return new Point(player.x - configured.x * cos - configured.z * sin,
                player.y + configured.y, player.z - configured.x * sin + configured.z * cos);
    }

    public static boolean sampleNow(int tick, int lastSample, int interval, boolean changed) {
        return changed || lastSample < 0 || tick - lastSample >= interval;
    }

    public static boolean dailyFollow(TargetMode targetMode, MovementMode movementMode) {
        return targetMode == TargetMode.LEGACY && movementMode == MovementMode.STEER;
    }

    /** Slot zero exactly preserves the original orbit; subsequent pairs form stable rows. */
    public static Point slotTarget(Point player, float yaw, int slot, double spacing) {
        if (slot == 0) return target(TargetMode.LEGACY, player, yaw, new Point(0, 0, 0));
        double side = Math.sqrt(2) * 1.2;
        return target(TargetMode.PLAYER_LOCAL, player, yaw,
                new Point(slot % 2 == 0 ? -side : side, 1.6, side - (slot / 2) * spacing));
    }

    /** Retract without overshooting the player or dividing by a zero-length vector. */
    public static Point retract(Point target, Point player, double distance) {
        double dx = target.x - player.x, dy = target.y - player.y, dz = target.z - player.z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= distance) return player;
        double scale = (length - distance) / length;
        return new Point(player.x + dx * scale, player.y + dy * scale, player.z + dz * scale);
    }

    /** Preserve legacy acceleration while applying a strict magnitude limit. */
    public static Point steer(Point delta, Point velocity, double maxSpeed) {
        double distance = Math.sqrt(delta.x * delta.x + delta.y * delta.y + delta.z * delta.z);
        if (distance == 0) return new Point(0, 0, 0);
        double speed = Math.min(maxSpeed, Math.max(Math.min(0.30, maxSpeed), distance * 0.20));
        double x = velocity.x * 0.30 + delta.x / distance * speed * 0.70;
        double y = velocity.y * 0.30 + delta.y / distance * speed * 0.70;
        double z = velocity.z * 0.30 + delta.z / distance * speed * 0.70;
        double magnitude = Math.sqrt(x * x + y * y + z * z);
        double scale = magnitude > speed ? speed / magnitude : 1;
        return new Point(x * scale, y * scale, z * scale);
    }
}
