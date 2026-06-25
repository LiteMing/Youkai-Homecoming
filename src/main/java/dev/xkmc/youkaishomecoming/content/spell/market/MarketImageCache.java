package dev.xkmc.youkaishomecoming.content.spell.market;

import com.mojang.blaze3d.platform.NativeImage;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

@OnlyIn(Dist.CLIENT)
public class MarketImageCache {

	private static final Logger LOGGER = LoggerFactory.getLogger("SpellMarket/ImageCache");
	private static final int MAX_IMAGE_BYTES = 4 * 1024 * 1024;
	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.executor(Executors.newFixedThreadPool(2))
			.build();
	private static final Map<String, Preview> CACHE = new ConcurrentHashMap<>();

	public static Preview get(String rawUrl) {
		String url = normalize(rawUrl);
		if (url == null) {
			return Preview.invalid();
		}
		return CACHE.computeIfAbsent(url, MarketImageCache::load);
	}

	private static Preview load(String url) {
		Preview preview = new Preview(url);
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(Duration.ofSeconds(12))
				.header("Accept", "image/png,image/jpeg,image/webp,image/gif,*/*")
				.GET()
				.build();
		CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
				.thenAccept(response -> {
					if (response.statusCode() != 200) {
						preview.fail();
						return;
					}
					byte[] body = response.body();
					if (body == null || body.length == 0 || body.length > MAX_IMAGE_BYTES) {
						preview.fail();
						return;
					}
					Minecraft.getInstance().execute(() -> registerTexture(preview, body));
				})
				.exceptionally(e -> {
					LOGGER.warn("Failed to fetch comment image: {}", url, e);
					preview.fail();
					return null;
				});
		return preview;
	}

	private static void registerTexture(Preview preview, byte[] bytes) {
		try {
			NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
			ResourceLocation id = new ResourceLocation(YoukaisHomecoming.MODID,
					"spell_market/comment/" + Integer.toUnsignedString(preview.url.hashCode(), 16));
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
			preview.ready(id, image.getWidth(), image.getHeight());
		} catch (Exception e) {
			LOGGER.warn("Failed to decode comment image: {}", preview.url, e);
			preview.fail();
		}
	}

	private static String normalize(String rawUrl) {
		if (rawUrl == null || rawUrl.isBlank()) {
			return null;
		}
		String value = rawUrl.trim();
		try {
			URI uri = URI.create(value);
			String scheme = uri.getScheme();
			if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
				return null;
			}
			if (uri.getHost() == null || uri.getHost().isBlank()) {
				return null;
			}
			return uri.toString();
		} catch (Exception e) {
			return null;
		}
	}

	public static class Preview {
		private static final Preview INVALID = new Preview("");

		public enum State {
			LOADING,
			READY,
			FAILED
		}

		private final String url;
		private volatile State state = State.LOADING;
		private volatile ResourceLocation texture;
		private volatile int width;
		private volatile int height;

		private Preview(String url) {
			this.url = url;
			if (url.isBlank()) {
				this.state = State.FAILED;
			}
		}

		private static Preview invalid() {
			return INVALID;
		}

		private void ready(ResourceLocation texture, int width, int height) {
			this.texture = texture;
			this.width = width;
			this.height = height;
			this.state = State.READY;
		}

		private void fail() {
			this.state = State.FAILED;
		}

		public State state() {
			return state;
		}

		public ResourceLocation texture() {
			return texture;
		}

		public int width() {
			return width;
		}

		public int height() {
			return height;
		}
	}
}
