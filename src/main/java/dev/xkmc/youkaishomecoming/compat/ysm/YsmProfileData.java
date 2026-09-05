package dev.xkmc.youkaishomecoming.compat.ysm;

import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;

/** Per-world shared presets. Revisions are per model, so unrelated authors do not conflict. */
public final class YsmProfileData extends SavedData {

	public record Entry(long revision, YsmModelProfile profile) { }
	private static final String ID = "youkaishomecoming_model_profiles";
	private final Map<String, Entry> profiles = new LinkedHashMap<>();

	public static YsmProfileData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(YsmProfileData::load, YsmProfileData::new, ID);
	}

	public Entry entry(String model) {
		model = YsmModelProfile.modelId(model);
		Entry entry = profiles.get(model);
		return entry == null ? new Entry(0, YsmModelProfile.empty(model)) : entry;
	}

	public Map<String, Entry> entries() { return Map.copyOf(profiles); }

	public Entry replace(YsmModelProfile profile, long expectedRevision, int maxProfiles) {
		Entry current = entry(profile.model());
		if (expectedRevision != current.revision()) throw new IllegalArgumentException("revision_conflict");
		if (!profiles.containsKey(profile.model()) && profiles.size() >= maxProfiles) throw new IllegalArgumentException("Profile count limit reached");
		if (profile.toJson().length() > YsmModelProfile.MAX_JSON_LENGTH) throw new IllegalArgumentException("Profile JSON is too large");
		Entry next = new Entry(Math.addExact(current.revision(), 1), profile);
		profiles.put(profile.model(), next);
		setDirty();
		return next;
	}

	@Override
	public CompoundTag save(CompoundTag tag) {
		ListTag entries = new ListTag();
		profiles.values().forEach(entry -> {
			CompoundTag value = new CompoundTag();
			value.putLong("revision", entry.revision());
			value.putString("json", entry.profile().toJson());
			entries.add(value);
		});
		tag.put("profiles", entries);
		return tag;
	}

	public static YsmProfileData load(CompoundTag tag) {
		YsmProfileData data = new YsmProfileData();
		ListTag entries = tag.getList("profiles", Tag.TAG_COMPOUND);
		for (int i = 0; i < Math.min(entries.size(), 4096); i++) {
			try {
				CompoundTag value = entries.getCompound(i);
				YsmModelProfile profile = YsmModelProfile.fromJson(value.getString("json"));
				data.profiles.put(profile.model(), new Entry(Math.max(1, value.getLong("revision")), profile));
			} catch (IllegalArgumentException ex) {
				YoukaisHomecoming.LOGGER.warn("Skipping invalid saved model profile {}: {}", i, ex.getMessage());
			}
		}
		return data;
	}
}
