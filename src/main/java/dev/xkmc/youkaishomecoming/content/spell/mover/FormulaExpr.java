package dev.xkmc.youkaishomecoming.content.spell.mover;

/**
 * Lightweight expression evaluator for formula mover.
 * Supports: +, -, *, /, %, parentheses, and functions:
 * sin_rad, cos_rad, sin_deg, cos_deg, abs, sqrt, min, max, pow, floor, ceil.
 * Variable: tick/bullet_tick (the current mover tick).
 * Constants: pi, e.
 *
 * Extended variables (available when using {@link RichEvaluable}):
 *   targetX, targetY, targetZ — target entity position
 *   casterX, casterY, casterZ — caster/owner entity position
 *   originX, originY, originZ — bullet spawn position
 *
 * Grammar:
 *   expr   = term (('+' | '-') term)*
 *   term   = unary (('*' | '/' | '%') unary)*
 *   unary  = '-' unary | atom
 *   atom   = number | 'tick' | 'pi' | 'e' | var | func '(' expr (',' expr)* ')' | '(' expr ')'
 */
public final class FormulaExpr {

	private final String source;
	private int pos;
	private boolean richMode = false;

	private FormulaExpr(String source) {
		this.source = source;
		this.pos = 0;
	}

	/**
	 * Parse a formula string into a compiled expression.
	 * Returns null if parsing fails.
	 */
	public static Evaluable parse(String formula) {
		if (formula == null || formula.isBlank()) return tick -> 0;
		try {
			FormulaExpr parser = new FormulaExpr(formula.trim());
			Evaluable result = parser.parseExpr();
			if (parser.pos < parser.source.length()) {
				return null; // Trailing garbage
			}
			return result;
		} catch (Exception e) {
			return null;
		}
	}

	@FunctionalInterface
	public interface Evaluable {
		double eval(double tick);
	}

	/**
	 * Extended evaluable that receives a full context with target/caster/origin positions.
	 * Used by TranslateMover and any future mover that needs runtime entity positions.
	 */
	@FunctionalInterface
	public interface RichEvaluable {
		double eval(double tick, double targetX, double targetY, double targetZ,
					double casterX, double casterY, double casterZ,
					double originX, double originY, double originZ);
	}

	/**
	 * Parse a formula string into a rich evaluable that supports extended variables.
	 * Supports all standard variables plus: targetX/Y/Z, casterX/Y/Z, originX/Y/Z.
	 */
	public static RichEvaluable parseRich(String formula) {
		if (formula == null || formula.isBlank()) return (t, tx, ty, tz, cx, cy, cz, ox, oy, oz) -> 0;
		try {
			FormulaExpr parser = new FormulaExpr(formula.trim());
			parser.richMode = true;
			RichEvaluable result = parser.parseExprRich();
			if (parser.pos < parser.source.length()) {
				return null;
			}
			return result;
		} catch (Exception e) {
			return null;
		}
	}

	// --- Parser ---

	private Evaluable parseExpr() {
		Evaluable left = parseTerm();
		while (pos < source.length()) {
			skipWhitespace();
			if (pos >= source.length()) break;
			char c = source.charAt(pos);
			if (c == '+') {
				pos++;
				Evaluable right = parseTerm();
				Evaluable l = left, r = right;
				left = t -> l.eval(t) + r.eval(t);
			} else if (c == '-') {
				pos++;
				Evaluable right = parseTerm();
				Evaluable l = left, r = right;
				left = t -> l.eval(t) - r.eval(t);
			} else {
				break;
			}
		}
		return left;
	}

	private Evaluable parseTerm() {
		Evaluable left = parseUnary();
		while (pos < source.length()) {
			skipWhitespace();
			if (pos >= source.length()) break;
			char c = source.charAt(pos);
			if (c == '*') {
				pos++;
				Evaluable right = parseUnary();
				Evaluable l = left, r = right;
				left = t -> l.eval(t) * r.eval(t);
			} else if (c == '/') {
				pos++;
				Evaluable right = parseUnary();
				Evaluable l = left, r = right;
				left = t -> { double d = r.eval(t); return d == 0 ? 0 : l.eval(t) / d; };
			} else if (c == '%') {
				pos++;
				Evaluable right = parseUnary();
				Evaluable l = left, r = right;
				left = t -> { double d = r.eval(t); return d == 0 ? 0 : l.eval(t) % d; };
			} else {
				break;
			}
		}
		return left;
	}

	private Evaluable parseUnary() {
		skipWhitespace();
		if (pos < source.length() && source.charAt(pos) == '-') {
			pos++;
			Evaluable inner = parseUnary();
			return t -> -inner.eval(t);
		}
		return parseAtom();
	}

	private Evaluable parseAtom() {
		skipWhitespace();
		if (pos >= source.length()) return t -> 0;

		char c = source.charAt(pos);

		// Parenthesized expression
		if (c == '(') {
			pos++;
			Evaluable inner = parseExpr();
			skipWhitespace();
			if (pos < source.length() && source.charAt(pos) == ')') pos++;
			return inner;
		}

		// Number literal
		if (Character.isDigit(c) || c == '.') {
			return parseNumber();
		}

		// Identifier: variable or function
		String id = parseIdentifier();
		if (id.isEmpty()) return t -> 0;

		return switch (id) {
			case "tick", "t", "bullet_tick", "bulletTick" -> t -> t;
			case "pi" -> t -> Math.PI;
			case "e" -> t -> Math.E;
			case "sin_rad" -> parseFunc1(Math::sin);
			case "cos_rad" -> parseFunc1(Math::cos);
			case "sin_deg" -> parseFunc1(v -> Math.sin(Math.toRadians(v)));
			case "cos_deg" -> parseFunc1(v -> Math.cos(Math.toRadians(v)));
			case "abs" -> parseFunc1(Math::abs);
			case "sqrt" -> parseFunc1(Math::sqrt);
			case "floor" -> parseFunc1(Math::floor);
			case "ceil" -> parseFunc1(Math::ceil);
			case "round" -> parseFunc1(Math::rint);
			case "log", "ln" -> parseFunc1(v -> v > 0 ? Math.log(v) : 0);
			case "exp" -> parseFunc1(Math::exp);
			case "min" -> parseFunc2(Math::min);
			case "max" -> parseFunc2(Math::max);
			case "clamp" -> parseFunc3((v, min, max) -> Math.max(min, Math.min(max, v)));
			case "pow" -> parseFunc2(Math::pow);
			case "root" -> parseFunc2((value, degree) -> degree == 0 ? 0 : Math.pow(value, 1.0 / degree));
			default -> throw new IllegalArgumentException("Unknown identifier: " + id);
		};
	}

	@FunctionalInterface
	private interface TriFunction {
		double apply(double a, double b, double c);
	}

	private Evaluable parseFunc3(TriFunction op) {
		skipWhitespace();
		if (pos < source.length() && source.charAt(pos) == '(') {
			pos++;
			Evaluable arg1 = parseExpr();
			skipWhitespace();
			if (pos < source.length() && source.charAt(pos) == ',') pos++;
			Evaluable arg2 = parseExpr();
			skipWhitespace();
			if (pos < source.length() && source.charAt(pos) == ',') pos++;
			Evaluable arg3 = parseExpr();
			skipWhitespace();
			if (pos < source.length() && source.charAt(pos) == ')') pos++;
			return t -> op.apply(arg1.eval(t), arg2.eval(t), arg3.eval(t));
		}
		throw new IllegalArgumentException("Expected '(' for 3-argument function");
	}

	private Evaluable parseFunc1(java.util.function.DoubleUnaryOperator op) {
		skipWhitespace();
		if (pos < source.length() && source.charAt(pos) == '(') {
			pos++;
			Evaluable arg = parseExpr();
			skipWhitespace();
			if (pos < source.length() && source.charAt(pos) == ')') pos++;
			return t -> op.applyAsDouble(arg.eval(t));
		}
		return t -> 0;
	}

	private Evaluable parseFunc2(java.util.function.DoubleBinaryOperator op) {
		skipWhitespace();
		if (pos < source.length() && source.charAt(pos) == '(') {
			pos++;
			Evaluable arg1 = parseExpr();
			skipWhitespace();
			if (pos < source.length() && source.charAt(pos) == ',') pos++;
			Evaluable arg2 = parseExpr();
			skipWhitespace();
			if (pos < source.length() && source.charAt(pos) == ')') pos++;
			return t -> op.applyAsDouble(arg1.eval(t), arg2.eval(t));
		}
		return t -> 0;
	}

	private Evaluable parseNumber() {
		int start = pos;
		while (pos < source.length() && (Character.isDigit(source.charAt(pos)) || source.charAt(pos) == '.')) {
			pos++;
		}
		double value = Double.parseDouble(source.substring(start, pos));
		return t -> value;
	}

	private String parseIdentifier() {
		int start = pos;
		while (pos < source.length() && (Character.isLetterOrDigit(source.charAt(pos)) || source.charAt(pos) == '_')) {
			pos++;
		}
		return source.substring(start, pos);
	}

	private void skipWhitespace() {
		while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) {
			pos++;
		}
	}

	// --- Rich mode parser (supports extended variables) ---

	private RichEvaluable parseExprRich() {
		RichEvaluable left = parseTermRich();
		while (pos < source.length()) {
			skipWhitespace();
			if (pos >= source.length()) break;
			char c = source.charAt(pos);
			if (c == '+') {
				pos++;
				RichEvaluable right = parseTermRich();
				RichEvaluable l = left, r = right;
				left = (t, tx, ty, tz, cx, cy, cz, ox, oy, oz) -> l.eval(t, tx, ty, tz, cx, cy, cz, ox, oy, oz) + r.eval(t, tx, ty, tz, cx, cy, cz, ox, oy, oz);
			} else if (c == '-') {
				pos++;
				RichEvaluable right = parseTermRich();
				RichEvaluable l = left, r = right;
				left = (t, tx, ty, tz, cx, cy, cz, ox, oy, oz) -> l.eval(t, tx, ty, tz, cx, cy, cz, ox, oy, oz) - r.eval(t, tx, ty, tz, cx, cy, cz, ox, oy, oz);
			} else {
				break;
			}
		}
		return left;
	}

	private RichEvaluable parseTermRich() {
		RichEvaluable left = parseUnaryRich();
		while (pos < source.length()) {
			skipWhitespace();
			if (pos >= source.length()) break;
			char c = source.charAt(pos);
			if (c == '*') {
				pos++;
				RichEvaluable right = parseUnaryRich();
				RichEvaluable l = left, r = right;
				left = (t, tx, ty, tz, cx, cy, cz, ox, oy, oz) -> l.eval(t, tx, ty, tz, cx, cy, cz, ox, oy, oz) * r.eval(t, tx, ty, tz, cx, cy, cz, ox, oy, oz);
			} else if (c == '/') {
				pos++;
				RichEvaluable right = parseUnaryRich();
				RichEvaluable l = left, r = right;
				left = (t, tx, ty, tz, cx, cy, cz, ox, oy, oz) -> { double d = r.eval(t, tx, ty, tz, cx, cy, cz, ox, oy, oz); return d == 0 ? 0 : l.eval(t, tx, ty, tz, cx, cy, cz, ox, oy, oz) / d; };
			} else if (c == '%') {
				pos++;
				RichEvaluable right = parseUnaryRich();
				RichEvaluable l = left, r = right;
				left = (t, tx, ty, tz, cx, cy, cz, ox, oy, oz) -> { double d = r.eval(t, tx, ty, tz, cx, cy, cz, ox, oy, oz); return d == 0 ? 0 : l.eval(t, tx, ty, tz, cx, cy, cz, ox, oy, oz) % d; };
			} else {
				break;
			}
		}
		return left;
	}

	private RichEvaluable parseUnaryRich() {
		skipWhitespace();
		if (pos < source.length() && source.charAt(pos) == '-') {
			pos++;
			RichEvaluable inner = parseUnaryRich();
			return (t, tx, ty, tz, cx, cy, cz, ox, oy, oz) -> -inner.eval(t, tx, ty, tz, cx, cy, cz, ox, oy, oz);
		}
		return parseAtomRich();
	}

	private RichEvaluable parseAtomRich() {
		skipWhitespace();
		if (pos >= source.length()) return (t, tx, ty, tz, cx, cy, cz, ox, oy, oz) -> 0;

		char c = source.charAt(pos);

		if (c == '(') {
			pos++;
			RichEvaluable inner = parseExprRich();
			skipWhitespace();
			if (pos < source.length() && source.charAt(pos) == ')') pos++;
			return inner;
		}

		if (Character.isDigit(c) || c == '.') {
			double value = parseNumberValue();
			return (t, tx, ty, tz, cx, cy, cz, ox, oy, oz) -> value;
		}

		String id = parseIdentifier();
		if (id.isEmpty()) return (t, tx, ty, tz, cx, cy, cz, ox, oy, oz) -> 0;

		return switch (id) {
			case "tick", "t", "bullet_tick", "bulletTick" -> (t, tx, ty, tz, cx, cy, cz, ox, oy, oz) -> t;
			case "pi" -> (t, tx, ty, tz, cx, cy, cz, ox, oy, oz) -> Math.PI;
			case "e" -> (t, tx, ty, tz, cx, cy, cz, ox, oy, oz) -> Math.E;
			// Target position
			case "targetX", "tx" -> (t, tx, ty, tz, cx, cy, cz, ox, oy, oz) -> tx;
			case "targetY", "ty" -> (t, tx2, ty, tz, cx, cy, cz, ox, oy, oz) -> ty;
			case "targetZ", "tz" -> (t, tx, ty, tz, cx, cy, cz, ox, oy, oz) -> tz;
			// Caster position
			case "casterX", "cx" -> (t, tx, ty, tz, cx, cy, cz, ox, oy, oz) -> cx;
			case "casterY", "cy" -> (t, tx, ty, tz, cx, cy, cz, ox, oy, oz) -> cy;
			case "casterZ", "cz" -> (t, tx, ty, tz, cx, cy, cz, ox, oy, oz) -> cz;
			// Origin (spawn) position
			case "originX", "ox" -> (t, tx, ty, tz, cx, cy, cz, ox, oy, oz) -> ox;
			case "originY", "oy" -> (t, tx, ty, tz, cx, cy, cz, ox, oy, oz) -> oy;
			case "originZ", "oz" -> (t, tx, ty, tz, cx, cy, cz, ox, oy, oz) -> oz;
			// Derived: distance and normalized direction from origin to target
			case "dist" -> (t, tx, ty, tz, cx, cy, cz, ox, oy, oz) -> {
				double ddx = tx - ox, ddy = ty - oy, ddz = tz - oz;
				return Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
			};
			case "dx" -> (t, tx, ty, tz, cx, cy, cz, ox, oy, oz) -> {
				double ddx = tx - ox, ddy = ty - oy, ddz = tz - oz;
				double d = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
				return d > 1e-4 ? ddx / d : 0;
			};
			case "dy" -> (t, tx, ty, tz, cx, cy, cz, ox, oy, oz) -> {
				double ddx = tx - ox, ddy = ty - oy, ddz = tz - oz;
				double d = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
				return d > 1e-4 ? ddy / d : 0;
			};
			case "dz" -> (t, tx, ty, tz, cx, cy, cz, ox, oy, oz) -> {
				double ddx = tx - ox, ddy = ty - oy, ddz = tz - oz;
				double d = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
				return d > 1e-4 ? ddz / d : 0;
			};
			// Functions
			case "sin_rad" -> parseFuncRich1(Math::sin);
			case "cos_rad" -> parseFuncRich1(Math::cos);
			case "sin_deg" -> parseFuncRich1(v -> Math.sin(Math.toRadians(v)));
			case "cos_deg" -> parseFuncRich1(v -> Math.cos(Math.toRadians(v)));
			case "abs" -> parseFuncRich1(Math::abs);
			case "sqrt" -> parseFuncRich1(Math::sqrt);
			case "floor" -> parseFuncRich1(Math::floor);
			case "ceil" -> parseFuncRich1(Math::ceil);
			case "round" -> parseFuncRich1(Math::rint);
			case "log", "ln" -> parseFuncRich1(v -> v > 0 ? Math.log(v) : 0);
			case "exp" -> parseFuncRich1(Math::exp);
			case "min" -> parseFuncRich2(Math::min);
			case "max" -> parseFuncRich2(Math::max);
			case "clamp" -> parseFuncRich3((v, min, max) -> Math.max(min, Math.min(max, v)));
			case "pow" -> parseFuncRich2(Math::pow);
			case "root" -> parseFuncRich2((value, degree) -> degree == 0 ? 0 : Math.pow(value, 1.0 / degree));
			default -> throw new IllegalArgumentException("Unknown identifier in rich formula: " + id);
		};
	}

	private RichEvaluable parseFuncRich3(TriFunction op) {
		skipWhitespace();
		if (pos < source.length() && source.charAt(pos) == '(') {
			pos++;
			RichEvaluable arg1 = parseExprRich();
			skipWhitespace();
			if (pos < source.length() && source.charAt(pos) == ',') pos++;
			RichEvaluable arg2 = parseExprRich();
			skipWhitespace();
			if (pos < source.length() && source.charAt(pos) == ',') pos++;
			RichEvaluable arg3 = parseExprRich();
			skipWhitespace();
			if (pos < source.length() && source.charAt(pos) == ')') pos++;
			return (t, tx, ty, tz, cx, cy, cz, ox, oy, oz) -> op.apply(
					arg1.eval(t, tx, ty, tz, cx, cy, cz, ox, oy, oz),
					arg2.eval(t, tx, ty, tz, cx, cy, cz, ox, oy, oz),
					arg3.eval(t, tx, ty, tz, cx, cy, cz, ox, oy, oz));
		}
		throw new IllegalArgumentException("Expected '(' for 3-argument function");
	}

	private RichEvaluable parseFuncRich1(java.util.function.DoubleUnaryOperator op) {
		skipWhitespace();
		if (pos < source.length() && source.charAt(pos) == '(') {
			pos++;
			RichEvaluable arg = parseExprRich();
			skipWhitespace();
			if (pos < source.length() && source.charAt(pos) == ')') pos++;
			return (t, tx, ty, tz, cx, cy, cz, ox, oy, oz) -> op.applyAsDouble(arg.eval(t, tx, ty, tz, cx, cy, cz, ox, oy, oz));
		}
		return (t, tx, ty, tz, cx, cy, cz, ox, oy, oz) -> 0;
	}

	private RichEvaluable parseFuncRich2(java.util.function.DoubleBinaryOperator op) {
		skipWhitespace();
		if (pos < source.length() && source.charAt(pos) == '(') {
			pos++;
			RichEvaluable arg1 = parseExprRich();
			skipWhitespace();
			if (pos < source.length() && source.charAt(pos) == ',') pos++;
			RichEvaluable arg2 = parseExprRich();
			skipWhitespace();
			if (pos < source.length() && source.charAt(pos) == ')') pos++;
			return (t, tx, ty, tz, cx, cy, cz, ox, oy, oz) -> op.applyAsDouble(
					arg1.eval(t, tx, ty, tz, cx, cy, cz, ox, oy, oz),
					arg2.eval(t, tx, ty, tz, cx, cy, cz, ox, oy, oz));
		}
		return (t, tx, ty, tz, cx, cy, cz, ox, oy, oz) -> 0;
	}

	private double parseNumberValue() {
		int start = pos;
		while (pos < source.length() && (Character.isDigit(source.charAt(pos)) || source.charAt(pos) == '.')) {
			pos++;
		}
		return Double.parseDouble(source.substring(start, pos));
	}
}
