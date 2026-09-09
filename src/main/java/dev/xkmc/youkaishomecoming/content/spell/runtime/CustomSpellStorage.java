package dev.xkmc.youkaishomecoming.content.spell.runtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * JSON file-based storage for custom spell definitions.
 * Regular saves are stored under {@code <world>/youkaishomecoming_spells/<namespace>/}.
 * Exported spells are stored under {@code <game>/youkaishomecoming_spells/<namespace>/}
 * and are loaded for every save on the same game/server instance.
 * No NBT conversion — pure JSON, consistent with the export/import system.
 */
public class CustomSpellStorage {

	private static final Logger LOGGER = LoggerFactory.getLogger("YoukaiHomecoming/SpellStorage");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String DIR_NAME = "youkaishomecoming_spells";

	/**
	 * Get the global storage root directory shared by all saves on this game/server instance.
	 */
	public static File getGlobalStorageDir() {
		return new File(FMLPaths.GAMEDIR.get().toFile(), DIR_NAME);
	}

	/**
	 * Get the storage root directory for the given server's world.
	 */
	public static File getWorldStorageDir(MinecraftServer server) {
		return new File(server.getWorldPath(LevelResource.ROOT).toFile(), DIR_NAME);
	}

	/**
	 * Backwards-compatible name for world-local spell storage.
	 */
	public static File getStorageDir(MinecraftServer server) {
		return getWorldStorageDir(server);
	}

	/**
	 * Get the JSON file path for a specific spell ID. ResourceLocation path
	 * separators are kept as directories, e.g.
	 * {@code youkaishomecoming_spells/youkaishomecoming/cards/my_spell.json}.
	 */
	public static File getSpellFile(MinecraftServer server, ResourceLocation id) {
		return getSpellFile(getWorldStorageDir(server), id);
	}

	public static File getGlobalSpellFile(ResourceLocation id) {
		return getSpellFile(getGlobalStorageDir(), id);
	}

	private static File getSpellFile(File root, ResourceLocation id) {
		File file = new File(new File(root, id.getNamespace()), id.getPath() + ".json");
		File parent = file.getParentFile();
		if (parent != null) parent.mkdirs();
		return file;
	}

	/** Pre-0.29 storage flattened path separators into a single filename. */
	private static File getLegacySpellFile(File root, ResourceLocation id) {
		return new File(new File(root, id.getNamespace()), id.getPath().replace('/', '_') + ".json");
	}

	private static File findExistingSpellFile(File root, ResourceLocation id) {
		File canonical = getSpellFile(root, id);
		if (canonical.exists()) return canonical;
		File legacy = getLegacySpellFile(root, id);
		return legacy.exists() ? legacy : canonical;
	}

	/**
	 * Ownership metadata lives in a sidecar file next to the spell JSON
	 * ({@code <spell>.json.owner}) so the definition format itself stays intact.
	 * It protects deletion only; custom spell editing is collaborative.
	 */
	private static File getOwnerFile(File spellFile) {
		return new File(spellFile.getPath() + ".owner");
	}

	public static void saveOwner(MinecraftServer server, ResourceLocation id, UUID owner) {
		File file = getOwnerFile(getSpellFile(server, id));
		try {
			Files.writeString(file.toPath(), owner.toString());
		} catch (IOException e) {
			LOGGER.error("Failed to save owner for spell {}: {}", id, file.getPath(), e);
		}
	}

	@Nullable
	public static UUID loadOwner(MinecraftServer server, ResourceLocation id) {
		File file = getOwnerFile(findExistingSpellFile(getWorldStorageDir(server), id));
		if (!file.exists()) {
			return null;
		}
		try {
			return UUID.fromString(Files.readString(file.toPath()).trim());
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Save a spell definition as a JSON file. Overwrites if exists.
	 */
	public static boolean saveSpell(MinecraftServer server, SpellDefinition definition) {
		return saveSpell(getSpellFile(server, definition.id), definition);
	}

	/**
	 * Save a downloaded/custom definition without rewriting its JSON payload.  The
	 * market download path uses this overload so fields introduced by a newer
	 * client remain available to the Raw JSON editor after a world restart.
	 */
	public static boolean saveSpell(MinecraftServer server, SpellDefinition definition, String rawJson) {
		if (rawJson == null || rawJson.isBlank()) return saveSpell(server, definition);
		File file = getSpellFile(server, definition.id);
		try {
			File parent = file.getParentFile();
			if (parent != null) parent.mkdirs();
			File temp = new File(file.getPath() + ".tmp");
			Files.writeString(temp.toPath(), rawJson, StandardCharsets.UTF_8);
			try {
				Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
						java.nio.file.StandardCopyOption.ATOMIC_MOVE);
			} catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
				Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
			LOGGER.info("Saved custom spell {} to {}", definition.id, file.getPath());
			return true;
		} catch (Exception e) {
			LOGGER.error("Failed to save raw spell {} to {}", definition.id, file.getPath(), e);
			return false;
		}
	}

	/**
	 * Export a spell definition into the game/server directory so every save loads it.
	 */
	public static File saveGlobalSpell(SpellDefinition definition) {
		File file = getGlobalSpellFile(definition.id);
		saveSpell(file, definition);
		return file;
	}

	private static boolean saveSpell(File file, SpellDefinition definition) {
		try {
			var json = SpellDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
					.getOrThrow(false, s -> {});
			// FileWriter uses the host code page on Windows.  Spell display names are
			// commonly Chinese, so an old save could be GBK and become unreadable on
			// the next startup (Files.readString is UTF-8).  Persist the wire format
			// explicitly as UTF-8 and make the write atomic enough for a crash-safe
			// inventory transition.
			File parent = file.getParentFile();
			if (parent != null) parent.mkdirs();
			File temp = new File(file.getPath() + ".tmp");
			try (var writer = Files.newBufferedWriter(temp.toPath(), StandardCharsets.UTF_8)) {
				GSON.toJson(json, writer);
			}
			try {
				Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
						java.nio.file.StandardCopyOption.ATOMIC_MOVE);
			} catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
				Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
			LOGGER.info("Saved custom spell {} to {}", definition.id, file.getPath());
			return true;
		} catch (Exception e) {
			LOGGER.error("Failed to save spell {} to {}", definition.id, file.getPath(), e);
			return false;
		}
	}

	/**
	 * Delete a custom spell JSON file.
	 */
	public static void deleteSpell(MinecraftServer server, ResourceLocation id) {
		deleteSpellFile(getSpellFile(server, id));
		deleteSpellFile(getLegacySpellFile(getWorldStorageDir(server), id));
		deleteSpellFile(getGlobalSpellFile(id));
		deleteSpellFile(getLegacySpellFile(getGlobalStorageDir(), id));
	}

	private static void deleteSpellFile(File file) {
		if (file.exists()) {
			file.delete();
		}
		File ownerFile = getOwnerFile(file);
		if (ownerFile.exists()) {
			ownerFile.delete();
		}
		// Clean up empty path/namespace directories, but keep the storage root.
		File parent = file.getParentFile();
		while (parent != null && parent.isDirectory() && !DIR_NAME.equals(parent.getName())) {
			String[] entries = parent.list();
			if (entries == null || entries.length != 0) break;
			if (!parent.delete()) break;
			parent = parent.getParentFile();
		}
	}

	/**
	 * Load all custom spell JSON files into SpellRegistry.
	 * Call on server/world start.
	 */
	public static void loadAllIntoRegistry(MinecraftServer server) {
		loadStorageDir("global", getGlobalStorageDir(), true);
		loadStorageDir("world", getWorldStorageDir(server), false);
	}

	private static void loadStorageDir(String label, File dir, boolean allowDefaultOverrides) {
		LOGGER.info("Loading {} custom spells from {}", label, dir.getPath());
		if (!dir.exists() || !dir.isDirectory()) {
			LOGGER.info("No {} custom spell directory found at {}", label, dir.getPath());
			return;
		}
		loadRecursive(dir, dir, allowDefaultOverrides);
	}

	private static void loadRecursive(File dir, File storageRoot, boolean allowDefaultOverrides) {
		File[] files = dir.listFiles();
		if (files == null) return;
		for (File file : files) {
			if (file.isDirectory()) {
				loadRecursive(file, storageRoot, allowDefaultOverrides);
			} else if (file.getName().endsWith(".json")) {
				loadSpellFile(file, storageRoot, allowDefaultOverrides);
			}
		}
	}

	private static void loadSpellFile(File file, File storageRoot, boolean allowDefaultOverrides) {
		try {
			DecodedText decoded = readTextWithLegacyFallback(file);
			var json = com.google.gson.JsonParser.parseString(decoded.content());
			SpellDefinition.CODEC.parse(JsonOps.INSTANCE, json)
					.resultOrPartial(err -> LOGGER.warn("Failed to parse spell file {}: {}", file.getPath(), err))
					.ifPresent(def -> {
						File canonical = getSpellFile(storageRoot, def.id);
						boolean legacyPath = !file.toPath().toAbsolutePath().normalize()
								.equals(canonical.toPath().toAbsolutePath().normalize());
						// If both layouts exist, the canonical path wins regardless of
						// filesystem enumeration order.
						if (legacyPath && canonical.exists()) {
							LOGGER.info("Skipping legacy duplicate {} because {} exists", file.getPath(), canonical.getPath());
							return;
						}
						// Skip disk-cached versions of built-in spells — Java code is always authoritative.
						// This prevents stale auto-saved JSONs from overriding updated Java definitions.
						if (!allowDefaultOverrides && SpellRegistry.hasDefault(def.id)) {
							LOGGER.info("Skipping disk-cached built-in spell {} (Java definition takes priority)", def.id);
							file.delete();
							return;
						}
						SpellRegistry.register(def);
						LOGGER.info("Loaded custom spell {} from {}", def.id, file.getPath());
						if (decoded.legacy() || legacyPath) {
							// Older Windows builds wrote JSON using the host code page.  Rewrite
							// after a successful parse so the next restart is unambiguous UTF-8.
							if (saveSpell(canonical, def)) {
								if (legacyPath) {
									File oldOwner = getOwnerFile(file);
									File newOwner = getOwnerFile(canonical);
									if (oldOwner.exists() && !newOwner.exists()) {
										try { Files.move(oldOwner.toPath(), newOwner.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
										catch (IOException ignored) { /* owner metadata is optional */ }
									}
									deleteSpellFile(file);
								}
								LOGGER.info("Migrated spell {} to canonical UTF-8 path {}", def.id, canonical.getPath());
							}
						}
					});
		} catch (Exception e) {
			LOGGER.warn("Failed to read spell file {}: {}", file.getPath(), e.getMessage());
		}
	}

	/** Read modern UTF-8 files while accepting pre-0.26 Windows-code-page saves. */
	private static DecodedText readTextWithLegacyFallback(File file) throws IOException {
		byte[] bytes = Files.readAllBytes(file.toPath());
		try {
			return new DecodedText(StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(java.nio.ByteBuffer.wrap(bytes)).toString(), false);
		} catch (java.nio.charset.CharacterCodingException malformed) {
			// The old FileWriter output on the development Windows host was GBK.
			// Decode it once and migrate it immediately after parsing.  String's
			// replacement behavior is intentional here: it keeps a damaged legacy
			// file diagnosable by the JSON/codec error instead of aborting the whole
			// custom-spell scan with a second decoder exception.
			return new DecodedText(new String(bytes, Charset.forName("GBK")), true);
		}
	}

	private record DecodedText(String content, boolean legacy) {
	}

}
