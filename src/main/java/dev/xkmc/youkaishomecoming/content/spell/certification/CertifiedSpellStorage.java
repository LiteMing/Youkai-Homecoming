package dev.xkmc.youkaishomecoming.content.spell.certification;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * World-level certified spell storage (design doc §15, D1): immutable definitions
 * keyed by definition hash at {@code <world>/youkaishomecoming_certified_spells/}.
 * Files are written only by the server at certification success.
 */
public final class CertifiedSpellStorage {

	public static final String DIR_NAME = "youkaishomecoming_certified_spells";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private CertifiedSpellStorage() {
	}

	public static Path getDir(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve(DIR_NAME);
	}

	public static void save(MinecraftServer server, SpellCertificate certificate, SpellDefinition definition) {
		Path dir = getDir(server);
		try {
			Files.createDirectories(dir);
			JsonObject root = new JsonObject();
			root.add("certificate", GSON.toJsonTree(certificate));
			JsonObject defJson = SpellDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
					.result().orElseThrow(() -> new IllegalStateException("cannot encode certified definition"));
			root.add("definition", defJson);
			Files.writeString(dir.resolve(certificate.definitionHash() + ".json"),
					GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (IOException e) {
			YoukaisHomecoming.LOGGER.error("Failed to save certified spell " + certificate.definitionHash(), e);
		}
	}

	/** Loads the immutable definition for a hash, or null when missing/corrupt. */
	@Nullable
	public static SpellDefinition loadDefinition(MinecraftServer server, String definitionHash) {
		Path file = getDir(server).resolve(definitionHash + ".json");
		if (!Files.exists(file)) return null;
		try {
			JsonObject root = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
			if (root == null || !root.has("definition")) return null;
			return SpellDefinition.CODEC.parse(JsonOps.INSTANCE, root.get("definition"))
					.result().orElse(null);
		} catch (Exception e) {
			YoukaisHomecoming.LOGGER.error("Failed to load certified spell " + definitionHash, e);
			return null;
		}
	}

	@Nullable
	public static SpellCertificate loadCertificate(MinecraftServer server, String definitionHash) {
		Path file = getDir(server).resolve(definitionHash + ".json");
		if (!Files.exists(file)) return null;
		try {
			JsonObject root = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
			if (root == null || !root.has("certificate")) return null;
			return GSON.fromJson(root.get("certificate"), SpellCertificate.class);
		} catch (Exception e) {
			YoukaisHomecoming.LOGGER.error("Failed to load certificate " + definitionHash, e);
			return null;
		}
	}
}
