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
		return text == null || text.isEmpty() ? 0 : text.codePointCount(0, text.length());
	}

	/** Split into one string per code point, keeping surrogate pairs intact. */
	public static String[] split(String text) {
		int n = count(text);
		String[] ans = new String[n];
		if (n == 0) {
			return ans;
		}
		int idx = 0;
		for (int pos = 0; pos < text.length(); ) {
			int cp = text.codePointAt(pos);
			ans[idx++] = new String(Character.toChars(cp));
			pos += Character.charCount(cp);
		}
		return ans;
	}

}
