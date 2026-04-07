package dev.xkmc.youkaishomecoming.content.spell.definition;

import java.util.*;

/**
 * Recursive-descent parser for NumberProvider expression shorthand.
 * Input:  editbox string  (e.g. "rand(60,100)", "sin(tick*3)*20", "$angle")
 * Output: NumberProvider  (or null on parse failure)
 *
 * Grammar (precedence low→high):
 *   expr   ::= add
 *   add    ::= mul (('+' | '-') mul)*
 *   mul    ::= unary (('*' | '/') unary)*
 *   unary  ::= '-' unary | atom
 *   atom   ::= NUMBER | VARIABLE | function_call | '(' expr ')'
 *   function_call ::= NAME '(' expr (',' expr)* ')'
 *   VARIABLE     ::= '$' NAME
 */
public class NumberExprParser {

	private final String input;
	private final int len;
	private int pos;

	private NumberExprParser(String input) {
		this.input = input;
		this.len = input.length();
		this.pos = 0;
	}

	// ---- public API ----

	/**
	 * Parse an expression string into a NumberProvider.
	 * Returns null on any parse error.
	 */
	public static NumberProvider parse(String input) {
		if (input == null) return null;
		String trimmed = input.trim();
		if (trimmed.isEmpty()) return null;
		try {
			var parser = new NumberExprParser(trimmed);
			NumberProvider result = parser.parseExpr();
			if (parser.pos < parser.len) return null; // trailing junk
			return result;
		} catch (ParseException e) {
			return null;
		}
	}

	/**
	 * Convert a NumberProvider back to a human-readable shorthand string.
	 * Returns null if the provider is not one of the recognized types.
	 */
	public static String unparse(NumberProvider p) {
		if (p == null) return null;
		if (p instanceof NumberProviders.Constant c) return formatDouble(c.value());
		if (p instanceof NumberProviders.RandomRange r) return "rand(" + formatDouble(r.min()) + ", " + formatDouble(r.max()) + ")";
		if (p instanceof NumberProviders.LerpOverTime l) return "lerp(" + formatDouble(l.start()) + ", " + formatDouble(l.end()) + ", " + l.duration() + ")";
		if (p instanceof NumberProviders.ByHealthRatio h) return "hp(" + formatDouble(h.atFull()) + ", " + formatDouble(h.atEmpty()) + ")";
		if (p instanceof NumberProviders.PhaseTickMod m) return "tick_mod(" + m.period() + ")";
		if (p instanceof NumberProviders.Variable v) return "$" + v.key();
		if (p instanceof NumberProviders.PhaseTick) return "tick";
		if (p instanceof NumberProviders.TotalTick) return "total_tick";
		if (p instanceof NumberProviders.Sin s) return unparseTrig("sin", s.input(), s.amplitude(), s.phase());
		if (p instanceof NumberProviders.Cos c) return unparseTrig("cos", c.input(), c.amplitude(), c.phase());
		if (p instanceof NumberProviders.Add a) {
			// Special case: a + (-1 * b) → (a - b)
			if (a.b() instanceof NumberProviders.Mul m
					&& m.a() instanceof NumberProviders.Constant c && c.value() == -1) {
				String as = unparse(a.a());
				String bs = unparse(m.b());
				if (as == null || bs == null) return null;
				return "(" + as + " - " + bs + ")";
			}
			String as = unparse(a.a());
			String bs = unparse(a.b());
			if (as == null || bs == null) return null;
			return "(" + as + " + " + bs + ")";
		}
		if (p instanceof NumberProviders.Mul m) {
			// Special case: (-1) * x → -x (unary negation shorthand)
			if (m.a() instanceof NumberProviders.Constant c && c.value() == -1) {
				String bs = unparse(m.b());
				if (bs == null) return null;
				return "-" + bs;
			}
			if (m.b() instanceof NumberProviders.Constant c && c.value() == -1) {
				String as = unparse(m.a());
				if (as == null) return null;
				return "-" + as;
			}
			String as = unparse(m.a());
			String bs = unparse(m.b());
			if (as == null || bs == null) return null;
			return "(" + as + " * " + bs + ")";
		}
		if (p instanceof NumberProviders.Div d) {
			String as = unparse(d.a());
			String bs = unparse(d.b());
			if (as == null || bs == null) return null;
			return "(" + as + " / " + bs + ")";
		}
		if (p instanceof NumberProviders.Mod m) {
			String as = unparse(m.a());
			String bs = unparse(m.b());
			if (as == null || bs == null) return null;
			return "(" + as + " % " + bs + ")";
		}
		if (p instanceof NumberProviders.Sqrt s) {
			String inStr = unparse(s.input());
			if (inStr == null) return null;
			return "sqrt(" + inStr + ")";
		}
		if (p instanceof NumberProviders.Distance) return "distance";
		if (p instanceof NumberProviders.TargetHeight) return "target_height";
		if (p instanceof NumberProviders.GameDifficulty) return "game_difficulty";
		if (p instanceof NumberProviders.CasterX) return "caster_x";
		if (p instanceof NumberProviders.CasterY) return "caster_y";
		if (p instanceof NumberProviders.CasterZ) return "caster_z";
		if (p instanceof NumberProviders.TargetX) return "target_x";
		if (p instanceof NumberProviders.TargetY) return "target_y";
		if (p instanceof NumberProviders.TargetZ) return "target_z";
		if (p instanceof NumberProviders.HeightmapY h) {
			String xs = unparse(h.x()), zs = unparse(h.z());
			if (xs == null || zs == null) return null;
			return "heightmap_y(" + xs + ", " + zs + ")";
		}
		if (p instanceof NumberProviders.TargetFlyTime) return "target_fly_time";
		if (p instanceof NumberProviders.TargetSpeed) return "target_speed";
		if (p instanceof NumberProviders.Max m) {
			String as = unparse(m.a()), bs = unparse(m.b());
			if (as == null || bs == null) return null;
			return "max(" + as + ", " + bs + ")";
		}
		if (p instanceof NumberProviders.Min m) {
			String as = unparse(m.a()), bs = unparse(m.b());
			if (as == null || bs == null) return null;
			return "min(" + as + ", " + bs + ")";
		}
		if (p instanceof NumberProviders.Clamp c) {
			String vs = unparse(c.value()), mins = unparse(c.min()), maxs = unparse(c.max());
			if (vs == null || mins == null || maxs == null) return null;
			return "clamp(" + vs + ", " + mins + ", " + maxs + ")";
		}
		if (p instanceof NumberProviders.GaussianRandom g) {
			return "gaussian(" + formatDouble(g.mean()) + ", " + formatDouble(g.stdDev()) + ")";
		}
		if (p instanceof NumberProviders.RandomChoice rc) {
			StringBuilder sb = new StringBuilder("choose(");
			for (int i = 0; i < rc.values().size(); i++) {
				if (i > 0) sb.append(", ");
				sb.append(formatDouble(rc.values().get(i)));
			}
			sb.append(")");
			return sb.toString();
		}
		return null;
	}

	// ---- internal parser ----

	private NumberProvider parseExpr() {
		return parseAdd();
	}

	private NumberProvider parseAdd() {
		NumberProvider left = parseMul();
		while (pos < len) {
			skipWhitespace();
			if (pos >= len) break;
			char c = peek();
			if (c == '+') {
				consume('+');
				NumberProvider right = parseMul();
				left = new NumberProviders.Add(left, right);
			} else if (c == '-') {
				consume('-');
				NumberProvider right = parseMul();
				left = new NumberProviders.Add(left, new NumberProviders.Mul(new NumberProviders.Constant(-1), right));
			} else {
				break;
			}
		}
		return left;
	}

	private NumberProvider parseMul() {
		NumberProvider left = parseUnary();
		while (pos < len) {
			skipWhitespace();
			if (pos >= len) break;
			char c = peek();
			if (c == '*') {
				consume('*');
				NumberProvider right = parseUnary();
				left = new NumberProviders.Mul(left, right);
			} else if (c == '/') {
				consume('/');
				NumberProvider right = parseUnary();
				left = new NumberProviders.Div(left, right);
			} else if (c == '%') {
				consume('%');
				NumberProvider right = parseUnary();
				left = new NumberProviders.Mod(left, right);
			} else {
				break;
			}
		}
		return left;
	}

	private NumberProvider parseUnary() {
		skipWhitespace();
		if (pos < len && peek() == '-') {
			consume('-');
			skipWhitespace();
			// Optimization: if next token is a number literal, fold into negative constant
			if (pos < len && (Character.isDigit(peek()) || peek() == '.')) {
				NumberProvider num = parseNumber();
				if (num instanceof NumberProviders.Constant c) {
					return new NumberProviders.Constant(-c.value());
				}
			}
			NumberProvider inner = parseUnary();
			return new NumberProviders.Mul(new NumberProviders.Constant(-1), inner);
		}
		return parseAtom();
	}

	private NumberProvider parseAtom() {
		skipWhitespace();
		if (pos >= len) throw new ParseException("Unexpected end of input");

		char c = peek();

		// Number literal
		if (Character.isDigit(c) || c == '.') {
			return parseNumber();
		}

		// Variable reference: $name
		if (c == '$') {
			consume('$');
			String name = parseName();
			return new NumberProviders.Variable(name);
		}

		// Parenthesized expression
		if (c == '(') {
			consume('(');
			NumberProvider inner = parseExpr();
			expect(')');
			return inner;
		}

		// Named function or bare identifier
		if (Character.isLetter(c) || c == '_') {
			String name = parseName();
			skipWhitespace();
			// Bare identifiers without '(' are special keywords
			if (pos >= len || peek() != '(') {
				return parseBareKeyword(name);
			}
			return parseFunctionCall(name);
		}

		throw new ParseException("Unexpected character: " + c);
	}

	private NumberProvider parseNumber() {
		int start = pos;
		while (pos < len && (Character.isDigit(peek()) || peek() == '.')) {
			pos++;
		}
		String numStr = input.substring(start, pos);
		try {
			return new NumberProviders.Constant(Double.parseDouble(numStr));
		} catch (NumberFormatException e) {
			throw new ParseException("Invalid number: " + numStr);
		}
	}

	private String parseName() {
		int start = pos;
		while (pos < len && (Character.isLetterOrDigit(peek()) || peek() == '_')) {
			pos++;
		}
		String name = input.substring(start, pos);
		if (name.isEmpty()) throw new ParseException("Expected name");
		return name;
	}

	private NumberProvider parseFunctionCall(String name) {
		expect('(');
		List<NumberProvider> args = new ArrayList<>();
		skipWhitespace();
		if (pos < len && peek() != ')') {
			args.add(parseExpr());
			while (pos < len && peek() == ',') {
				consume(',');
				args.add(parseExpr());
			}
		}
		expect(')');

		switch (name) {
			case "rand", "random" -> {
				if (args.size() != 2) throw new ParseException("rand() requires 2 arguments");
				return new NumberProviders.RandomRange(
						resolveDoubleArg(args.get(0), "rand min"),
						resolveDoubleArg(args.get(1), "rand max")
				);
			}
			case "lerp", "lerp_time" -> {
				if (args.size() != 3) throw new ParseException("lerp() requires 3 arguments");
				return new NumberProviders.LerpOverTime(
						resolveDoubleArg(args.get(0), "lerp start"),
						resolveDoubleArg(args.get(1), "lerp end"),
						resolveIntArg(args.get(2), "lerp duration")
				);
			}
			case "hp", "health", "by_health" -> {
				if (args.size() != 2) throw new ParseException("hp() requires 2 arguments");
				return new NumberProviders.ByHealthRatio(
						resolveDoubleArg(args.get(0), "hp atFull"),
						resolveDoubleArg(args.get(1), "hp atEmpty")
				);
			}
			case "tick_mod" -> {
				if (args.size() != 1) throw new ParseException("tick_mod() requires 1 argument");
				return new NumberProviders.PhaseTickMod(
						resolveIntArg(args.get(0), "tick_mod period")
				);
			}
			case "sin" -> { return parseTrigArgs(args, false); }
			case "cos" -> { return parseTrigArgs(args, true); }
			case "sqrt" -> {
				if (args.size() != 1) throw new ParseException("sqrt() requires 1 argument");
				return new NumberProviders.Sqrt(args.get(0));
			}
			case "max" -> {
				if (args.size() != 2) throw new ParseException("max() requires 2 arguments");
				return new NumberProviders.Max(args.get(0), args.get(1));
			}
			case "min" -> {
				if (args.size() != 2) throw new ParseException("min() requires 2 arguments");
				return new NumberProviders.Min(args.get(0), args.get(1));
			}
			case "clamp" -> {
				if (args.size() != 3) throw new ParseException("clamp() requires 3 arguments");
				return new NumberProviders.Clamp(args.get(0), args.get(1), args.get(2));
			}
			case "gaussian" -> {
				if (args.size() != 2) throw new ParseException("gaussian() requires 2 arguments");
				return new NumberProviders.GaussianRandom(
						resolveDoubleArg(args.get(0), "gaussian mean"),
						resolveDoubleArg(args.get(1), "gaussian stddev"));
			}
			case "choose" -> {
				if (args.isEmpty()) throw new ParseException("choose() requires at least 1 argument");
				java.util.List<Double> values = new java.util.ArrayList<>();
				for (var a : args) values.add(resolveDoubleArg(a, "choose value"));
				return new NumberProviders.RandomChoice(values);
			}
			case "heightmap_y" -> {
				if (args.size() != 2) throw new ParseException("heightmap_y() requires 2 arguments");
				return new NumberProviders.HeightmapY(args.get(0), args.get(1));
			}
			default -> throw new ParseException("Unknown function: " + name);
		}
	}

	private NumberProvider parseBareKeyword(String name) {
		return switch (name) {
			case "tick", "phase_tick" -> new NumberProviders.PhaseTick();
			case "total_tick" -> new NumberProviders.TotalTick();
			case "distance" -> new NumberProviders.Distance();
			case "target_height" -> new NumberProviders.TargetHeight();
			case "game_difficulty" -> new NumberProviders.GameDifficulty();
			case "caster_x" -> new NumberProviders.CasterX();
			case "caster_y" -> new NumberProviders.CasterY();
			case "caster_z" -> new NumberProviders.CasterZ();
			case "target_x" -> new NumberProviders.TargetX();
			case "target_y" -> new NumberProviders.TargetY();
			case "target_z" -> new NumberProviders.TargetZ();
			case "target_fly_time" -> new NumberProviders.TargetFlyTime();
			case "target_speed" -> new NumberProviders.TargetSpeed();
			default -> throw new ParseException("Unknown keyword: " + name);
		};
	}

	private NumberProvider parseTrigArgs(List<NumberProvider> args, boolean isCos) {
		if (args.size() < 1 || args.size() > 3)
			throw new ParseException(isCos ? "cos() requires 1-3 arguments" : "sin() requires 1-3 arguments");
		NumberProvider input = args.get(0);
		double amplitude = args.size() >= 2 ? resolveDoubleArg(args.get(1), "amplitude") : 1.0;
		double phase = args.size() >= 3 ? resolveDoubleArg(args.get(2), "phase") : 0.0;
		return isCos ? new NumberProviders.Cos(input, amplitude, phase) : new NumberProviders.Sin(input, amplitude, phase);
	}

	// ---- helpers ----

	private char peek() {
		return input.charAt(pos);
	}

	private void consume(char expected) {
		if (pos >= len || input.charAt(pos) != expected)
			throw new ParseException("Expected '" + expected + "'");
		pos++;
	}

	private void expect(char expected) {
		skipWhitespace();
		consume(expected);
	}

	private void skipWhitespace() {
		while (pos < len && Character.isWhitespace(peek())) {
			pos++;
		}
	}

	private static int resolveIntArg(NumberProvider p, String desc) {
		if (p instanceof NumberProviders.Constant c) return (int) c.value();
		throw new ParseException(desc + " must be a constant integer");
	}

	private static double resolveDoubleArg(NumberProvider p, String desc) {
		if (p instanceof NumberProviders.Constant c) return c.value();
		throw new ParseException(desc + " must be a constant number");
	}

	private static String formatDouble(double v) {
		if (v == (long) v) return String.valueOf((long) v);
		return String.valueOf(v);
	}

	private static String unparseTrig(String name, NumberProvider input, double amp, double phase) {
		String inStr = unparse(input);
		if (inStr == null) return null;
		if (amp == 1.0 && phase == 0.0) return name + "(" + inStr + ")";
		if (phase == 0.0) return name + "(" + inStr + ", " + formatDouble(amp) + ")";
		return name + "(" + inStr + ", " + formatDouble(amp) + ", " + formatDouble(phase) + ")";
	}

	private static class ParseException extends RuntimeException {
		ParseException(String msg) {
			super(msg);
		}
	}

}
