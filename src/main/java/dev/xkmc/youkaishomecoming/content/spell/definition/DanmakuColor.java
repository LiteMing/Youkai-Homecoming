package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.world.item.DyeColor;

import java.util.Locale;
import java.util.Optional;

public record DanmakuColor(int argb) {

	public static final DanmakuColor WHITE = new DanmakuColor(0xffffffff);

	public static final Codec<DanmakuColor> CODEC = Codec.STRING.comapFlatMap(
			text -> parse(text).map(DataResult::success)
					.orElseGet(() -> DataResult.error(() -> "Invalid danmaku color: " + text)),
			DanmakuColor::format
	);

	public static DanmakuColor of(DyeColor color) {
		return new DanmakuColor(0xff000000 | color.getFireworkColor());
	}

	public static DanmakuColor rgb(int rgb) {
		return new DanmakuColor(0xff000000 | rgb & 0xffffff);
	}

	public int rgb() {
		return argb & 0xffffff;
	}

	public String format() {
		if ((argb >>> 24) == 0xff) {
			return String.format(Locale.ROOT, "#%06X", rgb());
		}
		return String.format(Locale.ROOT, "#%08X", argb);
	}

	public static Optional<DanmakuColor> parse(String raw) {
		if (raw == null) return Optional.empty();
		String value = raw.trim();
		if (value.isEmpty()) return Optional.empty();
		try {
			return Optional.of(of(DyeColor.valueOf(value.toUpperCase(Locale.ROOT))));
		} catch (IllegalArgumentException ignored) {
		}
		try {
			if (value.startsWith("#")) {
				String hex = value.substring(1);
				if (hex.length() == 6) return Optional.of(rgb(Integer.parseUnsignedInt(hex, 16)));
				if (hex.length() == 8) return Optional.of(new DanmakuColor((int) Long.parseLong(hex, 16)));
			}
			if (value.startsWith("0x") || value.startsWith("0X")) {
				String hex = value.substring(2);
				if (hex.length() == 6) return Optional.of(rgb(Integer.parseUnsignedInt(hex, 16)));
				if (hex.length() == 8) return Optional.of(new DanmakuColor((int) Long.parseLong(hex, 16)));
			}
		} catch (NumberFormatException ignored) {
		}
		return Optional.empty();
	}

}
