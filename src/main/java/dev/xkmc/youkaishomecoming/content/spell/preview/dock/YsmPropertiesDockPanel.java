package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.xkmc.youkaishomecoming.compat.ysm.*;
import dev.xkmc.youkaishomecoming.content.spell.preview.YsmEditorController;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

import static dev.xkmc.youkaishomecoming.content.spell.preview.YsmEditorController.text;

public final class YsmPropertiesDockPanel extends YsmEditorPanel {
	private int page;
	private String parameterName = "", parameterValue = "0";
	private YsmModelProfile.Trigger trigger = YsmModelProfile.Trigger.IDLE;
	public YsmPropertiesDockPanel(YsmEditorController editor) { super(editor); }
	@Override public String dockId() { return "ysm_properties"; }
	@Override public String dockTitle() { return text("properties").getString(); }

	@Override protected void build() {
		select("properties_page", text("workspace"), Integer.toString(page), List.of(
				new Option("0", text("bindings")), new Option("1", text("presets")), new Option("2", text("triggers"))),
				value -> { page = Integer.parseInt(value); toTop(); });
		if (page == 0) bindings(); else if (page == 1) presets(); else triggers();
	}

	private void bindings() {
		select("target_scope", text("target_scope"), editor.typeTarget() ? "type" : "uuid", List.of(
				new Option("uuid", text("target_uuid")), new Option("type", text("target_type"))),
				value -> { if (editor.typeTarget() != value.equals("type")) editor.toggleTargetType(); });
		editOptions("target", text(editor.typeTarget() ? "target_type_id" : "target_uuid_id"), editor.target(), 256, editor::target,
				editor.typeTarget() ? this::entityTypes : localOptions(this::entityUuids), null);
		button(text("pick_target"), editor::pickTarget, true);
		button(text("read_binding"), editor::loadBinding, true);
		edit("model", text("model"), editor.modelInput(), 256, editor::modelInput, () -> {
			var models = new TreeSet<>(YSMClientCompat.loadedModelIds());
			models.addAll(YsmClientProfiles.models());
			return List.copyOf(models);
		});
		button(text("load_model"), () -> editor.selectModel(editor.modelInput()), true);
		edit("texture", text("texture"), editor.texture(), 256, editor::texture, () -> YSMClientCompat.loadedTextureNames(editor.modelInput()));
		button(text("default_texture"), () -> { editor.texture(YSMClientCompat.defaultTextureName(editor.modelInput())); changed(); }, true);
		button(text("bind_save"), () -> editor.saveBinding("set"), editor.mayWriteWorld());
		button(text("bind_off"), () -> editor.saveBinding("off"), editor.mayWriteWorld());
		button(text("bind_unset"), () -> editor.saveBinding("unset"), editor.mayWriteWorld());
		label(text("binding_priority"));
	}

	/** Same vanilla Brigadier dispatcher/source as run_command's CommandSuggestions; never executes summon. */
	private CompletableFuture<List<Option>> entityTypes(String input, int caret) {
		var connection = Minecraft.getInstance().getConnection();
		if (connection != null && connection.getCommands().getRoot().getChild("summon") != null) {
			String prefix = "summon ", command = prefix + input;
			var dispatcher = connection.getCommands();
			return dispatcher.getCompletionSuggestions(dispatcher.parse(command, connection.getSuggestionsProvider()),
					prefix.length() + Math.min(caret, input.length())).thenApply(suggestions -> suggestions.getList().stream()
					.map(suggestion -> suggestion.apply(command).substring(prefix.length()).trim())
					.filter(value -> ResourceLocation.tryParse(value) != null).distinct().map(this::typeOption).toList());
		}
		// Read-only users may not receive the summon node. Use vanilla registry-ID matching, not a YH-only list.
		return SharedSuggestionProvider.suggestResource(ForgeRegistries.ENTITY_TYPES.getKeys(), new SuggestionsBuilder(input, 0))
				.thenApply(suggestions -> suggestions.getList().stream().map(suggestion -> typeOption(suggestion.getText())).toList());
	}
	private Option typeOption(String id) {
		var type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(id));
		return new Option(id, Component.literal(id), type == null ? Component.empty() : Component.translatable(type.getDescriptionId()));
	}
	private List<Option> entityUuids() {
		var mc = Minecraft.getInstance();
		if (mc.level == null) return List.of();
		var entities = new ArrayList<net.minecraft.world.entity.Entity>();
		for (var entity : mc.level.entitiesForRendering()) if (entity instanceof YsmRenderOverrideTarget) entities.add(entity);
		if (mc.player != null) entities.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(mc.player)));
		return entities.stream().map(entity -> {
			String type = String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()));
			String distance = mc.player == null ? "?" : String.format(Locale.ROOT, "%.1f", entity.distanceTo(mc.player));
			return new Option(entity.getUUID().toString(), text("entity_candidate", type, distance),
					Component.literal(entity.getUUID().toString()));
		}).toList();
	}
	private List<Option> presetOptions(boolean allowNone) {
		List<Option> options = new ArrayList<>();
		if (allowNone) options.add(new Option("", text("no_preset")));
		if (editor.profile() != null) editor.profile().presets().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
				.forEach(entry -> options.add(new Option(entry.getKey(), Component.literal(entry.getKey()),
						Component.literal(entry.getValue().description()))));
		return options;
	}
	private void presets() {
		if (editor.profile() == null) { label(text("select_model_first")); return; }
		var presets = new ArrayList<>(presetOptions(false));
		presets.add(0, new Option("", text("new_preset")));
		select("saved_preset", text("pick_preset"), editor.presetId(), presets, editor::selectPreset);
		edit("id", text("preset_id"), editor.presetId(), 128, editor::presetId);
		edit("description", text("description"), editor.description(), 256, editor::description);
		edit("clip", text("clip"), editor.clip(), 128, editor::clip, () -> editor.catalog().animations());
		edit("ticks", text("ticks"), editor.ticks(), 10, editor::ticks);
		label(text("parameter_count", editor.parameters().size()));
		editOptions("parameter", text("parameter_name"), parameterName, 128, value -> {
			boolean wasEmpty = parameterName.isBlank(), wasIncluded = editor.parameters().containsKey(parameterName);
			parameterName = value;
			if (wasEmpty != value.isBlank() || wasIncluded != editor.parameters().containsKey(value)) changed();
		},
				localOptions(this::parameterOptions), option -> {
					parameterName = option.value();
					parameterValue = Float.toString(editor.parameters().getOrDefault(parameterName, 0f));
					changed();
				});
		edit("value", text("parameter_value"), parameterValue, 40, value -> parameterValue = value);
		button(text("add_parameter"), () -> editor.parameter(parameterName, Float.parseFloat(parameterValue)), !parameterName.isBlank());
		button(text("remove_parameter"), () -> editor.removeParameter(parameterName), editor.parameters().containsKey(parameterName));
		button(text("store_preset"), editor::storePreset, true);
		button(text("discard_fields"), editor::discardPresetFields, true);
		button(text("delete_preset"), editor::deletePreset, editor.profile().presets().containsKey(editor.presetId()));
		button(text("save_profile"), editor::saveProfile, editor.mayWriteWorld());
		button(text("apply_entity"), () -> editor.applyToEntity(false), editor.mayWriteWorld() && !editor.typeTarget());
		button(text("clear_entity"), () -> editor.applyToEntity(true), editor.mayWriteWorld() && !editor.typeTarget());
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
	private void triggers() {
		if (editor.profile() == null) { label(text("select_model_first")); return; }
		select("trigger", text("trigger_choice"), trigger.id(), java.util.Arrays.stream(YsmModelProfile.Trigger.values())
				.map(value -> new Option(value.id(), text("trigger." + value.id()))).toList(),
				value -> { trigger = YsmModelProfile.Trigger.parse(value); changed(); });
		String selected = editor.profile().triggers().getOrDefault(trigger, "");
		select("route", text("trigger_preset"), selected, presetOptions(true), value -> editor.route(trigger, value));
		label(text(trigger.event() ? "trigger_event_duration" : "trigger_state_duration"));
		button(text("simulate", text("trigger." + trigger.id())), () -> editor.simulate(trigger), true);
		button(text("save_profile"), editor::saveProfile, editor.mayWriteWorld());
	}
}
