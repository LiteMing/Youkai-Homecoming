package dev.xkmc.youkaishomecoming.compat.exposure;

import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.youkaishomecoming.compat.stg.YHStgApi;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuProxyEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.EntitySpellProxyEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.IYHDanmaku;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DanmakuItem;
import dev.xkmc.youkaishomecoming.content.item.danmaku.SpellReplicaFilmItem;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellHash;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellCardType;
import dev.xkmc.youkaishomecoming.content.spell.replica.SpellReplicaService;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared server-side capture transaction used by Exposure and TLM cameras. */
public final class DanmakuCaptureService {

	public static final double DEFAULT_RANGE = 128.0;
	public static final int DEFAULT_LIMIT = 500;

	private DanmakuCaptureService() {
	}

	public static EraseResult collect(ServerPlayer player, float fovDegrees) {
		EraseResult result = new EraseResult();
		if (!(player.level() instanceof ServerLevel level) || YHStgApi.getBomb(player) < 1.0) return result;
		Vec3 eyePos = player.getEyePosition();
		DanmakuFrustum frustum = new DanmakuFrustum(eyePos, player.getLookAngle(), fovDegrees);
		AABB searchBox = AABB.ofSize(eyePos, DEFAULT_RANGE * 2, DEFAULT_RANGE * 2, DEFAULT_RANGE * 2);

		for (YoukaiEntity youkai : level.getEntitiesOfClass(YoukaiEntity.class, searchBox)) {
			if (youkai.isOwnedBy(player)) continue;
			youkai.countDanmakuInFrustum(frustum, result.remaining(DEFAULT_LIMIT), result);
			if (result.getTotal() >= DEFAULT_LIMIT) return result;
		}
		for (DanmakuProxyEntity proxy : level.getEntitiesOfClass(DanmakuProxyEntity.class, searchBox)) {
			if (proxy.isOwnedBy(player)) continue;
			proxy.countDanmakuInFrustum(frustum, result.remaining(DEFAULT_LIMIT), result);
			if (result.getTotal() >= DEFAULT_LIMIT) return result;
		}
		for (EntitySpellProxyEntity proxy : level.getEntitiesOfClass(EntitySpellProxyEntity.class, searchBox)) {
			if (proxy.isOwnedBy(player)) continue;
			proxy.countDanmakuInFrustum(frustum, result.remaining(DEFAULT_LIMIT), result);
			if (result.getTotal() >= DEFAULT_LIMIT) return result;
		}
		for (Entity entity : level.getEntities(player, searchBox, e -> e instanceof IYHDanmaku)) {
			if (result.getTotal() >= DEFAULT_LIMIT) break;
			if (entity instanceof SimplifiedProjectile projectile && projectile.getOwner() != player
					&& frustum.contains(entity.position())) {
				String[] typeAndColor = getDanmakuTypeAndColor(entity);
				result.record(typeAndColor[0], typeAndColor[1], null, null, projectile);
			}
		}
		return result;
	}

	/** Extracts display statistics without loading either camera compatibility layer. */
	public static String[] getDanmakuTypeAndColor(Entity entity) {
		if (entity instanceof ItemDanmakuEntity ide && ide.getItem().getItem() instanceof DanmakuItem item) {
			return new String[]{item.type.name(), item.color.getName()};
		}
		var key = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
		return new String[]{key != null ? key.getPath() : "unknown", ""};
	}

	/** Checks one displayed Bomb, erases this exact batch, then charges only a successful capture. */
	public static CaptureOutcome commit(ServerPlayer player, EraseResult result) {
		if (result == null || result.isEmpty() || !result.hasLiveCandidates()
				|| YHStgApi.getBomb(player) < 1.0) return CaptureOutcome.EMPTY;
		int erased = result.eraseCandidates(player);
		if (erased <= 0) return CaptureOutcome.EMPTY;
		YHStgApi.addBomb(player, -1.0);
		advanceReplica(player, result);
		return new CaptureOutcome(erased, result.calculateScore());
	}

	private static void advanceReplica(ServerPlayer player, EraseResult result) {
		ItemStack film = findReplicaFilm(player);
		if (film.isEmpty()) return;
		ResourceLocation source = result.selectReplicaSource(SpellReplicaService.source(film),
				DanmakuCaptureService::isReplicableSource);
		if (source == null) return;
		var definition = SpellRegistry.get(source);
		if (definition == null) return;
		String hash;
		try {
			hash = SpellHash.canonicalHash(definition);
		} catch (RuntimeException exception) {
			YoukaisHomecoming.LOGGER.warn("[SpellReplica] source {} cannot be snapshotted: {}",
					source, exception.getMessage());
			return;
		}
		SpellReplicaService.record(film, source, hash, result.countForSource(source),
				YHModConfig.COMMON.spellReplicaRequiredDanmaku.get());
		SpellReplicaService.markInventoryChanged(player);
		if (SpellReplicaService.isComplete(film)) SpellReplicaService.completeIntoDraft(player, film);
	}

	private static boolean isReplicableSource(ResourceLocation source) {
		var definition = SpellRegistry.get(source);
		return definition != null && definition.itemForm.cardType() != SpellCardType.NON_SPELL;
	}

	private static ItemStack findReplicaFilm(ServerPlayer player) {
		for (ItemStack stack : player.getInventory().items) {
			if (stack.getItem() instanceof SpellReplicaFilmItem) return stack;
		}
		for (ItemStack stack : player.getInventory().offhand) {
			if (stack.getItem() instanceof SpellReplicaFilmItem) return stack;
		}
		return ItemStack.EMPTY;
	}

	public record CaptureOutcome(int erased, int score) {
		public static final CaptureOutcome EMPTY = new CaptureOutcome(0, 0);
		public boolean success() { return erased > 0; }
	}
}
