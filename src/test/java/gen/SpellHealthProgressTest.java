package gen;

import dev.xkmc.youkaishomecoming.content.entity.youkai.CombatProgress;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellProgressSnapshot;

/** Regression for spell HP larger than a boss's vanilla maximum (600 versus 200). */
public final class SpellHealthProgressTest {

	private static int checks;

	public static void main(String[] args) {
		CombatProgress combat = new CombatProgress();
		combat.maxProgress = 600;
		combat.progress = combat.clampToMaximum(600, 200);
		check("initial spell HP can exceed base HP", combat.progress == 600);
		combat.progress = combat.clampToMaximum(combat.progress - 10, 200);
		check("damage above base HP does not refill the spell", combat.progress == 590);
		check("vanilla HP remains a bounded projection", CombatProgress.vanillaHealth(combat.progress, 200) == 200);
		check("legitimate spell HP is not illegal damage",
				CombatProgress.vanillaHealth(combat.progress, 200) - 200 == 0);
		check("unexpected vanilla HP loss is still detectable",
				CombatProgress.vanillaHealth(combat.progress, 200) - 190 == 10);
		check("Remilia healing adds only the healed amount", combat.clampToMaximum(combat.progress + 4, 200) == 594);
		check("healing caps at the spell maximum", combat.clampToMaximum(610, 200) == 600);
		check("fractional damage is preserved", combat.clampToMaximum(combat.progress - 0.25f, 200) == 589.75f);
		var partial = new SpellProgressSnapshot(590, 600, 0, 0, 0, new int[]{600});
		check("progress uses spell HP as its denominator", partial.totalRemainingHealth() == 590 && partial.totalHealth() == 600);

		combat.progress = 600;
		boolean decreasing = true;
		for (int i = 0; i < 60; i++) {
			float previous = combat.progress;
			combat.progress = combat.clampToMaximum(combat.progress - 10, 200);
			decreasing &= combat.progress < previous;
		}
		check("every accepted hit decreases HP", decreasing);
		check("sixty 10-damage hits reach the break threshold", combat.progress == 0);
		check("overkill cannot produce negative health", combat.clampToMaximum(-10, 200) == 0);
		check("vanilla projection reaches zero on break", CombatProgress.vanillaHealth(combat.progress, 200) == 0);
		check("projection below base maximum is exact", CombatProgress.vanillaHealth(190, 200) == 190);

		combat.maxProgress = 50;
		check("small spell HP also caps at its own maximum", combat.clampToMaximum(100, 200) == 50);
		check("small spell HP can be damaged", combat.clampToMaximum(45, 200) == 45);
		combat.maxProgress = 0;
		check("ordinary health retains the base maximum", combat.clampToMaximum(220, 200) == 200);
		check("ordinary damage is unchanged", combat.clampToMaximum(190, 200) == 190);
		combat.maxProgress = 1_000_000;
		check("large spell pools also decrease", combat.clampToMaximum(999_990, 200) == 999_990);
		var laterPhase = new SpellProgressSnapshot(300, 400, 0, 0, 600, new int[]{600, 400});
		check("multiphase progress keeps the complete denominator",
				laterPhase.totalHealth() == 1000 && laterPhase.totalRemainingHealth() == 300);
		System.out.println("SpellHealthProgressTest: all " + checks + " checks passed");
	}

	private static void check(String label, boolean condition) {
		if (!condition) throw new AssertionError(label);
		checks++;
	}
}
