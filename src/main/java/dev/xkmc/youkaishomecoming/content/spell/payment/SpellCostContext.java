package dev.xkmc.youkaishomecoming.content.spell.payment;

/**
 * Payment context — which gameplay situation a cost is being paid for
 * (design doc §14). Providers may quote/route differently per context.
 */
public enum SpellCostContext {

	/** Server-side certification start fee (Phase 2). */
	CERTIFICATION_START,
	/** Finalized certified spell issuance fee (Phase 2). */
	CERTIFICATION_ISSUE,
	/** Casting a dynamic spell inside STG danmaku combat. */
	SPELL_CAST_STG,
	/** Casting a dynamic spell outside STG danmaku combat. */
	SPELL_CAST_NON_STG
}
