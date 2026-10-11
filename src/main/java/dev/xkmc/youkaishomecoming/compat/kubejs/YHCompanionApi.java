package dev.xkmc.youkaishomecoming.compat.kubejs;

import dev.xkmc.youkaishomecoming.content.entity.goal.CompanionFlightSettings;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import java.util.UUID;
import static dev.xkmc.youkaishomecoming.content.entity.goal.CompanionFlightMath.TargetMode;

/** Explicit server-thread companion activation owned by this mod. */
public final class YHCompanionApi {
    public static final int API_VERSION = 2;
    private YHCompanionApi() {}
    public static int getApiVersion() { return API_VERSION; }
    public static String getDefaultsJson() { return CompanionFlightSettings.defaults().json(); }
    public static String getConfigJson(Entity entity) { return CompanionFlightSettings.configured(entity.getPersistentData()).map(binding -> binding.settings().json()).orElseGet(() -> CompanionFlightSettings.defaults().json()); }
    public static String getStatus(Entity entity) { return entity.getPersistentData().getString("yh_companion_flight_status"); }
    public static boolean isActive(Entity entity) { return CompanionFlightSettings.configured(entity.getPersistentData()).isPresent(); }
    public static boolean isRunning(Entity entity) {
        if (!isActive(entity)) return false;
        String status = getStatus(entity);
        return "ACTIVE".equals(status) || "BLOCKED_TARGET".equals(status);
    }

    /** Throws for invalid settings, returns false for a conflicting owner; no partial writes. */
    public static boolean activate(Entity entity, String owner, UUID playerUuid, String json) {
        requireServer(entity, owner);
        if (playerUuid == null) throw new IllegalArgumentException("A companion player UUID is required");
        var settings = CompanionFlightSettings.parse(json);
        if (settings.targetMode() == TargetMode.ABSOLUTE && !settings.dimension().equals(entity.level().dimension().location().toString()))
            throw new IllegalArgumentException("Absolute target must use the entity's current dimension");
        var data = entity.getPersistentData();
        var current = CompanionFlightSettings.configured(data);
        if (current.isPresent() && !current.get().owner().equals(owner)) return false;
        var tag = new CompoundTag();
        tag.putString(CompanionFlightSettings.OWNER_KEY, owner);
        tag.putUUID(CompanionFlightSettings.PLAYER_UUID_KEY, playerUuid);
        tag.putString(CompanionFlightSettings.SETTINGS_KEY, settings.json());
        data.put(CompanionFlightSettings.NBT_KEY, tag);
        data.putString("yh_companion_flight_status", "PENDING");
        return true;
    }

    public static boolean deactivate(Entity entity, String owner) {
        requireServer(entity, owner);
        var data = entity.getPersistentData();
        var current = CompanionFlightSettings.configured(data);
        if (current.isEmpty() || !current.get().owner().equals(owner)) return false;
        data.remove(CompanionFlightSettings.NBT_KEY);
        data.putString("yh_companion_flight_status", "INACTIVE");
        return true;
    }

    private static void requireServer(Entity entity, String owner) {
        if (!(entity instanceof YoukaiEntity) || entity.isRemoved() || entity.level().isClientSide || entity.getServer() == null || !entity.getServer().isSameThread())
            throw new IllegalArgumentException("Companion configuration requires a Youkai on the server thread");
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("A nonblank companion owner is required");
    }
}
