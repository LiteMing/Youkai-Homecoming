package dev.xkmc.youkaishomecoming.content.spell.certification;

import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysisProfile;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalyzer;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellHash;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Certified spell item validation (design doc §15, §22): a certified item loads
 * its immutable definition from the world certificate storage, verifies the
 * stored hash, re-checks the capability policy and clamps the run duration to
 * the certified maximum. Tampered NBT, overwritten storage or revoked
 * capabilities all reject the cast.
 */
public final class CertifiedSpellValidator {

	public static final String TAG_CERTIFICATE_ID = "certificate_id";
	public static final String TAG_CERTIFIED_HASH = "certified_hash";
	public static final String TAG_CERTIFIED_DURATION = "certified_duration";
	public static final String TAG_CERTIFIED_COST = "certified_cost";

	private CertifiedSpellValidator() {
	}

	public static boolean isCertified(ItemStack stack) {
		return stack.hasTag() && stack.getTag().contains(TAG_CERTIFIED_HASH);
	}

	@Nullable
	public static String getCertifiedHash(ItemStack stack) {
		return stack.hasTag() ? stack.getTag().getString(TAG_CERTIFIED_HASH) : null;
	}

	public static int getCertifiedDuration(ItemStack stack) {
		return stack.hasTag() ? stack.getTag().getInt(TAG_CERTIFIED_DURATION) : 0;
	}

	public static long getCertifiedCost(ItemStack stack) {
		return stack.hasTag() ? stack.getTag().getLong(TAG_CERTIFIED_COST) : 0;
	}

	public static int getCertifiedCastDuration(ItemStack stack) {
		return CertificationService.rewardDurationTicks(getCertifiedDuration(stack));
	}

	public static void tagCertified(ItemStack stack, SpellCertificate certificate) {
		stack.getOrCreateTag().putString(TAG_CERTIFICATE_ID, certificate.certificateId().toString());
		stack.getOrCreateTag().putString(TAG_CERTIFIED_HASH, certificate.definitionHash());
		stack.getOrCreateTag().putInt(TAG_CERTIFIED_DURATION, certificate.certifiedDuration());
		stack.getOrCreateTag().putLong(TAG_CERTIFIED_COST, certificate.costUnits());
	}

	/**
	 * Resolves the immutable certified definition for a stack, or null when the
	 * certificate is missing, the stored definition hash mismatches, or the
	 * capability policy no longer permits certification.
	 */
	@Nullable
	public static SpellDefinition resolveCertifiedDefinition(ServerPlayer player, ItemStack stack) {
		String hash = getCertifiedHash(stack);
		if (hash == null || hash.isEmpty()) return null;
		SpellCertificate certificate = CertifiedSpellStorage.loadCertificate(player.server, hash);
		if (certificate == null) return null;
		if (certificate.certificateId() == null
				|| !hash.equals(certificate.definitionHash())
				|| certificate.certifiedDuration() <= 0
				|| !certificate.certificateId().toString().equals(stack.getTag().getString(TAG_CERTIFICATE_ID))
				|| getCertifiedDuration(stack) != certificate.certifiedDuration()
				|| getCertifiedCost(stack) != certificate.costUnits()
				|| certificate.costUnits() < 0) {
			return null;
		}
		SpellDefinition def = CertifiedSpellStorage.loadDefinition(player.server, hash);
		if (def == null) return null;
		String actual = SpellHash.canonicalHash(def);
		if (!actual.equals(hash)) return null;
		try {
			SpellAnalyzer.analyze(def, SpellAnalysisProfile.CERTIFICATION);
		} catch (IllegalArgumentException e) {
			// capability policy or hard limits now reject this definition
			return null;
		}
		return def;
	}
}
