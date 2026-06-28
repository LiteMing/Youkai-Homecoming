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
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@OnlyIn(Dist.CLIENT)
public class MarketImageCache {

	private static final Logger LOGGER = LoggerFactory.getLogger("SpellMarket/ImageCache");
	private static final int MAX_IMAGE_BYTES = 4 * 1024 * 1024;
	private static final int MAX_GIF_FRAMES = 80;
	private static final int GIF_TEXTURE_MAX_SIZE = 512;
	private static final int MAX_CACHE_ENTRIES = 48;
	private static final Pattern META_IMAGE = Pattern.compile(
			"(?is)<meta[^>]+(?:property|name)=[\"'](?:og:image|twitter:image)[\"'][^>]+content=[\"']([^\"']+)[\"']");
	private static final Pattern IMG_SRC = Pattern.compile("(?is)<img[^>]+src=[\"']([^\"']+)[\"']");
	private static final Pattern ABSOLUTE_IMAGE_URL = Pattern.compile(
			"(?is)https?://[^\"'<>\\s]+?\\.(?:png|jpe?g|webp|gif)(?:\\?[^\"'<>\\s]*)?");
	private static final Pattern ROOT_IMAGE_PATH = Pattern.compile(
			"(?is)/[^\"'<>\\s]+?\\.(?:png|jpe?g|webp|gif)(?:\\?[^\"'<>\\s]*)?");

	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.executor(Executors.newFixedThreadPool(2))
			.build();
	private static final Map<String, Preview> CACHE = Collections.synchronizedMap(
			new LinkedHashMap<String, Preview>(MAX_CACHE_ENTRIES, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<String, Preview> eldest) {
					boolean remove = size() > MAX_CACHE_ENTRIES;
					if (remove) {
						eldest.getValue().close();
					}
					return remove;
				}
			});

	public static Preview get(String rawUrl) {
		String url = normalize(rawUrl);
		if (url == null) {
			return Preview.invalid();
		}
		synchronized (CACHE) {
			Preview preview = CACHE.get(url);
			if (preview == null) {
				preview = load(url);
				CACHE.put(url, preview);
			}
			return preview;
		}
	}

	public static void clear() {
		synchronized (CACHE) {
			for (Preview preview : CACHE.values()) {
				preview.close();
			}
			CACHE.clear();
		}
	}

	private static Preview load(String url) {
		Preview preview = new Preview(url);
		fetch(preview, candidates(url), 0);
		return preview;
	}

	private static void fetch(Preview preview, List<String> candidates, int index) {
		if (index >= candidates.size()) {
			preview.fail();
			return;
		}
		String url = candidates.get(index);
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(Duration.ofSeconds(12))
				.header("Accept", "image/png,image/jpeg,image/webp,image/gif,image/*,*/*")
				.header("User-Agent", "Mozilla/5.0 YoukaiHomecomingSpellMarket/1.0")
				.GET()
				.build();
		CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
				.thenAccept(response -> {
					byte[] body = response.body();
					if (response.statusCode() != 200 || body == null || body.length == 0 ||
							body.length > MAX_IMAGE_BYTES) {
						fetch(preview, candidates, index + 1);
						return;
					}
					String contentType = response.headers().firstValue("Content-Type").orElse("");
					if (isHtml(contentType, body)) {
						List<String> next = new ArrayList<>(candidates);
						for (String candidate : extractHtmlImages(url, body)) {
							addCandidate(next, candidate);
						}
						fetch(preview, next, index + 1);
						return;
					}
					try {
						DecodedImage decoded = decodeImage(body);
						Minecraft.getInstance().execute(() -> registerTexture(preview, decoded));
					} catch (Exception e) {
						LOGGER.warn("Failed to decode comment image from {}", url, e);
						fetch(preview, candidates, index + 1);
					}
				})
				.exceptionally(e -> {
					LOGGER.warn("Failed to fetch comment image: {}", url, e);
					fetch(preview, candidates, index + 1);
					return null;
				});
	}

	private static void registerTexture(Preview preview, DecodedImage decoded) {
		if (preview.closed()) {
			closeDecoded(decoded);
			return;
		}
		List<ResourceLocation> registered = new ArrayList<>();
		try {
			String hash = Integer.toUnsignedString(preview.url.hashCode(), 16);
			if (decoded.frames.size() == 1) {
				NativeImage image = decoded.frames.get(0).image;
				ResourceLocation id = new ResourceLocation(YoukaisHomecoming.MODID,
						"spell_market/comment/" + hash);
				Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
				registered.add(id);
				preview.ready(id, decoded.width, decoded.height);
				return;
			}
			List<Preview.Frame> frames = new ArrayList<>();
			for (int i = 0; i < decoded.frames.size(); i++) {
				DecodedFrame frame = decoded.frames.get(i);
				ResourceLocation id = new ResourceLocation(YoukaisHomecoming.MODID,
						"spell_market/comment/" + hash + "_" + i);
				Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(frame.image));
				registered.add(id);
				frames.add(new Preview.Frame(id, frame.delayMs));
			}
			preview.readyAnimated(frames, decoded.width, decoded.height);
		} catch (Exception e) {
			releaseTextures(registered);
			LOGGER.warn("Failed to register comment image texture: {}", preview.url, e);
			preview.fail();
		}
	}

	private static void closeDecoded(DecodedImage decoded) {
		for (DecodedFrame frame : decoded.frames) {
			frame.image.close();
		}
	}

	private static void releaseTextures(List<ResourceLocation> textures) {
		if (textures.isEmpty()) {
			return;
		}
		Minecraft.getInstance().execute(() -> {
			for (ResourceLocation texture : textures) {
				Minecraft.getInstance().getTextureManager().release(texture);
			}
		});
	}

	private static DecodedImage decodeImage(byte[] bytes) throws IOException {
		if (isGif(bytes)) {
			DecodedImage gif = decodeGif(bytes);
			if (gif != null && !gif.frames.isEmpty()) {
				return gif;
			}
		}
		NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
		return new DecodedImage(List.of(new DecodedFrame(image, 0)), image.getWidth(), image.getHeight());
	}

	private static DecodedImage decodeGif(byte[] bytes) throws IOException {
		Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
		ImageReader reader = readers.hasNext() ? readers.next() : null;
		if (reader == null) {
			return null;
		}
		try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
			if (stream == null) {
				return null;
			}
			reader.setInput(stream, false, false);
			Size screen = readScreenSize(reader.getStreamMetadata());
			if (screen.width <= 0 || screen.height <= 0) {
				BufferedImage first = reader.read(0);
				screen = new Size(first.getWidth(), first.getHeight());
			}
			if (screen.width > 4096 || screen.height > 4096 ||
					(long) screen.width * (long) screen.height > 8_388_608L) {
				return null;
			}
			Size textureSize = scaledSize(screen.width, screen.height, GIF_TEXTURE_MAX_SIZE);
			BufferedImage canvas = new BufferedImage(screen.width, screen.height, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = canvas.createGraphics();
			List<DecodedFrame> frames = new ArrayList<>();
			try {
				for (int i = 0; i < MAX_GIF_FRAMES; i++) {
					GifFrameMeta meta;
					BufferedImage frame;
					try {
						meta = readFrameMeta(reader.getImageMetadata(i), screen);
						frame = reader.read(i);
					} catch (IndexOutOfBoundsException | IOException e) {
						break;
					}
					BufferedImage restore = null;
					if ("restoreToPrevious".equals(meta.disposalMethod)) {
						restore = copyImage(canvas);
					}
					graphics.drawImage(frame, meta.left, meta.top, null);
					BufferedImage rendered = textureSize.width == screen.width && textureSize.height == screen.height ?
							canvas : scaleImage(canvas, textureSize.width, textureSize.height);
					frames.add(new DecodedFrame(toNativeImage(rendered), meta.delayMs));
					if ("restoreToBackgroundColor".equals(meta.disposalMethod)) {
						graphics.setComposite(AlphaComposite.Clear);
						graphics.fillRect(meta.left, meta.top, meta.width, meta.height);
						graphics.setComposite(AlphaComposite.SrcOver);
					} else if ("restoreToPrevious".equals(meta.disposalMethod) && restore != null) {
						graphics.setComposite(AlphaComposite.Src);
						graphics.drawImage(restore, 0, 0, null);
						graphics.setComposite(AlphaComposite.SrcOver);
					}
				}
			} finally {
				graphics.dispose();
			}
			return new DecodedImage(frames, textureSize.width, textureSize.height);
		} finally {
			reader.dispose();
		}
	}

	private static List<String> candidates(String url) {
		List<String> list = new ArrayList<>();
		addCandidate(list, url);
		return list;
	}

	private static void addCandidate(List<String> list, String url) {
		String normalized = normalize(url);
		if (normalized != null && !list.contains(normalized)) {
			list.add(normalized);
		}
	}

	private static List<String> extractHtmlImages(String baseUrl, byte[] body) {
		String html = new String(body, StandardCharsets.UTF_8).replace("\\/", "/");
		List<String> found = new ArrayList<>();
		addResolvedCandidate(found, baseUrl, firstMatch(META_IMAGE, html));
		addResolvedCandidate(found, baseUrl, firstMatch(IMG_SRC, html));
		addPatternCandidates(found, baseUrl, ABSOLUTE_IMAGE_URL, html);
		addPatternCandidates(found, baseUrl, ROOT_IMAGE_PATH, html);
		return found;
	}

	private static void addPatternCandidates(List<String> list, String baseUrl, Pattern pattern, String html) {
		Matcher matcher = pattern.matcher(html);
		while (matcher.find() && list.size() < 8) {
			addResolvedCandidate(list, baseUrl, matcher.group());
		}
	}

	private static void addResolvedCandidate(List<String> list, String baseUrl, String value) {
		if (value == null || value.isBlank()) {
			return;
		}
		try {
			String resolved = URI.create(baseUrl).resolve(value).toString();
			if (normalize(resolved) != null && !list.contains(resolved)) {
				list.add(resolved);
			}
		} catch (Exception e) {
		}
	}

	private static String firstMatch(Pattern pattern, String value) {
		Matcher matcher = pattern.matcher(value);
		return matcher.find() ? matcher.group(1) : null;
	}

	private static boolean isHtml(String contentType, byte[] body) {
		String lower = contentType == null ? "" : contentType.toLowerCase();
		if (lower.contains("text/html")) {
			return true;
		}
		for (byte b : body) {
			if (!Character.isWhitespace((char) b)) {
				return b == '<';
			}
		}
		return false;
	}

	private static boolean isGif(byte[] bytes) {
		return bytes.length >= 6 &&
				bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' &&
				bytes[3] == '8' && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a';
	}

	private static NativeImage toNativeImage(BufferedImage buffered) {
		NativeImage image = new NativeImage(buffered.getWidth(), buffered.getHeight(), false);
		for (int y = 0; y < buffered.getHeight(); y++) {
			for (int x = 0; x < buffered.getWidth(); x++) {
				int argb = buffered.getRGB(x, y);
				int a = (argb >>> 24) & 0xFF;
				int r = (argb >>> 16) & 0xFF;
				int g = (argb >>> 8) & 0xFF;
				int b = argb & 0xFF;
				image.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
			}
		}
		return image;
	}

	private static BufferedImage copyImage(BufferedImage source) {
		BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = copy.createGraphics();
		try {
			graphics.drawImage(source, 0, 0, null);
		} finally {
			graphics.dispose();
		}
		return copy;
	}

	private static BufferedImage scaleImage(BufferedImage source, int width, int height) {
		BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = scaled.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.drawImage(source, 0, 0, width, height, null);
		} finally {
			graphics.dispose();
		}
		return scaled;
	}

	private static Size scaledSize(int width, int height, int maxSize) {
		if (width <= maxSize && height <= maxSize) {
			return new Size(width, height);
		}
		float scale = Math.min(maxSize / (float) width, maxSize / (float) height);
		return new Size(Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale)));
	}

	private static Size readScreenSize(IIOMetadata metadata) {
		if (metadata == null) {
			return new Size(0, 0);
		}
		try {
			Node root = metadata.getAsTree("javax_imageio_gif_stream_1.0");
			Node node = findNode(root, "LogicalScreenDescriptor");
			return new Size(intAttr(node, "logicalScreenWidth", 0),
					intAttr(node, "logicalScreenHeight", 0));
		} catch (Exception e) {
			return new Size(0, 0);
		}
	}

	private static GifFrameMeta readFrameMeta(IIOMetadata metadata, Size fallback) {
		try {
			Node root = metadata.getAsTree("javax_imageio_gif_image_1.0");
			Node descriptor = findNode(root, "ImageDescriptor");
			Node gce = findNode(root, "GraphicControlExtension");
			int left = intAttr(descriptor, "imageLeftPosition", 0);
			int top = intAttr(descriptor, "imageTopPosition", 0);
			int width = intAttr(descriptor, "imageWidth", fallback.width);
			int height = intAttr(descriptor, "imageHeight", fallback.height);
			int delay = Math.max(20, intAttr(gce, "delayTime", 10) * 10);
			return new GifFrameMeta(left, top, width, height, delay,
					stringAttr(gce, "disposalMethod", "none"));
		} catch (Exception e) {
			return new GifFrameMeta(0, 0, fallback.width, fallback.height, 100, "none");
		}
	}

	private static Node findNode(Node node, String name) {
		if (node == null) {
			return null;
		}
		if (name.equals(node.getNodeName())) {
			return node;
		}
		NodeList children = node.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node found = findNode(children.item(i), name);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private static int intAttr(Node node, String name, int fallback) {
		String value = stringAttr(node, name, null);
		if (value == null || value.isBlank()) {
			return fallback;
		}
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static String stringAttr(Node node, String name, String fallback) {
		if (node == null) {
			return fallback;
		}
		NamedNodeMap attrs = node.getAttributes();
		if (attrs == null) {
			return fallback;
		}
		Node attr = attrs.getNamedItem(name);
		return attr == null ? fallback : attr.getNodeValue();
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

	private record DecodedImage(List<DecodedFrame> frames, int width, int height) {
	}

	private record DecodedFrame(NativeImage image, int delayMs) {
	}

	private record Size(int width, int height) {
	}

	private record GifFrameMeta(int left, int top, int width, int height, int delayMs, String disposalMethod) {
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
		private volatile List<Frame> frames = List.of();
		private volatile int width;
		private volatile int height;
		private volatile int totalDuration;
		private volatile long animationStart;
		private volatile boolean closed;

		private Preview(String url) {
			this.url = url;
			if (url.isBlank()) {
				this.state = State.FAILED;
			}
		}

		private static Preview invalid() {
			return INVALID;
		}

		private boolean closed() {
			return closed;
		}

		private void ready(ResourceLocation texture, int width, int height) {
			this.texture = texture;
			this.width = width;
			this.height = height;
			this.state = State.READY;
		}

		private void readyAnimated(List<Frame> frames, int width, int height) {
			this.frames = List.copyOf(frames);
			this.texture = frames.isEmpty() ? null : frames.get(0).texture;
			this.width = width;
			this.height = height;
			this.totalDuration = frames.stream().mapToInt(Frame::delayMs).sum();
			this.animationStart = System.currentTimeMillis();
			this.state = frames.isEmpty() ? State.FAILED : State.READY;
		}

		private void fail() {
			this.state = State.FAILED;
		}

		private void close() {
			if (closed) {
				return;
			}
			closed = true;
			List<ResourceLocation> textures = new ArrayList<>();
			List<Frame> activeFrames = frames;
			if (activeFrames.isEmpty()) {
				if (texture != null) {
					textures.add(texture);
				}
			} else {
				for (Frame frame : activeFrames) {
					textures.add(frame.texture);
				}
			}
			releaseTextures(textures);
		}

		public State state() {
			return state;
		}

		public ResourceLocation texture() {
			List<Frame> activeFrames = frames;
			if (activeFrames.isEmpty() || totalDuration <= 0) {
				return texture;
			}
			int elapsed = (int) ((System.currentTimeMillis() - animationStart) % totalDuration);
			int cursor = 0;
			for (Frame frame : activeFrames) {
				cursor += frame.delayMs;
				if (elapsed < cursor) {
					return frame.texture;
				}
			}
			return activeFrames.get(activeFrames.size() - 1).texture;
		}

		public int width() {
			return width;
		}

		public int height() {
			return height;
		}

		private record Frame(ResourceLocation texture, int delayMs) {
		}
	}
}
