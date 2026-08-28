package dev.xkmc.youkaishomecoming.util;

/**
 * Code-point helpers for laying out text one glyph at a time.
 *
 * <p>Both the text danmaku renderer and the magic circle text layer draw each
 * character into its own slot rather than letting the font advance naturally,
 * so they need to iterate code points instead of {@code char}s — otherwise a
 * surrogate pair (emoji, rare CJK) would be split into two broken halves.
 */
public final class GlyphRuns {

	private GlyphRuns() {
	}

	/** Number of code points, i.e. the number of glyph slots the text needs. */
	public static int count(String text) {
		return codePoints(decodeEscapes(text));
	}

	/** Split into one string per code point, keeping surrogate pairs intact. */
	public static String[] split(String text) {
		String decoded = decodeEscapes(text);
		String[] ans = new String[codePoints(decoded)];
		int idx = 0;
		for (int pos = 0; pos < decoded.length(); ) {
			int cp = decoded.codePointAt(pos);
			ans[idx++] = new String(Character.toChars(cp));
			pos += Character.charCount(cp);
		}
		return ans;
	}

	private static int codePoints(String decoded) {
		return decoded.isEmpty() ? 0 : decoded.codePointCount(0, decoded.length());
	}

	/**
	 * Resolve {@code \\uXXXX} escapes to real code points.
	 *
	 * <p>Private-use characters are the whole point of resource-pack glyph
	 * replacement, but they cannot be typed into an edit box — and unlike a command
	 * argument, nothing parses escapes on the way in, so the field would otherwise
	 * store six literal characters and render them as plain text.
	 *
	 * <p>Decoding here rather than on input keeps the stored text editable: the field
	 * still shows {@code \\uE001} while the renderer draws the custom glyph.
	 * Use {@code \\\\} for a literal backslash.
	 */
	public static String decodeEscapes(String text) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		if (text.indexOf('\\') < 0) {
			return text;
		}
		StringBuilder out = new StringBuilder(text.length());
		int i = 0;
		while (i < text.length()) {
			char c = text.charAt(i);
			if (c == '\\' && i + 1 < text.length()) {
				char next = text.charAt(i + 1);
				if ((next == 'u' || next == 'U') && i + 6 <= text.length()) {
					String hex = text.substring(i + 2, i + 6);
					if (isHex(hex)) {
						out.append((char) Integer.parseInt(hex, 16));
						i += 6;
						continue;
					}
				}
				if (next == '\\') {
					out.append('\\');
					i += 2;
					continue;
				}
			}
			out.append(c);
			i++;
		}
		return out.toString();
	}

	private static boolean isHex(String s) {
		for (int i = 0; i < s.length(); i++) {
			if (Character.digit(s.charAt(i), 16) < 0) {
				return false;
			}
		}
		return true;
	}

}
