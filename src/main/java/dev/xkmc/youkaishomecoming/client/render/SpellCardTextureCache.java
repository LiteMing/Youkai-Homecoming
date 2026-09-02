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
	private static final Map<String, DynamicTexture> GUI_DYNAMIC_TEXTURES = new ConcurrentHashMap<>();
	private static final Map<String, ResourceLocation> GUI_TEXTURES = new ConcurrentHashMap<>();
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
		return loadLocalSnapshot(spellId.toString(), false);
	}

	@Nullable
	public static ResourceLocation getLocalGuiBySpellId(ResourceLocation spellId) {
		if (spellId == null) return null;
		String key = toStorageKey(spellId.toString());
		loadLocalSnapshot(spellId.toString(), false);
		return ensureGuiTexture(key);
	}

	/**
	 * 按已认证的 definitionHash 查找或向服务端请求快照
	 */
	@Nullable
	public static ResourceLocation getOrRequestCertified(String definitionHash) {
		if (definitionHash == null || definitionHash.isBlank()) return null;
		ResourceLocation loc = loadLocalSnapshot(definitionHash, true);
		if (loc != null) return loc;

		// 向服务端发送原始 definitionHash 请求
		if (PENDING_REQUESTS.add(definitionHash)) {
			YoukaisHomecoming.HANDLER.toServer(new CertifiedSpellSnapshotRequestToServer(definitionHash));
		}
		return null;
	}

	@Nullable
	public static ResourceLocation getOrRequestCertifiedGui(String definitionHash) {
		if (definitionHash == null || definitionHash.isBlank()) return null;
		String key = toStorageKey(definitionHash);
		ResourceLocation gui = GUI_TEXTURES.get(key);
		if (gui != null) return gui;
		getOrRequestCertified(definitionHash);
		return ensureGuiTexture(key);
	}

	@Nullable
	private static ResourceLocation loadLocalSnapshot(String rawKey, boolean migrateLegacyHashFile) {
		String storageKey = toStorageKey(rawKey);
		ResourceLocation loc = TEXTURES.get(storageKey);
		if (loc != null) return loc;
		Path snapshotDir = Minecraft.getInstance().gameDirectory.toPath().resolve("spell_snapshots");
		Path localFile = snapshotDir.resolve(storageKey + ".png");
		if (!Files.isRegularFile(localFile) && migrateLegacyHashFile
				&& rawKey.matches("[0-9a-fA-F]{64}")) {
			// Older builds wrote the canonical hash without applying toStorageKey.
			// Migrate that file on first read so persistence survives the hotfix.
			Path legacyFile = snapshotDir.resolve(rawKey + ".png");
			if (Files.isRegularFile(legacyFile)) {
				localFile = legacyFile;
				try {
					Files.createDirectories(snapshotDir);
					Files.copy(legacyFile, snapshotDir.resolve(storageKey + ".png"),
							java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				} catch (Exception ignored) {
				}
			}
		}
		if (!Files.isRegularFile(localFile)) return null;
		try {
			return registerTextureByStorageKey(storageKey, Files.readAllBytes(localFile));
		} catch (Exception ignored) {
			return null;
		}
	}

	public static void onSnapshotReceived(String definitionHash, byte[] pngBytes) {
		if (definitionHash == null || definitionHash.isBlank()) return;
		PENDING_REQUESTS.remove(definitionHash);
		saveLocalSnapshot(definitionHash, pngBytes);
	}

	/** Writes a snapshot under the same derived key used by all cache lookups. */
	@Nullable
	public static ResourceLocation saveLocalSnapshot(String rawKey, byte[] pngBytes) {
		if (rawKey == null || rawKey.isBlank() || pngBytes == null || pngBytes.length == 0) return null;
		String storageKey = toStorageKey(rawKey);
		Path outDir = Minecraft.getInstance().gameDirectory.toPath().resolve("spell_snapshots");
		try {
			Files.createDirectories(outDir);
			Files.write(outDir.resolve(storageKey + ".png"), pngBytes);
		} catch (Exception e) {
			YoukaisHomecoming.LOGGER.error("Failed to persist spell card snapshot: {}", storageKey, e);
			return null;
		}
		return registerTextureByStorageKey(storageKey, pngBytes);
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
			NativeImage guiImg = brighten(img);
			DynamicTexture oldDyn = DYNAMIC_TEXTURES.get(storageKey);
			if (oldDyn != null) {
				// 复用/更新已有动态纹理，避免显存泄漏
				oldDyn.setPixels(img);
				oldDyn.upload();
				DynamicTexture oldGui = GUI_DYNAMIC_TEXTURES.get(storageKey);
				if (oldGui != null) {
					oldGui.setPixels(guiImg);
					oldGui.upload();
				} else {
					registerGuiTexture(storageKey, guiImg);
				}
				return TEXTURES.get(storageKey);
			} else {
				DynamicTexture dyn = new DynamicTexture(img);
				ResourceLocation loc = Minecraft.getInstance().getTextureManager().register("spell_card_" + storageKey, dyn);
				DYNAMIC_TEXTURES.put(storageKey, dyn);
				TEXTURES.put(storageKey, loc);
				registerGuiTexture(storageKey, guiImg);
				return loc;
			}
		} catch (Exception e) {
			YoukaisHomecoming.LOGGER.error("Failed to load spell card dynamic texture: {}", storageKey, e);
			return null;
		}
	}

	private static void registerGuiTexture(String storageKey, NativeImage image) {
		DynamicTexture dyn = new DynamicTexture(image);
		ResourceLocation loc = Minecraft.getInstance().getTextureManager().register("spell_card_gui_" + storageKey, dyn);
		GUI_DYNAMIC_TEXTURES.put(storageKey, dyn);
		GUI_TEXTURES.put(storageKey, loc);
	}

	@Nullable
	private static ResourceLocation ensureGuiTexture(String storageKey) {
		ResourceLocation existing = GUI_TEXTURES.get(storageKey);
		if (existing != null) return existing;
		DynamicTexture source = DYNAMIC_TEXTURES.get(storageKey);
		if (source == null || source.getPixels() == null) return null;
		registerGuiTexture(storageKey, brighten(source.getPixels()));
		return GUI_TEXTURES.get(storageKey);
	}

	private static NativeImage brighten(NativeImage source) {
		NativeImage result = new NativeImage(source.getWidth(), source.getHeight(), false);
		for (int y = 0; y < source.getHeight(); y++) {
			for (int x = 0; x < source.getWidth(); x++) {
				int pixel = source.getPixelRGBA(x, y);
				int r = brightenChannel(pixel & 0xFF);
				int g = brightenChannel((pixel >>> 8) & 0xFF);
				int b = brightenChannel((pixel >>> 16) & 0xFF);
				result.setPixelRGBA(x, y, (pixel & 0xFF000000) | r | (g << 8) | (b << 16));
			}
		}
		return result;
	}

	private static int brightenChannel(int value) {
		if (value == 0) return 0;
		return Math.min(255, value * 2 + 12);
	}

	public static void invalidate(String rawKey) {
		if (rawKey == null || rawKey.isBlank()) return;
		String storageKey = toStorageKey(rawKey);
		DYNAMIC_TEXTURES.remove(storageKey);
		DynamicTexture gui = GUI_DYNAMIC_TEXTURES.remove(storageKey);
		if (gui != null) gui.close();
		ResourceLocation loc = TEXTURES.remove(storageKey);
		if (loc != null) {
			Minecraft.getInstance().getTextureManager().release(loc);
		}
		ResourceLocation guiLoc = GUI_TEXTURES.remove(storageKey);
		if (guiLoc != null) Minecraft.getInstance().getTextureManager().release(guiLoc);
	}
}
