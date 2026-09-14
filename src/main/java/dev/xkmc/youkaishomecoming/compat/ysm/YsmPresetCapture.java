package dev.xkmc.youkaishomecoming.compat.ysm;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Captures reproducible inputs, never animation outputs, query state or arbitrary Molang expressions. */
public final class YsmPresetCapture {

	private YsmPresetCapture() { }

	public static List<String> parameterNames(YsmModelCatalog catalog, YsmPresentationResolver.Resolved rendered) {
		var names = new LinkedHashSet<String>();
		for (var control : catalog.controls()) if (control.editable()) names.add(control.parameter());
		names.addAll(rendered.parameters().keySet());
		if (names.size() > YsmPresentationState.WIRE_MAX_PARAMETERS)
			throw new IllegalArgumentException("Too many parameters in preview snapshot");
		return List.copyOf(names);
	}

	/** Missing inputs are an error. In particular, a missing value is never invented as zero. */
	public static YsmModelProfile.Preset capture(YsmModelCatalog catalog, YsmPresentationResolver.Resolved rendered,
			Map<String, Float> inputs, String description, int ticks) {
		if (catalog.status() != YsmModelCatalog.Status.READY || !catalog.detail().isEmpty())
			throw new IllegalArgumentException("preview_capture_unavailable");
		String clip = rendered.body() == null ? "" : rendered.body().clip();
		if (!clip.isEmpty() && !catalog.animations().contains(clip))
			throw new IllegalArgumentException("preview_capture_clip");
		var values = new LinkedHashMap<String, Float>();
		for (String name : parameterNames(catalog, rendered)) {
			Float value = inputs.get(name);
			if (value == null || !Float.isFinite(value))
				throw new IllegalArgumentException("preview_capture_parameter:" + name);
			if (!catalog.accepts(name, value))
				throw new IllegalArgumentException("preview_capture_value:" + name);
			values.put(name, value);
		}
		return new YsmModelProfile.Preset(description, clip, ticks, values);
	}
}
