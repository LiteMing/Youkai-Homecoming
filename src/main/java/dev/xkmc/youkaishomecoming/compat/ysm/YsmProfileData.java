package dev.xkmc.youkaishomecoming.compat.ysm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/** Server-authoritative JSON library shared by every world in this game/server installation. */
public final class YsmProfileData {

	public record Entry(long revision, YsmModelProfile profile) { }
	private static final Logger LOGGER = LoggerFactory.getLogger("YoukaiHomecoming/YsmProfiles");
	private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final int FORMAT = 1;
	// The same hard library bound used by the legacy SavedData reader.
	private static final int MAX_PROFILES = 4096;
	private static final Map<MinecraftServer, YsmProfileData> SERVERS = new WeakHashMap<>();
	private final Path file;
	private final Map<String, Entry> profiles = new LinkedHashMap<>();
	// Retain deleted revisions for this connection, so an old editor cannot recreate a deleted model.
	private final Map<String, Long> revisions = new LinkedHashMap<>();
	private String diskJson;
	private boolean loaded;

	YsmProfileData(Path file) {
		this.file = file.toAbsolutePath();
	}

	public static YsmProfileData get(MinecraftServer server) {
		return SERVERS.computeIfAbsent(server, current -> {
			var data = new YsmProfileData(FMLPaths.CONFIGDIR.get().resolve(YoukaisHomecoming.MODID).resolve("ysm_presets.json"));
			try {
				data.reload();
				data.migrateLegacy(current.getWorldPath(LevelResource.ROOT).resolve("data/youkaishomecoming_model_profiles.dat"));
			} catch (IllegalArgumentException ex) {
				// A bad external file must not crash player login or get overwritten with an empty library.
				LOGGER.error("Could not load global YSM presets: {}", ex.getMessage());
			}
			return data;
		});
	}

	public Entry entry(String model) {
		model = YsmModelProfile.modelId(model);
		Entry entry = profiles.get(model);
		return entry == null ? new Entry(revisions.getOrDefault(model, 0L), YsmModelProfile.empty(model)) : entry;
	}

	public Map<String, Entry> entries() { return Map.copyOf(profiles); }

	/** Only explicit reloads/editor requests touch disk; presentation ticks use the synchronized snapshot. */
	public boolean reload() {
		String json = readFile();
		if (loaded && Objects.equals(json, diskJson)) return false;
		Map<String, YsmModelProfile> next = decode(json);
		if (json == null) {
			json = encode(next);
			write(json, null);
		}
		boolean changed = adopt(next);
		diskJson = json;
		loaded = true;
		return changed;
	}

	public Entry replace(YsmModelProfile profile, long expectedRevision, int maxProfiles) {
		Entry current = entry(profile.model());
		if (expectedRevision != current.revision()) throw new IllegalArgumentException("revision_conflict");
		if (!profiles.containsKey(profile.model()) && profiles.size() >= Math.min(maxProfiles, MAX_PROFILES))
			throw new IllegalArgumentException("Profile count limit reached");
		checkSize(profile);
		Entry next = new Entry(Math.addExact(current.revision(), 1), profile);
		Map<String, YsmModelProfile> values = values();
		values.put(profile.model(), profile);
		String json = encode(values);
		// Check the actual file even if an external editor has not issued /yhysm preset reload yet.
		write(json, diskJson);
		profiles.put(profile.model(), next);
		revisions.put(profile.model(), next.revision());
		diskJson = json;
		loaded = true;
		return next;
	}

	private boolean adopt(Map<String, YsmModelProfile> values) {
		Map<String, Entry> next = new LinkedHashMap<>();
		Map<String, Long> nextRevisions = new LinkedHashMap<>(revisions);
		values.forEach((model, profile) -> {
			Entry current = entry(model);
			long revision = profiles.containsKey(model) && current.profile().equals(profile)
					? current.revision() : Math.addExact(current.revision(), 1);
			next.put(model, new Entry(revision, profile));
			nextRevisions.put(model, revision);
		});
		profiles.forEach((model, entry) -> {
			if (!next.containsKey(model)) nextRevisions.put(model, Math.addExact(entry.revision(), 1));
		});
		boolean changed = !profiles.equals(next);
		profiles.clear();
		profiles.putAll(next);
		revisions.clear();
		revisions.putAll(nextRevisions);
		return changed;
	}

	private Map<String, YsmModelProfile> values() {
		Map<String, YsmModelProfile> values = new LinkedHashMap<>();
		profiles.forEach((model, entry) -> values.put(model, entry.profile()));
		return values;
	}

	private static Map<String, YsmModelProfile> decode(String json) {
		Map<String, YsmModelProfile> values = new LinkedHashMap<>();
		if (json == null) return values;
		try {
			JsonObject root = JsonParser.parseString(json).getAsJsonObject();
			if (!root.keySet().equals(Set.of("format", "profiles")) || !root.get("format").isJsonPrimitive()
					|| !root.getAsJsonPrimitive("format").isNumber() || root.get("format").getAsBigDecimal().intValueExact() != FORMAT)
				throw new IllegalArgumentException("Expected format=1 and a profiles array");
			JsonArray entries = root.getAsJsonArray("profiles");
			if (entries.size() > MAX_PROFILES) throw new IllegalArgumentException("Profile count limit reached");
			for (var element : entries) {
				var profile = YsmModelProfile.fromJson(element.toString());
				checkSize(profile);
				if (values.putIfAbsent(profile.model(), profile) != null)
					throw new IllegalArgumentException("Duplicate model: " + profile.model());
			}
			return values;
		} catch (RuntimeException ex) {
			throw new IllegalArgumentException("profile_storage: Invalid global preset JSON: " + ex.getMessage(), ex);
		}
	}

	private static String encode(Map<String, YsmModelProfile> values) {
		JsonObject root = new JsonObject();
		root.addProperty("format", FORMAT);
		JsonArray entries = new JsonArray();
		values.values().forEach(profile -> entries.add(JsonParser.parseString(profile.toJson())));
		root.add("profiles", entries);
		return JSON.toJson(root) + "\n";
	}

	private static void checkSize(YsmModelProfile profile) {
		if (profile.toJson().length() > YsmModelProfile.MAX_JSON_LENGTH)
			throw new IllegalArgumentException("Profile JSON is too large");
	}

	private String readFile() {
		try {
			return Files.notExists(file) ? null : Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException ex) {
			throw storageError(ex);
		}
	}

	private void write(String json, String expected) {
		Path temporary = null;
		try {
			if (!Objects.equals(readFile(), expected)) throw new IllegalArgumentException("profile_storage_changed");
			Files.createDirectories(file.getParent());
			temporary = Files.createTempFile(file.getParent(), "ysm_presets-", ".tmp");
			Files.writeString(temporary, json, StandardCharsets.UTF_8);
			if (!Objects.equals(readFile(), expected)) throw new IllegalArgumentException("profile_storage_changed");
			try {
				Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException ex) {
			throw storageError(ex);
		} finally {
			if (temporary != null) {
				try { Files.deleteIfExists(temporary); }
				catch (IOException ex) { LOGGER.warn("Could not remove YSM preset temporary file {}", temporary, ex); }
			}
		}
	}

	private IllegalArgumentException storageError(IOException ex) {
		return new IllegalArgumentException("profile_storage: " + file + ": " + ex.getMessage(), ex);
	}

	/** Preserve the original NBT as a migration backup; never write live profiles back to a world. */
	void migrateLegacy(Path legacy) {
		Path backup = legacy.resolveSibling(legacy.getFileName() + ".migrated");
		if (Files.notExists(legacy) || Files.exists(backup)) return;
		try {
			var root = NbtIo.readCompressed(legacy.toFile());
			if (!root.contains("data", Tag.TAG_COMPOUND) || !root.getCompound("data").contains("profiles", Tag.TAG_LIST))
				throw new IllegalArgumentException("Invalid legacy profile data: " + legacy);
			var entries = (ListTag) root.getCompound("data").get("profiles");
			if (!entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND)
				throw new IllegalArgumentException("Invalid legacy profile list: " + legacy);
			if (entries.size() > MAX_PROFILES) throw new IllegalArgumentException("Profile count limit reached");
			Map<String, YsmModelProfile> values = values();
			for (int i = 0; i < entries.size(); i++) {
				var profile = YsmModelProfile.fromJson(entries.getCompound(i).getString("json"));
				var current = values.get(profile.model());
				var merged = current == null ? profile : mergeLegacy(current, profile);
				checkSize(merged);
				values.put(profile.model(), merged);
			}
			if (values.size() > MAX_PROFILES) throw new IllegalArgumentException("Profile count limit reached");
			String json = encode(values);
			write(json, diskJson);
			adopt(values);
			diskJson = json;
			loaded = true;
			// Renaming only after the JSON is durable also prevents deleted presets from being imported again.
			Files.move(legacy, backup);
			LOGGER.info("Migrated YSM presets from {} to {}; original retained at {}", legacy, file, backup);
		} catch (IOException ex) {
			throw new IllegalArgumentException("profile_storage: Could not migrate " + legacy + ": " + ex.getMessage(), ex);
		}
	}

	private static YsmModelProfile mergeLegacy(YsmModelProfile current, YsmModelProfile legacy) {
		Map<String, YsmModelProfile.Preset> presets = new LinkedHashMap<>(current.presets());
		Map<String, String> names = new LinkedHashMap<>();
		legacy.presets().forEach((id, preset) -> {
			String name = id;
			int suffix = 1;
			while (presets.containsKey(name) && !presets.get(name).equals(preset)) {
				String tail = "_migrated_" + suffix++;
				name = id.substring(0, Math.min(id.length(), 128 - tail.length())) + tail;
			}
			presets.putIfAbsent(name, preset);
			names.put(id, name);
		});
		Map<YsmModelProfile.Trigger, String> triggers = new LinkedHashMap<>(current.triggers());
		legacy.triggers().forEach((trigger, id) -> triggers.putIfAbsent(trigger, names.get(id)));
		return new YsmModelProfile(current.model(), presets, triggers);
	}
}
