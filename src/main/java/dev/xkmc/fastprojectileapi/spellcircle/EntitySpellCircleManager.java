package dev.xkmc.fastprojectileapi.spellcircle;

import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class EntitySpellCircleManager {

	private static final String TAG = YoukaisHomecoming.MODID + ":spell_circle_override";
	private static final String KEY_ENABLED = "Enabled";
	private static final String KEY_CIRCLE = "Circle";
	private static final String KEY_SIZE = "Size";
	private static final float DEFAULT_SIZE = 1.0f;

	private static final Map<Integer, State> CLIENT_OVERRIDES = new HashMap<>();
	private static final Map<UUID, State> SERVER_SYNCED = new HashMap<>();

	public record State(String uuid, boolean enabled, @Nullable ResourceLocation circle, float size) {
	}

	public static boolean setOverride(Entity entity, ResourceLocation circle, float size) {
		State next = new State(entity.getUUID().toString(), true, circle, sanitizeSize(size));
		if (next.equals(getServerOverride(entity))) {
			return false;
		}
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(KEY_ENABLED, true);
		tag.putString(KEY_CIRCLE, circle.toString());
		tag.putFloat(KEY_SIZE, next.size());
		entity.getPersistentData().put(TAG, tag);
		syncTracking(entity);
		return true;
	}

	public static boolean setHidden(Entity entity) {
		State next = new State(entity.getUUID().toString(), false, null, DEFAULT_SIZE);
		if (next.equals(getServerOverride(entity))) {
			return false;
		}
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(KEY_ENABLED, false);
		tag.putFloat(KEY_SIZE, DEFAULT_SIZE);
		entity.getPersistentData().put(TAG, tag);
		syncTracking(entity);
		return true;
	}

	public static boolean clearOverride(Entity entity) {
		if (!entity.getPersistentData().contains(TAG)) {
			return false;
		}
		entity.getPersistentData().remove(TAG);
		syncTracking(entity);
		return true;
	}

	@Nullable
	public static State getServerOverride(Entity entity) {
		CompoundTag root = entity.getPersistentData();
		if (!root.contains(TAG, Tag.TAG_COMPOUND)) {
			return null;
		}
		CompoundTag tag = root.getCompound(TAG);
		boolean enabled = tag.getBoolean(KEY_ENABLED);
		ResourceLocation circle = null;
		if (enabled && tag.contains(KEY_CIRCLE, Tag.TAG_STRING)) {
			circle = ResourceLocation.tryParse(tag.getString(KEY_CIRCLE));
		}
		float size = tag.contains(KEY_SIZE, Tag.TAG_ANY_NUMERIC) ? tag.getFloat(KEY_SIZE) : DEFAULT_SIZE;
		return new State(entity.getUUID().toString(), enabled, circle, sanitizeSize(size));
	}

	@Nullable
	public static State getClientOverride(Entity entity) {
		State state = CLIENT_OVERRIDES.get(entity.getId());
		if (state != null && !state.uuid().equals(entity.getUUID().toString())) {
			CLIENT_OVERRIDES.remove(entity.getId());
			return null;
		}
		return state;
	}

	public static void clientUpdate(int entityId, String uuid, boolean hasOverride, boolean enabled,
									@Nullable ResourceLocation circle, float size) {
		if (hasOverride) {
			CLIENT_OVERRIDES.put(entityId, new State(uuid, enabled, circle, sanitizeSize(size)));
		} else {
			CLIENT_OVERRIDES.remove(entityId);
		}
	}

	public static void syncTracking(Entity entity) {
		SpellCircleStateToClient packet = new SpellCircleStateToClient(entity);
		YoukaisHomecoming.HANDLER.toTrackingPlayers(packet, entity);
		if (entity instanceof ServerPlayer player) {
			YoukaisHomecoming.HANDLER.toClientPlayer(packet, player);
		}
		rememberSynced(entity);
	}

	public static void syncToPlayer(Entity entity, ServerPlayer player) {
		YoukaisHomecoming.HANDLER.toClientPlayer(new SpellCircleStateToClient(entity), player);
	}

	public static void tickServerLevel(ServerLevel level) {
		if ((level.getGameTime() & 7) != 0) {
			return;
		}
		for (Entity entity : level.getAllEntities()) {
			State current = getServerOverride(entity);
			State synced = SERVER_SYNCED.get(entity.getUUID());
			if (current == null && synced == null) {
				continue;
			}
			if (!Objects.equals(current, synced)) {
				syncTracking(entity);
			}
		}
	}

	private static void rememberSynced(Entity entity) {
		State state = getServerOverride(entity);
		if (state == null) {
			SERVER_SYNCED.remove(entity.getUUID());
		} else {
			SERVER_SYNCED.put(entity.getUUID(), state);
		}
	}

	private static float sanitizeSize(float size) {
		if (!Float.isFinite(size)) {
			return DEFAULT_SIZE;
		}
		return Math.max(0.0f, Math.min(64.0f, size));
	}

}
