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

	private static final Map<String, ResourceLocation> TEXTURES = new ConcurrentHashMap<>();
	private static final Set<String> PENDING_REQUESTS = ConcurrentHashMap.newKeySet();

	private SpellCardTextureCache() {
	}

	@Nullable
	public static ResourceLocation getOrRequest(String hash) {
		if (hash == null || hash.isBlank()) return null;
		ResourceLocation loc = TEXTURES.get(hash);
		if (loc != null) return loc;

		// 检查本地本地游戏目录缓存
		Path localFile = Minecraft.getInstance().gameDirectory.toPath().resolve("spell_snapshots").resolve(hash + ".png");
		if (Files.isRegularFile(localFile)) {
			try {
				byte[] bytes = Files.readAllBytes(localFile);
				return registerTexture(hash, bytes);
			} catch (Exception ignored) {
			}
		}

		// 向服务端请求
		if (PENDING_REQUESTS.add(hash)) {
			YoukaisHomecoming.HANDLER.toServer(new CertifiedSpellSnapshotRequestToServer(hash));
		}
		return null;
	}

	public static void onSnapshotReceived(String hash, byte[] pngBytes) {
		PENDING_REQUESTS.remove(hash);
		registerTexture(hash, pngBytes);
	}

	@Nullable
	public static ResourceLocation registerTexture(String hash, byte[] pngBytes) {
		if (pngBytes == null || pngBytes.length == 0) return null;
		try {
			NativeImage img = NativeImage.read(new ByteArrayInputStream(pngBytes));
			DynamicTexture dyn = new DynamicTexture(img);
			ResourceLocation loc = Minecraft.getInstance().getTextureManager().register("spell_card_" + hash, dyn);
			TEXTURES.put(hash, loc);
			return loc;
		} catch (Exception e) {
			YoukaisHomecoming.LOGGER.error("Failed to load spell card dynamic texture: {}", hash, e);
			return null;
		}
	}
}
