package dev.xkmc.youkaishomecoming.compat.ysm;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Transient, server-authored presentation. No gameplay or OYSM classes belong here. */
public record YsmPresentationState(long sequence, @Nullable Animation animation, Map<String, Parameter> parameters) {

	// Wire-format bounds, not gameplay defaults. The configurable limit may be lower.
	public static final int WIRE_MAX_PARAMETERS = 128;
	public static final int WIRE_MAX_NAME_LENGTH = 128;
	public static final double WIRE_MAX_PARAMETER_VALUE = 1_000_000_000.0;
	public static final YsmPresentationState EMPTY = new YsmPresentationState(0, null, Map.of());
	private static final Pattern PARAMETER = Pattern.compile("v\\.(?:roaming\\.)?[a-z_][a-z0-9_]*");

	public YsmPresentationState {
		if (sequence < 0 || parameters.size() > WIRE_MAX_PARAMETERS) throw new IllegalArgumentException("Invalid presentation snapshot");
		Map<String, Parameter> copy = new LinkedHashMap<>();
		parameters.forEach((name, value) -> copy.put(normalizeParameter(name), Objects.requireNonNull(value)));
		parameters = Collections.unmodifiableMap(copy);
	}

	public enum Source { COMMAND, SCRIPT, PREVIEW, EDITOR }

	public record Animation(String clip, long startedAt, long expiresAt, Source source, String model) {
		public Animation(String clip, long startedAt, long expiresAt, Source source) { this(clip, startedAt, expiresAt, source, ""); }
		public Animation {
			clip = normalizeClip(clip);
			model = model == null || model.isEmpty() ? "" : YsmModelProfile.modelId(model);
			checkTime(startedAt, expiresAt);
			Objects.requireNonNull(source);
		}

		public boolean active(long now) {
			return isActive(startedAt, expiresAt, now);
		}

		public boolean matchesModel(String id) { return model.isEmpty() || model.equals(id); }
	}

	public record Parameter(float value, long startedAt, long expiresAt, Source source, String model) {
		public Parameter(float value, long startedAt, long expiresAt, Source source) { this(value, startedAt, expiresAt, source, ""); }
		public Parameter {
			model = model == null || model.isEmpty() ? "" : YsmModelProfile.modelId(model);
			if (!Float.isFinite(value) || Math.abs(value) > WIRE_MAX_PARAMETER_VALUE) throw new IllegalArgumentException("Model parameter exceeds the numeric wire bound");
			checkTime(startedAt, expiresAt);
			Objects.requireNonNull(source);
		}

		public boolean active(long now) {
			return isActive(startedAt, expiresAt, now);
		}

		public boolean matchesModel(String id) { return model.isEmpty() || model.equals(id); }
	}

	public static String normalizeClip(String clip) {
		String value = clip == null ? "" : clip.trim();
		if (value.isEmpty() || value.length() > WIRE_MAX_NAME_LENGTH ||
				value.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c) || ",;|=+\"\\".indexOf(c) >= 0)) {
			throw new IllegalArgumentException("Expected one animation clip name, not an animation hint or expression");
		}
		return value;
	}

	/** Only numeric leaf variables are accepted; never execute user-supplied Molang. */
	public static String normalizeParameter(String name) {
		String value = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
		if (value.startsWith("variable.")) value = "v." + value.substring("variable.".length());
		if (value.length() > WIRE_MAX_NAME_LENGTH || !PARAMETER.matcher(value).matches() || value.equals("v.roaming")) {
			throw new IllegalArgumentException("Expected v.name or v.roaming.name (numeric variable only)");
		}
		return value;
	}

	public YsmPresentationState play(String clip, long now, int duration, Source source) {
		var current = expire(now);
		long next = sequence == Long.MAX_VALUE ? 1 : sequence + 1;
		return new YsmPresentationState(next, new Animation(clip, now, deadline(now, duration), source), current.parameters);
	}

	public YsmPresentationState stop() {
		return animation == null ? this : new YsmPresentationState(sequence, null, parameters);
	}

	/** Atomic model-scoped preset overlay. Unmentioned parameters and a blank body are preserved. */
	public YsmPresentationState applyPreset(String model, YsmModelProfile.Preset preset, long now, int ticks, Source source, int limit) {
		model = YsmModelProfile.modelId(model);
		var current = expire(now);
		var body = current.animation;
		long next = sequence;
		long end = deadline(now, ticks);
		if (!preset.clip().isEmpty()) {
			next = sequence == Long.MAX_VALUE ? 1 : sequence + 1;
			body = new Animation(preset.clip(), now, end, source, model);
		}
		Map<String, Parameter> values = new LinkedHashMap<>(current.parameters);
		for (var entry : preset.parameters().entrySet()) values.put(entry.getKey(), new Parameter(entry.getValue(), now, end, source, model));
		if (values.size() > limit) throw new IllegalArgumentException("Too many simultaneous model parameters");
		return new YsmPresentationState(next, body, values);
	}

	public YsmPresentationState setParameter(String name, float value, long now, int duration, Source source, int limit) {
		String normalized = normalizeParameter(name);
		var current = expire(now);
		if (!current.parameters.containsKey(normalized) && current.parameters.size() >= limit) {
			throw new IllegalArgumentException("Too many simultaneous model parameters (limit " + limit + ")");
		}
		Map<String, Parameter> copy = new LinkedHashMap<>(current.parameters);
		copy.put(normalized, new Parameter(value, now, deadline(now, duration), source));
		return new YsmPresentationState(sequence, current.animation, copy);
	}

	public YsmPresentationState clearParameter(String name) {
		String normalized = normalizeParameter(name);
		if (!parameters.containsKey(normalized)) return this;
		Map<String, Parameter> copy = new LinkedHashMap<>(parameters);
		copy.remove(normalized);
		return new YsmPresentationState(sequence, animation, copy);
	}

	public YsmPresentationState clearParameters() {
		return parameters.isEmpty() ? this : new YsmPresentationState(sequence, animation, Map.of());
	}

	/** Idempotent; a replaced parameter has only its new deadline, never an old scheduled cleanup. */
	public YsmPresentationState expire(long now) {
		Animation active = animation == null || animation.active(now) ? animation : null;
		boolean parametersActive = true;
		for (Parameter value : parameters.values()) {
			if (!value.active(now)) {
				parametersActive = false;
				break;
			}
		}
		if (active == animation && parametersActive) return this;
		Map<String, Parameter> copy = new LinkedHashMap<>();
		parameters.forEach((name, value) -> { if (value.active(now)) copy.put(name, value); });
		return new YsmPresentationState(sequence, active, copy);
	}

	public static long remaining(long expiresAt, long now) {
		return expiresAt == 0 ? -1 : Math.max(0, expiresAt - now);
	}

	private static boolean isActive(long start, long end, long now) {
		return now >= start && (end == 0 || now < end);
	}

	private static long deadline(long now, int duration) {
		if (now < 0 || duration < 0) throw new IllegalArgumentException("Negative presentation time or duration");
		return duration == 0 ? 0 : Math.addExact(now, duration);
	}

	private static void checkTime(long start, long end) {
		if (start < 0 || end < 0 || end != 0 && end <= start) throw new IllegalArgumentException("Invalid presentation deadline");
	}

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putLong("sequence", sequence);
		if (animation != null) {
			CompoundTag entry = timeTag(animation.startedAt, animation.expiresAt, animation.source);
			entry.putString("clip", animation.clip);
			entry.putString("model", animation.model);
			tag.put("animation", entry);
		}
		ListTag entries = new ListTag();
		parameters.forEach((name, value) -> {
			CompoundTag entry = timeTag(value.startedAt, value.expiresAt, value.source);
			entry.putString("name", name);
			entry.putFloat("value", value.value);
			entry.putString("model", value.model);
			entries.add(entry);
		});
		tag.put("parameters", entries);
		return tag;
	}

	private static CompoundTag timeTag(long start, long end, Source source) {
		CompoundTag tag = new CompoundTag();
		tag.putLong("start", start);
		tag.putLong("end", end);
		tag.putString("source", source.name());
		return tag;
	}

	/** Invalid entries fail closed; decoding never turns an invalid value into a Molang program. */
	public static YsmPresentationState fromTag(CompoundTag tag) {
		long sequence = Math.max(0, tag.getLong("sequence"));
		Animation animation = null;
		if (tag.contains("animation", Tag.TAG_COMPOUND)) {
			CompoundTag entry = tag.getCompound("animation");
			try {
				animation = new Animation(entry.getString("clip"), entry.getLong("start"), entry.getLong("end"), Source.valueOf(entry.getString("source")), entry.getString("model"));
			} catch (IllegalArgumentException ignored) { }
		}
		Map<String, Parameter> parameters = new LinkedHashMap<>();
		ListTag entries = tag.getList("parameters", Tag.TAG_COMPOUND);
		for (int i = 0; i < Math.min(entries.size(), WIRE_MAX_PARAMETERS); i++) {
			CompoundTag entry = entries.getCompound(i);
			try {
				if (!entry.contains("value", Tag.TAG_ANY_NUMERIC)) continue;
				parameters.put(normalizeParameter(entry.getString("name")), new Parameter(entry.getFloat("value"),
						entry.getLong("start"), entry.getLong("end"), Source.valueOf(entry.getString("source")), entry.getString("model")));
			} catch (IllegalArgumentException ignored) { }
		}
		return new YsmPresentationState(sequence, animation, parameters);
	}

	/** EntityData replaces the tag on a sync; do not decode/allocate the map on every render. */
	public static final class Cache {
		private CompoundTag previous;
		private YsmPresentationState state = EMPTY;

		public YsmPresentationState read(CompoundTag tag) {
			if (tag != previous) {
				state = fromTag(tag);
				previous = tag;
			}
			return state;
		}
	}
}
