package dev.xkmc.youkaishomecoming.content.spell.market;

import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class SpellUploadDialog extends Screen {

	private static final String[] CATEGORIES = {
			"Original", "Creative", "Tech Demo", "Tutorial", "Challenge", "Other"
	};

	private static final ResourceLocation DRAFT_ID = new ResourceLocation("minecraft", "__yh_editor__");
	private static final int SPELL_ITEM_HEIGHT = 16;
	private static final int SPELL_LIST_TOP = 60;

	private final Screen parent;
	private SpellDefinition definition;
	private final SpellMarketAPI api;

	// Spell selection state (Phase 1)
	private final List<ResourceLocation> availableSpells = new ArrayList<>();
	private final List<ResourceLocation> filteredSpells = new ArrayList<>();
	private int spellListScroll = 0;
	private EditBox spellSearchBox;

	// Upload form state (Phase 2)
	private EditBox nameBox, descBox, authorBox, tagsBox;
	private Button categoryButton, uploadButton, cancelButton;
	private int selectedCategory = 0;
	private final List<String> tags = new ArrayList<>();
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

		if (definition == null) {
			initSpellSelection();
		} else {
			initUploadForm();
		}
	}

	// === Phase 1: Spell Selection ===

	private void initSpellSelection() {
		availableSpells.clear();
		for (ResourceLocation id : SpellRegistry.getAll().keySet()) {
			if (!id.equals(DRAFT_ID)) {
				availableSpells.add(id);
			}
		}
		availableSpells.sort(java.util.Comparator.comparing(ResourceLocation::toString));
		filteredSpells.clear();
		filteredSpells.addAll(availableSpells);
		spellListScroll = 0;

		int cx = width / 2;
		int iw = 300;
		int lx = cx - 150;

		spellSearchBox = new EditBox(font, lx, 30, iw, 20, Component.literal("Search"));
		spellSearchBox.setMaxLength(100);
		spellSearchBox.setHint(Component.literal("Search spells..."));
		spellSearchBox.setResponder(text -> filterSpells());
		addRenderableWidget(spellSearchBox);

		addRenderableWidget(Button.builder(SpellMarketLocalization.uploadCancel(), btn -> onClose())
				.bounds(cx - 50, height - 30, 100, 20).build());
	}

	private void filterSpells() {
		String filter = spellSearchBox.getValue().toLowerCase();
		filteredSpells.clear();
		for (ResourceLocation id : availableSpells) {
			if (id.toString().toLowerCase().contains(filter)) {
				filteredSpells.add(id);
			}
		}
		spellListScroll = 0;
	}

	private void selectSpell(ResourceLocation spellId) {
		SpellDefinition def = SpellRegistry.get(spellId);
		if (def != null) {
			this.definition = def;
			this.init(minecraft, width, height);
		}
	}

	// === Phase 2: Upload Form ===

	private void initUploadForm() {
		int cx = width / 2;
		int sy = 55;
		int lx = cx - 150;
		int iw = 300;

		// Back to spell selection
		addRenderableWidget(Button.builder(Component.literal("◀ Change Spell"), btn -> {
			this.definition = null;
			this.init(minecraft, width, height);
		}).bounds(lx, sy - 18, 100, 14).build());

		nameBox = new EditBox(font, lx, sy + 20, iw, 20, SpellMarketLocalization.uploadName());
		nameBox.setMaxLength(100);
		if (definition != null) nameBox.setValue(definition.id.toString());
		addRenderableWidget(nameBox);

		descBox = new EditBox(font, lx, sy + 60, iw, 20, SpellMarketLocalization.uploadDesc());
		descBox.setMaxLength(500);
		addRenderableWidget(descBox);

		authorBox = new EditBox(font, lx, sy + 100, iw, 20, SpellMarketLocalization.uploadAuthor());
		authorBox.setMaxLength(50);
		try { authorBox.setValue(Minecraft.getInstance().getUser().getName()); } catch (Exception ignored) {}
		addRenderableWidget(authorBox);

		categoryButton = Button.builder(
				Component.literal(SpellMarketLocalization.uploadCategory().getString() + " " + CATEGORIES[selectedCategory] + " \u25BC"),
				btn -> cycleCategory()).bounds(lx, sy + 140, iw, 20).build();
		addRenderableWidget(categoryButton);

		tagsBox = new EditBox(font, lx, sy + 180, iw - 60, 20, SpellMarketLocalization.uploadTags());
		tagsBox.setMaxLength(200);
		addRenderableWidget(tagsBox);

		addRenderableWidget(Button.builder(SpellMarketLocalization.uploadAddTag(), btn -> addTag())
				.bounds(lx + iw - 55, sy + 180, 55, 20).build());

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
				SpellMarketLocalization.uploadCategory().getString() + " " + CATEGORIES[selectedCategory] + " \u25BC"));
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

	/**
	 * Validates that the spell definition has valid content before uploading.
	 * Returns an error message string if invalid, or null if valid.
	 */
	private String validateSpellDefinition() {
		if (definition == null) return "No spell selected";
		if (definition.id == null) return "Spell ID is missing";
		if (definition.display == null) return "Spell display info is missing";
		if (definition.display.name() == null || definition.display.name().trim().isEmpty())
			return "Spell display name is empty";
		if (definition.entryPhase == null) return "Entry phase is not set";
		if (definition.phases == null || definition.phases.isEmpty())
			return "Spell has no phases";
		if (!definition.phases.containsKey(definition.entryPhase))
			return "Entry phase " + definition.entryPhase + " not found in spell phases";

		boolean hasContent = false;
		for (var phase : definition.phases.values()) {
			if (phase == null || phase.id == null) continue;
			if (!phase.onEnter.isEmpty() || !phase.onTick.isEmpty() ||
					!phase.onExit.isEmpty() || !phase.onDamage.isEmpty() ||
					!phase.transitions.isEmpty()) {
				hasContent = true;
				break;
			}
		}
		if (!hasContent) return "Spell has no actions or transitions in any phase";

		try {
			SpellDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
					.getOrThrow(false, e -> {});
		} catch (Exception e) {
			return "Invalid spell content: " + e.getMessage();
		}

		return null;
	}

	private void upload() {
		if (uploading || api == null) return;

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

		String authorUuid = "";
		try { authorUuid = Minecraft.getInstance().getUser().getProfileId().toString(); } catch (Exception ignored) {}

		addTag();
		if (tags.isEmpty()) { errorMessage = "At least one tag is required"; return; }

		String validationError = validateSpellDefinition();
		if (validationError != null) {
			errorMessage = validationError;
			return;
		}

		uploading = true;
		errorMessage = null;
		uploadButton.active = false;

		api.uploadSpell(definition, name, desc, author, authorUuid, CATEGORIES[selectedCategory], tags)
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

	// === Rendering ===

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		renderBackground(g);

		if (api == null) {
			g.drawCenteredString(font, errorMessage != null ? errorMessage : "Disabled", width / 2, height / 2, 0xFF5555);
			super.render(g, mx, my, pt);
			return;
		}

		g.drawCenteredString(font, getTitle(), width / 2, 10, 0xFFFFFF);

		if (definition == null) {
			renderSpellSelection(g, mx, my);
		} else {
			renderUploadForm(g, mx, my);
		}

		super.render(g, mx, my, pt);
	}

	private void renderSpellSelection(GuiGraphics g, int mx, int my) {
		int cx = width / 2;
		int lx = cx - 150;
		int iw = 300;
		int listTop = SPELL_LIST_TOP;
		int listBottom = height - 40;
		int listH = listBottom - listTop;

		g.drawString(font, "Select a spell to upload:", lx, 42, 0xAAAAAA);

		if (filteredSpells.isEmpty()) {
			g.drawCenteredString(font, "No spells available. Create or load a spell first.", width / 2, height / 2, 0xFF5555);
			return;
		}

		int maxScroll = Math.max(0, filteredSpells.size() * SPELL_ITEM_HEIGHT - listH);
		spellListScroll = Math.max(0, Math.min(spellListScroll, maxScroll));

		g.enableScissor(0, listTop, width, listBottom);

		int y = listTop - spellListScroll;
		for (ResourceLocation spellId : filteredSpells) {
			if (y + SPELL_ITEM_HEIGHT < listTop || y > listBottom) {
				y += SPELL_ITEM_HEIGHT;
				continue;
			}
			boolean hover = mx >= lx && mx <= lx + iw && my >= y && my <= y + SPELL_ITEM_HEIGHT - 2;
			g.fill(lx, y, lx + iw, y + SPELL_ITEM_HEIGHT - 2, hover ? 0x80404040 : 0x60202020);
			g.drawString(font, spellId.toString(), lx + 4, y + 3, 0xFFFFFF);
			y += SPELL_ITEM_HEIGHT;
		}

		g.disableScissor();

		// Scrollbar
		if (maxScroll > 0) {
			int scrollBarH = Math.max(10, listH * listH / (filteredSpells.size() * SPELL_ITEM_HEIGHT));
			int scrollBarY = listTop + (int)((long)(listH - scrollBarH) * spellListScroll / maxScroll);
			g.fill(lx + iw + 2, scrollBarY, lx + iw + 4, scrollBarY + scrollBarH, 0xFF606060);
		}
	}

	private void renderUploadForm(GuiGraphics g, int mx, int my) {
		int cx = width / 2, sy = 55, lx = cx - 150;
		g.drawString(font, SpellMarketLocalization.uploadName().getString(), lx, sy + 10, 0xAAAAAA);
		g.drawString(font, SpellMarketLocalization.uploadDesc().getString(), lx, sy + 50, 0xAAAAAA);
		g.drawString(font, SpellMarketLocalization.uploadAuthor().getString(), lx, sy + 90, 0xAAAAAA);
		g.drawString(font, SpellMarketLocalization.uploadTags().getString(), lx, sy + 170, 0xAAAAAA);

		if (definition != null) {
			g.drawString(font, "Spell: " + definition.id, lx + 110, sy - 14, 0x888888);
		}

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
				g.drawString(font, "\u00D7", dx + 3, ty + 2, 0xFFFFFF);
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
	}

	// === Input handling ===

	@Override
	public boolean mouseClicked(double mx, double my, int btn) {
		if (btn != 0) return super.mouseClicked(mx, my, btn);

		if (definition == null && api != null) {
			return handleClickSpellList(mx, my) || super.mouseClicked(mx, my, btn);
		}

		if (!tags.isEmpty()) {
			int cx = width / 2, sy = 55, lx = cx - 150;
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

	private boolean handleClickSpellList(double mx, double my) {
		int cx = width / 2;
		int lx = cx - 150;
		int iw = 300;
		int listTop = SPELL_LIST_TOP;
		int listBottom = height - 40;

		if (my < listTop || my > listBottom) return false;
		if (mx < lx || mx > lx + iw) return false;

		int listH = listBottom - listTop;
		int maxScroll = Math.max(0, filteredSpells.size() * SPELL_ITEM_HEIGHT - listH);
		spellListScroll = Math.max(0, Math.min(spellListScroll, maxScroll));

		int y = listTop - spellListScroll;
		for (ResourceLocation spellId : filteredSpells) {
			if (my >= y && my <= y + SPELL_ITEM_HEIGHT - 2) {
				selectSpell(spellId);
				return true;
			}
			y += SPELL_ITEM_HEIGHT;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double delta) {
		if (definition == null && api != null) {
			int listTop = SPELL_LIST_TOP;
			int listBottom = height - 40;
			int listH = listBottom - listTop;
			int maxScroll = Math.max(0, filteredSpells.size() * SPELL_ITEM_HEIGHT - listH);
			spellListScroll = Math.max(0, Math.min(spellListScroll - (int)(delta * 20), maxScroll));
			return true;
		}
		return super.mouseScrolled(mx, my, delta);
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
			renderBackground(g);
			g.drawCenteredString(font, "\u2713 Upload Successful!", width / 2, height / 2 - 40, 0x55FF55);
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
