package dev.xkmc.youkaishomecoming.content.spell.market;

import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.market.dto.LikeResult;
import dev.xkmc.youkaishomecoming.content.spell.market.dto.SpellListEntry;
import dev.xkmc.youkaishomecoming.content.spell.market.dto.SpellListResponse;
import dev.xkmc.youkaishomecoming.content.spell.preview.SpellEditorNetworkClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class SpellMarketScreen extends Screen {

	private static final int LIST_TOP = 60;
	private static final int ITEM_HEIGHT = 85;
	private static final int ITEMS_PER_PAGE = 5;

	private final Screen parent;
	private final SpellMarketAPI api;
	private final SpellDefinition pendingDefinition;

	private EditBox searchBox;
	private Button prevButton, nextButton, refreshButton, uploadButton, editorButton, closeButton;

	private List<SpellListEntry> loadedSpells = new ArrayList<>();
	private List<SpellListEntry> spells = new ArrayList<>();
	private int currentPage = 1;
	private int totalPages = 1;
	private int serverTotalPages = 1;
	private boolean loading = false;
	private String errorMessage = null;
	private int scrollOffset = 0;

	// 搜索防抖
	private long lastSearchTime = 0;
	private static final long SEARCH_DEBOUNCE_MS = 500;

	private String currentTag = null;
	private final List<String> excludedTags = new ArrayList<>();
	private boolean filterLiked = false;
	private String pendingSearch = null;
	private String authorFilterUuid = null;
	private String authorFilterName = null;

	public SpellMarketScreen(Screen parent) {
		this(parent, null);
	}

	public SpellMarketScreen(Screen parent, SpellDefinition pendingDefinition) {
		super(SpellMarketLocalization.title());
		this.parent = parent;
		this.api = SpellMarketManager.getInstance().getAPI();
		this.pendingDefinition = pendingDefinition;
	}

	@Override
	protected void init() {
		// 从磁盘加载已点赞符卡数据（懒加载，仅首次执行）
		LikedSpellsStore.load();

		if (api == null) {
			errorMessage = SpellMarketLocalization.errorDisabled().getString();
			addRenderableWidget(Button.builder(SpellMarketLocalization.ok(), btn -> onClose())
					.bounds(width / 2 - 50, height / 2 + 20, 100, 20).build());
			return;
		}

		int cx = width / 2;

		// 搜索框
		searchBox = new EditBox(font, cx - 100, 20, 180, 20, SpellMarketLocalization.search());
		searchBox.setMaxLength(50);
		searchBox.setHint(SpellMarketLocalization.search());
		searchBox.setResponder(text -> {
			currentPage = 1;
			currentTag = null;
			pendingSearch = text;
			loadList();
		});
		addRenderableWidget(searchBox);

		// 刷新
		refreshButton = Button.builder(SpellMarketLocalization.refresh(), btn -> reload())
				.bounds(cx + 90, 20, 50, 20).build();
		addRenderableWidget(refreshButton);

		// 上传
		uploadButton = Button.builder(SpellMarketLocalization.upload(), btn -> openUpload())
				.bounds(cx + 150, 20, 50, 20).build();
		addRenderableWidget(uploadButton);

		// Editor 切换按钮
		editorButton = Button.builder(SpellMarketLocalization.toEditor(), btn -> openEditor())
				.bounds(cx + 210, 20, 50, 20).build();
		addRenderableWidget(editorButton);

		// 关闭
		closeButton = Button.builder(SpellMarketLocalization.close(), btn -> onClose())
				.bounds(cx + 270, 20, 50, 20).build();
		addRenderableWidget(closeButton);

		// 分页
		prevButton = Button.builder(SpellMarketLocalization.prev(), btn -> {
			if (currentPage > 1) { currentPage--; loadList(); }
		}).bounds(cx - 80, height - 30, 60, 20).build();
		addRenderableWidget(prevButton);

		nextButton = Button.builder(SpellMarketLocalization.next(), btn -> {
			if (currentPage < totalPages) { currentPage++; loadList(); }
		}).bounds(cx + 20, height - 30, 60, 20).build();
		addRenderableWidget(nextButton);

		loadList();
	}

	private void reload() {
		currentPage = 1;
		currentTag = null;
		excludedTags.clear();
		filterLiked = false;
		authorFilterUuid = null;
		authorFilterName = null;
		loadList();
	}

	@Override
	public void tick() {
		super.tick();
		if (pendingSearch != null && !loading && api != null) {
			long now = System.currentTimeMillis();
			if (now - lastSearchTime >= SEARCH_DEBOUNCE_MS) {
				pendingSearch = null;
				loadList();
			}
		}
	}

	private void loadList() {
		if (loading || api == null) return;

		// 搜索防抖
		long now = System.currentTimeMillis();
		if (now - lastSearchTime < SEARCH_DEBOUNCE_MS) {
			pendingSearch = searchBox.getValue();
			return;
		}
		lastSearchTime = now;
		pendingSearch = null;

		loading = true;
		errorMessage = null;
		String search = SpellMarketBuiltinTags.resolveSearchQuery(searchBox.getValue());
		if (currentTag != null) search = currentTag;

		api.getSpellList(currentPage, ITEMS_PER_PAGE,
				search.isEmpty() ? null : search, null, authorFilterUuid, authorFilterName).thenAccept(resp -> {
			Minecraft.getInstance().execute(() -> {
				loading = false;
				if (resp != null) {
					loadedSpells = resp.spells != null ? new ArrayList<>(resp.spells) : new ArrayList<>();
					serverTotalPages = Math.max(1, (int) Math.ceil(resp.total / (double) ITEMS_PER_PAGE));
					// 恢复点赞计数
					for (SpellListEntry e : loadedSpells) {
						int cachedCount = LikedSpellsStore.getLikeCount(e.uuid);
						if (cachedCount >= 0) {
							e.likesCount = cachedCount;
						}
					}
					applyClientFilters();
					scrollOffset = 0;
				} else {
					errorMessage = SpellMarketLocalization.errorNetwork().getString();
				}
			});
		});
	}

	private void openUpload() {
		if (api == null) return;
		if (!api.getRateLimiter().canUpload()) {
			long secs = api.getRateLimiter().getUploadCooldownRemaining();
			minecraft.setScreen(msgScreen(SpellMarketLocalization.uploadCooldown(secs).getString()));
			return;
		}
		minecraft.setScreen(new SpellUploadDialog(this, pendingDefinition));
	}

	private void openEditor() {
		if (minecraft == null) return;
		if (parent instanceof dev.xkmc.youkaishomecoming.content.spell.preview.SpellPreviewScreen) {
			minecraft.setScreen(parent);
		} else if (pendingDefinition != null) {
			minecraft.setScreen(new dev.xkmc.youkaishomecoming.content.spell.preview.SpellPreviewScreen(pendingDefinition));
		} else {
			minecraft.setScreen(dev.xkmc.youkaishomecoming.content.spell.preview.SpellPreviewScreen.createDraftEditor());
		}
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		renderBackground(g);

		if (api == null) {
			g.drawCenteredString(font, errorMessage != null ? errorMessage :
					SpellMarketLocalization.disabled().getString(), width / 2, height / 2, 0xFF5555);
			super.render(g, mx, my, pt);
			return;
		}

		g.drawCenteredString(font, getTitle(), width / 2, 6, 0xFFFFFF);

		// 分页信息
		g.drawCenteredString(font, SpellMarketLocalization.page(currentPage, totalPages), width / 2, height - 45, 0xAAAAAA);

		// 已选标签栏（始终可见，可点击取消）
		renderSelectedTags(g, mx, my);

		if (loading) {
			g.drawCenteredString(font, SpellMarketLocalization.loading(), width / 2, height / 2, 0xFFFFFF);
		} else if (errorMessage != null) {
			g.drawCenteredString(font, errorMessage, width / 2, height / 2, 0xFF5555);
		} else if (spells.isEmpty()) {
			g.drawCenteredString(font, SpellMarketLocalization.noSpells(), width / 2, height / 2, 0xAAAAAA);
		} else {
			renderList(g, mx, my);
		}

		super.render(g, mx, my, pt);
	}

	private void renderSelectedTags(GuiGraphics g, int mx, int my) {
		int y = 42;
		int x = width / 2 - 100;

		// ♥ Liked 筛选按钮（始终可见，激活时红色背景）
		String likedLabel = "\u2665 " + SpellMarketLocalization.likedFilter().getString();
		int tw = font.width(likedLabel) + 12;
		boolean lh = mx >= x && mx <= x + tw && my >= y && my <= y + 14;
		int lbg = filterLiked ? 0x90AA4444 : (lh ? 0x60404040 : 0x40303030);
		g.fill(x, y, x + tw, y + 14, lbg);
		g.drawString(font, likedLabel, x + 6, y + 3, filterLiked ? 0xFFDDDD : 0xAAAAAA);
		x += tw + 6;

		// 当前选中的标签（可点击取消）
		if (currentTag != null) {
			String tagLabel = tagLabel(currentTag);
			tw = font.width(tagLabel) + 16;
			boolean th = mx >= x && mx <= x + tw && my >= y && my <= y + 14;
			g.fill(x, y, x + tw, y + 14, th ? 0xC055AA55 : 0x8055AA55);
			g.drawString(font, tagLabel, x + 4, y + 3, 0xFFFFFF);
			g.drawString(font, "\u00D7", x + tw - 10, y + 3, 0xFFAAAA);
			x += tw + 6;
		}

		for (String tag : excludedTags) {
			String label = "-" + tagLabel(tag);
			tw = font.width(label) + 16;
			boolean eh = mx >= x && mx <= x + tw && my >= y && my <= y + 14;
			g.fill(x, y, x + tw, y + 14, eh ? 0xC0AA4444 : 0x80AA4444);
			g.drawString(font, label, x + 4, y + 3, 0xFFFFFF);
			g.drawString(font, "\u00D7", x + tw - 10, y + 3, 0xFFDDDD);
			x += tw + 6;
		}

		// 作者筛选（可点击取消）
		if (authorFilterName != null) {
			String label = "\u263A " + authorFilterName;
			tw = font.width(label) + 16;
			boolean ah = mx >= x && mx <= x + tw && my >= y && my <= y + 14;
			g.fill(x, y, x + tw, y + 14, ah ? 0xC05555AA : 0x805555AA);
			g.drawString(font, label, x + 4, y + 3, 0xFFFFFF);
			g.drawString(font, "\u00D7", x + tw - 10, y + 3, 0xFFAAAA);
		}
	}

	private void renderList(GuiGraphics g, int mx, int my) {
		int listH = height - LIST_TOP - 60;
		int maxScroll = Math.max(0, spells.size() * ITEM_HEIGHT - listH);
		scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

		g.enableScissor(0, LIST_TOP, width, LIST_TOP + listH);

		int y = LIST_TOP - scrollOffset;
		for (SpellListEntry spell : spells) {
			if (y + ITEM_HEIGHT < LIST_TOP || y > LIST_TOP + listH) {
				y += ITEM_HEIGHT;
				continue;
			}
			boolean hover = mx >= 20 && mx <= width - 20 && my >= y && my <= y + ITEM_HEIGHT - 5;
			g.fill(20, y, width - 20, y + ITEM_HEIGHT - 5, hover ? 0x80404040 : 0x60202020);

			// 名称
			String spellName = textOrUnknown(spell.name);
			g.drawString(font, spellName, 30, y + 5, 0xFFFFFF);
			// 作者（可点击筛选）
			String authorText = authorText(spell.authorName);
			boolean authorHover = mx >= 30 && mx <= 30 + font.width(authorText) && my >= y + 20 && my <= y + 32;
			g.drawString(font, authorText, 30, y + 20, authorHover ? 0x55AAFF : 0xAAAAAA);
			// 分类
			if (spell.category != null && !spell.category.isEmpty()) {
				g.drawString(font, "[" + SpellMarketLocalization.category(spell.category).getString() + "]",
						30, y + 35, 0x55FFFF);
			}
			// 标签
			if (spell.tags != null && !spell.tags.isEmpty()) {
				int tx = 150, ty = y + 35;
				for (String tag : spell.tags) {
					if (tx > width - 250) break;
					String tagLabel = tagLabel(tag);
					int tw = font.width(tagLabel) + 8;
					boolean th = mx >= tx && mx <= tx + tw && my >= ty && my <= ty + 12;
					int tc = SpellMarketBuiltinTags.normalize(tag).equals(currentTag) ? 0xA055AA55 : (th ? 0xA0606060 : 0x80404040);
					g.fill(tx, ty, tx + tw, ty + 12, tc);
					g.drawString(font, tagLabel, tx + 4, ty + 2, 0xAAFFAA);
					tx += tw + 4;
				}
			}

			// 点赞、下载和评论统计
			String stats = "❤ " + spell.likesCount + "  ⬇ " + spell.downloadsCount + "  " +
					SpellMarketLocalization.commentCount(spell.commentsCount).getString();
			g.drawString(font, stats, 30, y + 50, 0xFFDD55);
			g.drawString(font, formatDate(spell.uploadDate), 30, y + 65, 0x888888);

			// 点赞/取消点赞按钮
			boolean liked = LikedSpellsStore.contains(spell.uuid);
			int lx = width - 220, ly = y + 25;
			boolean lh = mx >= lx && mx <= lx + 60 && my >= ly && my <= ly + 20;
			int lc = liked ? 0xFFFF5555 : (lh ? 0xFFAA5555 : 0xFF885555);
			g.fill(lx, ly, lx + 60, ly + 20, lc);
			g.drawCenteredString(font, liked ? SpellMarketLocalization.unlike().getString() : SpellMarketLocalization.like().getString(),
					lx + 30, ly + 6, 0xFFFFFF);

			// 详情按钮
			int ix = width - 150, iy = y + 25;
			boolean ih = mx >= ix && mx <= ix + 70 && my >= iy && my <= iy + 20;
			g.fill(ix, iy, ix + 70, iy + 20, ih ? 0xFF555588 : 0xFF333366);
			g.drawCenteredString(font, SpellMarketLocalization.detail().getString(), ix + 35, iy + 6, 0xFFFFFF);

			// 下载按钮
			int dx = width - 150, dy = y + 50;
			boolean dh = mx >= dx && mx <= dx + 80 && my >= dy && my <= dy + 20;
			int dc = dh ? 0xFF55AA55 : 0xFF338833;
			g.fill(dx, dy, dx + 80, dy + 20, dc);
			g.drawCenteredString(font, SpellMarketLocalization.download().getString(), dx + 40, dy + 6, 0xFFFFFF);

			// 删除按钮（仅本人符卡）
			if (isOwnSpell(spell)) {
				int dlx = width - 40, dly = y + 25;
				boolean dlh = mx >= dlx && mx <= dlx + 20 && my >= dly && my <= dly + 20;
				g.fill(dlx, dly, dlx + 20, dly + 20, dlh ? 0xFFCC3333 : 0xFF883333);
				g.drawCenteredString(font, "\u00D7", dlx + 10, dly + 6, 0xFFFFFF);
			}

			y += ITEM_HEIGHT;
		}
		g.disableScissor();
	}

	@Override
	public boolean mouseClicked(double mx, double my, int btn) {
		if ((btn != 0 && btn != 1) || loading) return super.mouseClicked(mx, my, btn);

		// 已选标签栏点击处理（y=42~56）
		if (btn == 0 && my >= 42 && my <= 56) {
			int x = width / 2 - 100;
			String likedLabel = "\u2665 " + SpellMarketLocalization.likedFilter().getString();
			int tw = font.width(likedLabel) + 12;
			if (mx >= x && mx <= x + tw) {
				filterLiked = !filterLiked;
				currentPage = 1;
				scrollOffset = 0;
				applyClientFilters();
				return true;
			}
			x += tw + 6;
			if (currentTag != null) {
				tw = font.width(tagLabel(currentTag)) + 16;
				if (mx >= x && mx <= x + tw) {
					currentTag = null;
					currentPage = 1;
					loadList();
					return true;
				}
				x += tw + 6;
			}
			for (int i = 0; i < excludedTags.size(); i++) {
				String label = "-" + tagLabel(excludedTags.get(i));
				tw = font.width(label) + 16;
				if (mx >= x && mx <= x + tw) {
					excludedTags.remove(i);
					currentPage = 1;
					scrollOffset = 0;
					applyClientFilters();
					return true;
				}
				x += tw + 6;
			}
			if (authorFilterName != null) {
				String label = "\u263A " + authorFilterName;
				tw = font.width(label) + 16;
				if (mx >= x && mx <= x + tw) {
					authorFilterUuid = null;
					authorFilterName = null;
					currentPage = 1;
					loadList();
					return true;
				}
			}
		}

		int y = LIST_TOP - scrollOffset;
		for (SpellListEntry spell : spells) {
			// 作者点击筛选
			String authorText = authorText(spell.authorName);
			if (btn == 0 && mx >= 30 && mx <= 30 + font.width(authorText) && my >= y + 20 && my <= y + 32) {
				filterByAuthor(spell);
				return true;
			}
			// 标签点击
			if (spell.tags != null) {
				int tx = 150, ty = y + 35;
				for (String tag : spell.tags) {
					if (tx > width - 250) break;
					int tw = font.width(tagLabel(tag)) + 8;
					if (mx >= tx && mx <= tx + tw && my >= ty && my <= ty + 12) {
						if (btn == 1) {
							filterOutTag(tag);
						} else {
							filterByTag(tag);
						}
						return true;
					}
					tx += tw + 4;
				}
			}
			// 点赞/取消点赞
			int lx = width - 220, ly = y + 25;
			if (btn == 0 && mx >= lx && mx <= lx + 60 && my >= ly && my <= ly + 20) {
				toggleLike(spell);
				return true;
			}
			// 详情
			int ix = width - 150, iy = y + 25;
			if (btn == 0 && mx >= ix && mx <= ix + 70 && my >= iy && my <= iy + 20) {
				openDetail(spell);
				return true;
			}
			// 下载
			int dx = width - 150, dy = y + 50;
			if (btn == 0 && mx >= dx && mx <= dx + 80 && my >= dy && my <= dy + 20) {
				downloadSpell(spell);
				return true;
			}
			// 删除（仅本人符卡）
			if (isOwnSpell(spell)) {
				int dlx = width - 40, dly = y + 25;
				if (btn == 0 && mx >= dlx && mx <= dlx + 20 && my >= dly && my <= dly + 20) {
					deleteSpell(spell);
					return true;
				}
			}
			y += ITEM_HEIGHT;
		}
		return super.mouseClicked(mx, my, btn);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double delta) {
		scrollOffset -= (int) (delta * 20);
		return true;
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		if (key == 257 || key == 335) {
			authorFilterUuid = null;
			authorFilterName = null;
			currentPage = 1;
			loadList();
			return true;
		}
		return super.keyPressed(key, scan, mods);
	}

	private void filterByTag(String tag) {
		tag = SpellMarketBuiltinTags.normalize(tag);
		if (tag.equals(currentTag)) {
			currentTag = null;
		} else {
			currentTag = tag;
		}
		authorFilterUuid = null;
		authorFilterName = null;
		currentPage = 1;
		loadList();
	}

	private void filterOutTag(String tag) {
		tag = SpellMarketBuiltinTags.normalize(tag);
		if (tag.isBlank()) return;
		if (excludedTags.contains(tag)) {
			excludedTags.remove(tag);
		} else {
			excludedTags.add(tag);
		}
		currentPage = 1;
		scrollOffset = 0;
		applyClientFilters();
	}

	private void filterByAuthor(SpellListEntry spell) {
		if (spell.authorUuid != null && !spell.authorUuid.isEmpty()) {
			authorFilterUuid = spell.authorUuid;
			authorFilterName = textOrUnknown(spell.authorName);
		} else if (spell.authorName != null && !spell.authorName.isEmpty()) {
			authorFilterName = spell.authorName;
			authorFilterUuid = null;
		} else {
			return;
		}
		currentTag = null;
		currentPage = 1;
		loadList();
	}

	private void toggleLike(SpellListEntry entry) {
		if (api == null) return;
		if (LikedSpellsStore.contains(entry.uuid)) {
			// 已点赞 → 取消点赞
			api.unlikeSpell(entry.uuid).thenAccept(success -> {
				Minecraft.getInstance().execute(() -> {
					if (success) {
						LikedSpellsStore.remove(entry.uuid);
						entry.likesCount = Math.max(0, entry.likesCount - 1);
					}
				});
			});
		} else {
			// 未点赞 → 点赞
			api.likeSpell(entry.uuid).thenAccept(result -> {
				Minecraft.getInstance().execute(() -> {
					switch (result) {
						case SUCCESS:
							entry.likesCount++;
							LikedSpellsStore.add(entry.uuid, entry.likesCount);
							break;
						case ALREADY_LIKED:
							LikedSpellsStore.add(entry.uuid, entry.likesCount);
							break;
						case ERROR:
							break;
					}
				});
			});
		}
	}

	private void deleteSpell(SpellListEntry entry) {
		if (api == null) return;
		String playerUuid = getPlayerUuid();
		String playerName = "";
		try { playerName = Minecraft.getInstance().getUser().getName(); } catch (Exception ignored) {}
		api.deleteSpell(entry.uuid, playerUuid, playerName).thenAccept(success -> {
			Minecraft.getInstance().execute(() -> {
				if (success) {
					spells.remove(entry);
					loadedSpells.remove(entry);
					LikedSpellsStore.remove(entry.uuid);
				} else {
					errorMessage = SpellMarketLocalization.deleteFail().getString();
				}
			});
		});
	}

	private void applyClientFilters() {
		spells = new ArrayList<>(loadedSpells);
		if (filterLiked) {
			spells.removeIf(e -> !LikedSpellsStore.contains(e.uuid));
		}
		if (!excludedTags.isEmpty()) {
			spells.removeIf(this::hasExcludedTag);
		}
		totalPages = filterLiked ? 1 : serverTotalPages;
	}

	private void openDetail(SpellListEntry entry) {
		if (minecraft != null) {
			minecraft.setScreen(new SpellDetailScreen(this, entry));
		}
	}

	private String getPlayerUuid() {
		try {
			return Minecraft.getInstance().getUser().getProfileId().toString();
		} catch (Exception e) {
			return "";
		}
	}

	private boolean isOwnSpell(SpellListEntry spell) {
		String playerUuid = getPlayerUuid();
		if (spell.authorUuid != null && !spell.authorUuid.isEmpty()) {
			return spell.authorUuid.equals(playerUuid);
		}
		// 旧符卡没有UUID，用author_name匹配
		try {
			String playerName = Minecraft.getInstance().getUser().getName();
			return spell.authorName != null && spell.authorName.equals(playerName);
		} catch (Exception e) {
			return false;
		}
	}

	private void downloadSpell(SpellListEntry entry) {
		if (api == null) return;
		String name = textOrUnknown(entry.name);
		minecraft.setScreen(new InfoScreen(this, SpellMarketLocalization.downloading(name).getString()));
		api.downloadSpell(entry.uuid).thenAccept(def -> {
			Minecraft.getInstance().execute(() -> {
				if (def != null) {
					// 保存到世界存档
					try {
						SpellEditorNetworkClient.importMarket(def);
						minecraft.setScreen(msgScreen(SpellMarketLocalization.downloadSuccess(name).getString()));
					} catch (Exception e) {
						org.slf4j.LoggerFactory.getLogger("SpellMarket").error("Error saving downloaded spell '{}'", entry.uuid, e);
						minecraft.setScreen(msgScreen(SpellMarketLocalization.saveFail(e.getMessage() == null ? "" : e.getMessage()).getString()));
					}
				} else {
					minecraft.setScreen(msgScreen(SpellMarketLocalization.downloadIncompatible().getString()));
				}
			});
		});
	}

	private static String textOrUnknown(String value) {
		return value == null || value.isBlank() ? SpellMarketLocalization.unknown().getString() : value;
	}

	private static String authorText(String authorName) {
		return SpellMarketLocalization.authorBy(textOrUnknown(authorName)).getString();
	}

	private String tagLabel(String tag) {
		return SpellMarketLocalization.tag(tag).getString();
	}

	private boolean hasExcludedTag(SpellListEntry entry) {
		if (entry.tags == null || entry.tags.isEmpty()) return false;
		for (String tag : entry.tags) {
			if (excludedTags.contains(SpellMarketBuiltinTags.normalize(tag))) {
				return true;
			}
		}
		return false;
	}

	private String formatDate(long ts) {
		java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
		return sdf.format(new java.util.Date(ts * 1000));
	}

	@Override
	public void onClose() {
		if (minecraft != null) minecraft.setScreen(parent);
	}

	// 简单的提示界面
	private Screen msgScreen(String msg) {
		return new InfoScreen(parent, msg) {
			@Override
			protected void addButtons() {
				addRenderableWidget(Button.builder(SpellMarketLocalization.ok(), b -> {
					if (minecraft != null) minecraft.setScreen(SpellMarketScreen.this);
				}).bounds(width / 2 - 50, height / 2 + 40, 100, 20).build());
			}
		};
	}

	// 基础信息提示界面
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
			addButtons();
		}

		protected void addButtons() {
			addRenderableWidget(Button.builder(SpellMarketLocalization.ok(), b -> {
				if (minecraft != null) minecraft.setScreen(parent);
			}).bounds(width / 2 - 50, height / 2 + 40, 100, 20).build());
		}

		@Override
		public void render(GuiGraphics g, int mx, int my, float pt) {
			renderBackground(g);
			List<String> lines = java.util.List.of(message.split("\n"));
			int y = height / 2 - 10 - (lines.size() * 12) / 2;
			for (String line : lines) {
				g.drawCenteredString(font, line, width / 2, y, 0xAAAAAA);
				y += 12;
			}
			super.render(g, mx, my, pt);
		}
	}

}
