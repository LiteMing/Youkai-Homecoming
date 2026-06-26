package dev.xkmc.youkaishomecoming.content.spell.market;

import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.market.dto.Comment;
import dev.xkmc.youkaishomecoming.content.spell.market.dto.SpellListEntry;
import dev.xkmc.youkaishomecoming.content.spell.preview.SpellEditorNetworkClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@OnlyIn(Dist.CLIENT)
public class SpellDetailScreen extends Screen {

	private static final int HEADER_TOP = 16;
	private static final int COMMENTS_TOP = 92;
	private static final int COMMENT_GAP = 6;
	private static final int IMAGE_STATUS_HEIGHT = 12;
	private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");

	private final Screen parent;
	private final SpellMarketAPI api;
	private final SpellListEntry entry;

	private final List<Comment> comments = new ArrayList<>();
	private EditBox commentBox;
	private EditBox imageBox;
	private Button postButton;
	private boolean loading = false;
	private boolean posting = false;
	private String errorMessage = null;
	private int scrollOffset = 0;

	public SpellDetailScreen(Screen parent, SpellListEntry entry) {
		super(Component.literal(entry.name == null || entry.name.isBlank() ?
				SpellMarketLocalization.detail().getString() : entry.name));
		this.parent = parent;
		this.entry = entry;
		this.api = SpellMarketManager.getInstance().getAPI();
	}

	@Override
	protected void init() {
		int topY = HEADER_TOP;
		addRenderableWidget(Button.builder(SpellMarketLocalization.back(), btn -> onClose())
				.bounds(20, topY, 54, 20).build());
		addRenderableWidget(Button.builder(SpellMarketLocalization.refresh(), btn -> loadComments())
				.bounds(80, topY, 58, 20).build());
		addRenderableWidget(Button.builder(SpellMarketLocalization.download(), btn -> downloadSpell())
				.bounds(width - 110, topY, 90, 20).build());

		int formY = Math.max(64, height - 56);
		commentBox = new EditBox(font, 20, formY, Math.max(80, width - 230), 20,
				SpellMarketLocalization.commentPlaceholder());
		commentBox.setMaxLength(500);
		commentBox.setHint(SpellMarketLocalization.commentPlaceholder());
		addRenderableWidget(commentBox);

		imageBox = new EditBox(font, 20, formY + 24, Math.max(80, width - 230), 20,
				SpellMarketLocalization.commentImage());
		imageBox.setMaxLength(300);
		imageBox.setHint(SpellMarketLocalization.commentImage());
		addRenderableWidget(imageBox);

		postButton = Button.builder(SpellMarketLocalization.commentPost(), btn -> postComment())
				.bounds(width - 195, formY + 12, 80, 20).build();
		addRenderableWidget(postButton);

		if (comments.isEmpty()) {
			loadComments();
		}
	}

	private void loadComments() {
		if (api == null || entry.uuid == null || entry.uuid.isBlank() || loading) return;
		loading = true;
		errorMessage = null;
		api.getComments(entry.uuid).thenAccept(list -> Minecraft.getInstance().execute(() -> {
			loading = false;
			comments.clear();
			if (list != null) {
				comments.addAll(list);
			}
			entry.commentsCount = comments.size();
			scrollOffset = 0;
		}));
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		renderBackground(g);
		renderHeader(g);
		renderComments(g, mx, my);
		renderStatus(g);
		super.render(g, mx, my, pt);
	}

	private void renderHeader(GuiGraphics g) {
		String name = safe(entry.name, SpellMarketLocalization.unknown().getString());
		g.drawCenteredString(font, name, width / 2, 18, 0xFFFFFF);
		String meta = SpellMarketLocalization.authorBy(
				safe(entry.authorName, SpellMarketLocalization.unknown().getString())).getString();
		if (entry.category != null && !entry.category.isBlank()) {
			meta += "  [" + SpellMarketLocalization.category(entry.category).getString() + "]";
		}
		g.drawCenteredString(font, meta, width / 2, 34, 0xAAAAAA);
		if (entry.description != null && !entry.description.isBlank()) {
			List<FormattedCharSequence> lines = font.split(Component.literal(entry.description), width - 60);
			int y = 52;
			for (int i = 0; i < Math.min(2, lines.size()); i++) {
				g.drawCenteredString(font, lines.get(i), width / 2, y, 0x888888);
				y += 10;
			}
		}
		g.drawString(font, SpellMarketLocalization.comments().getString(), 20, COMMENTS_TOP - 14, 0xFFFFFF);
	}

	private void renderComments(GuiGraphics g, int mx, int my) {
		int bottom = height - 64;
		if (bottom <= COMMENTS_TOP) {
			return;
		}
		int listHeight = Math.max(20, bottom - COMMENTS_TOP);
		int contentWidth = Math.max(60, width - 80);
		int totalHeight = totalCommentHeight(contentWidth);
		int maxScroll = Math.max(0, totalHeight - listHeight);
		scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

		if (loading && comments.isEmpty()) {
			g.drawCenteredString(font, SpellMarketLocalization.loading(), width / 2,
					COMMENTS_TOP + listHeight / 2, 0xAAAAAA);
			return;
		}
		if (comments.isEmpty()) {
			g.drawCenteredString(font, SpellMarketLocalization.noComments(), width / 2,
					COMMENTS_TOP + listHeight / 2, 0xAAAAAA);
			return;
		}

		g.enableScissor(0, COMMENTS_TOP, width, bottom);
		int y = COMMENTS_TOP - scrollOffset;
		for (Comment comment : comments) {
			int h = commentHeight(comment, contentWidth);
			if (y + h >= COMMENTS_TOP && y <= bottom) {
				renderComment(g, comment, 20, y, contentWidth, h, mx, my);
			}
			y += h + COMMENT_GAP;
		}
		g.disableScissor();
	}

	private void renderComment(GuiGraphics g, Comment comment, int x, int y, int w, int h, int mx, int my) {
		boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
		g.fill(x, y, x + w, y + h, hover ? 0x70404040 : 0x50202020);
		String author = safe(comment.authorName, SpellMarketLocalization.anonymous().getString());
		g.drawString(font, author, x + 8, y + 6, 0xFFFFFF);
		g.drawString(font, formatDate(comment.timestamp), x + 8 + font.width(author) + 10, y + 6, 0x777777);
		if (isOwnComment(comment) && comment.uuid != null && !comment.uuid.isBlank()) {
			int dx = x + w - 52;
			boolean dh = mx >= dx && mx <= dx + 44 && my >= y + 4 && my <= y + 18;
			g.fill(dx, y + 4, dx + 44, y + 18, dh ? 0xB0AA3333 : 0x80333333);
			g.drawCenteredString(font, SpellMarketLocalization.commentDelete().getString(), dx + 22, y + 7, 0xFFFFFF);
		}

		int cy = y + 22;
		String content = safe(comment.content, "");
		for (FormattedCharSequence line : font.split(Component.literal(content), w - 16)) {
			g.drawString(font, line, x + 8, cy, 0xCCCCCC);
			cy += 10;
		}

		String imageUrl = getImageUrl(comment);
		if (imageUrl != null) {
			cy += 3;
			renderImagePreview(g, imageUrl, x + 8, cy, w - 16);
		}
	}

	private void renderImagePreview(GuiGraphics g, String imageUrl, int x, int y, int availableWidth) {
		MarketImageCache.Preview preview = MarketImageCache.get(imageUrl);
		int maxWidth = imageMaxWidth(availableWidth);
		g.drawString(font, font.plainSubstrByWidth(imageUrl, maxWidth), x, y, 0x55AAFF);
		y += 12;
		if (preview.state() == MarketImageCache.Preview.State.READY && preview.texture() != null) {
			ImageSize size = previewSize(preview, availableWidth);
			int drawW = size.width();
			int drawH = size.height();
			g.fill(x - 1, y - 1, x + drawW + 1, y + drawH + 1, 0xFF101010);
			g.blit(preview.texture(), x, y, 0, 0, drawW, drawH,
					Math.max(1, preview.width()), Math.max(1, preview.height()));
		} else {
			String text = preview.state() == MarketImageCache.Preview.State.LOADING ?
					SpellMarketLocalization.imageLoading().getString() :
					SpellMarketLocalization.imageUnavailable().getString();
			g.drawString(font, text, x, y, 0x888888);
		}
	}

	private void renderStatus(GuiGraphics g) {
		if (posting) {
			g.drawString(font, SpellMarketLocalization.loading().getString(), width - 110, height - 39, 0xFFFF55);
		} else if (errorMessage != null) {
			g.drawString(font, errorMessage, 20, height - 66, 0xFF5555);
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int btn) {
		if (super.mouseClicked(mx, my, btn)) {
			return true;
		}
		if (btn != 0) {
			return false;
		}
		Comment clicked = findDeleteTarget(mx, my);
		if (clicked != null) {
			deleteComment(clicked);
			return true;
		}
		return false;
	}

	private Comment findDeleteTarget(double mx, double my) {
		int bottom = height - 64;
		int contentWidth = Math.max(60, width - 80);
		if (my < COMMENTS_TOP || my > bottom) return null;
		int y = COMMENTS_TOP - scrollOffset;
		for (Comment comment : comments) {
			int h = commentHeight(comment, contentWidth);
			if (my >= y && my <= y + h && isOwnComment(comment) && comment.uuid != null && !comment.uuid.isBlank()) {
				int dx = 20 + contentWidth - 52;
				if (mx >= dx && mx <= dx + 44 && my >= y + 4 && my <= y + 18) {
					return comment;
				}
			}
			y += h + COMMENT_GAP;
		}
		return null;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double delta) {
		scrollOffset -= (int) (delta * 20);
		return true;
	}

	private void postComment() {
		if (api == null || posting || entry.uuid == null || entry.uuid.isBlank()) return;
		String text = commentBox.getValue().trim();
		String image = imageBox.getValue().trim();
		if (text.isEmpty() && image.isEmpty()) {
			errorMessage = SpellMarketLocalization.commentFail().getString();
			return;
		}
		if (!image.isEmpty() && !isHttpUrl(image)) {
			errorMessage = SpellMarketLocalization.imageUnavailable().getString();
			return;
		}
		posting = true;
		errorMessage = null;
		postButton.active = false;
		api.addComment(entry.uuid, text, image, getPlayerName(), getPlayerUuid()).thenAccept(success ->
				Minecraft.getInstance().execute(() -> {
					posting = false;
					postButton.active = true;
					if (success) {
						commentBox.setValue("");
						imageBox.setValue("");
						loadComments();
					} else {
						errorMessage = SpellMarketLocalization.commentFail().getString();
					}
				}));
	}

	private void deleteComment(Comment comment) {
		if (api == null || comment.uuid == null || comment.uuid.isBlank()) return;
		api.deleteComment(entry.uuid, comment.uuid, getPlayerUuid(), getPlayerName()).thenAccept(success ->
				Minecraft.getInstance().execute(() -> {
					if (success) {
						comments.remove(comment);
						entry.commentsCount = comments.size();
					} else {
						errorMessage = SpellMarketLocalization.commentFail().getString();
					}
				}));
	}

	private void downloadSpell() {
		if (api == null || entry.uuid == null || entry.uuid.isBlank()) return;
		String name = safe(entry.name, SpellMarketLocalization.unknown().getString());
		minecraft.setScreen(new InfoScreen(this, SpellMarketLocalization.downloading(name).getString()));
		api.downloadSpell(entry.uuid).thenAccept(def -> Minecraft.getInstance().execute(() -> {
			if (def != null) {
				try {
					SpellEditorNetworkClient.importMarket(def);
					minecraft.setScreen(new InfoScreen(this, SpellMarketLocalization.downloadSuccess(name).getString()));
				} catch (Exception e) {
					minecraft.setScreen(new InfoScreen(this, SpellMarketLocalization.downloadFail().getString()));
				}
			} else {
				minecraft.setScreen(new InfoScreen(this, SpellMarketLocalization.downloadFail().getString()));
			}
		}));
	}

	private int totalCommentHeight(int width) {
		int total = 0;
		for (Comment comment : comments) {
			total += commentHeight(comment, width) + COMMENT_GAP;
		}
		return Math.max(0, total - COMMENT_GAP);
	}

	private int commentHeight(Comment comment, int width) {
		int lines = font.split(Component.literal(safe(comment.content, "")), width - 16).size();
		int h = 30 + Math.max(1, lines) * 10;
		String imageUrl = getImageUrl(comment);
		if (imageUrl != null) {
			MarketImageCache.Preview preview = MarketImageCache.get(imageUrl);
			h += (preview.state() == MarketImageCache.Preview.State.READY ?
					previewSize(preview, width - 16).height() : IMAGE_STATUS_HEIGHT) + 20;
		}
		return Math.max(48, h);
	}

	private static int imageMaxWidth(int availableWidth) {
		return Math.max(80, availableWidth);
	}

	private static ImageSize previewSize(MarketImageCache.Preview preview, int availableWidth) {
		int iw = Math.max(1, preview.width());
		int ih = Math.max(1, preview.height());
		float scale = imageMaxWidth(availableWidth) / (float) iw;
		scale = Math.min(scale, 1.0f);
		return new ImageSize(Math.max(1, Math.round(iw * scale)), Math.max(1, Math.round(ih * scale)));
	}

	private boolean isOwnComment(Comment comment) {
		String uuid = getPlayerUuid();
		if (comment.authorUuid != null && !comment.authorUuid.isBlank() && !uuid.isBlank()) {
			return comment.authorUuid.equals(uuid);
		}
		return comment.authorName != null && comment.authorName.equals(getPlayerName());
	}

	private String getPlayerUuid() {
		try {
			return Minecraft.getInstance().getUser().getProfileId().toString();
		} catch (Exception e) {
			return "";
		}
	}

	private String getPlayerName() {
		try {
			return Minecraft.getInstance().getUser().getName();
		} catch (Exception e) {
			return SpellMarketLocalization.anonymous().getString();
		}
	}

	private String getImageUrl(Comment comment) {
		if (comment.imageUrl != null && isHttpUrl(comment.imageUrl)) {
			return comment.imageUrl.trim();
		}
		if (comment.content == null) {
			return null;
		}
		Matcher matcher = URL_PATTERN.matcher(comment.content);
		while (matcher.find()) {
			String url = trimUrl(matcher.group());
			if (isLikelyImageUrl(url)) {
				return url;
			}
		}
		return null;
	}

	private static boolean isLikelyImageUrl(String url) {
		if (!isHttpUrl(url)) return false;
		String lower = url.toLowerCase();
		return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
				lower.endsWith(".gif") || lower.endsWith(".webp");
	}

	private static boolean isHttpUrl(String value) {
		if (value == null || value.isBlank()) return false;
		try {
			URI uri = URI.create(value.trim());
			String scheme = uri.getScheme();
			return uri.getHost() != null && scheme != null &&
					(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
		} catch (Exception e) {
			return false;
		}
	}

	private static String trimUrl(String value) {
		String out = value;
		while (!out.isEmpty() && ".,;)]}".indexOf(out.charAt(out.length() - 1)) >= 0) {
			out = out.substring(0, out.length() - 1);
		}
		return out;
	}

	private static String safe(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private String formatDate(long timestamp) {
		if (timestamp <= 0) {
			return "";
		}
		long ms = timestamp > 10_000_000_000L ? timestamp : timestamp * 1000L;
		return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(ms));
	}

	private record ImageSize(int width, int height) {
	}

	@Override
	public void onClose() {
		if (minecraft != null) minecraft.setScreen(parent);
	}

	@OnlyIn(Dist.CLIENT)
	private static class InfoScreen extends Screen {
		private final Screen parent;
		private final String message;

		protected InfoScreen(Screen parent, String message) {
			super(Component.literal(""));
			this.parent = parent;
			this.message = message;
		}

		@Override
		protected void init() {
			addRenderableWidget(Button.builder(SpellMarketLocalization.ok(), b -> {
				if (minecraft != null) minecraft.setScreen(parent);
			}).bounds(width / 2 - 50, height / 2 + 40, 100, 20).build());
		}

		@Override
		public void render(GuiGraphics g, int mx, int my, float pt) {
			renderBackground(g);
			List<String> lines = List.of(message.split("\n"));
			int y = height / 2 - 10 - (lines.size() * 12) / 2;
			for (String line : lines) {
				g.drawCenteredString(font, line, width / 2, y, 0xAAAAAA);
				y += 12;
			}
			super.render(g, mx, my, pt);
		}
	}
}
