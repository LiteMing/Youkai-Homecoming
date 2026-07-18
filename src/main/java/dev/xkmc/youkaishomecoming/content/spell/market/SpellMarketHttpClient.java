package dev.xkmc.youkaishomecoming.content.spell.market;

import com.google.gson.Gson;
import dev.xkmc.youkaishomecoming.content.spell.market.dto.SpellListResponse;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Common, side-safe asynchronous transport for market list and spell downloads. */
public class SpellMarketHttpClient {

	public static final int MAX_LIST_BYTES = 2 * 1024 * 1024;
	public static final int MAX_SPELL_BYTES = 1024 * 1024;
	private static final Gson GSON = new Gson();
	private static final Executor EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
		Thread thread = new Thread(runnable, "YH-Spell-Market-HTTP");
		thread.setDaemon(true);
		return thread;
	});

	private final HttpClient client;
	private final String baseUrl;

	public SpellMarketHttpClient(String baseUrl) {
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.client = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(5))
				.executor(EXECUTOR)
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
	}

	public String baseUrl() {
		return baseUrl;
	}

	public boolean usesHttps() {
		return "https".equalsIgnoreCase(URI.create(baseUrl).getScheme());
	}

	public CompletableFuture<SpellListResponse> listByExactTag(int page, int perPage, String tag) {
		String url = baseUrl + "/spells?page=" + page + "&per_page=" + perPage +
				"&tag=" + encode(tag);
		return sendBytes(url, Duration.ofSeconds(15), MAX_LIST_BYTES).thenApply(bytes ->
				GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), SpellListResponse.class));
	}

	public CompletableFuture<DownloadedSpell> download(String uuid, String expectedHash) {
		String url = baseUrl + "/spells/" + encode(uuid) + "/download";
		return sendBytes(url, Duration.ofSeconds(30), MAX_SPELL_BYTES).thenApply(bytes -> {
			String hash = sha256(bytes);
			if (expectedHash != null && !expectedHash.isBlank() && !hash.equalsIgnoreCase(normalizeHash(expectedHash))) {
				throw new IllegalArgumentException("Content hash mismatch for market spell " + uuid);
			}
			return new DownloadedSpell(new String(bytes, StandardCharsets.UTF_8), hash);
		});
	}

	private CompletableFuture<byte[]> sendBytes(String url, Duration timeout, int maxBytes) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(timeout)
				.header("Accept", "application/json")
				.header("User-Agent", "YoukaiHomecoming-SpellMarket/2")
				.GET().build();
		return client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).thenApply(response -> {
			if (response.statusCode() != 200) {
				try {
					response.body().close();
				} catch (Exception ignored) {
				}
				throw new IllegalStateException("Market HTTP " + response.statusCode() + " for " + url);
			}
			try (InputStream stream = response.body()) {
				byte[] body = stream.readNBytes(maxBytes + 1);
				if (body.length > maxBytes) {
					throw new IllegalArgumentException("Market response exceeds " + maxBytes + " bytes");
				}
				return body;
			} catch (java.io.IOException e) {
				throw new IllegalStateException("Failed to read market response", e);
			}
		});
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (Exception e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}

	private static String normalizeHash(String hash) {
		int split = hash.indexOf(':');
		return split >= 0 ? hash.substring(split + 1) : hash;
	}

	public record DownloadedSpell(String json, String sha256) {
	}
}
