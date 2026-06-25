package dev.xkmc.youkaishomecoming.content.spell.market;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.market.dto.SpellListEntry;
import dev.xkmc.youkaishomecoming.content.spell.market.dto.SpellListResponse;
import dev.xkmc.youkaishomecoming.content.spell.preview.SpellEditorNetworkClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public class SpellMarketScreen extends Screen {

	private static final int LIST_TOP = 60;
	private static final int ITEM_HEIGHT = 85;
	private static final int ITEMS_PER_PAGE = 5;

	private final Screen parent;
	private final SpellMarketAPI api;

	private EditBox searchBox;
	private Button prevButton, nextButton, refreshButton, uploadButton, editorButton, closeButton;

	private List<SpellListEntry> spells = new ArrayList<>();
	private int currentPage = 1;
	private int totalPages = 1;
	private boolean loading = false;
	private String errorMessage = null;
	private int scrollOffset = 0;

	// 限流和状态
	private final Set<String> likedSpells = new HashSet<>();
	private final Map<String, Integer> likeCounts = new HashMap<>();
	private long lastSearchTime = 0;
	private static final long SEARCH_DEBOUNCE_MS = 500;

	private String currentTag = null;
	private String pendingSearch = null;

	public SpellMarketScreen(Screen parent) {
		super(SpellMarketLocalization.title());
		this.parent = parent;
		this.api = SpellMarketManager.getInstance().getAPI();
	}

	@Override
	protected void init() {
		if (api == null) {
			errorMessage = SpellMarketLocalization.errorDisabled().getString();
			addRenderableWidget(Button.builder(Component.literal("OK"), btn -> onClose())
					.bounds(width / 2 - 50, height / 2 + 20, 100, 20).build());
			return;
		}

		int cx = width / 2;

		// 搜索框
		searchBox = new EditBox(font, cx - 100, 20, 180, 20, SpellMarketLocalization.search());
		searchBox.setMaxLength(50);
		searchBox.setHint(SpellMarketLocalization.search());
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
		loadList();
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

		loading = true;
		errorMessage = null;
		String search = searchBox.getValue();
		if (currentTag != null) search = currentTag;

		api.getSpellList(currentPage, ITEMS_PER_PAGE,
				search.isEmpty() ? null : search, "latest").thenAccept(resp -> {
			Minecraft.getInstance().execute(() -> {
				loading = false;
				if (resp != null) {
					spells = resp.spells;
					totalPages = Math.max(1, (int) Math.ceil(resp.total / (double) ITEMS_PER_PAGE));
					scrollOffset = 0;
					// 恢复点赞计数
					for (SpellListEntry e : spells) {
						if (likeCounts.containsKey(e.uuid)) {
							e.likesCount = likeCounts.get(e.uuid);
						}
					}
				} else {
					errorMessage = SpellMarketLocalization.errorNetwork().getString();
				}
			});
		});
	}

	private void openUpload() {
		if (api == null || !api.getRateLimiter().canUpload()) {
			long secs = api.getRateLimiter().getUploadCooldownRemaining();
			minecraft.setScreen(msgScreen("Cooldown",
					SpellMarketLocalization.uploadCooldown(secs).getString()));
			return;
		}
		minecraft.setScreen(new SpellUploadDialog(this, null));
	}

	private void openEditor() {
		// 打开编辑器
		if (minecraft != null) {
			minecraft.setScreen(parent);
		}
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		renderBackground(g, mx, my, pt);

		if (api == null) {
			g.drawCenteredString(font, errorMessage != null ? errorMessage : "Disabled", width / 2, height / 2, 0xFF5555);
			super.render(g, mx, my, pt);
			return;
		}

		g.drawCenteredString(font, getTitle(), width / 2, 6, 0xFFFFFF);

		// 分页信息
		g.drawCenteredString(font, SpellMarketLocalization.page(currentPage, totalPages), width / 2, height - 45, 0xAAAAAA);

		// 筛选提示
		if (currentTag != null) {
			g.drawCenteredString(font, SpellMarketLocalization.filterTag(currentTag), width / 2, 45, 0x55FFAA);
		}

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

	private void renderList(GuiGraphics g, int mx, int my) {
		int listH = height - LIST_TOP - 60;
		int maxScroll = Math.max(0, spells.size() * ITEM_HEIGHT - listH);
		scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

		RenderSystem.enableScissor(0, LIST_TOP, width, listH);

		int y = LIST_TOP - scrollOffset;
		for (SpellListEntry spell : spells) {
			if (y + ITEM_HEIGHT < LIST_TOP || y > LIST_TOP + listH) {
				y += ITEM_HEIGHT;
				continue;
			}
			boolean hover = mx >= 20 && mx <= width - 20 && my >= y && my <= y + ITEM_HEIGHT - 5;
			g.fill(20, y, width - 20, y + ITEM_HEIGHT - 5, hover ? 0x80404040 : 0x60202020);

			// 名称
			g.drawString(font, spell.name, 30, y + 5, 0xFFFFFF);
			// 作者
			g.drawString(font, "by " + spell.authorName, 30, y + 20, 0xAAAAAA);
			// 分类
			if (spell.category != null && !spell.category.isEmpty()) {
				g.drawString(font, "[" + spell.category + "]", 30, y + 35, 0x55FFFF);
			}
			// 标签
			if (spell.tags != null && !spell.tags.isEmpty()) {
				int tx = 150, ty = y + 35;
				for (String tag : spell.tags) {
					if (tx > width - 250) break;
					int tw = font.width(tag) + 8;
					boolean th = mx >= tx && mx <= tx + tw && my >= ty && my <= ty + 12;
					int tc = tag.equals(currentTag) ? 0xA055AA55 : (th ? 0xA0606060 : 0x80404040);
					g.fill(tx, ty, tx + tw, ty + 12, tc);
					g.drawString(font, tag, tx + 4, ty + 2, 0xAAFFAA);
					tx += tw + 4;
				}
			}

			// 点赞和下载统计
			g.drawString(font, "❤ " + spell.likesCount + "  ⬇ " + spell.downloadsCount, 30, y + 50, 0xFFDD55);
			g.drawString(font, formatDate(spell.uploadDate), 30, y + 65, 0x888888);

			// 点赞按钮
			boolean liked = likedSpells.contains(spell.uuid);
			int lx = width - 200, ly = y + 25;
			boolean lh = mx >= lx && mx <= lx + 60 && my >= ly && my <= ly + 20;
			int lc = liked ? 0xFFFF5555 : (lh ? 0xFFAA5555 : 0xFF885555);
			g.fill(lx, ly, lx + 60, ly + 20, lc);
			g.drawCenteredString(font, liked ? SpellMarketLocalization.liked().getString() : SpellMarketLocalization.like().getString(),
					lx + 30, ly + 6, 0xFFFFFF);

			// 下载按钮
			int dx = width - 130, dy = y + 50;
			boolean dh = mx >= dx && mx <= dx + 80 && my >= dy && my <= dy + 20;
			int dc = dh ? 0xFF55AA55 : 0xFF338833;
			g.fill(dx, dy, dx + 80, dy + 20, dc);
			g.drawCenteredString(font, SpellMarketLocalization.download().getString(), dx + 40, dy + 6, 0xFFFFFF);

			y += ITEM_HEIGHT;
		}
		RenderSystem.disableScissor();
	}

	@Override
	public boolean mouseClicked(double mx, double my, int btn) {
		if (btn != 0 || loading) return super.mouseClicked(mx, my, btn);
		int y = LIST_TOP - scrollOffset;
		for (SpellListEntry spell : spells) {
			// 标签点击
			if (spell.tags != null) {
				int tx = 150, ty = y + 35;
				for (String tag : spell.tags) {
					if (tx > width - 250) break;
					int tw = font.width(tag) + 8;
					if (mx >= tx && mx <= tx + tw && my >= ty && my <= ty + 12) {
						filterByTag(tag);
						return true;
					}
					tx += tw + 4;
				}
			}
			// 点赞
			int lx = width - 200, ly = y + 25;
			if (mx >= lx && mx <= lx + 60 && my >= ly && my <= ly + 20) {
				likeSpell(spell);
				return true;
			}
			// 下载
			int dx = width - 130, dy = y + 50;
			if (mx >= dx && mx <= dx + 80 && my >= dy && my <= dy + 20) {
				downloadSpell(spell);
				return true;
			}
			y += ITEM_HEIGHT;
		}
		return super.mouseClicked(mx, my, btn);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double dx, double dy) {
		scrollOffset -= (int) (dy * 20);
		return true;
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		if (key == 257 || key == 335) {
			currentPage = 1;
			loadList();
			return true;
		}
		return super.keyPressed(key, scan, mods);
	}

	private void filterByTag(String tag) {
		if (tag.equals(currentTag)) {
			currentTag = null;
		} else {
			currentTag = tag;
		}
		currentPage = 1;
		loadList();
	}

	private void likeSpell(SpellListEntry entry) {
		if (api == null || likedSpells.contains(entry.uuid)) return;
		api.likeSpell(entry.uuid).thenAccept(success -> {
			Minecraft.getInstance().execute(() -> {
				if (success) {
					likedSpells.add(entry.uuid);
					entry.likesCount++;
					likeCounts.put(entry.uuid, entry.likesCount);
				}
			});
		});
	}

	private void downloadSpell(SpellListEntry entry) {
		if (api == null) return;
		minecraft.setScreen(new InfoScreen(this, SpellMarketLocalization.downloading(entry.name).getString()));
		api.downloadSpell(entry.uuid).thenAccept(def -> {
			Minecraft.getInstance().execute(() -> {
				if (def != null) {
					// 保存到世界存档
					try {
						SpellEditorNetworkClient.save(def);
						minecraft.setScreen(msgScreen("Success",
								SpellMarketLocalization.downloadSuccess(entry.name).getString()));
					} catch (Exception e) {
						minecraft.setScreen(msgScreen("Error",
								SpellMarketLocalization.downloadFail().getString()));
					}
				} else {
					minecraft.setScreen(msgScreen("Error",
							SpellMarketLocalization.downloadFail().getString()));
				}
			});
		});
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
	private Screen msgScreen(String title, String msg) {
		return new InfoScreen(parent, msg) {
			@Override
			protected void addButtons() {
				addRenderableWidget(Button.builder(Component.literal("OK"), b -> {
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
			addRenderableWidget(Button.builder(Component.literal("OK"), b -> {
				if (minecraft != null) minecraft.setScreen(parent);
			}).bounds(width / 2 - 50, height / 2 + 40, 100, 20).build());
		}

		@Override
		public void render(GuiGraphics g, int mx, int my, float pt) {
			renderBackground(g, mx, my, pt);
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
