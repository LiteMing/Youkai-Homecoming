package dev.xkmc.youkaishomecoming.content.spell.analysis;

/**
 * A single analysis finding. Paths follow the editor convention:
 * {@code phase/<phase_id>/<list>/<index>[/<hook>/<index>...]}.
 */
public record SpellDiagnostic(Severity severity, String code, String path, String message) {

	public enum Severity {
		INFO, WARNING, ERROR
	}

	public static SpellDiagnostic info(String code, String path, String message) {
		return new SpellDiagnostic(Severity.INFO, code, path, message);
	}

	public static SpellDiagnostic warning(String code, String path, String message) {
		return new SpellDiagnostic(Severity.WARNING, code, path, message);
	}

	public static SpellDiagnostic error(String code, String path, String message) {
		return new SpellDiagnostic(Severity.ERROR, code, path, message);
	}

	public boolean isError() {
		return severity == Severity.ERROR;
	}

	@Override
	public String toString() {
		return "[" + severity + "] " + code + " @" + path + ": " + message;
	}
}
