package dev.xkmc.youkaishomecoming.compat.kubejs.spell;

import dev.xkmc.youkaishomecoming.content.entity.danmaku.EntitySpellProxyEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.spell.SpellCardBlockHelper;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.market.MarketImportManifest;
import dev.xkmc.youkaishomecoming.content.spell.market.SpellMarketServerManager;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntimeHost;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class YHSpellMarket {

	public static SyncJob syncTag(MinecraftServer server, String tag, Map<String, Object> options) {
		boolean force = options != null && Boolean.TRUE.equals(options.get("force"));
		String jobId = UUID.randomUUID().toString();
		CompletableFuture<SpellMarketServerManager.SyncResult> future =
				SpellMarketServerManager.get(server).syncTag(tag, force);
		SyncJob job = new SyncJob(jobId, future);
		future.thenAccept(result -> server.execute(() -> {
			job.result = result;
			job.complete = true;
			if (YHSpellKubeJSEvents.MARKET_SYNC_COMPLETED.hasListeners()) {
				YHSpellKubeJSEvents.MARKET_SYNC_COMPLETED.post(new MarketSyncEventJS(jobId, result));
			}
		}));
		return job;
	}

	public static SyncJob syncTag(MinecraftServer server, String tag) {
		return syncTag(server, tag, Map.of());
	}

	public static List<String> listByTag(MinecraftServer server, String tag) {
		return SpellMarketServerManager.get(server).listByTag(tag).stream().map(e -> e.localSpellId).toList();
	}

	public static String randomByTag(MinecraftServer server, String tag) {
		List<String> ids = listByTag(server, tag);
		return ids.isEmpty() ? null : ids.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(ids.size()));
	}

	public static String origin(String spellId) {
		ResourceLocation id = parseId(spellId);
		SpellRegistry.Origin origin = SpellRegistry.getOrigin(id);
		return origin == null ? null : origin.name().toLowerCase(java.util.Locale.ROOT);
	}

	public static MarketImportManifest.Entry metadata(MinecraftServer server, String spellId) {
		return SpellMarketServerManager.get(server).metadata(parseId(spellId));
	}

	public static boolean setSpell(Entity entity, String spellId) {
		if (!(entity instanceof SpellRuntimeHost host)) return false;
		SpellDefinition definition = requireSpell(spellId);
		host.setSpellRuntime(new SpellRuntime(definition));
		host.syncSpellState();
		return true;
	}

	public static EntitySpellProxyEntity fireTemporary(Entity host, String spellId, int duration) {
		SpellDefinition definition = requireSpell(spellId);
		LivingEntity target = host instanceof Mob mob ? mob.getTarget() : null;
		return SpellCardBlockHelper.spawnProxy(host, definition, duration, target);
	}

	public static boolean stop(Entity entity, boolean eraseProjectiles) {
		if (!(entity instanceof SpellRuntimeHost host)) return false;
		if (eraseProjectiles) host.eraseDanmaku(null);
		if (entity instanceof YoukaiEntity youkai) youkai.spellCard = null;
		host.setSpellRuntime(null);
		host.syncSpellState();
		return true;
	}

	public static boolean unloadManaged(MinecraftServer server, String spellId, boolean eraseProjectiles) {
		return SpellMarketServerManager.get(server).deleteManaged(parseId(spellId), eraseProjectiles);
	}

	private static SpellDefinition requireSpell(String spellId) {
		ResourceLocation id = parseId(spellId);
		SpellDefinition definition = SpellRegistry.get(id);
		if (definition == null) throw new IllegalArgumentException("Unknown spell: " + id);
		return definition;
	}

	private static ResourceLocation parseId(String spellId) {
		ResourceLocation id = ResourceLocation.tryParse(spellId);
		if (id == null) throw new IllegalArgumentException("Invalid spell id: " + spellId);
		return id;
	}

	public static class SyncJob {
		public final String id;
		public volatile boolean complete;
		public volatile SpellMarketServerManager.SyncResult result;

		private SyncJob(String id, CompletableFuture<SpellMarketServerManager.SyncResult> future) {
			this.id = id;
		}
	}
}
