package dev.xkmc.youkaishomecoming.compat.kubejs;

import dev.xkmc.youkaishomecoming.content.entity.goal.CompanionFlightSettings;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import static dev.xkmc.youkaishomecoming.content.entity.goal.CompanionFlightMath.TargetMode;

/** Trusted server-script configuration. Activation remains owned by the caller. */
public final class YHCompanionApi {
    public static final int API_VERSION = 1;
    private YHCompanionApi() {}
    public static int getApiVersion() { return API_VERSION; }
    public static String getDefaultsJson() { return CompanionFlightSettings.defaults().json(); }
    public static String getConfigJson(Entity entity) { return CompanionFlightSettings.read(entity.getPersistentData()).json(); }
    public static String getStatus(Entity entity) { return entity.getPersistentData().getString("yh_companion_flight_status"); }

    /** Throws for invalid settings, returns false for a conflicting owner; no partial writes. */
    public static boolean configure(Entity entity, String owner, String json) {
        requireServer(entity, owner);
        var settings = CompanionFlightSettings.parse(json);
        if (settings.targetMode() == TargetMode.ABSOLUTE && !settings.dimension().equals(entity.level().dimension().location().toString()))
            throw new IllegalArgumentException("Absolute target must use the entity's current dimension");
        var data = entity.getPersistentData();
        var current = data.getCompound(CompanionFlightSettings.NBT_KEY);
        if (!current.getString("owner").isEmpty() && !current.getString("owner").equals(owner)) return false;
        var tag = new CompoundTag(); tag.putString("owner", owner); tag.putString("settings", settings.json());
        data.put(CompanionFlightSettings.NBT_KEY, tag); return true;
    }

    public static boolean clear(Entity entity, String owner) {
        requireServer(entity, owner);
        var data = entity.getPersistentData();
        if (!data.getCompound(CompanionFlightSettings.NBT_KEY).getString("owner").equals(owner)) return false;
        data.remove(CompanionFlightSettings.NBT_KEY); return true;
    }

    private static void requireServer(Entity entity, String owner) {
        if (!(entity instanceof YoukaiEntity) || entity.isRemoved() || entity.level().isClientSide || entity.getServer() == null || !entity.getServer().isSameThread())
            throw new IllegalArgumentException("Companion configuration requires a Youkai on the server thread");
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("A nonblank configuration owner is required");
    }
}
