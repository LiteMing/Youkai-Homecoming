package dev.xkmc.youkaishomecoming.compat.ysm;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Read-only, provider-class-free catalog shared by command inspection and the YSM editor. */
public record YsmModelCatalog(Status status, String detail, List<String> animations,
		List<WheelEntry> wheel, List<Control> controls) {

	private static final Pattern ASSIGNMENT = Pattern.compile(
			"((?:v|variable)\\.(?:roaming\\.)?[a-z_][a-z0-9_]*)\\s*=\\s*([-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][-+]?[0-9]+)?)\\s*;?",
			Pattern.CASE_INSENSITIVE);

	public enum Status { READY, NOT_INSTALLED, API_UNAVAILABLE, MODEL_NOT_READY, MODEL_MISSING }

	public YsmModelCatalog {
		animations = List.copyOf(animations);
		wheel = List.copyOf(wheel);
		controls = List.copyOf(controls);
	}

	public static YsmModelCatalog unavailable(Status status, String detail) {
		return new YsmModelCatalog(status, detail, List.of(), List.of(), List.of());
	}

	/** A wheel entry may expose BOTH a clip and a configuration group; a submenu is not a clip. */
	public record WheelEntry(String group, String id, String label, boolean clipAvailable, String configGroup, String submenu) { }

	public record Choice(String label, String expression, @Nullable Float numericValue) { }

	public record Control(String group, String groupLabel, String title, String description, String type, String expression,
			String parameter, double min, double max, double step, List<Choice> choices) {
		public Control {
			choices = List.copyOf(choices);
		}

		public boolean accepts(float value) {
			if (parameter.isEmpty() || !Float.isFinite(value)) return false;
			return switch (type) {
				case "checkbox" -> value == 0 || value == 1;
				case "range" -> Double.isFinite(min) && Double.isFinite(max) && min <= max && value >= min && value <= max;
				case "radio" -> choices.stream().anyMatch(choice -> choice.numericValue != null && choice.numericValue == value);
				default -> false;
			};
		}
	}

	/** Complex label actions remain inspectable, but are never evaluated by the numeric parameter API. */
	public static Choice choice(String parameter, String label, String expression) {
		Float value = null;
		var matcher = ASSIGNMENT.matcher(expression.trim());
		if (!parameter.isEmpty() && matcher.matches()) {
			try {
				float parsed = Float.parseFloat(matcher.group(2));
				if (YsmPresentationState.normalizeParameter(matcher.group(1)).equals(parameter) && Float.isFinite(parsed)) value = parsed;
			} catch (IllegalArgumentException ignored) { }
		}
		return new Choice(label, expression, value);
	}

	public static WheelEntry wheelEntry(String group, String id, String label, List<String> animations, Map<?, ?> configGroups) {
		String config = label.startsWith("#") && configGroups.containsKey(label.substring(1)) ? label.substring(1) : "";
		String submenu = id.startsWith("#") ? id.substring(1) : "";
		return new WheelEntry(group, id, label, submenu.isEmpty() && animations.contains(id), config, submenu);
	}

	/** Unknown numeric variables are allowed as raw inputs; declared forms constrain known variables. */
	public boolean accepts(String parameter, float value) {
		boolean declared = false;
		for (Control control : controls) {
			if (!parameter.equals(control.parameter)) continue;
			declared = true;
			if (control.accepts(value)) return true;
		}
		return !declared;
	}
}
