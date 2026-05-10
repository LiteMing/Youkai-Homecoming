package dev.xkmc.youkaishomecoming.content.spell.mover;

/**
 * Lightweight expression evaluator for formula mover.
 * Supports: +, -, *, /, parentheses, and functions: sin, cos, abs, sqrt, min, max, pow, floor, ceil.
 * Variable: tick (the current mover tick).
 * Constants: pi, e.
 *
 * Grammar:
 *   expr   = term (('+' | '-') term)*
 *   term   = unary (('*' | '/') unary)*
 *   unary  = '-' unary | atom
 *   atom   = number | 'tick' | 'pi' | 'e' | func '(' expr (',' expr)* ')' | '(' expr ')'
 */
public final class FormulaExpr {

	private final String source;
	private int pos;

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
			case "tick", "t" -> t -> t;
			case "pi" -> t -> Math.PI;
			case "e" -> t -> Math.E;
			case "sin" -> parseFunc1(Math::sin);
			case "cos" -> parseFunc1(Math::cos);
			case "abs" -> parseFunc1(Math::abs);
			case "sqrt" -> parseFunc1(Math::sqrt);
			case "floor" -> parseFunc1(Math::floor);
			case "ceil" -> parseFunc1(Math::ceil);
			case "min" -> parseFunc2(Math::min);
			case "max" -> parseFunc2(Math::max);
			case "pow" -> parseFunc2(Math::pow);
			default -> t -> 0; // Unknown identifier
		};
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
}
