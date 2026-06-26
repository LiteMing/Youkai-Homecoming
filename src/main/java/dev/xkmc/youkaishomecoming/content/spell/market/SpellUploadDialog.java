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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class SpellUploadDialog extends Screen {

	private static final String CATEGORY_CANON = "Canon";
	private static final String CATEGORY_ORIGINAL = "Original";
	private static final ResourceLocation DRAFT_ID = new ResourceLocation("minecraft", "__yh_editor__");
	private static final int SPELL_ITEM_HEIGHT = 16;
	private static final int SPELL_LIST_TOP = 60;
	private static final int FORM_TOP = 45;
	private static final int DROPDOWN_ROW_HEIGHT = 14;
	private static final int DROPDOWN_MAX_VISIBLE = 10;

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
	private Button sourceButton, characterButton, uploadButton, cancelButton;
	private String selectedSourceTag = null;
	private String selectedCharacterTag = null;
	private final List<String> tags = new ArrayList<>();
	private DropdownState dropdown = null;
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

		spellSearchBox = new EditBox(font, lx, 30, iw, 20, SpellMarketLocalization.search());
		spellSearchBox.setMaxLength(100);
		spellSearchBox.setHint(SpellMarketLocalization.search());
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
		int sy = FORM_TOP;
		int lx = cx - 150;
		int iw = 300;
		dropdown = null;

		// Back to spell selection
		addRenderableWidget(Button.builder(SpellMarketLocalization.uploadChangeSpell(), btn -> {
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

		sourceButton = Button.builder(sourceButtonText(), btn -> openSourceDropdown())
				.bounds(lx, sy + 125, iw, 20).build();
		addRenderableWidget(sourceButton);

		characterButton = Button.builder(characterButtonText(), btn -> openCharacterDropdown())
				.bounds(lx, sy + 150, iw, 20).build();
		addRenderableWidget(characterButton);

		tagsBox = new EditBox(font, lx, sy + 185, iw - 60, 20, SpellMarketLocalization.uploadTags());
		tagsBox.setMaxLength(200);
		addRenderableWidget(tagsBox);

		addRenderableWidget(Button.builder(SpellMarketLocalization.uploadAddTag(), btn -> addTag())
				.bounds(lx + iw - 55, sy + 185, 55, 20).build());

		uploadButton = Button.builder(SpellMarketLocalization.uploadButton(), btn -> upload())
				.bounds(cx - 80, height - 40, 70, 20).build();
		addRenderableWidget(uploadButton);

		cancelButton = Button.builder(SpellMarketLocalization.uploadCancel(), btn -> onClose())
				.bounds(cx + 10, height - 40, 70, 20).build();
		addRenderableWidget(cancelButton);
	}

	private void addCloseButton() {
		addRenderableWidget(Button.builder(SpellMarketLocalization.close(), btn -> onClose())
				.bounds(width / 2 - 50, height / 2 + 20, 100, 20).build());
	}

	private void openSourceDropdown() {
		List<DropdownOption> options = new ArrayList<>();
		options.add(new DropdownOption(null, SpellMarketLocalization.uploadNone(), ""));
		for (SpellMarketBuiltinTags.SourceTag source : SpellMarketBuiltinTags.SOURCES) {
			options.add(new DropdownOption(source.tag(), source.display(), source.id()));
		}
		dropdown = new DropdownState(sourceButton.getX(), sourceButton.getY() + 20,
				sourceButton.getWidth(), options, selectedSourceTag, option -> {
			selectedSourceTag = option.tag();
			if (!characterAllowedForSource(selectedCharacterTag)) {
				selectedCharacterTag = null;
				characterButton.setMessage(characterButtonText());
			}
			sourceButton.setMessage(sourceButtonText());
			errorMessage = null;
		});
	}

	private void openCharacterDropdown() {
		List<DropdownOption> options = new ArrayList<>();
		options.add(new DropdownOption(null, SpellMarketLocalization.uploadNone(), ""));
		for (SpellMarketBuiltinTags.CharacterTag character : SpellMarketBuiltinTags.charactersForSource(selectedSourceTag)) {
			options.add(new DropdownOption(character.tag(), character.display(), character.id() + " " + character.englishName()));
		}
		dropdown = new DropdownState(characterButton.getX(), characterButton.getY() + 20,
				characterButton.getWidth(), options, selectedCharacterTag, option -> {
			selectedCharacterTag = option.tag();
			characterButton.setMessage(characterButtonText());
			errorMessage = null;
		});
	}

	private Component sourceButtonText() {
		return SpellMarketLocalization.uploadSource()
				.append(" ")
				.append(selectedSourceDisplay())
				.append(" \u25BC");
	}

	private Component characterButtonText() {
		return SpellMarketLocalization.uploadCharacter()
				.append(" ")
				.append(selectedCharacterDisplay())
				.append(" \u25BC");
	}

	private Component selectedSourceDisplay() {
		if (selectedSourceTag == null) {
			return SpellMarketLocalization.uploadNone();
		}
		return SpellMarketBuiltinTags.display(selectedSourceTag);
	}

	private Component selectedCharacterDisplay() {
		if (selectedCharacterTag == null) {
			return SpellMarketLocalization.uploadNone();
		}
		return SpellMarketBuiltinTags.display(selectedCharacterTag);
	}

	private boolean characterAllowedForSource(String characterTag) {
		if (characterTag == null || selectedSourceTag == null) return true;
		for (SpellMarketBuiltinTags.CharacterTag character : SpellMarketBuiltinTags.charactersForSource(selectedSourceTag)) {
			if (character.tag().equals(characterTag)) {
				return true;
			}
		}
		return false;
	}

	private void addTag() {
		String input = tagsBox.getValue().trim();
		if (input.isEmpty()) return;
		for (String part : input.split(",")) {
			String tag = SpellMarketBuiltinTags.normalize(part);
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
		if (definition == null) return SpellMarketLocalization.validationNoSpell().getString();
		if (definition.id == null) return SpellMarketLocalization.validationIdMissing().getString();
		if (definition.display == null) return SpellMarketLocalization.validationDisplayMissing().getString();
		if (definition.display.name() == null || definition.display.name().trim().isEmpty())
			return SpellMarketLocalization.validationDisplayNameEmpty().getString();
		if (definition.entryPhase == null) return SpellMarketLocalization.validationEntryPhaseMissing().getString();
		if (definition.phases == null || definition.phases.isEmpty())
			return SpellMarketLocalization.validationNoPhases().getString();
		if (!definition.phases.containsKey(definition.entryPhase))
			return SpellMarketLocalization.validationEntryPhaseNotFound(definition.entryPhase.toString()).getString();

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
		if (!hasContent) return SpellMarketLocalization.validationNoContent().getString();

		try {
			SpellDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
					.getOrThrow(false, e -> {});
		} catch (Exception e) {
			return SpellMarketLocalization.validationInvalidContent(e.getMessage() == null ? "" : e.getMessage()).getString();
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
		if (name.isEmpty()) { errorMessage = SpellMarketLocalization.validationNameRequired().getString(); return; }

		String desc = descBox.getValue().trim();
		if (desc.isEmpty()) { errorMessage = SpellMarketLocalization.validationDescRequired().getString(); return; }

		String author = authorBox.getValue().trim();
		if (author.isEmpty()) author = SpellMarketLocalization.anonymous().getString();

		String authorUuid = "";
		try { authorUuid = Minecraft.getInstance().getUser().getProfileId().toString(); } catch (Exception ignored) {}

		addTag();
		List<String> uploadTags = collectUploadTags();
		if (uploadTags.isEmpty()) { errorMessage = SpellMarketLocalization.validationTagRequired().getString(); return; }

		String validationError = validateSpellDefinition();
		if (validationError != null) {
			errorMessage = validationError;
			return;
		}

		uploading = true;
		errorMessage = null;
		uploadButton.active = false;

		api.uploadSpell(definition, name, desc, author, authorUuid, categoryFor(uploadTags), uploadTags)
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

	private List<String> collectUploadTags() {
		LinkedHashSet<String> merged = new LinkedHashSet<>();
		for (String tag : tags) {
			addUploadTag(merged, tag);
		}
		addUploadTag(merged, selectedSourceTag());
		addUploadTag(merged, selectedCharacterTag());
		return new ArrayList<>(merged);
	}

	private void addUploadTag(LinkedHashSet<String> merged, String tag) {
		String normalized = SpellMarketBuiltinTags.normalize(tag);
		if (!normalized.isBlank()) {
			merged.add(normalized);
		}
	}

	private String selectedSourceTag() {
		return selectedSourceTag;
	}

	private String selectedCharacterTag() {
		return selectedCharacterTag;
	}

	private List<String> selectedBuiltinTags() {
		List<String> selected = new ArrayList<>();
		String source = selectedSourceTag();
		String character = selectedCharacterTag();
		if (source != null) selected.add(source);
		if (character != null) selected.add(character);
		return selected;
	}

	private String categoryFor(List<String> uploadTags) {
		for (String tag : uploadTags) {
			if (SpellMarketBuiltinTags.isBuiltin(tag)) {
				return CATEGORY_CANON;
			}
		}
		return CATEGORY_ORIGINAL;
	}

	// === Rendering ===

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		renderBackground(g);

		if (api == null) {
			g.drawCenteredString(font, errorMessage != null ? errorMessage :
					SpellMarketLocalization.disabled().getString(), width / 2, height / 2, 0xFF5555);
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
		if (definition != null) {
			renderDropdown(g, mx, my);
		}
	}

	private void renderSpellSelection(GuiGraphics g, int mx, int my) {
		int cx = width / 2;
		int lx = cx - 150;
		int iw = 300;
		int listTop = SPELL_LIST_TOP;
		int listBottom = height - 40;
		int listH = listBottom - listTop;

		g.drawString(font, SpellMarketLocalization.uploadSelect().getString(), lx, 42, 0xAAAAAA);

		if (filteredSpells.isEmpty()) {
			g.drawCenteredString(font, SpellMarketLocalization.uploadNoSpells(), width / 2, height / 2, 0xFF5555);
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
		int cx = width / 2, sy = FORM_TOP, lx = cx - 150;
		g.drawString(font, SpellMarketLocalization.uploadName().getString(), lx, sy + 10, 0xAAAAAA);
		g.drawString(font, SpellMarketLocalization.uploadDesc().getString(), lx, sy + 45, 0xAAAAAA);
		g.drawString(font, SpellMarketLocalization.uploadAuthor().getString(), lx, sy + 80, 0xAAAAAA);
		g.drawString(font, SpellMarketLocalization.uploadBuiltinTags().getString(), lx, sy + 115, 0xAAAAAA);
		g.drawString(font, SpellMarketLocalization.uploadTags().getString(), lx, sy + 175, 0xAAAAAA);

		if (definition != null) {
			g.drawString(font, SpellMarketLocalization.uploadSpell(definition.id.toString()).getString(),
					lx + 110, sy - 14, 0x888888);
		}

		List<String> builtinTags = selectedBuiltinTags();
		int labelY = sy + 215;
		if (!builtinTags.isEmpty()) {
			g.drawString(font, SpellMarketLocalization.uploadBuiltinTags().getString(), lx, labelY, 0xAAAAAA);
			renderTagChips(g, builtinTags, lx, labelY + 15, false, mx, my);
			labelY += 30;
		}

		if (!tags.isEmpty()) {
			g.drawString(font, SpellMarketLocalization.uploadAddedTags().getString(), lx, labelY, 0xAAAAAA);
			renderTagChips(g, tags, lx, labelY + 15, true, mx, my);
		}

		if (errorMessage != null) {
			g.drawCenteredString(font, errorMessage, width / 2, height - 60, 0xFF5555);
		}
		if (uploading) {
			g.drawCenteredString(font, SpellMarketLocalization.uploadUploading(), width / 2, height - 60, 0xFFFF55);
		}
	}

	private void renderTagChips(GuiGraphics g, List<String> renderTags, int startX, int startY, boolean removable, int mx, int my) {
		int tx = startX, ty = startY;
		for (String tag : renderTags) {
			String label = tagLabel(tag);
			int tw = font.width(label) + 8;
			g.fill(tx, ty, tx + tw, ty + 12, removable ? 0x80404040 : 0x80555588);
			g.drawString(font, label, tx + 4, ty + 2, removable ? 0x55FFFF : 0xCCCCFF);
			if (removable) {
				int dx = tx + tw + 2;
				g.fill(dx, ty, dx + 12, ty + 12, 0x80AA0000);
				g.drawString(font, "\u00D7", dx + 3, ty + 2, 0xFFFFFF);
				if (mx >= dx && mx <= dx + 12 && my >= ty && my <= ty + 12) {
					g.fill(dx, ty, dx + 12, ty + 12, 0xC0FF0000);
				}
			}
			tx += tw + (removable ? 16 : 4);
			if (tx > width - 100) { tx = startX; ty += 15; }
		}
	}

	private void renderDropdown(GuiGraphics g, int mx, int my) {
		if (dropdown == null) return;
		int visible = dropdown.visibleRows();
		int searchH = DROPDOWN_ROW_HEIGHT + 2;
		int h = searchH + visible * DROPDOWN_ROW_HEIGHT;
		g.fill(dropdown.x - 1, dropdown.y - 1, dropdown.x + dropdown.w + 1, dropdown.y + h + 1, 0xE0101010);
		String searchLabel = SpellMarketLocalization.search().getString() + " " + dropdown.search;
		g.fill(dropdown.x, dropdown.y, dropdown.x + dropdown.w, dropdown.y + searchH, 0xE0181828);
		g.drawString(font, font.plainSubstrByWidth(searchLabel, dropdown.w - 8), dropdown.x + 4, dropdown.y + 3, 0xBBBBBB);
		g.enableScissor(dropdown.x, dropdown.y + searchH, dropdown.x + dropdown.w, dropdown.y + h);
		for (int row = 0; row < visible; row++) {
			int idx = dropdown.scroll + row;
			int y = dropdown.y + searchH + row * DROPDOWN_ROW_HEIGHT;
			boolean hover = mx >= dropdown.x && mx <= dropdown.x + dropdown.w &&
					my >= y && my <= y + DROPDOWN_ROW_HEIGHT;
			DropdownOption option = dropdown.filtered.get(idx);
			int bg = option.matches(dropdown.selectedTag) ? 0xC055AA55 : (hover ? 0xC0505050 : 0xC0282828);
			g.fill(dropdown.x, y, dropdown.x + dropdown.w, y + DROPDOWN_ROW_HEIGHT, bg);
			String label = font.plainSubstrByWidth(option.label().getString(), dropdown.w - 8);
			g.drawString(font, label, dropdown.x + 4, y + 3, 0xFFFFFF);
		}
		g.disableScissor();
		if (dropdown.filtered.size() > visible) {
			int listH = visible * DROPDOWN_ROW_HEIGHT;
			int barH = Math.max(8, listH * visible / dropdown.filtered.size());
			int maxScroll = dropdown.maxScroll();
			int barY = dropdown.y + searchH + (maxScroll == 0 ? 0 : (listH - barH) * dropdown.scroll / maxScroll);
			g.fill(dropdown.x + dropdown.w - 3, barY, dropdown.x + dropdown.w - 1, barY + barH, 0xFFAAAAAA);
		}
	}

	// === Input handling ===

	@Override
	public boolean mouseClicked(double mx, double my, int btn) {
		if (btn != 0) return super.mouseClicked(mx, my, btn);
		if (handleDropdownClick(mx, my)) return true;

		if (definition == null && api != null) {
			return handleClickSpellList(mx, my) || super.mouseClicked(mx, my, btn);
		}

		if (!tags.isEmpty()) {
			int cx = width / 2, lx = cx - 150;
			int ty = manualTagChipY(), tx = lx;
			for (int i = 0; i < tags.size(); i++) {
				int tw = font.width(tagLabel(tags.get(i))) + 8;
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

	private boolean handleDropdownClick(double mx, double my) {
		if (dropdown == null) return false;
		if (mx < dropdown.x || mx > dropdown.x + dropdown.w ||
				my < dropdown.y || my > dropdown.y + dropdown.height()) {
			dropdown = null;
			return false;
		}
		int searchH = DROPDOWN_ROW_HEIGHT + 2;
		if (my < dropdown.y + searchH) {
			return true;
		}
		int idx = dropdown.scroll + (int)((my - dropdown.y - searchH) / DROPDOWN_ROW_HEIGHT);
		if (idx >= 0 && idx < dropdown.filtered.size()) {
			dropdown.onSelect.accept(dropdown.filtered.get(idx));
		}
		dropdown = null;
		return true;
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
		if (dropdown != null && mx >= dropdown.x && mx <= dropdown.x + dropdown.w &&
				my >= dropdown.y && my <= dropdown.y + dropdown.height()) {
			dropdown.scroll = Math.max(0, Math.min(dropdown.scroll - (int) delta, dropdown.maxScroll()));
			return true;
		}
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
	public boolean keyPressed(int key, int scan, int mods) {
		if (dropdown != null && key == 256) {
			dropdown = null;
			return true;
		}
		if (dropdown != null && key == 259) {
			dropdown.backspace();
			return true;
		}
		if (dropdown != null && (key == 257 || key == 335)) {
			DropdownOption option = dropdown.selectedOrFirst();
			if (option != null) {
				dropdown.onSelect.accept(option);
			}
			dropdown = null;
			return true;
		}
		return super.keyPressed(key, scan, mods);
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		if (dropdown != null && !Character.isISOControl(codePoint)) {
			dropdown.type(codePoint);
			return true;
		}
		return super.charTyped(codePoint, modifiers);
	}

	private int manualTagChipY() {
		return FORM_TOP + (selectedBuiltinTags().isEmpty() ? 230 : 260);
	}

	private String tagLabel(String tag) {
		return SpellMarketLocalization.tag(tag).getString();
	}

	@Override
	public void onClose() {
		if (minecraft != null) minecraft.setScreen(parent);
	}

	@OnlyIn(Dist.CLIENT)
	private record DropdownOption(String tag, Component label, String searchText) {
		private boolean matches(String selectedTag) {
			return tag == null ? selectedTag == null : tag.equals(selectedTag);
		}

		private boolean matchesSearch(String query) {
			if (query.isBlank()) return true;
			String lower = query.toLowerCase(Locale.ROOT);
			return label.getString().toLowerCase(Locale.ROOT).contains(lower) ||
					searchText.toLowerCase(Locale.ROOT).contains(lower) ||
					(tag != null && tag.toLowerCase(Locale.ROOT).contains(lower));
		}
	}

	@OnlyIn(Dist.CLIENT)
	private static class DropdownState {
		private final int x, y, w;
		private final List<DropdownOption> options;
		private final List<DropdownOption> filtered = new ArrayList<>();
		private final String selectedTag;
		private final Consumer<DropdownOption> onSelect;
		private String search = "";
		private int scroll;

		private DropdownState(int x, int y, int w, List<DropdownOption> options, String selectedTag, Consumer<DropdownOption> onSelect) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.options = options;
			this.selectedTag = selectedTag;
			this.onSelect = onSelect;
			updateFilter();
			int selected = selectedIndex();
			this.scroll = Math.max(0, Math.min(selected - DROPDOWN_MAX_VISIBLE / 2, maxScroll()));
		}

		private int visibleRows() {
			return Math.min(DROPDOWN_MAX_VISIBLE, filtered.size());
		}

		private int height() {
			return DROPDOWN_ROW_HEIGHT + 2 + visibleRows() * DROPDOWN_ROW_HEIGHT;
		}

		private int maxScroll() {
			return Math.max(0, filtered.size() - visibleRows());
		}

		private void type(char codePoint) {
			search += codePoint;
			updateFilter();
		}

		private void backspace() {
			if (!search.isEmpty()) {
				search = search.substring(0, search.length() - 1);
				updateFilter();
			}
		}

		private void updateFilter() {
			filtered.clear();
			for (DropdownOption option : options) {
				if (option.matchesSearch(search)) {
					filtered.add(option);
				}
			}
			scroll = Math.max(0, Math.min(scroll, maxScroll()));
		}

		private int selectedIndex() {
			for (int i = 0; i < filtered.size(); i++) {
				if (filtered.get(i).matches(selectedTag)) {
					return i;
				}
			}
			return 0;
		}

		private DropdownOption selectedOrFirst() {
			for (DropdownOption option : filtered) {
				if (option.matches(selectedTag)) {
					return option;
				}
			}
			return filtered.isEmpty() ? null : filtered.get(0);
		}
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
			addRenderableWidget(Button.builder(SpellMarketLocalization.ok(), btn -> onClose())
					.bounds(width / 2 - 50, height / 2 + 40, 100, 20).build());
		}

		@Override
		public void render(GuiGraphics g, int mx, int my, float pt) {
			renderBackground(g);
			g.drawCenteredString(font, "\u2713 " + SpellMarketLocalization.uploadSuccessTitle().getString(),
					width / 2, height / 2 - 40, 0x55FF55);
			g.drawCenteredString(font, SpellMarketLocalization.uploadSpell(name).getString(),
					width / 2, height / 2 - 20, 0xFFFFFF);
			g.drawCenteredString(font, "UUID: " + uuid, width / 2, height / 2, 0xAAAAAA);
			super.render(g, mx, my, pt);
		}

		@Override
		public void onClose() {
			if (minecraft != null) minecraft.setScreen(parent);
		}
	}

}
