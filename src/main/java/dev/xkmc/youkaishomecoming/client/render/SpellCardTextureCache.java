package dev.xkmc.youkaishomecoming.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.xkmc.youkaishomecoming.content.spell.certification.network.CertifiedSpellSnapshotRequestToServer;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端 84x128 符卡卡面材质管理器。
 * 当遇到带认证 hash 的符卡物品时，优先检查本地内存与本地文件缓存，
 * 若未找到则向服务端发起拉取并在收到后动态注册为 DynamicTexture。
 */
@OnlyIn(Dist.CLIENT)
public final class SpellCardTextureCache {

	private static final Map<String, DynamicTexture> DYNAMIC_TEXTURES = new ConcurrentHashMap<>();
	private static final Map<String, ResourceLocation> TEXTURES = new ConcurrentHashMap<>();
	private static final Set<String> PENDING_REQUESTS = ConcurrentHashMap.newKeySet();

	private SpellCardTextureCache() {
	}

	public static String toStorageKey(String rawKey) {
		if (rawKey == null) return "";
		// 使用 SHA-256 哈希确保绝对无碰撞与合法跨平台文件名
		return org.apache.commons.codec.digest.DigestUtils.sha256Hex(rawKey);
	}

	/**
	 * 按本地未认证的 spell_id (例如 "namespace:path") 查找本地快照
	 */
	@Nullable
	public static ResourceLocation getLocalBySpellId(ResourceLocation spellId) {
		if (spellId == null) return null;
		String storageKey = toStorageKey(spellId.toString());
		ResourceLocation loc = TEXTURES.get(storageKey);
		if (loc != null) return loc;

		Path localFile = Minecraft.getInstance().gameDirectory.toPath().resolve("spell_snapshots").resolve(storageKey + ".png");
		if (Files.isRegularFile(localFile)) {
			try {
				byte[] bytes = Files.readAllBytes(localFile);
				return registerTextureByStorageKey(storageKey, bytes);
			} catch (Exception ignored) {
			}
		}
		return null;
	}

	/**
	 * 按已认证的 definitionHash 查找或向服务端请求快照
	 */
	@Nullable
	public static ResourceLocation getOrRequestCertified(String definitionHash) {
		if (definitionHash == null || definitionHash.isBlank()) return null;
		String storageKey = toStorageKey(definitionHash);
		ResourceLocation loc = TEXTURES.get(storageKey);
		if (loc != null) return loc;

		// 检查本地游戏目录缓存
		Path localFile = Minecraft.getInstance().gameDirectory.toPath().resolve("spell_snapshots").resolve(storageKey + ".png");
		if (Files.isRegularFile(localFile)) {
			try {
				byte[] bytes = Files.readAllBytes(localFile);
				return registerTextureByStorageKey(storageKey, bytes);
			} catch (Exception ignored) {
			}
		}

		// 向服务端发送原始 definitionHash 请求
		if (PENDING_REQUESTS.add(definitionHash)) {
			YoukaisHomecoming.HANDLER.toServer(new CertifiedSpellSnapshotRequestToServer(definitionHash));
		}
		return null;
	}

	public static void onSnapshotReceived(String definitionHash, byte[] pngBytes) {
		if (definitionHash == null || definitionHash.isBlank()) return;
		PENDING_REQUESTS.remove(definitionHash);
		String storageKey = toStorageKey(definitionHash);
		registerTextureByStorageKey(storageKey, pngBytes);
	}

	@Nullable
	public static ResourceLocation registerTexture(String rawKey, byte[] pngBytes) {
		if (rawKey == null || rawKey.isBlank()) return null;
		String storageKey = toStorageKey(rawKey);
		return registerTextureByStorageKey(storageKey, pngBytes);
	}

	@Nullable
	private static ResourceLocation registerTextureByStorageKey(String storageKey, byte[] pngBytes) {
		if (pngBytes == null || pngBytes.length == 0 || storageKey == null || storageKey.isBlank()) return null;
		try {
			NativeImage img = NativeImage.read(new ByteArrayInputStream(pngBytes));
			DynamicTexture oldDyn = DYNAMIC_TEXTURES.get(storageKey);
			if (oldDyn != null) {
				// 复用/更新已有动态纹理，避免显存泄漏
				oldDyn.setPixels(img);
				oldDyn.upload();
				return TEXTURES.get(storageKey);
			} else {
				DynamicTexture dyn = new DynamicTexture(img);
				ResourceLocation loc = Minecraft.getInstance().getTextureManager().register("spell_card_" + storageKey, dyn);
				DYNAMIC_TEXTURES.put(storageKey, dyn);
				TEXTURES.put(storageKey, loc);
				return loc;
			}
		} catch (Exception e) {
			YoukaisHomecoming.LOGGER.error("Failed to load spell card dynamic texture: {}", storageKey, e);
			return null;
		}
	}

	public static void invalidate(String rawKey) {
		if (rawKey == null || rawKey.isBlank()) return;
		String storageKey = toStorageKey(rawKey);
		DYNAMIC_TEXTURES.remove(storageKey);
		ResourceLocation loc = TEXTURES.remove(storageKey);
		if (loc != null) {
			Minecraft.getInstance().getTextureManager().release(loc);
		}
	}
}
