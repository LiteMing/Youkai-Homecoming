package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import dev.xkmc.youkaishomecoming.compat.ysm.YsmModelProfile;
import dev.xkmc.youkaishomecoming.content.spell.preview.YsmEditorController;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static dev.xkmc.youkaishomecoming.content.spell.preview.YsmEditorController.text;

public final class YsmPresetsDockPanel extends YsmEditorPanel {

	private final Runnable openScenarios;
	private String parameterName = "", parameterValue = "0";

	public YsmPresetsDockPanel(YsmEditorController editor, Runnable openScenarios) {
		super(editor);
		this.openScenarios = openScenarios;
	}

	@Override public String dockId() { return "ysm_presets"; }
	@Override public String dockTitle() { return text("presets").getString(); }

	@Override
	protected void build() {
		if (editor.profile() == null) {
			label(text("select_model_first"));
			return;
		}
		label(text("scene_model", editor.model()));
		if (editor.editingTrigger() != null) label(text("editing_scene", text("trigger." + editor.editingTrigger().id())));
		var presets = new ArrayList<>(presetOptions());
		presets.add(0, new Option("", text("new_preset")));
		select("saved_preset", text("pick_preset"), editor.presetId(), presets, editor::selectPreset);
		edit("id", text("preset_id"), editor.presetId(), 128, editor::presetId);
		edit("description", text("description"), editor.description(), 256, editor::description);
		edit("clip", text("clip"), editor.clip(), 128, editor::clip, () -> editor.catalog().animations());
		edit("ticks", text("ticks"), editor.ticks(), 10, editor::ticks);
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
		button(text("remove_parameter"), () -> editor.removeParameter(parameterName), editor.parameters().containsKey(parameterName));
		button(text("store_preset"), editor::storePreset, true);
		button(text("back_to_scenarios"), () -> {
			if (editor.stagePresetEdits()) openScenarios.run();
		}, true);
		button(text("discard_fields"), editor::discardPresetFields, true);
		button(text("delete_preset"), editor::deletePreset, editor.profile().presets().containsKey(editor.presetId()));
		button(text("save_profile"), editor::saveProfile, editor.mayWriteWorld());
		button(text("apply_entity"), () -> editor.applyToEntity(false),
				editor.mayWriteWorld() && !editor.typeTarget() && !editor.presetId().isEmpty());
		button(text("clear_entity"), () -> editor.applyToEntity(true), editor.mayWriteWorld() && !editor.typeTarget());
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
					Component.literal(control.title().isBlank() ? control.parameter() : control.title()),
					Component.literal(control.parameter())));
		editor.parameters().forEach((name, value) -> {
			var known = options.get(name);
			options.put(name, new Option(name, known == null ? Component.literal(name) : known.label(),
					Component.literal(name + " = " + value)));
		});
		return List.copyOf(options.values());
	}
}
