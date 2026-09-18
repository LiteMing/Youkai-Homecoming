package dev.xkmc.youkaishomecoming.compat.ysm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Filesystem contracts for global persistence, external edits and one-time world migration. No game launch. */
public final class YsmProfileStorageTest {

	private static int passed;

	public static void main(String[] args) throws Exception {
		Path root = Files.createTempDirectory("yh-global-ysm-");
		try {
			globalAndExternalEdits(root.resolve("global.json"));
			migration(root.resolve("migration"));
			invalidMigration(root.resolve("invalid-migration"));
			System.out.println("YsmProfileStorageTest: " + passed + " contracts passed");
		} finally {
			try (var paths = Files.walk(root)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
			}
		}
	}

	private static YsmModelProfile profile(String model, String id, float value) {
		return new YsmModelProfile(model, Map.of(id, new YsmModelProfile.Preset("中文表情", "", 20, Map.of("v.face", value))), Map.of());
	}

	private static YsmProfileData open(Path file) {
		var data = new YsmProfileData(file);
		data.reload();
		return data;
	}

	private static void globalAndExternalEdits(Path file) throws IOException {
		var firstWorld = open(file);
		var builtin = firstWorld.entry("YH内置/remilia").profile();
		check("fresh library seeds the built-in Remilia profile", builtin.presets().keySet().containsAll(java.util.Set.of("sit", "swim")));
		check("fresh library routes built-in sit and swim", builtin.triggers().get(YsmModelProfile.Trigger.SIT).equals("sit")
				&& builtin.triggers().get(YsmModelProfile.Trigger.SWIM).equals("swim"));
		var original = profile("test/remilia", "happy", 1);
		var first = firstWorld.replace(original, 0, 256);
		check("UTF-8 remains human-readable", Files.readString(file, StandardCharsets.UTF_8).contains("中文表情"));
		var otherWorld = open(file);
		equal("another world reads the same global preset", otherWorld.entry(original.model()).profile(), original);
		var anotherServer = open(file.resolveSibling("another-server.json"));
		check("another server installation has an independent library", !anotherServer.entries().containsKey(original.model()));
		String originalFile = Files.readString(file);
		reject("stale editor cannot save", () -> firstWorld.replace(YsmModelProfile.empty(original.model()), 0, 256));
		equal("rejected revision leaves file unchanged", Files.readString(file), originalFile);

		var external = profile(original.model(), "happy", 2);
		writeLibrary(file, external);
		String externalFile = Files.readString(file);
		reject("unreloaded external edit cannot be overwritten", () -> firstWorld.replace(original, first.revision(), 256));
		equal("external edit stays on disk", Files.readString(file), externalFile);
		equal("failed save leaves server memory unchanged", firstWorld.entry(original.model()), first);
		check("reload reports a changed profile", firstWorld.reload());
		equal("external edit becomes the server snapshot", firstWorld.entry(original.model()).profile(), external);
		equal("external edit increments runtime revision without JSON metadata", firstWorld.entry(original.model()).revision(), 2L);
		reject("editor opened before external reload conflicts", () -> firstWorld.replace(original, first.revision(), 256));
		Files.writeString(file, Files.readString(file) + "\n");
		check("formatting-only edits do not conflict with model revisions", !firstWorld.reload());
		equal("formatting keeps revision", firstWorld.entry(original.model()).revision(), 2L);

		var other = profile("another/model", "idle", 3);
		writeLibrary(file, external, other);
		firstWorld.reload();
		equal("unrelated external model leaves existing revision", firstWorld.entry(original.model()).revision(), 2L);
		firstWorld.replace(original, 2, 256);
		equal("saving one model retains another externally added model", open(file).entry(other.model()).profile(), other);
		writeLibrary(file, other);
		check("external deletion changes snapshot", firstWorld.reload());
		check("deleted model is absent from full sync", !firstWorld.entries().containsKey(original.model()));
		equal("deleted model loses its preset values", firstWorld.entry(original.model()).profile(), YsmModelProfile.empty(original.model()));
		reject("deleted model cannot be recreated by stale revision", () -> firstWorld.replace(original, 3, 256));
		reject("deleted model cannot be recreated as a never-seen model", () -> firstWorld.replace(original, 0, 256));

		var valid = firstWorld.entries();
		String validFile = Files.readString(file);
		Files.writeString(file, "{\"format\":1,\"profiles\":[");
		reject("incomplete agent write is rejected", firstWorld::reload);
		equal("bad JSON retains last valid server snapshot", firstWorld.entries(), valid);
		reject("saving cannot erase invalid external JSON", () -> firstWorld.replace(other, firstWorld.entry(other.model()).revision(), 256));
		check("invalid JSON stays available for repair", Files.readString(file).endsWith("["));
		writeLibrary(file, other, other);
		reject("duplicate models reject the whole reload", firstWorld::reload);
		equal("duplicate reload does not partially apply", firstWorld.entries(), valid);
		Files.writeString(file, "{\"format\":2,\"profiles\":[]}");
		reject("unknown library format is rejected", firstWorld::reload);
		Files.writeString(file, validFile);
		firstWorld.reload();
		equal("repaired file restores normal reads", firstWorld.entries(), valid);

		Files.delete(file);
		Files.createDirectory(file);
		reject("filesystem read/write failure is reported", () -> firstWorld.replace(other, firstWorld.entry(other.model()).revision(), 256));
		equal("I/O failure does not acknowledge a new revision", firstWorld.entries(), valid);
		Files.delete(file);
		Files.writeString(file, validFile);
		equal("restart retains all committed profiles", open(file).entry(other.model()).profile(), other);
	}

	private static void migration(Path directory) throws IOException {
		Path file = directory.resolve("global.json");
		var data = open(file);
		var global = profile("test/model", "happy", 1);
		global = new YsmModelProfile(global.model(), global.presets(), Map.of(YsmModelProfile.Trigger.IDLE, "happy"));
		data.replace(global, 0, 256);
		var legacy = profile(global.model(), "happy", 2);
		legacy = new YsmModelProfile(legacy.model(), legacy.presets(), Map.of(YsmModelProfile.Trigger.IDLE, "happy", YsmModelProfile.Trigger.WALK, "happy"));
		var independent = profile("legacy/model", "sleep", 3);
		Path world = directory.resolve("world-a/data/youkaishomecoming_model_profiles.dat");
		writeLegacy(world, legacy, independent);
		byte[] originalNbt = Files.readAllBytes(world);
		data.migrateLegacy(world);
		var merged = data.entry(global.model()).profile();
		equal("global preset wins an ID collision", merged.presets().get("happy"), global.presets().get("happy"));
		equal("conflicting old preset gets a retained alias", merged.presets().get("happy_migrated_1"), legacy.presets().get("happy"));
		equal("global trigger keeps its selected preset", merged.triggers().get(YsmModelProfile.Trigger.IDLE), "happy");
		equal("new legacy trigger follows the renamed preset", merged.triggers().get(YsmModelProfile.Trigger.WALK), "happy_migrated_1");
		equal("all legacy models are imported", data.entry(independent.model()).profile(), independent);
		check("world no longer stores the live preset library", Files.notExists(world));
		Path backup = world.resolveSibling(world.getFileName() + ".migrated");
		check("legacy backup is byte-for-byte intact", java.util.Arrays.equals(Files.readAllBytes(backup), originalNbt));
		equal("migrated content is durable JSON", open(file).entry(global.model()).profile(), merged);

		Path secondWorld = directory.resolve("world-b/data/youkaishomecoming_model_profiles.dat");
		writeLegacy(secondWorld, legacy);
		data.migrateLegacy(secondWorld);
		equal("repeated legacy content does not make duplicate aliases", data.entry(global.model()).profile(), merged);
		writeLibrary(file, independent);
		data.reload();
		data.migrateLegacy(world);
		check("global deletion is not undone by revisiting migrated world", !data.entries().containsKey(global.model()));
		Files.copy(backup, world);
		data.migrateLegacy(world);
		check("backup marker prevents old versions from reimporting deleted presets", !data.entries().containsKey(global.model()));
	}

	private static void invalidMigration(Path directory) throws IOException {
		Path file = directory.resolve("global.json");
		var data = open(file);
		var presets = new LinkedHashMap<String, YsmModelProfile.Preset>();
		for (int i = 0; i < YsmModelProfile.MAX_PRESETS; i++) presets.put("preset_" + i, new YsmModelProfile.Preset("", "", 1, Map.of()));
		var full = new YsmModelProfile("full/model", presets, Map.of());
		data.replace(full, 0, 256);
		String before = Files.readString(file);
		Path world = directory.resolve("world/data/youkaishomecoming_model_profiles.dat");
		writeLegacy(world, profile(full.model(), "extra", 1));
		reject("overflowing migration fails before committing", () -> data.migrateLegacy(world));
		equal("migration failure preserves global JSON", Files.readString(file), before);
		equal("migration failure preserves runtime profiles", data.entry(full.model()).profile(), full);
		check("failed migration retains legacy source", Files.exists(world));
		check("failed migration does not mark source as migrated", Files.notExists(world.resolveSibling(world.getFileName() + ".migrated")));
		Files.writeString(world, "broken NBT");
		reject("corrupt legacy data is rejected", () -> data.migrateLegacy(world));
		equal("corrupt legacy data stays intact", Files.readString(world), "broken NBT");
		equal("corrupt legacy data cannot empty global presets", Files.readString(file), before);
	}

	private static void writeLibrary(Path file, YsmModelProfile... profiles) throws IOException {
		JsonObject root = new JsonObject();
		root.addProperty("format", 1);
		JsonArray entries = new JsonArray();
		for (var profile : profiles) entries.add(JsonParser.parseString(profile.toJson()));
		root.add("profiles", entries);
		Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
	}

	private static void writeLegacy(Path file, YsmModelProfile... profiles) throws IOException {
		CompoundTag root = new CompoundTag();
		CompoundTag data = new CompoundTag();
		ListTag entries = new ListTag();
		for (var profile : profiles) {
			CompoundTag entry = new CompoundTag();
			entry.putLong("revision", 10);
			entry.putString("json", profile.toJson());
			entries.add(entry);
		}
		data.put("profiles", entries);
		root.put("data", data);
		root.putInt("DataVersion", 3465);
		Files.createDirectories(file.getParent());
		NbtIo.writeCompressed(root, file.toFile());
	}

	private static void check(String message, boolean value) {
		if (!value) throw new AssertionError(message);
		passed++;
	}

	private static void equal(String message, Object actual, Object expected) {
		if (!Objects.equals(actual, expected)) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
		passed++;
	}

	private static void reject(String message, Runnable operation) {
		try { operation.run(); }
		catch (IllegalArgumentException expected) { passed++; return; }
		throw new AssertionError(message);
	}
}
