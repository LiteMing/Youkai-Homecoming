package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import dev.xkmc.youkaishomecoming.compat.ysm.YsmModelCatalog;
import dev.xkmc.youkaishomecoming.content.spell.preview.YsmEditorController;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static dev.xkmc.youkaishomecoming.content.spell.preview.YsmEditorController.text;

/** Author-curated actions and whole native forms beside the live preview. */
public final class YsmPresetsDockPanel extends YsmEditorPanel {

	private final Runnable openScenarios, openCatalog;
	private String parameterName = "", parameterValue = "0", page = "wheel";
	private String wheelGroup = "", wheelTitle = "", controlGroup = "", shownModel = "";
	private final ArrayDeque<Menu> parents = new ArrayDeque<>();
	private record Menu(String group, String title, int page) { }
	private int wheelPage, readinessTicks;
	private boolean details, needsInputs;
	private YsmModelCatalog shownCatalog;

	public YsmPresetsDockPanel(YsmEditorController editor, Runnable openScenarios, Runnable openCatalog) {
		super(editor);
		this.openScenarios = openScenarios;
		this.openCatalog = openCatalog;
	}

	@Override public String dockId() { return "ysm_presets"; }
	@Override public String dockTitle() { return text("presets").getString(); }

	@Override protected void build() {
		if (editor.profile() == null) {
			label(text("select_model_first"));
			button(text("choose_model"), openCatalog, true);
			return;
		}
		if (!shownModel.equals(editor.model())) {
			shownModel = editor.model();
			wheelGroup = wheelTitle = controlGroup = "";
			wheelPage = 0;
			parents.clear();
		}
		shownCatalog = editor.catalog();
		needsInputs = false;
		label(text("scene_model", editor.model()));
		if (editor.editingTrigger() != null) label(text("editing_scene", text("trigger." + editor.editingTrigger().id())));
		var presets = new ArrayList<>(presetOptions());
		presets.add(0, new Option("", text("new_preset")));
		select("saved_preset", text("pick_preset"), editor.profile().presets().containsKey(editor.presetId()) ? editor.presetId() : "", presets, editor::selectPreset);
		edit("id", text("preset_id"), editor.presetId(), 128, editor::presetId);
		buttonRow(new Action(text("save_preview"), editor::savePreview, editor.mayWriteWorld()),
				new Action(text("save_preview_as"), editor::savePreviewAsNew, editor.mayWriteWorld()),
				new Action(text("delete_preset"), editor::deletePreset, editor.profile().presets().containsKey(editor.presetId()), text("delete_preset_help")));
		button(text(details ? "preset_details_hide" : "preset_details"), () -> { details = !details; changed(); }, true);
		if (details) {
			edit("description", text("description"), editor.description(), 256, editor::description);
			edit("ticks", text("ticks"), editor.ticks(), 10, editor::ticks);
			label(text("capture_help"));
			button(text("discard_fields"), editor::discardPresetFields, true);
		}
		choiceGrid("author_page", List.of(new Option("wheel", text("author_wheel")), new Option("controls", text("controls")),
				new Option("advanced", text("advanced"))), page, 3, value -> { page = value; toTop(); });
		if (page.equals("advanced")) advanced();
		else if (shownCatalog.status() != YsmModelCatalog.Status.READY) label(text("native_unavailable"));
		else if (page.equals("controls")) controls(shownCatalog);
		else wheel(shownCatalog);
		button(text("back_to_scenarios"), () -> {
			if (editor.stagePresetEdits()) openScenarios.run();
		}, true);
		button(text("apply_entity"), () -> editor.applyToEntity(false),
				editor.mayWriteWorld() && !editor.typeTarget() && !editor.presetId().isEmpty());
		button(text("clear_entity"), () -> editor.applyToEntity(true), editor.mayWriteWorld() && !editor.typeTarget());
	}

	private void wheel(YsmModelCatalog catalog) {
		label(text("wheel_quick_help"));
		if (!parents.isEmpty()) button(text("wheel_parent"), () -> {
			var parent = parents.removeLast();
			wheelGroup = parent.group(); wheelTitle = parent.title(); wheelPage = parent.page(); toTop();
		}, true);
		if (!wheelTitle.isBlank()) label(Component.literal(wheelTitle));
		String playing = editor.previewClip();
		label(text("current_action", actionTitle(catalog, playing)));
		button(text("default_body"), () -> editor.selectClip(""), true);
		var entries = catalog.wheelEntries(wheelGroup);
		if (entries.isEmpty()) {
			label(text("no_author_wheel"));
			button(text("advanced"), () -> { page = "advanced"; toTop(); }, true);
			return;
		}
		int pages = (entries.size() + 7) / 8;
		wheelPage = Math.max(0, Math.min(wheelPage, pages - 1));
		var visible = entries.subList(wheelPage * 8, Math.min(entries.size(), (wheelPage + 1) * 8));
		choiceGrid("author_wheel", visible.stream().map(entry -> new Option(entry.id(), wheelTitle(catalog, entry),
				text(!entry.submenu().isEmpty() ? "submenu" : !entry.configGroup().isEmpty() ? "open_controls" : "clip_help"))).toList(),
				playing.isEmpty() ? "\n" : playing, w >= 320 ? 3 : 2,
				id -> visible.stream().filter(entry -> entry.id().equals(id)).findFirst().ifPresent(entry -> chooseWheel(catalog, entry)));
		if (pages > 1) {
			label(text("wheel_page", wheelPage + 1, pages));
			buttonRow(new Action(text("previous_page"), () -> { wheelPage--; changed(); }, wheelPage > 0),
					new Action(text("next_page"), () -> { wheelPage++; changed(); }, wheelPage + 1 < pages));
		}
	}

	private void chooseWheel(YsmModelCatalog catalog, YsmModelCatalog.WheelEntry entry) {
		if (!entry.submenu().isEmpty()) {
			parents.addLast(new Menu(wheelGroup, wheelTitle, wheelPage));
			wheelGroup = entry.submenu(); wheelTitle = wheelTitle(catalog, entry).getString(); wheelPage = 0;
		} else if (!entry.configGroup().isEmpty()) {
			// Opening a form keeps the playing expression even if this entry also names a clip.
			controlGroup = entry.configGroup();
			page = "controls";
		} else if (entry.clipAvailable()) {
			editor.selectClip(entry.id());
			changed();
			return;
		}
		toTop();
	}

	private Component wheelTitle(YsmModelCatalog catalog, YsmModelCatalog.WheelEntry entry) {
		if (!entry.configGroup().isEmpty()) {
			String title = catalog.controls().stream().filter(control -> control.group().equals(entry.configGroup()))
					.map(YsmModelCatalog.Control::groupLabel).filter(label -> !label.isBlank()).findFirst().orElse(entry.configGroup());
			return Component.literal(title);
		}
		return Component.literal(entry.label().isBlank() ? entry.id() : entry.label());
	}
	private Component actionTitle(YsmModelCatalog catalog, String clip) {
		if (clip.isEmpty()) return text("default_body");
		return catalog.wheel().stream().filter(entry -> entry.id().equals(clip)).findFirst()
				.map(entry -> wheelTitle(catalog, entry)).orElse(Component.literal(clip));
	}

	private void controls(YsmModelCatalog catalog) {
		var groups = new LinkedHashMap<String, Option>();
		for (var control : catalog.controls()) groups.putIfAbsent(control.group(), new Option(control.group(),
				Component.literal(control.groupLabel().isBlank() ? control.group() : control.groupLabel())));
		if (groups.isEmpty()) { label(text("no_editable_parameters")); return; }
		if (!groups.containsKey(controlGroup)) controlGroup = groups.keySet().iterator().next();
		select("native_group", text("control_group"), controlGroup, List.copyOf(groups.values()), value -> { controlGroup = value; changed(); });
		label(text("native_live_help"));
		var values = editor.previewInputs();
		for (var control : catalog.controls()) if (control.group().equals(controlGroup)) {
			if (!control.editable()) {
				label(text("control_unsupported", control.title()));
				continue;
			}
			Float value = values.get(control.parameter());
			if (value == null) needsInputs = true;
			nativeControl(control, value, number -> editor.parameter(control.parameter(), number));
		}
		if (needsInputs) label(text("control_waiting"));
	}

	private void advanced() {
		label(text("advanced_help"));
		edit("clip", text("clip"), editor.clip(), 128, editor::clip, () -> editor.catalog().animations());
		button(text("preview_play"), editor::previewDraft, true);
		label(text("parameter_count", editor.parameters().size()));
		editOptions("parameter", text("parameter_name"), parameterName, 128, value -> {
			boolean wasEmpty = parameterName.isBlank(), wasIncluded = editor.parameters().containsKey(parameterName);
			parameterName = value;
			if (wasEmpty != value.isBlank() || wasIncluded != editor.parameters().containsKey(value)) changed();
		}, localOptions(this::parameterOptions), option -> {
			parameterName = option.value();
			parameterValue = Float.toString(editor.parameters().getOrDefault(parameterName, 0f));
			changed();
		});
		edit("value", text("parameter_value"), parameterValue, 40, value -> parameterValue = value);
		button(text("add_parameter"), () -> editor.parameter(parameterName, Float.parseFloat(parameterValue)), !parameterName.isBlank());
		button(text("restore_preview_parameter"), () -> editor.removeParameter(parameterName), editor.parameters().containsKey(parameterName));
		button(text("catalog"), openCatalog, true);
	}

	@Override public void tick() {
		super.tick();
		if (editor.profile() != null && (!editor.catalog().equals(shownCatalog) || needsInputs && ++readinessTicks % 10 == 0)) changed();
	}

	private List<Option> presetOptions() {
		List<Option> options = new ArrayList<>();
		editor.profile().presets().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
				.forEach(entry -> options.add(new Option(entry.getKey(), Component.literal(entry.getKey()),
						Component.literal(entry.getValue().description()))));
		return options;
	}

	private List<Option> parameterOptions() {
		var options = new LinkedHashMap<String, Option>();
		for (var control : editor.catalog().controls()) if (!control.parameter().isEmpty())
			options.putIfAbsent(control.parameter(), new Option(control.parameter(),
					Component.literal(control.title().isBlank() ? control.parameter() : control.title()), Component.literal(control.parameter())));
		editor.parameters().forEach((name, value) -> {
			var known = options.get(name);
			options.put(name, new Option(name, known == null ? Component.literal(name) : known.label(), Component.literal(name + " = " + value)));
		});
		return List.copyOf(options.values());
	}
}
