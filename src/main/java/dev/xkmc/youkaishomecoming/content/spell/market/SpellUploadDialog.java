package dev.xkmc.youkaishomecoming.content.spell.market;

import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.market.dto.UploadResponse;
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
public class SpellUploadDialog extends Screen {

	private static final String[] CATEGORIES = {
			"Original", "Creative", "Tech Demo", "Tutorial", "Challenge", "Other"
	};

	private final Screen parent;
	private final SpellDefinition definition;
	private final SpellMarketAPI api;

	private EditBox nameBox, descBox, authorBox, tagsBox;
	private Button categoryButton, uploadButton, cancelButton;
	private int selectedCategory = 0;
	private List<String> tags = new ArrayList<>();
	private String errorMessage = null;
	private boolean uploading = false;

	public SpellUploadDialog(Screen parent, SpellDefinition definition) {
		super(SpellMarketLocalization.uploadTitle());
		this.parent = parent;
		this.definition = definition;
		this.api = SpellMarketManager.getInstance().getAPI();
	}

	@Override
	protected void init() {
		if (api == null) {
			errorMessage = SpellMarketLocalization.errorDisabled().getString();
			addCloseButton();
			return;
		}

		int cx = width / 2;
		int sy = 50;
		int lx = cx - 150;
		int ix = cx - 150;
		int iw = 300;

		nameBox = new EditBox(font, ix, sy + 20, iw, 20, SpellMarketLocalization.uploadName());
		nameBox.setMaxLength(100);
		if (definition != null) nameBox.setValue(definition.id.toString());
		addRenderableWidget(nameBox);

		descBox = new EditBox(font, ix, sy + 60, iw, 20, SpellMarketLocalization.uploadDesc());
		descBox.setMaxLength(500);
		addRenderableWidget(descBox);

		authorBox = new EditBox(font, ix, sy + 100, iw, 20, SpellMarketLocalization.uploadAuthor());
		authorBox.setMaxLength(50);
		try { authorBox.setValue(Minecraft.getInstance().getUser().getName()); } catch (Exception ignored) {}
		addRenderableWidget(authorBox);

		categoryButton = Button.builder(
				Component.literal(SpellMarketLocalization.uploadCategory() + " " + CATEGORIES[selectedCategory] + " ▼"),
				btn -> cycleCategory()).bounds(ix, sy + 140, iw, 20).build();
		addRenderableWidget(categoryButton);

		tagsBox = new EditBox(font, ix, sy + 180, iw - 60, 20, SpellMarketLocalization.uploadTags());
		tagsBox.setMaxLength(200);
		addRenderableWidget(tagsBox);

		addRenderableWidget(Button.builder(SpellMarketLocalization.uploadAddTag(), btn -> addTag())
				.bounds(ix + iw - 55, sy + 180, 55, 20).build());

		uploadButton = Button.builder(SpellMarketLocalization.uploadButton(), btn -> upload())
				.bounds(cx - 80, height - 40, 70, 20).build();
		addRenderableWidget(uploadButton);

		cancelButton = Button.builder(SpellMarketLocalization.uploadCancel(), btn -> onClose())
				.bounds(cx + 10, height - 40, 70, 20).build();
		addRenderableWidget(cancelButton);
	}

	private void addCloseButton() {
		addRenderableWidget(Button.builder(Component.literal("Close"), btn -> onClose())
				.bounds(width / 2 - 50, height / 2 + 20, 100, 20).build());
	}

	private void cycleCategory() {
		selectedCategory = (selectedCategory + 1) % CATEGORIES.length;
		categoryButton.setMessage(Component.literal(
				SpellMarketLocalization.uploadCategory() + " " + CATEGORIES[selectedCategory] + " ▼"));
	}

	private void addTag() {
		String input = tagsBox.getValue().trim();
		if (input.isEmpty()) return;
		for (String part : input.split(",")) {
			String tag = part.trim();
			if (!tag.isEmpty() && !tags.contains(tag)) tags.add(tag);
		}
		tagsBox.setValue("");
		errorMessage = null;
	}

	private void upload() {
		if (uploading || api == null) return;

		// 限流检查
		if (!api.getRateLimiter().canUpload()) {
			long secs = api.getRateLimiter().getUploadCooldownRemaining();
			errorMessage = SpellMarketLocalization.uploadCooldown(secs).getString();
			return;
		}

		String name = nameBox.getValue().trim();
		if (name.isEmpty()) { errorMessage = "Name is required"; return; }

		String desc = descBox.getValue().trim();
		if (desc.isEmpty()) { errorMessage = "Description is required"; return; }

		String author = authorBox.getValue().trim();
		if (author.isEmpty()) author = "Anonymous";

		addTag();
		if (tags.isEmpty()) { errorMessage = "At least one tag is required"; return; }

		if (definition == null) {
			errorMessage = "No spell loaded. Use /yhspell market upload <spell_id>";
			return;
		}

		uploading = true;
		errorMessage = null;
		uploadButton.active = false;

		api.uploadSpell(definition, name, desc, author, CATEGORIES[selectedCategory], tags)
				.thenAccept(resp -> {
					Minecraft.getInstance().execute(() -> {
						uploading = false;
						uploadButton.active = true;
						if (resp != null && resp.uuid != null) {
							minecraft.setScreen(new SuccessScreen(parent, resp.uuid, name));
						} else {
							errorMessage = SpellMarketLocalization.uploadFail().getString();
						}
					});
				});
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		renderBackground(g, mx, my, pt);

		if (api == null) {
			g.drawCenteredString(font, errorMessage != null ? errorMessage : "Disabled", width / 2, height / 2, 0xFF5555);
			super.render(g, mx, my, pt);
			return;
		}

		g.drawCenteredString(font, getTitle(), width / 2, 20, 0xFFFFFF);

		int cx = width / 2, sy = 50, lx = cx - 150;
		g.drawString(font, SpellMarketLocalization.uploadName().getString(), lx, sy + 10, 0xAAAAAA);
		g.drawString(font, SpellMarketLocalization.uploadDesc().getString(), lx, sy + 50, 0xAAAAAA);
		g.drawString(font, SpellMarketLocalization.uploadAuthor().getString(), lx, sy + 90, 0xAAAAAA);
		g.drawString(font, SpellMarketLocalization.uploadTags().getString(), lx, sy + 170, 0xAAAAAA);

		if (definition != null) {
			g.drawString(font, "Spell ID: " + definition.id, lx, sy - 15, 0x888888);
		}

		// 已添加的标签
		if (!tags.isEmpty()) {
			int ty = sy + 210, tx = lx;
			g.drawString(font, "Added tags:", tx, ty, 0xAAAAAA);
			ty += 15;
			for (int i = 0; i < tags.size(); i++) {
				String tag = tags.get(i);
				int tw = font.width(tag) + 8;
				g.fill(tx, ty, tx + tw, ty + 12, 0x80404040);
				g.drawString(font, tag, tx + 4, ty + 2, 0x55FFFF);
				int dx = tx + tw + 2;
				g.fill(dx, ty, dx + 12, ty + 12, 0x80AA0000);
				g.drawString(font, "×", dx + 3, ty + 2, 0xFFFFFF);
				if (mx >= dx && mx <= dx + 12 && my >= ty && my <= ty + 12) {
					g.fill(dx, ty, dx + 12, ty + 12, 0xC0FF0000);
				}
				tx += tw + 16;
				if (tx > width - 100) { tx = lx; ty += 15; }
			}
		}

		if (errorMessage != null) {
			g.drawCenteredString(font, errorMessage, width / 2, height - 60, 0xFF5555);
		}
		if (uploading) {
			g.drawCenteredString(font, "Uploading...", width / 2, height - 60, 0xFFFF55);
		}

		super.render(g, mx, my, pt);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int btn) {
		if (btn == 0 && !tags.isEmpty()) {
			int cx = width / 2, sy = 50, lx = cx - 150;
			int ty = sy + 225, tx = lx;
			for (int i = 0; i < tags.size(); i++) {
				int tw = font.width(tags.get(i)) + 8;
				int dx = tx + tw + 2;
				if (mx >= dx && mx <= dx + 12 && my >= ty && my <= ty + 12) {
					tags.remove(i);
					return true;
				}
				tx += tw + 16;
				if (tx > width - 100) { tx = lx; ty += 15; }
			}
		}
		return super.mouseClicked(mx, my, btn);
	}

	@Override
	public void onClose() {
		if (minecraft != null) minecraft.setScreen(parent);
	}

	@OnlyIn(Dist.CLIENT)
	private static class SuccessScreen extends Screen {
		private final Screen parent;
		private final String uuid;
		private final String name;

		protected SuccessScreen(Screen parent, String uuid, String name) {
			super(SpellMarketLocalization.uploadTitle());
			this.parent = parent;
			this.uuid = uuid;
			this.name = name;
		}

		@Override
		protected void init() {
			addRenderableWidget(Button.builder(Component.literal("OK"), btn -> onClose())
					.bounds(width / 2 - 50, height / 2 + 40, 100, 20).build());
		}

		@Override
		public void render(GuiGraphics g, int mx, int my, float pt) {
			renderBackground(g, mx, my, pt);
			g.drawCenteredString(font, "✓ Upload Successful!", width / 2, height / 2 - 40, 0x55FF55);
			g.drawCenteredString(font, "Spell: " + name, width / 2, height / 2 - 20, 0xFFFFFF);
			g.drawCenteredString(font, "UUID: " + uuid, width / 2, height / 2, 0xAAAAAA);
			super.render(g, mx, my, pt);
		}

		@Override
		public void onClose() {
			if (minecraft != null) minecraft.setScreen(parent);
		}
	}

}
