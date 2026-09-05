package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import dev.xkmc.youkaishomecoming.compat.ysm.*;
import dev.xkmc.youkaishomecoming.content.spell.preview.YsmEditorController;
import dev.xkmc.youkaishomecoming.content.spell.preview.PreviewCardHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import static dev.xkmc.youkaishomecoming.content.spell.preview.YsmEditorController.text;

public final class YsmCatalogDockPanel extends YsmEditorPanel {
	private int page;
	private String modelQuery = "", animationQuery = "", wheelQuery = "", controlQuery = "";
	private String modelFolder = "", animationFolder = "";
	private String wheelGroup = "", selectedWheel = "", controlGroup = "", selectedControl = "", previousModel = "";
	private YsmModelCatalog previous;
	private List<String> previousModels = List.of();
	private final Map<String, String> rangeInputs = new LinkedHashMap<>();
	private PreviewCardHolder candidatePreview;
	private String candidateModel = "";
	public YsmCatalogDockPanel(YsmEditorController editor) { super(editor); }
	@Override public String dockId() { return "ysm_catalog"; }
	@Override public String dockTitle() { return text("catalog").getString(); }
	@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		var catalog = editor.catalog();
		if (page == 0) {
			var ids = new TreeSet<>(YSMClientCompat.loadedModelIds());
			ids.addAll(YsmClientProfiles.models());
			var next = List.copyOf(ids);
			if (!previousModels.equals(next)) { previousModels = next; changed(); }
		}
		if (!editor.model().equals(previousModel)) {
			previousModel = editor.model();
			modelQuery = editor.model();
			animationQuery = wheelQuery = controlQuery = wheelGroup = selectedWheel = controlGroup = selectedControl = "";
			animationFolder = "";
			rangeInputs.clear();
			toTop();
		}
		if (!catalog.equals(previous)) { previous = catalog; changed(); }
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override protected void build() {
		select("catalog_page", text("catalog_section"), Integer.toString(page), List.of(
				new Option("0", text("models")), new Option("1", text("animations")),
				new Option("2", text("wheel")), new Option("3", text("controls"))),
				value -> { page = Integer.parseInt(value); toTop(); });
		if (page == 0) { models(); return; }
		var catalog = editor.catalog();
		if (catalog.status() != YsmModelCatalog.Status.READY) {
			label(Component.translatable("commands.youkaishomecoming.model.catalog." + catalog.status().name().toLowerCase(Locale.ROOT)));
			return;
		}
		if (page == 1) animations(catalog); else if (page == 2) wheel(catalog); else controls(catalog);
	}
	private void models() {
		var models = new TreeSet<>(YSMClientCompat.loadedModelIds());
		models.addAll(YsmClientProfiles.models());
		folders("model_folder", modelFolder, models.stream().map(YsmCatalogDockPanel::parent).toList(),
				YsmClientPresentationBridge.modelFolderNames(), folder -> { modelFolder = folder; modelQuery = ""; changed(); });
		editOptions("model_picker", text("model"), modelQuery, 256, value -> modelQuery = value,
				localOptions(() -> models.stream().filter(id -> inside(id, modelFolder))
						.map(id -> new Option(id, Component.literal(leaf(id)), Component.literal(id))).toList()),
				option -> { editor.selectModel(option.value()); modelQuery = editor.model(); changed(); });
		if (!YSMClientCompat.isLoaded()) label(Component.translatable("commands.youkaishomecoming.model.catalog.not_installed"));
	}
	private void animations(YsmModelCatalog catalog) {
		folders("animation_folder", animationFolder, catalog.animations().stream().map(YsmCatalogDockPanel::animationGroup).toList(),
				Map.of(), folder -> { animationFolder = folder; animationQuery = ""; changed(); });
		editOptions("animation_picker", text("animation_picker"), animationQuery, 128, value -> animationQuery = value,
				localOptions(() -> catalog.animations().stream()
						.filter(clip -> animationFolder.isEmpty() || animationGroup(clip).equals(animationFolder) || inside(animationGroup(clip), animationFolder))
						.map(clip -> {
					String title = catalog.wheel().stream().filter(entry -> entry.id().equals(clip) && !entry.label().isBlank())
							.map(YsmModelCatalog.WheelEntry::label).findFirst().orElse(clip);
					return new Option(clip, Component.literal(title), title.equals(clip) ? Component.empty() : Component.literal(clip));
				}).toList()), option -> { animationQuery = option.label().getString(); editor.selectClip(option.value()); });
		label(text("clip_help"));
	}

	private static String parent(String path) { int slash = path.lastIndexOf('/'); return slash < 0 ? "" : path.substring(0, slash); }
	private static String leaf(String path) { return path.substring(path.lastIndexOf('/') + 1); }
	private static boolean inside(String path, String folder) { return folder.isEmpty() || path.startsWith(folder + "/"); }
	private static String animationGroup(String clip) {
		String path = clip.replace('.', '/').replace('_', '/');
		if (path.contains("/")) return parent(path);
		// OYSM's extra1/extra2/... are a naming family, not separate guessed JSON files.
		return clip.replaceFirst("[0-9]+$", "").equals(clip) ? "" : clip.replaceFirst("[0-9]+$", "");
	}
	private void folders(String id, String current, List<String> paths, Map<String, String> labels,
			java.util.function.Consumer<String> selected) {
		var options = new LinkedHashMap<String, Option>();
		options.put(current, new Option(current, current.isEmpty() ? text("folder_all") : Component.literal(current)));
		if (!current.isEmpty()) {
			options.put("", new Option("", text("folder_all")));
			if (!parent(current).isEmpty()) options.put(parent(current), new Option(parent(current), text("folder_parent"), Component.literal(parent(current))));
		}
		var children = new TreeSet<String>();
		for (String path : paths) if (!path.isEmpty() && inside(path, current) && !path.equals(current)) {
			String relative = current.isEmpty() ? path : path.substring(current.length() + 1);
			String next = relative.split("/", 2)[0];
			if (!next.isEmpty()) children.add(current.isEmpty() ? next : current + "/" + next);
		}
		for (String child : children) options.put(child, new Option(child,
				text("folder_open", labels.getOrDefault(child, leaf(child))), Component.literal(child)));
		select(id, text("folder"), current, List.copyOf(options.values()), selected);
	}

	@Override protected void renderOptionPreview(GuiGraphics graphics, String anchor, Option option, int[] bounds) {
		if (!anchor.equals("model_picker") || !YSMClientCompat.isLoaded()) return;
		var mc = Minecraft.getInstance();
		if (mc.level == null || mc.screen == null) return;
		int width = 140, height = Math.min(190, mc.screen.height - 12);
		int left = bounds[0] + bounds[2] + 5;
		if (left + width > mc.screen.width - 4) left = bounds[0] - width - 5;
		if (left < 4 || height < 80) return;
		int top = Math.max(4, Math.min(bounds[1], mc.screen.height - height - 4));
		if (candidatePreview == null || !candidateModel.equals(option.value())) {
			closeCandidatePreview();
			candidateModel = option.value();
			candidatePreview = new PreviewCardHolder(mc.level);
			candidatePreview.getFakeCaster().setInvisible(false);
			candidatePreview.getFakeCaster().setPos(0, 0, 0);
			candidatePreview.setYsmRenderOverride(candidateModel, YSMClientCompat.defaultTextureName(candidateModel), "", 0, "changed");
			YsmClientProfiles.preview(candidatePreview.getFakeCaster(), YsmModelProfile.empty(candidateModel));
		}
		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 800);
		graphics.fill(left - 1, top - 1, left + width + 1, top + height + 1, 0xff7892ab);
		graphics.fill(left, top, left + width, top + height, 0xff121b23);
		YsmPreviewDockPanel.renderModel(graphics, candidatePreview.getFakeCaster(), left + 1, top + 1, width - 2, height - 22,
				Math.max(1, (int) Math.min(width * .36, (height - 26) * .43)), 180, 0);
		graphics.drawString(mc.font, mc.font.plainSubstrByWidth(option.label().getString(), width - 10), left + 5, top + height - 15, 0xffdce9f2, false);
		graphics.pose().popPose();
	}
	@Override public void tick() {
		super.tick();
		if (candidatePreview != null) candidatePreview.tickModelPreview();
	}
	private void closeCandidatePreview() {
		if (candidatePreview == null) return;
		YsmClientPresentationBridge.forgetPreview(candidatePreview.getFakeCaster());
		YsmClientProfiles.forgetPreview(candidatePreview.getFakeCaster());
		candidatePreview.getFakeCaster().discard();
		candidatePreview = null;
		candidateModel = "";
	}
	@Override public void closeOverlay() { super.closeOverlay(); closeCandidatePreview(); }
	private String wheelKey(YsmModelCatalog.WheelEntry entry) { return entry.group() + "\n" + entry.id(); }
	private void wheel(YsmModelCatalog catalog) {
		var entries = catalog.wheel().stream()
				.filter(entry -> wheelGroup.isEmpty() || entry.group().equals(wheelGroup))
				.filter(entry -> entry.clipAvailable() || !entry.configGroup().isEmpty() || !entry.submenu().isEmpty()).toList();
		if (!wheelGroup.isEmpty()) button(text("wheel_back"), () -> { wheelGroup = wheelQuery = selectedWheel = ""; toTop(); }, true);
		editOptions("wheel_picker", text("wheel_picker"), wheelQuery, 256, value -> {
			wheelQuery = value;
			if (!selectedWheel.isEmpty()) { selectedWheel = ""; changed(); }
		},
				localOptions(() -> entries.stream().map(entry -> new Option(wheelKey(entry),
						Component.literal(entry.label().isBlank() ? entry.id() : entry.label()),
						Component.literal(entry.group()))).toList()),
				option -> { selectedWheel = option.value(); wheelQuery = option.label().getString(); changed(); });
		var entry = entries.stream().filter(value -> wheelKey(value).equals(selectedWheel)).findFirst().orElse(null);
		if (entry == null) return;
		if (entry.clipAvailable()) button(text("play_clip", entry.label().isBlank() ? entry.id() : entry.label()), () -> editor.selectClip(entry.id()), true);
		if (!entry.configGroup().isEmpty()) button(text("open_controls"), () -> {
			page = 3; controlGroup = entry.configGroup(); selectedControl = controlQuery = ""; toTop();
		}, true);
		if (!entry.submenu().isEmpty()) button(text("submenu"), () -> {
			wheelGroup = entry.submenu(); selectedWheel = wheelQuery = ""; toTop();
		}, true);
	}
	private boolean editable(YsmModelCatalog.Control control) {
		if (control.parameter().isEmpty()) return false;
		return switch (control.type()) {
			case "checkbox" -> true;
			case "radio" -> control.choices().stream().anyMatch(choice -> choice.numericValue() != null);
			case "range" -> Double.isFinite(control.min()) && Double.isFinite(control.max()) && control.min() <= control.max();
			default -> false;
		};
	}
	private String controlKey(YsmModelCatalog.Control control) { return control.group() + "\n" + control.parameter() + "\n" + control.title(); }
	private String controlTitle(YsmModelCatalog.Control control) { return control.title().isBlank() ? control.parameter() : control.title(); }
	private String rangeKey(YsmModelCatalog.Control control) { return editor.model() + "\n" + controlKey(control); }
	private Float value(YsmModelCatalog.Control control) {
		var explicit = editor.controlParameters().get(control.parameter());
		if (explicit != null) return explicit;
		if (editor.presetControls() && editor.bindingParameters().containsKey(control.parameter()))
			return editor.bindingParameters().get(control.parameter());
		var preview = editor.preview();
		Object base = preview == null ? null : YsmClientPresentationBridge.parameterBaseValue(preview.getFakeCaster(), control.parameter());
		return base instanceof Number number && Float.isFinite(number.floatValue()) ? number.floatValue() : null;
	}
	private void controls(YsmModelCatalog catalog) {
		select("control_scope", text("control_scope"), editor.presetControls() ? "preset" : "binding", List.of(
				new Option("binding", text("control_scope.binding")), new Option("preset", text("control_scope.preset"))),
				value -> { editor.presetControls(value.equals("preset")); rangeInputs.clear(); });
		label(editor.presetControls() ? text("control_preset", editor.presetId()) : text("control_binding"));
		var editable = catalog.controls().stream().filter(this::editable).toList();
		if (editable.isEmpty()) { label(text("no_editable_parameters")); return; }
		var groups = new LinkedHashMap<String, Option>();
		groups.put("", new Option("", text("all_groups")));
		for (var control : editable) if (!control.group().isEmpty())
			groups.putIfAbsent(control.group(), new Option(control.group(),
					Component.literal(control.groupLabel().isBlank() ? control.group() : control.groupLabel())));
		select("control_group", text("control_group"), controlGroup, List.copyOf(groups.values()), group -> {
			controlGroup = group; controlQuery = selectedControl = ""; changed();
		});
		var controls = editable.stream().filter(control -> controlGroup.isEmpty() || control.group().equals(controlGroup)).toList();
		editOptions("control_picker", text("control_picker"), controlQuery, 256, input -> {
			controlQuery = input;
			var current = controls.stream().filter(control -> controlKey(control).equals(selectedControl)).findFirst().orElse(null);
			if (current != null && !controlTitle(current).equals(input)) { selectedControl = ""; changed(); }
		}, localOptions(() -> controls.stream().map(control -> new Option(controlKey(control), Component.literal(controlTitle(control)),
				Component.literal(control.parameter()))).toList()), option -> {
			selectedControl = option.value(); controlQuery = option.label().getString();
			controls.stream().filter(control -> controlKey(control).equals(selectedControl)).findFirst().ifPresent(control -> {
				Float value = value(control);
				rangeInputs.put(rangeKey(control), value == null ? "" : Float.toString(value));
			});
			changed();
		});
		var control = controls.stream().filter(candidate -> controlKey(candidate).equals(selectedControl)).findFirst().orElse(null);
		if (control == null) return;
		if (!control.description().isBlank()) label(Component.literal(control.description()));
		Float current = value(control);
		if (control.type().equals("range")) {
			String key = rangeKey(control);
			String initial = current == null ? "" : Float.toString(current);
			edit("native_value", text("parameter_value"), rangeInputs.getOrDefault(key, initial), 40, input -> rangeInputs.put(key, input));
			label(text("range", control.min(), control.max(), control.step()));
			button(text("use_value"), () -> editor.controlParameter(control.parameter(),
					Float.parseFloat(rangeInputs.getOrDefault(key, initial))), true);
		} else {
			var choices = new ArrayList<Option>();
			if (control.type().equals("checkbox")) {
				choices.add(new Option("0.0", text("value_off")));
				choices.add(new Option("1.0", text("value_on")));
			} else for (var choice : control.choices()) if (choice.numericValue() != null) {
				String number = Float.toString(choice.numericValue());
				choices.add(new Option(number, Component.literal(choice.label().isBlank() ? number : choice.label())));
			}
			select("native_choice", text("parameter_value"), current == null ? "" : Float.toString(current), choices,
					input -> editor.controlParameter(control.parameter(), Float.parseFloat(input)));
		}
		button(text(editor.presetControls() ? "remove_parameter" : "remove_binding_parameter"),
				() -> editor.removeControlParameter(control.parameter()), editor.controlParameters().containsKey(control.parameter()));
		label(text("control_help"));
	}
}
