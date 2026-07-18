package dev.xkmc.youkaishomecoming.content.spell.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.market.dto.*;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@OnlyIn(Dist.CLIENT)
public class SpellMarketAPI {

	private static final Logger LOGGER = LoggerFactory.getLogger("SpellMarket");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Executor EXECUTOR = Executors.newFixedThreadPool(2);

	private final HttpClient client;
	private final SpellMarketHttpClient transport;
	private final String baseUrl;
	private final SpellMarketRateLimiter rateLimiter;

	public SpellMarketAPI(String baseUrl) {
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.client = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(5))
				.executor(EXECUTOR)
				.build();
		this.transport = new SpellMarketHttpClient(this.baseUrl);
		this.rateLimiter = new SpellMarketRateLimiter();
	}

	public SpellMarketRateLimiter getRateLimiter() {
		return rateLimiter;
	}

	public CompletableFuture<SpellListResponse> getSpellList(int page, int perPage, String search, String sort) {
		return getSpellList(page, perPage, search, sort, null, null);
	}

	public CompletableFuture<SpellListResponse> getSpellList(int page, int perPage, String search, String sort, String authorUuid, String authorName) {
		if (!rateLimiter.canSearch()) {
			return CompletableFuture.completedFuture(null);
		}
		rateLimiter.markSearch();

		StringBuilder url = new StringBuilder(baseUrl + "/spells?page=" + page + "&per_page=" + perPage);
		if (search != null && !search.isEmpty()) {
			url.append("&search=").append(urlEncode(search));
		}
		if (sort != null && !sort.isEmpty()) {
			url.append("&sort=").append(urlEncode(sort));
		}
		if (authorUuid != null && !authorUuid.isEmpty()) {
			url.append("&author_uuid=").append(urlEncode(authorUuid));
		} else if (authorName != null && !authorName.isEmpty()) {
			url.append("&author_name=").append(urlEncode(authorName));
		}

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url.toString()))
				.timeout(Duration.ofSeconds(10))
				.GET()
				.build();

		return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(response -> {
					if (response.statusCode() == 200) {
						return GSON.fromJson(response.body(), SpellListResponse.class);
					}
					LOGGER.warn("Failed to fetch spell list: HTTP {}", response.statusCode());
					return null;
				})
				.exceptionally(e -> {
					LOGGER.error("Error fetching spell list", e);
					return null;
				});
	}

	public CompletableFuture<SpellDefinition> downloadSpell(String uuid) {
		return transport.download(uuid, null)
				.thenApply(download -> {
					try {
						var json = GSON.fromJson(download.json(), com.google.gson.JsonElement.class);
						return SpellDefinition.CODEC.parse(JsonOps.INSTANCE, json)
								.getOrThrow(false, err -> LOGGER.warn("Parse error: {}", err));
					} catch (Exception e) {
						LOGGER.error("Error parsing spell", e);
						return null;
					}
				})
				.exceptionally(e -> {
					LOGGER.error("Error downloading spell", e);
					return null;
				});
	}

	public CompletableFuture<SpellDetail> getSpellDetail(String uuid) {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/spells/" + pathSegment(uuid)))
				.timeout(Duration.ofSeconds(10))
				.GET()
				.build();

		return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(response -> {
					if (response.statusCode() == 200) {
						return GSON.fromJson(response.body(), SpellDetail.class);
					}
					LOGGER.warn("Failed to fetch spell detail: HTTP {}", response.statusCode());
					return null;
				})
				.exceptionally(e -> {
					LOGGER.error("Error fetching spell detail", e);
					return null;
				});
	}

	public CompletableFuture<UploadResponse> uploadSpell(
			SpellDefinition definition, String name, String description,
			String authorName, String authorUuid, String category, List<String> tags) {
		if (!rateLimiter.canUpload()) {
			return CompletableFuture.completedFuture(null);
		}
		rateLimiter.markUpload();

		try {
			var json = SpellDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
					.getOrThrow(false, err -> LOGGER.error("Encode error: {}", err));
			String jsonString = GSON.toJson(json);

			String boundary = "----SpellMarketBoundary" + UUID.randomUUID();
			StringBuilder body = new StringBuilder();

			body.append("--").append(boundary).append("\r\n");
			body.append("Content-Disposition: form-data; name=\"file\"; filename=\"spell.json\"\r\n");
			body.append("Content-Type: application/json\r\n\r\n");
			body.append(jsonString).append("\r\n");

			addFormField(body, boundary, "name", name);
			addFormField(body, boundary, "description", description);
			addFormField(body, boundary, "author_name", authorName);
			addFormField(body, boundary, "author_uuid", authorUuid != null ? authorUuid : "");
			addFormField(body, boundary, "category", category);
			addFormField(body, boundary, "tags", GSON.toJson(tags));

			body.append("--").append(boundary).append("--\r\n");

			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(baseUrl + "/spells/upload"))
					.timeout(Duration.ofSeconds(30))
					.header("Content-Type", "multipart/form-data; boundary=" + boundary)
					.POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
					.build();

			return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
					.thenApply(response -> {
						if (response.statusCode() == 200) {
							return GSON.fromJson(response.body(), UploadResponse.class);
						}
						LOGGER.warn("Failed to upload spell: HTTP {} - {}", response.statusCode(), response.body());
						return null;
					})
					.exceptionally(e -> {
						LOGGER.error("Error uploading spell", e);
						return null;
					});
		} catch (Exception e) {
			LOGGER.error("Error preparing upload", e);
			return CompletableFuture.completedFuture(null);
		}
	}

	public CompletableFuture<LikeResult> likeSpell(String uuid) {
		if (!rateLimiter.canLike(uuid)) {
			return CompletableFuture.completedFuture(LikeResult.ERROR);
		}
		rateLimiter.markLike(uuid);

		JsonObject body = new JsonObject();
		body.addProperty("fingerprint", getClientFingerprint());

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/spells/" + uuid + "/like"))
				.timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/json")
				.POST(jsonBody(body))
				.build();

		return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(response -> {
					if (response.statusCode() == 200) return LikeResult.SUCCESS;
					if (response.statusCode() == 400) return LikeResult.ALREADY_LIKED;
					LOGGER.warn("Failed to like spell: HTTP {}", response.statusCode());
					return LikeResult.ERROR;
				})
				.exceptionally(e -> {
					LOGGER.error("Error liking spell", e);
					return LikeResult.ERROR;
				});
	}

	public CompletableFuture<Boolean> unlikeSpell(String uuid) {
		JsonObject body = new JsonObject();
		body.addProperty("fingerprint", getClientFingerprint());

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/spells/" + uuid + "/like"))
				.timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/json")
				.method("DELETE", jsonBody(body))
				.build();

		return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(response -> {
					if (response.statusCode() == 200) return true;
					LOGGER.warn("Failed to unlike spell: HTTP {}", response.statusCode());
					return false;
				})
				.exceptionally(e -> {
					LOGGER.error("Error unliking spell", e);
					return false;
				});
	}

	public CompletableFuture<Boolean> deleteSpell(String uuid, String authorUuid, String authorName) {
		JsonObject body = new JsonObject();
		body.addProperty("author_uuid", authorUuid);
		body.addProperty("author_name", authorName);

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/spells/" + uuid))
				.timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/json")
				.method("DELETE", jsonBody(body))
				.build();

		return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(response -> {
					if (response.statusCode() == 200) return true;
					LOGGER.warn("Failed to delete spell: HTTP {}", response.statusCode());
					return false;
				})
				.exceptionally(e -> {
					LOGGER.error("Error deleting spell", e);
					return false;
				});
	}

	public CompletableFuture<List<Comment>> getComments(String uuid) {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/spells/" + pathSegment(uuid) + "/comments"))
				.timeout(Duration.ofSeconds(10))
				.GET()
				.build();

		return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.<List<Comment>>thenApply(response -> {
					if (response.statusCode() == 200) {
						CommentsResponse commentsResponse = GSON.fromJson(response.body(), CommentsResponse.class);
						return commentsResponse != null && commentsResponse.comments != null ?
								commentsResponse.comments : List.<Comment>of();
					}
					return List.<Comment>of();
				})
				.exceptionally(e -> {
					LOGGER.error("Error fetching comments", e);
					return List.<Comment>of();
				});
	}

	public CompletableFuture<Boolean> addComment(String spellUuid, String content, String imageUrl,
												 String authorName, String authorUuid) {
		JsonObject body = new JsonObject();
		body.addProperty("author_name", authorName);
		body.addProperty("author_uuid", authorUuid);
		body.addProperty("content", content);
		if (imageUrl != null && !imageUrl.isBlank()) {
			body.addProperty("image_url", imageUrl);
		}

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/spells/" + pathSegment(spellUuid) + "/comments"))
				.timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/json")
				.POST(jsonBody(body))
				.build();

		return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(response -> {
					if (response.statusCode() == 200 || response.statusCode() == 201) return true;
					LOGGER.warn("Failed to add comment: HTTP {} - {}", response.statusCode(), response.body());
					return false;
				})
				.exceptionally(e -> {
					LOGGER.error("Error adding comment", e);
					return false;
				});
	}

	public CompletableFuture<Boolean> deleteComment(String spellUuid, String commentUuid,
													String authorUuid, String authorName) {
		JsonObject body = new JsonObject();
		body.addProperty("author_uuid", authorUuid);
		body.addProperty("author_name", authorName);

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/spells/" + pathSegment(spellUuid) + "/comments/" + pathSegment(commentUuid)))
				.timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/json")
				.method("DELETE", jsonBody(body))
				.build();

		return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(response -> {
					if (response.statusCode() == 200 || response.statusCode() == 204) return true;
					LOGGER.warn("Failed to delete comment: HTTP {} - {}", response.statusCode(), response.body());
					return false;
				})
				.exceptionally(e -> {
					LOGGER.error("Error deleting comment", e);
					return false;
				});
	}

	private String getClientFingerprint() {
		try {
			return Minecraft.getInstance().getUser().getProfileId().toString();
		} catch (Exception e) {
			return "anonymous";
		}
	}

	private String urlEncode(String str) {
		return java.net.URLEncoder.encode(str, StandardCharsets.UTF_8);
	}

	private String pathSegment(String str) {
		return urlEncode(str).replace("+", "%20");
	}

	private HttpRequest.BodyPublisher jsonBody(JsonObject json) {
		return HttpRequest.BodyPublishers.ofString(GSON.toJson(json), StandardCharsets.UTF_8);
	}

	private void addFormField(StringBuilder body, String boundary, String name, String value) {
		body.append("--").append(boundary).append("\r\n");
		body.append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n");
		body.append(safeFormValue(value)).append("\r\n");
	}

	private static String safeFormValue(String value) {
		return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ');
	}

}
