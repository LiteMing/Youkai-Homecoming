package dev.xkmc.youkaishomecoming.compat.ysm;

import dev.xkmc.youkaishomecoming.compat.ysm.YSMCompatConfig.RenderBinding;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side persistent storage for the /yhysm manual model overrides
 * ({@code type set/off/unset}, {@code entity set/off/unset}, {@code reset}).
 * Saved with the world; the server broadcasts the full table to every client
 * so all players render the same mappings.
 */
public class YsmOverrideData extends SavedData {

	private static final String ID = "youkaishomecoming_ysm_overrides";
	private static final String KEY_TYPE = "type_overrides";
	private static final String KEY_ENTITY = "entity_overrides";
	private static final String KEY_MODEL = "model";
	private static final String KEY_TEXTURE = "texture";
	private static final String KEY_ENABLED = "enabled";

	private final Map<ResourceLocation, RenderBinding> typeOverrides = new LinkedHashMap<>();
	private final Map<UUID, RenderBinding> entityOverrides = new LinkedHashMap<>();

	public static YsmOverrideData get(MinecraftServer server) {
		return server.overworld().getDataStorage()
				.computeIfAbsent(YsmOverrideData::load, YsmOverrideData::new, ID);
	}

	public static YsmOverrideData load(CompoundTag tag) {
		YsmOverrideData data = new YsmOverrideData();
		CompoundTag typeTag = tag.getCompound(KEY_TYPE);
		for (String key : typeTag.getAllKeys()) {
			ResourceLocation id = ResourceLocation.tryParse(key);
			if (id != null) {
				data.typeOverrides.put(id, bindingFromTag(typeTag.getCompound(key)));
			}
		}
		CompoundTag entityTag = tag.getCompound(KEY_ENTITY);
		for (String key : entityTag.getAllKeys()) {
			try {
				data.entityOverrides.put(UUID.fromString(key), bindingFromTag(entityTag.getCompound(key)));
			} catch (IllegalArgumentException ignored) {
			}
		}
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag) {
		CompoundTag typeTag = new CompoundTag();
		typeOverrides.forEach((id, binding) -> typeTag.put(id.toString(), bindingToTag(binding)));
		tag.put(KEY_TYPE, typeTag);
		CompoundTag entityTag = new CompoundTag();
		entityOverrides.forEach((uuid, binding) -> entityTag.put(uuid.toString(), bindingToTag(binding)));
		tag.put(KEY_ENTITY, entityTag);
		return tag;
	}

	static CompoundTag bindingToTag(RenderBinding binding) {
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY_MODEL, binding.modelId());
		tag.putString(KEY_TEXTURE, binding.textureName());
		tag.putBoolean(KEY_ENABLED, binding.enabled());
		return tag;
	}

	static RenderBinding bindingFromTag(CompoundTag tag) {
		if (tag.getBoolean(KEY_ENABLED)) {
			return RenderBinding.enabled(tag.getString(KEY_MODEL), tag.getString(KEY_TEXTURE));
		}
		return RenderBinding.disabled();
	}

	public Map<ResourceLocation, RenderBinding> getTypeOverrides() {
		return new LinkedHashMap<>(typeOverrides);
	}

	public Map<UUID, RenderBinding> getEntityOverrides() {
		return new LinkedHashMap<>(entityOverrides);
	}

	public void setType(ResourceLocation type, RenderBinding binding) {
		typeOverrides.put(type, binding);
		setDirty();
	}

	public void removeType(ResourceLocation type) {
		typeOverrides.remove(type);
		setDirty();
	}

	public void setEntity(UUID uuid, RenderBinding binding) {
		entityOverrides.put(uuid, binding);
		setDirty();
	}

	public void removeEntity(UUID uuid) {
		entityOverrides.remove(uuid);
		setDirty();
	}

	public void clearAll() {
		typeOverrides.clear();
		entityOverrides.clear();
		setDirty();
	}

}
