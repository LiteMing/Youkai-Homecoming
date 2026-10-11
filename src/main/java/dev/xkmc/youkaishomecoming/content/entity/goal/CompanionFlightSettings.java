package dev.xkmc.youkaishomecoming.content.entity.goal;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.nbt.CompoundTag;
import java.util.Optional;
import java.util.Set;
import static dev.xkmc.youkaishomecoming.content.entity.goal.CompanionFlightMath.*;

public record CompanionFlightSettings(TargetMode targetMode, double x, double y, double z,
        String dimension, double maxSpeed, int updateIntervalTicks, MovementMode movementMode, FacingMode facingMode) {
    public static final String NBT_KEY = "yh_companion_flight";
    public static final String OWNER_KEY = "owner";
    public static final String PLAYER_UUID_KEY = "player_uuid";
    public static final String SETTINGS_KEY = "settings";
    private static final Gson GSON = new Gson();
    private static final Set<String> FIELDS = Set.of("target_mode", "x", "y", "z", "dimension", "max_speed",
            "update_interval_ticks", "movement_mode", "facing_mode");

    public CompanionFlightSettings {
        if (targetMode == null || movementMode == null || facingMode == null || dimension == null
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(maxSpeed) || maxSpeed < 0.01 || maxSpeed > 4
                || updateIntervalTicks < 1 || updateIntervalTicks > 200)
            throw new IllegalArgumentException("Invalid companion flight settings");
        if (targetMode != TargetMode.ABSOLUTE && (Math.abs(x) > 128 || Math.abs(y) > 128 || Math.abs(z) > 128))
            throw new IllegalArgumentException("Player offsets must be within -128..128 blocks");
        if (targetMode == TargetMode.ABSOLUTE && dimension.isBlank())
            throw new IllegalArgumentException("Absolute target requires a dimension");
    }

    public static CompanionFlightSettings defaults() {
        var c = YHModConfig.COMMON;
        return new CompanionFlightSettings(c.companionTargetMode.get(), c.companionOffsetX.get(), c.companionOffsetY.get(),
                c.companionOffsetZ.get(), "", c.companionMaxSpeed.get(), c.companionUpdateIntervalTicks.get(),
                c.companionMovementMode.get(), c.companionFacingMode.get());
    }

    public static CompanionFlightSettings parse(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        if (!FIELDS.containsAll(obj.keySet())) throw new IllegalArgumentException("Unknown companion flight field");
        for (var field : obj.entrySet()) {
            if (!field.getValue().isJsonPrimitive()) throw new IllegalArgumentException("Expected scalar: " + field.getKey());
            var primitive = field.getValue().getAsJsonPrimitive();
            boolean numeric = Set.of("x", "y", "z", "max_speed", "update_interval_ticks").contains(field.getKey());
            if (numeric ? !primitive.isNumber() : !primitive.isString()) throw new IllegalArgumentException("Wrong field type: " + field.getKey());
        }
        var d = defaults();
        var mode = obj.has("target_mode") ? TargetMode.valueOf(obj.get("target_mode").getAsString()) : d.targetMode;
        if (mode == TargetMode.ABSOLUTE && (!obj.has("x") || !obj.has("y") || !obj.has("z") || !obj.has("dimension")))
            throw new IllegalArgumentException("Absolute target requires dimension and all three coordinates");
        double interval = obj.has("update_interval_ticks") ? obj.get("update_interval_ticks").getAsDouble() : d.updateIntervalTicks;
        if (interval != Math.rint(interval)) throw new IllegalArgumentException("Update interval must be an integer");
        return new CompanionFlightSettings(mode, number(obj, "x", d.x), number(obj, "y", d.y), number(obj, "z", d.z),
                obj.has("dimension") ? obj.get("dimension").getAsString() : "", number(obj, "max_speed", d.maxSpeed),
                (int) interval, obj.has("movement_mode") ? MovementMode.valueOf(obj.get("movement_mode").getAsString()) : d.movementMode,
                obj.has("facing_mode") ? FacingMode.valueOf(obj.get("facing_mode").getAsString()) : d.facingMode);
    }

    private static double number(JsonObject obj, String key, double fallback) { return obj.has(key) ? obj.get(key).getAsDouble() : fallback; }
    public String json() {
        var obj = new JsonObject();
        obj.addProperty("target_mode", targetMode.name()); obj.addProperty("x", x); obj.addProperty("y", y); obj.addProperty("z", z);
        obj.addProperty("dimension", dimension); obj.addProperty("max_speed", maxSpeed); obj.addProperty("update_interval_ticks", updateIntervalTicks);
        obj.addProperty("movement_mode", movementMode.name()); obj.addProperty("facing_mode", facingMode.name());
        return GSON.toJson(obj);
    }
    /** Returns only an explicitly configured, valid YH binding. */
    public static Optional<Configured> configured(CompoundTag data) {
        if (!data.contains(NBT_KEY, 10)) return Optional.empty();
        var binding = data.getCompound(NBT_KEY);
        if (!binding.hasUUID(PLAYER_UUID_KEY) || !binding.contains(SETTINGS_KEY, 8)
                || binding.getString(SETTINGS_KEY).isBlank()) return Optional.empty();
        try {
            String owner = binding.getString(OWNER_KEY);
            if (owner.isBlank()) return Optional.empty();
            return Optional.of(new Configured(owner, binding.getUUID(PLAYER_UUID_KEY),
                    parse(binding.getString(SETTINGS_KEY))));
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
    }

    public record Configured(String owner, java.util.UUID playerUuid, CompanionFlightSettings settings) {
        public Configured {
            if (owner == null || owner.isBlank() || playerUuid == null || settings == null) throw new IllegalArgumentException("Invalid companion binding");
        }
    }
}
