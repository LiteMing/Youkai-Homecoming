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

	public static String sanitizeKey(String rawKey) {
		if (rawKey == null) return "";
		// 使用 SHA-256 哈希确保绝对无碰撞与合法跨平台文件名
		return org.apache.commons.codec.digest.DigestUtils.sha256Hex(rawKey);
	}

	@Nullable
	public static ResourceLocation getOrRequest(String key) {
		if (key == null || key.isBlank()) return null;
		String safeKey = sanitizeKey(key);
		ResourceLocation loc = TEXTURES.get(safeKey);
		if (loc != null) return loc;

		// 检查本地游戏目录缓存
		Path localFile = Minecraft.getInstance().gameDirectory.toPath().resolve("spell_snapshots").resolve(safeKey + ".png");
		if (Files.isRegularFile(localFile)) {
			try {
				byte[] bytes = Files.readAllBytes(localFile);
				return registerTexture(safeKey, bytes);
			} catch (Exception ignored) {
			}
		}

		// 向服务端请求
		if (PENDING_REQUESTS.add(safeKey)) {
			YoukaisHomecoming.HANDLER.toServer(new CertifiedSpellSnapshotRequestToServer(safeKey));
		}
		return null;
	}

	public static void onSnapshotReceived(String key, byte[] pngBytes) {
		String safeKey = sanitizeKey(key);
		PENDING_REQUESTS.remove(safeKey);
		registerTexture(safeKey, pngBytes);
	}

	@Nullable
	public static ResourceLocation registerTexture(String key, byte[] pngBytes) {
		if (pngBytes == null || pngBytes.length == 0 || key == null || key.isBlank()) return null;
		String safeKey = sanitizeKey(key);
		try {
			NativeImage img = NativeImage.read(new ByteArrayInputStream(pngBytes));
			DynamicTexture oldDyn = DYNAMIC_TEXTURES.get(safeKey);
			if (oldDyn != null) {
				// 复用/更新已有动态纹理，避免显存泄漏
				oldDyn.setPixels(img);
				oldDyn.upload();
				return TEXTURES.get(safeKey);
			} else {
				DynamicTexture dyn = new DynamicTexture(img);
				ResourceLocation loc = Minecraft.getInstance().getTextureManager().register("spell_card_" + safeKey, dyn);
				DYNAMIC_TEXTURES.put(safeKey, dyn);
				TEXTURES.put(safeKey, loc);
				return loc;
			}
		} catch (Exception e) {
			YoukaisHomecoming.LOGGER.error("Failed to load spell card dynamic texture: {}", safeKey, e);
			return null;
		}
	}

	public static void invalidate(String key) {
		if (key == null || key.isBlank()) return;
		String safeKey = sanitizeKey(key);
		DYNAMIC_TEXTURES.remove(safeKey);
		ResourceLocation loc = TEXTURES.remove(safeKey);
		if (loc != null) {
			Minecraft.getInstance().getTextureManager().release(loc);
		}
	}
}
