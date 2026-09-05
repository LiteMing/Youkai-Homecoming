package dev.xkmc.youkaishomecoming.content.spell.certification;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pending certified reward storage (design doc §16): rewards not collected when
 * the player logs off or the server restarts are persisted per player.
 */
public final class PendingRewardStorage {

	public static final String DIR_NAME = "youkaishomecoming_pending_rewards";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private PendingRewardStorage() {
	}

	public static Path getDir(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve(DIR_NAME);
	}

	public static void save(MinecraftServer server, UUID playerId, String definitionHash) {
		Path dir = getDir(server);
		try {
			Files.createDirectories(dir);
			Path file = dir.resolve(playerId + ".json");
			List<String> hashes = loadHashes(server, playerId);
			if (!hashes.contains(definitionHash)) {
				hashes.add(definitionHash);
			}
			Files.writeString(file, GSON.toJson(hashes), StandardCharsets.UTF_8);
		} catch (IOException e) {
			YoukaisHomecoming.LOGGER.error("Failed to save pending reward for " + playerId, e);
		}
	}

	public static List<String> loadHashes(MinecraftServer server, UUID playerId) {
		Path file = getDir(server).resolve(playerId + ".json");
		if (!Files.exists(file)) return new ArrayList<>();
		try {
			String[] arr = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), String[].class);
			return arr == null ? new ArrayList<>() : new ArrayList<>(List.of(arr));
		} catch (Exception e) {
			YoukaisHomecoming.LOGGER.error("Failed to load pending rewards for " + playerId, e);
			return new ArrayList<>();
		}
	}

	/** Removes one claimed reward entry; deletes the file when empty. */
	public static void claim(MinecraftServer server, UUID playerId, String definitionHash) {
		Path file = getDir(server).resolve(playerId + ".json");
		List<String> hashes = loadHashes(server, playerId);
		hashes.remove(definitionHash);
		try {
			if (hashes.isEmpty()) {
				Files.deleteIfExists(file);
			} else {
				Files.writeString(file, GSON.toJson(hashes), StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			YoukaisHomecoming.LOGGER.error("Failed to claim pending reward for " + playerId, e);
		}
	}

	@Nullable
	public static String peek(MinecraftServer server, UUID playerId) {
		List<String> hashes = loadHashes(server, playerId);
		return hashes.isEmpty() ? null : hashes.get(0);
	}
}
