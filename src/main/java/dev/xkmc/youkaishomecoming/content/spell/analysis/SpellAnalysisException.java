package dev.xkmc.youkaishomecoming.content.spell.analysis;

import java.util.List;

/**
 * Thrown when a spell definition is rejected by static analysis
 * (structural violation, legacy ticker, hard limit, banned/denied capability).
 * Extends {@link IllegalArgumentException} so the historical
 * SpellMarketValidator facade keeps its contract (callers catch IAE).
 */
public class SpellAnalysisException extends IllegalArgumentException {

	private final List<SpellDiagnostic> diagnostics;

	public SpellAnalysisException(String message) {
		this(message, List.of());
	}

	public SpellAnalysisException(String message, List<SpellDiagnostic> diagnostics) {
		super(message);
		this.diagnostics = List.copyOf(diagnostics);
	}

	public List<SpellDiagnostic> diagnostics() {
		return diagnostics;
	}
}
