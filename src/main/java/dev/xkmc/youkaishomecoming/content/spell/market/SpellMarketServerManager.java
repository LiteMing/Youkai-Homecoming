package dev.xkmc.youkaishomecoming.content.spell.market;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.market.dto.SpellListEntry;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntimeAccess;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SpellMarketServerManager {

	private static final Logger LOGGER = LoggerFactory.getLogger("SpellMarket/Server");
	private static volatile SpellMarketServerManager instance;

	private final MinecraftServer server;
	private final SpellMarketHttpClient http;
	private final ScheduledExecutorService scheduler;
	private final Map<String, Long> lastSync = new ConcurrentHashMap<>();
	private MarketImportManifest manifest;

	private SpellMarketServerManager(MinecraftServer server) {
		this.server = server;
		this.http = new SpellMarketHttpClient(YHModConfig.COMMON.spellMarketUrl.get());
		this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, "YH-Spell-Market-Poller");
			thread.setDaemon(true);
			return thread;
		});
		this.manifest = SpellMarketStorage.loadManifest(server);
		SpellMarketStorage.loadManagedSpells(server, manifest);
	}

	public static synchronized void start(MinecraftServer server) {
		stop();
		instance = new SpellMarketServerManager(server);
		instance.startPolling();
	}

	public static synchronized void stop() {
		if (instance != null) instance.scheduler.shutdownNow();
		instance = null;
	}

	public static SpellMarketServerManager get(MinecraftServer server) {
		SpellMarketServerManager current = instance;
		if (current == null || current.server != server) {
			throw new IllegalStateException("Spell market server manager is not running");
		}
		return current;
	}

	private void startPolling() {
		if (!YHModConfig.COMMON.spellMarketEnabled.get() || !YHModConfig.COMMON.spellMarketAutoSyncEnabled.get()) return;
		if (!http.usesHttps()) {
			LOGGER.error("Automatic spell market synchronization requires HTTPS; configured URL is {}", http.baseUrl());
			return;
		}
		long period = YHModConfig.COMMON.spellMarketPollMinutes.get();
		scheduler.scheduleAtFixedRate(() -> {
			for (String tag : YHModConfig.COMMON.spellMarketAutoSyncTags.get()) {
				syncTag(tag, false).thenAccept(result -> {
					if (!result.success) LOGGER.warn("Automatic market sync failed for {}: {}", tag, result.errors);
				});
			}
		}, 5, period, TimeUnit.MINUTES);
	}

	public CompletableFuture<SyncResult> syncTag(String rawTag, boolean force) {
		String tag = normalizeTag(rawTag);
		if (!YHModConfig.COMMON.spellMarketEnabled.get()) {
			return CompletableFuture.completedFuture(SyncResult.failed(tag, "Spell market is disabled"));
		}
		if (!http.usesHttps()) {
			return CompletableFuture.completedFuture(SyncResult.failed(tag, "Automatic imports require an HTTPS market URL"));
		}
		long minInterval = TimeUnit.MINUTES.toMillis(YHModConfig.COMMON.spellMarketPollMinutes.get());
		long now = System.currentTimeMillis();
		Long previous = lastSync.get(tag);
		if (!force && previous != null && now - previous < minInterval) {
			return CompletableFuture.completedFuture(SyncResult.failed(tag, "Synchronization frequency limit is active"));
		}
		lastSync.put(tag, now);
		return fetchExactTag(tag).thenCompose(entries -> stage(tag, entries))
				.thenCompose(staged -> applyOnServerThread(tag, staged))
				.exceptionally(error -> {
					Throwable cause = unwrap(error);
					LOGGER.error("Market sync failed for exact tag {}", tag, cause);
					return SyncResult.failed(tag, cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
				});
	}

	public synchronized List<MarketImportManifest.Entry> listByTag(String tag) {
		return manifest.entries.values().stream().filter(e -> e.managedTag.equals(tag)).toList();
	}

	public synchronized MarketImportManifest.Entry metadata(ResourceLocation id) {
		return manifest.entries.values().stream().filter(e -> e.localSpellId.equals(id.toString())).findFirst().orElse(null);
	}

	public CompletableFuture<SyncResult> pruneTag(String rawTag) {
		String tag = normalizeTag(rawTag);
		CompletableFuture<SyncResult> future = new CompletableFuture<>();
		server.execute(() -> {
			try {
				future.complete(applyStaged(tag, List.of()));
			} catch (Exception e) {
				future.complete(SyncResult.failed(tag, e.getMessage()));
			}
		});
		return future;
	}

	public synchronized boolean deleteManaged(ResourceLocation id, boolean eraseProjectiles) {
		if (SpellRegistry.hasDefault(id) || SpellRegistry.getOrigin(id) != SpellRegistry.Origin.MARKET) return false;
		String key = manifest.entries.entrySet().stream()
				.filter(e -> e.getValue().localSpellId.equals(id.toString()))
				.map(Map.Entry::getKey).findFirst().orElse(null);
		if (key == null) return false;
		try {
			MarketImportManifest next = new MarketImportManifest();
			next.entries.putAll(manifest.entries);
			next.entries.remove(key);
			SpellMarketStorage.saveManifest(server, next);
			SpellRuntimeAccess.stop(server, id, eraseProjectiles);
			SpellRegistry.remove(id);
			SpellMarketStorage.deleteSpell(server, id);
			manifest = next;
			return true;
		} catch (Exception e) {
			LOGGER.error("Failed to delete managed market spell {}", id, e);
			return false;
		}
	}

	private CompletableFuture<List<SpellListEntry>> fetchExactTag(String tag) {
		return fetchPage(tag, 1, new ArrayList<>());
	}

	private CompletableFuture<List<SpellListEntry>> fetchPage(String tag, int page, List<SpellListEntry> collected) {
		return http.listByExactTag(page, 100, tag).thenCompose(response -> {
			if (response == null || response.spells == null) throw new IllegalStateException("Malformed market list response");
			for (SpellListEntry entry : response.spells) {
				if (entry.tags != null && entry.tags.stream().anyMatch(tag::equals)) collected.add(entry);
			}
			int max = YHModConfig.COMMON.spellMarketMaxSpellsPerTag.get();
			if (collected.size() > max) throw new IllegalArgumentException("Exact tag exceeds configured spell cap " + max);
			int pages = Math.max(1, (response.total + Math.max(1, response.perPage) - 1) / Math.max(1, response.perPage));
			if (page < pages && page < 20) return fetchPage(tag, page + 1, collected);
			if (page >= 20 && page < pages) throw new IllegalArgumentException("Market pagination exceeds 20 pages");
			return CompletableFuture.completedFuture(collected);
		});
	}

	private CompletableFuture<List<StagedSpell>> stage(String tag, List<SpellListEntry> entries) {
		List<CompletableFuture<StagedSpell>> futures = new ArrayList<>();
		Set<String> uuids = new LinkedHashSet<>();
		for (SpellListEntry entry : entries) {
			if (entry.uuid == null || entry.uuid.isBlank() || !uuids.add(entry.uuid)) {
				return CompletableFuture.failedFuture(new IllegalArgumentException("Duplicate or missing market UUID"));
			}
			futures.add(http.download(entry.uuid, entry.contentHash).thenApply(download -> parseStaged(tag, entry, download)));
		}
		return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
				.thenApply(ignored -> futures.stream().map(CompletableFuture::join).toList());
	}

	private StagedSpell parseStaged(String tag, SpellListEntry entry, SpellMarketHttpClient.DownloadedSpell download) {
		var json = JsonParser.parseString(download.json());
		SpellDefinition definition = SpellDefinition.CODEC.parse(JsonOps.INSTANCE, json)
				.getOrThrow(false, message -> LOGGER.warn("Market spell {} parse error: {}", entry.uuid, message));
		SpellMarketValidator.validate(download.json(), json, definition);
		return new StagedSpell(entry, definition, download.json(), download.sha256(), tag);
	}

	private CompletableFuture<SyncResult> applyOnServerThread(String tag, List<StagedSpell> staged) {
		CompletableFuture<SyncResult> future = new CompletableFuture<>();
		server.execute(() -> {
			try {
				future.complete(applyStaged(tag, staged));
			} catch (Exception e) {
				future.completeExceptionally(e);
			}
		});
		return future;
	}

	private synchronized SyncResult applyStaged(String tag, List<StagedSpell> staged) throws Exception {
		Map<String, MarketImportManifest.Entry> oldByUuid = new LinkedHashMap<>();
		for (var entry : manifest.entries.entrySet()) {
			if (entry.getValue().managedTag.equals(tag)) oldByUuid.put(entry.getKey(), entry.getValue());
		}
		Set<ResourceLocation> stagedIds = new LinkedHashSet<>();
		for (StagedSpell spell : staged) {
			ResourceLocation id = spell.definition.id;
			if (!stagedIds.add(id)) throw new IllegalArgumentException("Multiple market spells resolve to ID " + id);
			if (SpellRegistry.hasDefault(id)) throw new IllegalArgumentException("Market spell collides with built-in/KJS default " + id);
			SpellRegistry.Origin origin = SpellRegistry.getOrigin(id);
			if (origin == SpellRegistry.Origin.CUSTOM) throw new IllegalArgumentException("Market spell collides with custom spell " + id);
			if (origin == SpellRegistry.Origin.MARKET && !ownedByTag(id, tag)) {
				throw new IllegalArgumentException("Market spell ID is owned by another managed pool: " + id);
			}
		}

		MarketImportManifest next = copyManifestWithoutTag(tag);
		int added = 0, updated = 0, unchanged = 0;
		long importTime = Instant.now().toEpochMilli();
		for (StagedSpell spell : staged) {
			MarketImportManifest.Entry old = oldByUuid.get(spell.entry.uuid);
			MarketImportManifest.Entry entry = new MarketImportManifest.Entry();
			entry.marketUuid = spell.entry.uuid;
			entry.localSpellId = spell.definition.id.toString();
			entry.exactTags = spell.entry.tags == null ? List.of() : List.copyOf(spell.entry.tags);
			entry.managedTag = tag;
			entry.updatedAt = spell.entry.updatedAt != 0 ? spell.entry.updatedAt : spell.entry.uploadDate;
			entry.contentHash = spell.hash;
			entry.importTime = importTime;
			next.entries.put(entry.marketUuid, entry);
			SpellMarketStorage.saveSpell(server, spell.definition, spell.rawJson);
			if (old == null) added++;
			else if (old.contentHash.equalsIgnoreCase(spell.hash) && old.localSpellId.equals(entry.localSpellId)) unchanged++;
			else updated++;
		}
		SpellMarketStorage.saveManifest(server, next);

		Set<String> incomingUuids = staged.stream().map(s -> s.entry.uuid).collect(java.util.stream.Collectors.toSet());
		int removed = 0;
		for (var old : oldByUuid.entrySet()) {
			if (incomingUuids.contains(old.getKey())) continue;
			ResourceLocation id = ResourceLocation.tryParse(old.getValue().localSpellId);
			if (id != null && !SpellRegistry.hasDefault(id)) {
				SpellRuntimeAccess.stop(server, id, true);
				SpellRegistry.remove(id);
				SpellMarketStorage.deleteSpell(server, id);
			}
			removed++;
		}
		for (StagedSpell spell : staged) {
			MarketImportManifest.Entry old = oldByUuid.get(spell.entry.uuid);
			if (old != null && !old.localSpellId.equals(spell.definition.id.toString())) {
				ResourceLocation oldId = ResourceLocation.tryParse(old.localSpellId);
				if (oldId != null) {
					SpellRuntimeAccess.stop(server, oldId, true);
					SpellRegistry.remove(oldId);
					SpellMarketStorage.deleteSpell(server, oldId);
				}
			}
			SpellRegistry.registerMarket(spell.definition);
			if (old != null && !old.contentHash.equalsIgnoreCase(spell.hash)) {
				SpellRuntimeAccess.reapply(server, spell.definition.id, true);
			}
		}
		manifest = next;
		SyncResult result = new SyncResult(tag, true, added, updated, unchanged, removed, 0, List.of());
		LOGGER.info("Market sync {}: added={}, updated={}, unchanged={}, removed={}", tag, added, updated, unchanged, removed);
		return result;
	}

	private boolean ownedByTag(ResourceLocation id, String tag) {
		return manifest.entries.values().stream().anyMatch(e -> e.managedTag.equals(tag) && e.localSpellId.equals(id.toString()));
	}

	private MarketImportManifest copyManifestWithoutTag(String tag) {
		MarketImportManifest copy = new MarketImportManifest();
		for (var entry : manifest.entries.entrySet()) {
			if (!entry.getValue().managedTag.equals(tag)) copy.entries.put(entry.getKey(), entry.getValue());
		}
		return copy;
	}

	private static String normalizeTag(String tag) {
		if (tag == null || tag.isBlank()) throw new IllegalArgumentException("Market tag may not be blank");
		String normalized = tag.trim();
		if (normalized.length() > 64) throw new IllegalArgumentException("Market tag exceeds 64 characters");
		return normalized;
	}

	private static Throwable unwrap(Throwable error) {
		while ((error instanceof java.util.concurrent.CompletionException || error instanceof java.util.concurrent.ExecutionException)
				&& error.getCause() != null) error = error.getCause();
		return error;
	}

	private record StagedSpell(SpellListEntry entry, SpellDefinition definition, String rawJson, String hash, String tag) {
	}

	public static class SyncResult {
		public final String tag;
		public final boolean success;
		public final int added;
		public final int updated;
		public final int unchanged;
		public final int removed;
		public final int rejected;
		public final List<String> errors;

		public SyncResult(String tag, boolean success, int added, int updated, int unchanged, int removed, int rejected, List<String> errors) {
			this.tag = tag;
			this.success = success;
			this.added = added;
			this.updated = updated;
			this.unchanged = unchanged;
			this.removed = removed;
			this.rejected = rejected;
			this.errors = errors;
		}

		public static SyncResult failed(String tag, String error) {
			return new SyncResult(tag, false, 0, 0, 0, 0, 1, List.of(error == null ? "Unknown market error" : error));
		}
	}
}
