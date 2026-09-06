package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.youkaishomecoming.compat.ysm.*;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/** Persistent drafts for the third editor mode. Only explicit Save/Bind/Apply operations send packets. */
public final class YsmEditorController {
	private YsmModelProfile profile;
	private String savedJson;
	private long revision;
	private String presetId = "", description = "", clip = "", ticks = Integer.toString(YHModel.defaultDuration());
	private final Map<String, Float> parameters = new LinkedHashMap<>();
	private final Map<String, Float> bindingParameters = new LinkedHashMap<>();
	private boolean presetControls;
	private boolean presetDirty;
	private String rawDraft;
	private RawBaseline rawBaseline;
	private record RawBaseline(YsmModelProfile profile, String id, String description, String clip, String ticks,
			Map<String, Float> parameters, YsmModelProfile.Trigger trigger, YsmEditorDocument.Binding binding) { }
	private String modelInput = "", texture = "default", target = "";
	private boolean typeTarget, bindingDirty;
	private long bindingRevision;
	private YsmEditorDocument.Binding bindingBaseline, sentBinding;
	private String pending = "", pendingKind = "";
	private long requestAt;
	private Component status = text("welcome");
	private int viewVersion;
	private PreviewCardHolder preview;
	private boolean paused;
	private YsmModelProfile.Trigger previewState = YsmModelProfile.Trigger.IDLE;
	private YsmModelProfile.Trigger editingTrigger;
	private final Runnable confirmReload;

	public YsmEditorController(Runnable confirmReload) {
		this.confirmReload = confirmReload;
		pickTarget();
		if (profile == null && !modelInput.isEmpty()) loadModel();
		bindingDirty = false;
		rememberBinding();
	}

	public static Component text(String key, Object... args) {
		return Component.translatable("youkaishomecoming.ysm_editor." + key, args);
	}
	public int viewVersion() { return viewVersion; }
	public void refresh() { viewVersion++; }
	public YsmModelProfile profile() { return profile; }
	public String model() { return profile == null ? "" : profile.model(); }
	public String modelInput() { return modelInput; }
	public String texture() { return texture; }
	public String target() { return target; }
	public boolean typeTarget() { return typeTarget; }
	public String presetId() { return presetId; }
	public String description() { return description; }
	public String clip() { return clip; }
	public String ticks() { return ticks; }
	public Map<String, Float> parameters() { return Collections.unmodifiableMap(parameters); }
	public Map<String, Float> bindingParameters() { return Collections.unmodifiableMap(bindingParameters); }
	public boolean presetControls() { return presetControls; }
	public Map<String, Float> controlParameters() { return presetControls ? parameters() : bindingParameters(); }
	public void presetControls(boolean value) {
		if (waiting()) return;
		if (value != presetControls && preview != null)
			preview.setYsmPresentation(preview.getYsmPresentation().clearParameters());
		presetControls = value;
		refresh();
	}
	public Component status() { return status; }
	public long revision() { return revision; }
	public boolean waiting() { return !pending.isEmpty(); }
	public boolean paused() { return paused; }
	public YsmModelProfile.Trigger previewState() { return previewState; }
	public YsmModelProfile.Trigger editingTrigger() { return editingTrigger; }
	public boolean mayWriteWorld() { return Minecraft.getInstance().player != null && Minecraft.getInstance().player.hasPermissions(2); }
	public boolean profileDirty() { return rawDraft != null || presetDirty || profile != null && !profile.toJson().equals(savedJson); }
	public boolean isDirty() { return profileDirty() || bindingDirty; }
	public void modelInput(String value) {
		if (!modelInput.equals(value)) bindingParameters.clear();
		modelInput = value; bindingDirty = true;
	}
	public void texture(String value) { texture = value; bindingDirty = true; }
	public void target(String value) { target = value; bindingDirty = true; }
	public void presetId(String value) { presetId = value; presetDirty = true; }
	public void description(String value) { description = value; presetDirty = true; }
	public void clip(String value) { clip = value; presetDirty = true; }
	public void ticks(String value) { ticks = value; presetDirty = true; }
	public void toggleTargetType() { if (waiting()) return; typeTarget = !typeTarget; target = ""; bindingDirty = true; refresh(); }
	public void requestReload() { if (!waiting()) confirmReload.run(); }

	public void attempt(Runnable operation) {
		try { operation.run(); }
		catch (NumberFormatException ex) { status = text("error_number"); refresh(); }
		catch (IllegalArgumentException | IllegalStateException ex) { status = inputError(ex.getMessage()); refresh(); }
	}

	private Component inputError(String message) {
		String detail = Objects.toString(message, "");
		if (detail.equals("revision_conflict")) return text("conflict");
		if (detail.equals("permission") || detail.contains("operator permission")) return text("error_permission");
		if (detail.contains("UUID") || detail.contains("unloaded entity") || detail.contains("has been removed")) return text("error_target");
		if (detail.contains("entity type")) return text("error_type");
		if (detail.startsWith("Load the imported")) return text("error_import_model");
		if (detail.contains("Load a model") || detail.contains("Invalid model ID")) return text("select_model_first");
		if (detail.startsWith("Preset ID must")) return text("error_preset_id");
		if (detail.startsWith("Unknown preset")) return text("error_preset");
		if (detail.startsWith("Event presets")) return text("error_event_duration");
		if (detail.startsWith("Save the shared profile")) return text("error_save_first");
		if (detail.equals("binding_model_mismatch")) return text("error_binding_model");
		if (detail.equals("Invalid binding texture")) return text("error_binding_texture");
		if (detail.equals("unsaved_model")) return text("dirty_model");
		if (detail.equals("raw_draft_conflict")) return text("error_raw_conflict");
		if (detail.contains("Expected v.name")) return text("error_parameter_name");
		if (detail.contains("animation clip name")) return text("error_clip");
		if (detail.contains("Value outside") || detail.contains("numeric") || detail.contains("maxParameterValue")) return text("error_parameter_value");
		if (detail.contains("duration") || detail.contains("Duration") || detail.contains("Expected integer")) return text("error_duration");
		if (detail.contains("Too many") || detail.contains("too large") || detail.contains("limit")) return text("error_limit");
		if (detail.contains("profile") || detail.contains("Profile")) return text("error_import");
		YoukaisHomecoming.LOGGER.debug("YSM editor input rejected: {}", detail);
		return text("error_input");
	}

	public void selectModel(String model) {
		if (waiting()) return;
		if (profileDirty()) { status = text("dirty_model"); refresh(); return; }
		modelInput(model);
		loadModel();
	}

	/** Explicitly discards this profile's local draft; the Screen confirms first. Bindings are separate. */
	public void loadModel() {
		if (waiting()) return;
		attempt(() -> {
			var entry = YsmClientProfiles.entry(modelInput);
			if (profile != null && !profile.model().equals(modelInput)) bindingParameters.clear();
			profile = entry.profile();
			rawDraft = null; rawBaseline = null;
			savedJson = profile.toJson();
			revision = entry.revision();
			presetDirty = false;
			presetControls = false;
			editingTrigger = null;
			loadPresetFields("");
			status = text("loaded", profile.model());
			resetPreview();
			refresh();
		});
	}

	public void pickTarget() {
		if (waiting()) return;
		if (Minecraft.getInstance().hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living) {
			target = typeTarget ? Objects.toString(ForgeRegistries.ENTITY_TYPES.getKey(living.getType()), "") : living.getUUID().toString();
			loadBinding();
		} else { status = text("no_target"); refresh(); }
	}

	public void loadBinding() {
		if (waiting()) return;
		attempt(() -> {
			YSMCompatConfig.RenderBinding binding;
			String source;
			if (typeTarget) {
				var type = ResourceLocation.tryParse(target);
				if (type == null) throw new IllegalArgumentException("Invalid entity type");
				binding = YSMClientCompat.typeBindings().get(type);
				source = binding == null ? "default" : "type";
				if (binding == null) binding = YSMCompatConfig.defaultBinding(type);
			} else {
				UUID uuid = UUID.fromString(target);
				binding = YSMClientCompat.entityBindings().get(uuid);
				source = "entity";
				var level = Minecraft.getInstance().level;
				if (binding == null && level != null) {
					for (var entity : level.entitiesForRendering()) if (entity.getUUID().equals(uuid) && entity instanceof LivingEntity living) {
						var type = ForgeRegistries.ENTITY_TYPES.getKey(living.getType());
						binding = YSMClientCompat.typeBindings().get(type);
						source = binding == null ? "default" : "type";
						if (binding == null) binding = YSMCompatConfig.defaultBinding(type);
						break;
					}
				}
			}
			if (binding != null && binding.enabled()) {
				if (profileDirty() && !model().equals(binding.modelId())) throw new IllegalArgumentException("unsaved_model");
				modelInput = binding.modelId(); texture = binding.textureName();
				if (!model().equals(modelInput)) loadModel();
			}
			bindingParameters.clear();
			if (binding != null && binding.enabled()) bindingParameters.putAll(binding.parameters());
			status = binding == null ? text("binding_none") : !binding.enabled() ? text("binding_disabled") :
					text("binding_source", text("binding_source." + source), binding.modelId());
			bindingDirty = false;
			bindingRevision = YSMClientCompat.bindingRevision();
			rememberBinding();
			refresh();
		});
	}

	private void rememberBinding() {
		rememberBinding(YSMClientCompat.bindingRevision());
	}

	private void rememberBinding(long acknowledgedRevision) {
		bindingBaseline = currentBinding();
		bindingRevision = acknowledgedRevision;
	}

	private YsmEditorDocument.Binding currentBinding() {
		return new YsmEditorDocument.Binding(typeTarget, target, modelInput, texture, bindingParameters);
	}

	private YsmEditorDocument document() { return new YsmEditorDocument(profile, currentBinding()); }

	public void saveBinding(String operation) {
		if (waiting()) return;
		attempt(() -> {
			if (!mayWriteWorld()) throw new IllegalArgumentException("Requires operator permission (level 2)");
			if (operation.equals("set") && !applyRawDraft()) return;
			if (typeTarget) {
				if (ResourceLocation.tryParse(target) == null) throw new IllegalArgumentException("Invalid entity type");
			} else UUID.fromString(target);
			if (operation.equals("set")) {
				YsmModelProfile.modelId(modelInput);
				if (texture.isBlank()) throw new IllegalArgumentException("Invalid binding texture");
			}
			var request = new YsmOverrideRequestToServer((typeTarget ? "type_" : "entity_") + operation,
					typeTarget ? target : "", modelInput, texture, typeTarget ? "" : target);
			request.expectedRevision = bindingRevision;
			request.parameters = new LinkedHashMap<>(bindingParameters);
			sentBinding = currentBinding();
			request.requestId = beginRequest("binding");
			YoukaisHomecoming.HANDLER.toServer(request);
		});
	}

	public void selectPreset(String id) {
		if (waiting()) return;
		if (presetDirty) { storePreset(); if (presetDirty) return; }
		editingTrigger = null;
		presetControls = true;
		loadPresetFields(id);
	}

	/** Creating a scenario's preset and route is one local edit. */
	public boolean editScenario(YsmModelProfile.Trigger trigger) {
		if (waiting() || profile == null || !applyRawDraft()) return false;
		if (presetDirty) { storePreset(); if (presetDirty) return false; }
		String id = profile.triggers().getOrDefault(trigger, trigger.id());
		loadPresetFields(id);
		editingTrigger = trigger;
		presetControls = true;
		if (!profile.presets().containsKey(id)) description = text("trigger." + trigger.id()).getString();
		presetDirty = !id.equals(profile.triggers().get(trigger));
		refresh();
		return true;
	}

	private void loadPresetFields(String id) {
		editingTrigger = null;
		presetId = id;
		var preset = profile == null ? null : profile.presets().get(id);
		description = preset == null ? "" : preset.description();
		clip = preset == null ? "" : preset.clip();
		ticks = Integer.toString(preset == null ? YHModel.defaultDuration() : preset.ticks());
		parameters.clear();
		if (preset != null) parameters.putAll(preset.parameters());
		presetDirty = false;
		refresh();
	}

	public void discardPresetFields() { if (!waiting()) loadPresetFields(presetId); }
	public boolean stagePresetEdits() { if (presetDirty) storePreset(); return !presetDirty; }

	public YsmModelProfile.Preset currentPreset() {
		return new YsmModelProfile.Preset(description, clip, Integer.parseInt(ticks.trim()), parameters);
	}

	public void storePreset() {
		if (waiting()) return;
		attempt(() -> {
			if (profile == null) throw new IllegalArgumentException("Load a model first");
			String id = YsmModelProfile.presetId(presetId);
			var presets = new LinkedHashMap<>(profile.presets());
			presets.put(id, currentPreset());
			var routes = new LinkedHashMap<>(profile.triggers());
			if (editingTrigger != null) routes.put(editingTrigger, id);
			profile = new YsmModelProfile(profile.model(), presets, routes);
			presetDirty = false;
			status = text("staged");
			refresh();
		});
	}

	public void deletePreset() {
		if (waiting() || profile == null) return;
		var presets = new LinkedHashMap<>(profile.presets());
		presets.remove(presetId);
		var triggers = new LinkedHashMap<>(profile.triggers());
		triggers.values().removeIf(presetId::equals);
		profile = new YsmModelProfile(profile.model(), presets, triggers);
		loadPresetFields("");
		status = text("staged");
	}

	public void route(YsmModelProfile.Trigger trigger, String id) {
		if (waiting() || profile == null) return;
		attempt(() -> {
			var routes = new LinkedHashMap<>(profile.triggers());
			if (id.isEmpty()) routes.remove(trigger); else routes.put(trigger, id);
			profile = new YsmModelProfile(profile.model(), profile.presets(), routes);
			status = text("staged");
			refresh();
		});
	}

	public void parameter(String name, float value) {
		if (waiting()) return;
		attempt(() -> {
			String key = YsmPresentationState.normalizeParameter(name);
			if (!Float.isFinite(value) || Math.abs(value) > YsmPresentationState.WIRE_MAX_PARAMETER_VALUE || !catalog().accepts(key, value))
				throw new IllegalArgumentException("Value outside the model's declared options or numeric limit");
			if (!parameters.containsKey(key) && parameters.size() >= YsmPresentationState.WIRE_MAX_PARAMETERS) throw new IllegalArgumentException("Too many parameters");
			int configuredDuration = Integer.parseInt(ticks.trim());
			if (configuredDuration < 0) throw new IllegalArgumentException("Duration must not be negative");
			ensurePresetId();
			parameters.put(key, value);
			presetDirty = true;
			var holder = preview();
			if (holder != null) holder.setYsmPresentation(holder.getYsmPresentation().setParameter(key, value,
					holder.getYsmPresentationTime(), 0, YsmPresentationState.Source.PREVIEW, YsmPresentationState.WIRE_MAX_PARAMETERS));
			paused = false;
			status = text("preset_parameter_staged");
			refresh();
		});
	}

	/** Native controls edit persistent binding appearance unless a preset was explicitly selected. */
	public void controlParameter(String name, float value) {
		if (presetControls) { parameter(name, value); return; }
		if (waiting()) return;
		attempt(() -> {
			String key = YsmPresentationState.normalizeParameter(name);
			if (!Float.isFinite(value) || Math.abs(value) > YsmPresentationState.WIRE_MAX_PARAMETER_VALUE || !catalog().accepts(key, value))
				throw new IllegalArgumentException("Value outside the model's declared options or numeric limit");
			if (!bindingParameters.containsKey(key) && bindingParameters.size() >= YsmPresentationState.WIRE_MAX_PARAMETERS)
				throw new IllegalArgumentException("Too many parameters");
			bindingParameters.put(key, value);
			bindingDirty = true;
			if (preview != null) preview.setYsmPresentation(preview.getYsmPresentation().clearParameter(key));
			paused = false;
			status = text("binding_parameter_staged");
			refresh();
		});
	}

	public void removeControlParameter(String name) {
		if (presetControls) { removeParameter(name); return; }
		if (!waiting() && bindingParameters.remove(name) != null) {
			bindingDirty = true;
			if (preview != null) preview.setYsmPresentation(preview.getYsmPresentation().clearParameter(name));
			status = text("binding_parameter_staged");
			refresh();
		}
	}

	private void ensurePresetId() {
		if (!presetId.isBlank()) return;
		String id = "custom";
		for (int suffix = 2; profile != null && profile.presets().containsKey(id); suffix++) id = "custom_" + suffix;
		presetId = id;
	}
	public void removeParameter(String name) {
		if (!waiting() && parameters.containsKey(name)) {
			parameters.remove(name); presetDirty = true;
			if (preview != null) preview.setYsmPresentation(preview.getYsmPresentation().clearParameter(name));
			refresh();
		}
	}
	public void selectClip(String name) { if (!waiting()) { ensurePresetId(); presetControls = true; clip(name); refresh(); previewDraft(); } }
	public YsmModelCatalog catalog() { return YsmClientPresentationBridge.catalog(model()); }

	public void saveProfile() { saveProfile(false); }
	public void saveAndBind() { saveProfile(true); }
	private void saveProfile(boolean bindAfterSave) {
		if (waiting()) return;
		attempt(() -> {
			if (!mayWriteWorld()) throw new IllegalArgumentException("Requires operator permission (level 2)");
			if (!applyRawDraft()) return;
			if (presetDirty) { storePreset(); if (presetDirty) return; }
			if (profile == null) throw new IllegalArgumentException("Load a model first");
			if (bindAfterSave) {
				if (!model().equals(modelInput)) throw new IllegalArgumentException("binding_model_mismatch");
				if (typeTarget) {
					var type = ResourceLocation.tryParse(target);
					if (type == null || !ForgeRegistries.ENTITY_TYPES.containsKey(type)) throw new IllegalArgumentException("Invalid entity type");
				} else UUID.fromString(target);
			}
			var request = request("save");
			request.json = profile.toJson();
			request.expectedRevision = revision;
			request.requestId = beginRequest(bindAfterSave ? "profile_then_binding" : "profile");
			YoukaisHomecoming.HANDLER.toServer(request);
		});
	}

	public void applyToEntity(boolean clear) {
		if (waiting()) return;
		attempt(() -> {
			if (typeTarget) throw new IllegalArgumentException("Select one entity UUID to apply a preset");
			if (!clear && profileDirty()) throw new IllegalArgumentException("Save the shared profile before applying it to a real entity");
			UUID.fromString(target);
			var request = request(clear ? "clear" : "apply");
			request.target = target;
			request.preset = presetId;
			request.requestId = beginRequest(clear ? "clear" : "apply");
			YoukaisHomecoming.HANDLER.toServer(request);
		});
	}

	private YsmProfileRequestToServer request(String action) {
		if (profile == null) throw new IllegalArgumentException("Load a model first");
		var request = new YsmProfileRequestToServer();
		request.action = action;
		request.model = model();
		return request;
	}
	private String beginRequest(String kind) {
		pending = UUID.randomUUID().toString();
		pendingKind = kind;
		requestAt = System.nanoTime();
		status = text("pending");
		refresh();
		return pending;
	}

	public void exportClipboard() {
		attempt(() -> {
			if (rawDraft != null) {
				Minecraft.getInstance().keyboardHandler.setClipboard(rawDraft);
				status = text("raw_copied"); refresh(); return;
			}
			if (presetDirty) { storePreset(); if (presetDirty) return; }
			if (profile == null) return;
			Minecraft.getInstance().keyboardHandler.setClipboard(document().toJson());
			status = text("copied");
			refresh();
		});
	}
	public void importClipboard() {
		if (waiting()) return;
		attempt(() -> {
			var imported = YsmEditorDocument.fromJson(Minecraft.getInstance().keyboardHandler.getClipboard());
			if (profile == null || !imported.profile().model().equals(model())) throw new IllegalArgumentException("Load the imported profile's model first");
			adoptDocument(imported);
			rawDraft = null; rawBaseline = null;
			loadPresetFields(profile.presets().keySet().stream().findFirst().orElse(""));
			status = text("staged");
			resetPreview();
		});
	}

	/** Raw text includes the optional binding; the shared profile API still receives only format=1. */
	public boolean prepareRawDraft() {
		if (rawDraft == null && presetDirty) storePreset();
		return profile != null && (!presetDirty || rawDraft != null);
	}
	public String rawJson() { return rawDraft != null ? rawDraft : profile == null ? "" : document().toJson(); }
	public boolean rawDirty() { return rawDraft != null; }
	public boolean rawEditable() { return profile != null && (!presetDirty || rawDraft != null); }
	private RawBaseline rawBaseline() { return new RawBaseline(profile, presetId, description, clip, ticks, Map.copyOf(parameters), editingTrigger, currentBinding()); }
	public void rawJson(String value) {
		if (waiting() || profile == null) return;
		boolean wasDirty = rawDraft != null;
		if (!wasDirty) rawBaseline = rawBaseline();
		rawDraft = value;
		if (!presetDirty && value.equals(document().toJson())) { rawDraft = null; rawBaseline = null; }
		if (wasDirty != (rawDraft != null)) refresh();
	}
	public boolean applyRawDraft() {
		if (rawDraft == null) return true;
		if (waiting()) return false;
		attempt(() -> {
			if (!Objects.equals(rawBaseline, rawBaseline())) throw new IllegalArgumentException("raw_draft_conflict");
			var parsed = YsmEditorDocument.fromJson(rawDraft);
			if (!parsed.profile().model().equals(model())) throw new IllegalArgumentException("Load the imported profile's model first");
			adoptDocument(parsed);
			rawDraft = null; rawBaseline = null;
			loadPresetFields(profile.presets().containsKey(presetId) ? presetId : profile.presets().keySet().stream().findFirst().orElse(""));
			status = text("staged");
			resetPreview();
		});
		return rawDraft == null;
	}

	private void adoptDocument(YsmEditorDocument document) {
		profile = document.profile();
		if (document.binding() != null) {
			var binding = document.binding();
			typeTarget = binding.typeTarget(); target = binding.target(); modelInput = binding.model(); texture = binding.texture();
			bindingParameters.clear(); bindingParameters.putAll(binding.parameters());
			bindingDirty = !binding.equals(bindingBaseline);
		}
	}
	public void discardRawDraft() { if (!waiting()) { rawDraft = null; rawBaseline = null; refresh(); } }

	public PreviewCardHolder preview() {
		if (preview == null && Minecraft.getInstance().level != null) {
			preview = new PreviewCardHolder(Minecraft.getInstance().level);
			preview.setYsmProfiles(model -> profile != null && profile.model().equals(model) ? profile : YsmClientProfiles.entry(model).profile());
			preview.getFakeCaster().setInvisible(false);
			preview.getFakeCaster().setPos(0, 0, 0);
		}
		if (preview != null && profile != null) {
			preview.setYsmRenderOverride(model(), texture, "", 0, "changed");
			YsmClientProfiles.preview(preview.getFakeCaster(), profile, model().equals(modelInput) ? bindingParameters : Map.of());
		}
		return preview;
	}
	public void previewDraft() {
		attempt(() -> {
			var holder = preview();
			if (holder == null || profile == null) return;
			var preset = currentPreset();
			holder.setYsmPresentation(holder.getYsmPresentation().stop().clearParameters().applyPreset(model(), preset,
					holder.getYsmPresentationTime(), 0, YsmPresentationState.Source.PREVIEW, YsmPresentationState.WIRE_MAX_PARAMETERS));
			paused = false;
			status = text("preview_only");
			refresh();
		});
	}
	public void simulate(YsmModelProfile.Trigger trigger) {
		if (!applyRawDraft() || !stagePresetEdits()) return;
		var holder = preview();
		if (holder == null) return;
		// Starting a simulated trigger drops the explicit audition so lower-priority mappings are visible.
		holder.setYsmPresentation(holder.getYsmPresentation().stop().clearParameters());
		long now = holder.getYsmPresentationTime();
		var signal = holder.getYsmSignals();
		if (trigger.event() && previewState.beaten()) { previewState = YsmModelProfile.Trigger.IDLE; signal = signal.advance(previewState, false, now); }
		if (trigger == YsmModelProfile.Trigger.ENTER_COMBAT)
			signal = signal.advance(previewState, false, now).withCombatMode(YsmPresentationSignals.CombatMode.STG, now).advance(previewState, true, now);
		else if (trigger.event()) {
			if (trigger == YsmModelProfile.Trigger.SPELL_SWITCH)
				signal = signal.withCombatMode(YsmPresentationSignals.CombatMode.STG, now).advance(previewState, true, now);
			else if (trigger == YsmModelProfile.Trigger.BOSS_VICTORY)
				signal = signal.withCombatMode(YsmPresentationSignals.CombatMode.NONE, now).advance(previewState, false, now);
			signal = signal.fire(trigger, now);
		}
		else if (trigger.combatCondition()) {
			signal = signal.withCombatMode(trigger == YsmModelProfile.Trigger.STG_COMBAT
					? YsmPresentationSignals.CombatMode.STG : YsmPresentationSignals.CombatMode.NORMAL, now);
		}
		else {
			previewState = trigger;
			signal = signal.withCombatMode(YsmPresentationSignals.CombatMode.NONE, now).advance(trigger, false, now);
		}
		holder.setYsmSignals(signal);
		paused = false;
		status = text("preview_only");
		refresh();
	}
	public void stopPreview() {
		if (preview != null) { preview.setYsmPresentation(preview.getYsmPresentation().stop().clearParameters()); preview.setYsmSignals(YsmPresentationSignals.EMPTY); }
		previewState = YsmModelProfile.Trigger.IDLE;
		paused = false;
		refresh();
	}
	public void togglePause() { paused = !paused; refresh(); }
	public void resetPreview() { closePreview(); previewState = YsmModelProfile.Trigger.IDLE; paused = false; refresh(); }
	public void closePreview() {
		if (preview != null) {
			YsmClientPresentationBridge.forgetPreview(preview.getFakeCaster());
			YsmClientProfiles.forgetPreview(preview.getFakeCaster());
			preview.getFakeCaster().discard();
			preview = null;
		}
	}

	public void discardAll() {
		rawDraft = null; rawBaseline = null;
		if (profile != null && savedJson != null) { profile = YsmModelProfile.fromJson(savedJson); loadPresetFields(""); }
		if (bindingBaseline != null) {
			typeTarget = bindingBaseline.typeTarget(); target = bindingBaseline.target(); modelInput = bindingBaseline.model(); texture = bindingBaseline.texture();
			bindingParameters.clear(); bindingParameters.putAll(bindingBaseline.parameters());
		}
		bindingDirty = false;
		resetPreview();
	}

	public void tick() {
		if (waiting()) {
			var response = YsmClientProfiles.takeResponse(pending);
			if (response != null) {
				if (response.success() && (pendingKind.equals("profile") || pendingKind.equals("profile_then_binding"))) {
					// The cache may already contain another author's newer broadcast. Only adopt OUR acknowledgement.
					revision = response.revision();
					savedJson = response.json();
				}
				if (response.success() && (pendingKind.equals("binding") || pendingKind.equals("binding_after_profile"))) {
					bindingBaseline = sentBinding;
					bindingRevision = response.revision();
					bindingDirty = !Objects.equals(currentBinding(), sentBinding);
				}
				boolean bindNext = response.success() && pendingKind.equals("profile_then_binding");
				status = response.success() ? text(switch (pendingKind) {
					case "profile", "profile_then_binding" -> "profile_saved";
					case "binding" -> "binding_saved";
					case "binding_after_profile" -> "saved_and_bound";
					case "clear" -> "entity_cleared";
					default -> "entity_applied";
				}) : pendingKind.equals("binding_after_profile") ? text("profile_saved_binding_failed", inputError(response.message())) : inputError(response.message());
				pending = "";
				if (bindNext) {
					saveBinding("set");
					if (waiting()) { pendingKind = "binding_after_profile"; status = text("saving_binding"); }
					else status = text("profile_saved_binding_failed", status);
				}
				refresh();
			} else if (System.nanoTime() - requestAt > 15_000_000_000L) {
				pending = "";
				status = pendingKind.equals("binding_after_profile") ? text("profile_saved_binding_failed", text("timeout")) : text("timeout");
				refresh();
			}
		}
		var holder = preview();
		if (holder != null && !paused) holder.tickModelPreview();
	}
}
