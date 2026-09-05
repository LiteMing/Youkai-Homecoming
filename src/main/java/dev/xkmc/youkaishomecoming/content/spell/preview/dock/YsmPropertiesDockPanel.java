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
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

import static dev.xkmc.youkaishomecoming.content.spell.preview.YsmEditorController.text;

public final class YsmPropertiesDockPanel extends YsmEditorPanel {
	private final Runnable openPresets;
	private int page;
	private YsmModelProfile.Trigger trigger = YsmModelProfile.Trigger.IDLE;
	private String scenario = "model";
	public YsmPropertiesDockPanel(YsmEditorController editor, Runnable openPresets) {
		super(editor);
		this.openPresets = openPresets;
	}
	@Override public String dockId() { return "ysm_properties"; }
	@Override public String dockTitle() { return text("properties").getString(); }

	@Override protected void build() {
		select("properties_page", text("workspace"), Integer.toString(page), List.of(
				new Option("0", text("scenarios")), new Option("1", text("bindings")),
				new Option("2", text("triggers"))),
				value -> { page = Integer.parseInt(value); toTop(); });
		if (page == 0) scenarios(); else if (page == 1) bindings(); else triggers();
	}

	public void showScenarios() { page = 0; toTop(); }

	private void targetAndModel() {
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
	}

	private void scenarios() {
		var options = new ArrayList<Option>();
		options.add(new Option("model", text("scenario.model")));
		for (var event : List.of(YsmModelProfile.Trigger.ENTER_COMBAT, YsmModelProfile.Trigger.SPELL_SWITCH, YsmModelProfile.Trigger.MELEE_ATTACK))
			options.add(new Option(event.id(), text("trigger." + event.id())));
		options.add(new Option("preset", text("scenario.preset")));
		for (var event : List.of(YsmModelProfile.Trigger.IDLE, YsmModelProfile.Trigger.WALK, YsmModelProfile.Trigger.FLY,
				YsmModelProfile.Trigger.HURT, YsmModelProfile.Trigger.DEFEAT, YsmModelProfile.Trigger.FALLING, YsmModelProfile.Trigger.PRONE))
			options.add(new Option(event.id(), text("trigger." + event.id())));
		select("scenario", text("scenario_choice"), scenario, options, value -> {
			scenario = value;
			if (value.equals("model")) editor.stopPreview();
			else if (value.equals("preset")) editor.previewDraft();
			else editor.simulate(YsmModelProfile.Trigger.parse(value));
			toTop();
		});
		targetAndModel();
		if (scenario.equals("model")) {
			edit("texture", text("texture"), editor.texture(), 256, editor::texture, () -> YSMClientCompat.loadedTextureNames(editor.modelInput()));
			button(text("bind_save"), () -> editor.saveBinding("set"), editor.mayWriteWorld() && !editor.modelInput().isBlank());
			return;
		}
		if (editor.profile() == null) { label(text("select_model_first")); return; }
		label(text("scene_model", editor.model()));
		if (scenario.equals("preset")) {
			select("use_preset", text("trigger_preset"), editor.presetId(), presetOptions(false), editor::selectPreset);
			button(text("preview_play"), editor::previewDraft, !editor.presetId().isEmpty());
			button(text("apply_entity"), () -> editor.applyToEntity(false), editor.mayWriteWorld() && !editor.typeTarget() && !editor.presetId().isEmpty());
			button(text("clear_entity"), () -> editor.applyToEntity(true), editor.mayWriteWorld() && !editor.typeTarget());
			button(text("edit_selected_preset"), openPresets, true);
			return;
		}
		var event = YsmModelProfile.Trigger.parse(scenario);
		select("scene_preset", text("trigger_preset"), editor.profile().triggers().getOrDefault(event, ""),
				presetOptions(true), id -> editor.route(event, id));
		button(text("configure_scene"), () -> { if (editor.editScenario(event)) openPresets.run(); }, true);
		button(text("simulate", text("trigger." + event.id())), () -> editor.simulate(event), true);
		button(text("save_and_bind"), editor::saveAndBind, editor.mayWriteWorld());
		label(text(event.event() ? "trigger_event_duration" : "trigger_state_duration"));
	}

	private void bindings() {
		targetAndModel();
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
	private void triggers() {
		if (editor.profile() == null) { label(text("select_model_first")); return; }
		select("trigger", text("trigger_choice"), trigger.id(), java.util.Arrays.stream(YsmModelProfile.Trigger.values())
				.map(value -> new Option(value.id(), text("trigger." + value.id()))).toList(),
				value -> { trigger = YsmModelProfile.Trigger.parse(value); editor.simulate(trigger); changed(); });
		String selected = editor.profile().triggers().getOrDefault(trigger, "");
		select("route", text("trigger_preset"), selected, presetOptions(true), value -> editor.route(trigger, value));
		label(text(trigger.event() ? "trigger_event_duration" : "trigger_state_duration"));
		button(text("simulate", text("trigger." + trigger.id())), () -> editor.simulate(trigger), true);
		button(text("save_profile"), editor::saveProfile, editor.mayWriteWorld());
	}
}
