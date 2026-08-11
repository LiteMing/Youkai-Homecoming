package dev.xkmc.youkaishomecoming.content.spell.certification;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellHealthPlan;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

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

	public static void save(MinecraftServer server, SpellCertificate certificate, SpellHealthPlan plan) {
		Path dir = getDir(server);
		try {
			Files.createDirectories(dir);
			JsonObject root = new JsonObject();
			root.add("certificate", GSON.toJsonTree(certificate));
			SpellDefinition definition = plan.rootDefinition();
			JsonElement defJson = SpellDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
					.result().orElseThrow(() -> new IllegalStateException("cannot encode certified definition"));
			root.add("definition", defJson);
			JsonObject dependencies = new JsonObject();
			for (var entry : plan.definitions().entrySet()) {
				if (entry.getKey().equals(definition.id)) continue;
				JsonElement dependency = SpellDefinition.CODEC.encodeStart(JsonOps.INSTANCE, entry.getValue())
						.result().orElseThrow(() -> new IllegalStateException(
								"cannot encode certified dependency " + entry.getKey()));
				dependencies.add(entry.getKey().toString(), dependency);
			}
			root.add("definitions", dependencies);
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
	public static SpellHealthPlan loadHealthPlan(MinecraftServer server, String definitionHash) {
		Map<net.minecraft.resources.ResourceLocation, SpellDefinition> definitions =
				loadDefinitions(server, definitionHash);
		if (definitions == null || definitions.isEmpty()) return null;
		SpellDefinition root = loadDefinition(server, definitionHash);
		if (root == null) return null;
		try {
			return SpellHealthPlan.analyze(root, definitions::get);
		} catch (IllegalArgumentException e) {
			YoukaisHomecoming.LOGGER.error("Invalid certified spell-health plan " + definitionHash, e);
			return null;
		}
	}

	@Nullable
	public static Map<net.minecraft.resources.ResourceLocation, SpellDefinition> loadDefinitions(
			MinecraftServer server, String definitionHash) {
		Path file = getDir(server).resolve(definitionHash + ".json");
		if (!Files.exists(file)) return null;
		try {
			JsonObject root = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
			if (root == null || !root.has("definition")) return null;
			SpellDefinition rootDefinition = SpellDefinition.CODEC.parse(JsonOps.INSTANCE, root.get("definition"))
					.result().orElse(null);
			if (rootDefinition == null) return null;
			Map<net.minecraft.resources.ResourceLocation, SpellDefinition> definitions = new HashMap<>();
			definitions.put(rootDefinition.id, rootDefinition);
			if (root.has("definitions") && root.get("definitions").isJsonObject()) {
				for (var entry : root.getAsJsonObject("definitions").entrySet()) {
					net.minecraft.resources.ResourceLocation id =
							net.minecraft.resources.ResourceLocation.tryParse(entry.getKey());
					SpellDefinition dependency = SpellDefinition.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
							.result().orElse(null);
					if (id == null || dependency == null || !id.equals(dependency.id)) return null;
					definitions.put(id, dependency);
				}
			}
			return Map.copyOf(definitions);
		} catch (Exception e) {
			YoukaisHomecoming.LOGGER.error("Failed to load certified spell bundle " + definitionHash, e);
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
