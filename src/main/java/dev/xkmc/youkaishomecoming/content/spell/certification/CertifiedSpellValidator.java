package dev.xkmc.youkaishomecoming.content.spell.certification;

import dev.xkmc.youkaishomecoming.content.spell.analysis.SpecialNodeCounter;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysisLimits;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysisProfile;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalyzer;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCapabilityPolicies;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCapabilityPolicy;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellHash;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellHealthPlan;
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

	@Nullable
	public static String getCertificateId(ItemStack stack) {
		if (!stack.hasTag()) return null;
		String id = stack.getTag().getString(TAG_CERTIFICATE_ID);
		return id.isBlank() ? null : id;
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
		SpellHealthPlan plan = resolveCertifiedPlan(player, stack);
		return plan == null ? resolveLegacyDefinition(player, stack) : plan.rootDefinition();
	}

	@Nullable
	public static SpellHealthPlan resolveCertifiedPlan(ServerPlayer player, ItemStack stack) {
		String hash = getCertifiedHash(stack);
		if (hash == null || hash.isEmpty()) return null;
		SpellCertificate certificate = CertifiedSpellStorage.loadCertificate(player.server, hash);
		if (certificate == null) return null;
		if (certificate.healthPlanVersion() < SpellCertificate.CURRENT_HEALTH_PLAN_VERSION) return null;
		if (certificate.certificateId() == null
				|| !hash.equals(certificate.definitionHash())
				|| certificate.certifiedDuration() < 0
				|| certificate.certifiedDuration() > 1200
				|| !certificate.certificateId().toString().equals(stack.getTag().getString(TAG_CERTIFICATE_ID))
				|| getCertifiedDuration(stack) != certificate.certifiedDuration()
				|| getCertifiedCost(stack) != certificate.costUnits()
				|| certificate.costUnits() < 0) {
			return null;
		}
		SpellHealthPlan plan = certificate.healthPlanVersion() >= SpellCertificate.CURRENT_HEALTH_PLAN_VERSION
				? CertifiedSpellStorage.loadHealthPlan(player.server, hash) : null;
		if (plan == null) return null;
		SpellDefinition def = plan == null ? CertifiedSpellStorage.loadDefinition(player.server, hash) : plan.rootDefinition();
		if (def == null) return null;
		String actual = plan == null ? SpellHash.canonicalHash(def)
				: SpellHash.canonicalBundleHash(plan.definitions());
		if (!actual.equals(hash)) return null;
		if (plan != null && (plan.totalDurationTicks() != certificate.certifiedDuration()
				|| plan.totalHealth() <= 0)) return null;
		SpellAnalysisLimits configuredLimits = SpellAnalysisLimits.certification();
		SpellAnalysisLimits projectionLimits = plan != null && plan.totalDurationTicks() > 0
				? configuredLimits.withCertificationWindow(Math.min(configuredLimits.certificationWindowTicks(),
						plan.totalDurationTicks()))
				: configuredLimits;
		try {
			boolean operatorTest = certificate.operatorTest() || hasLegacyOperatorCapability(certificate);
			if (operatorTest) {
				// Derive required capabilities from the immutable definition rather than
				// trusting certificate metadata. Possession never grants OP permission.
				var capabilities = java.util.EnumSet.noneOf(dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCapability.class);
				for (SpellDefinition candidate : plan == null ? java.util.List.of(def) : plan.definitions().values()) {
					capabilities.addAll(SpellAnalyzer.analyzeOperatorTest(candidate,
							projectionLimits).requiredCapabilities());
				}
				boolean requiresOperator = capabilities.stream()
						.anyMatch(cap -> SpellCapabilityPolicies.currentPolicy(cap) == SpellCapabilityPolicy.OP_ONLY);
				if (requiresOperator && !player.hasPermissions(2)) return null;
			} else if (certificate.draftBudget() != null) {
				CertificationService.validateCertifiedPlan(plan, certificate.draftBudget());
			} else {
				if (certificate.certificationRulesVersion() >= SpellCertificate.CURRENT_RULES_VERSION) return null;
				try {
					for (SpellDefinition candidate : plan == null ? java.util.List.of(def) : plan.definitions().values()) {
						SpellAnalyzer.analyze(candidate, SpellAnalysisProfile.CERTIFICATION, projectionLimits);
					}
				} catch (IllegalArgumentException e) {
					int count = (plan == null ? java.util.List.of(def) : plan.definitions().values()).stream()
							.mapToInt(SpecialNodeCounter::count).sum();
					if (count <= 0 || count > certificate.specialNodeQuota()) return null;
					for (SpellDefinition candidate : plan == null ? java.util.List.of(def) : plan.definitions().values()) {
						SpellAnalyzer.analyze(candidate, SpellAnalysisProfile.CERTIFICATION,
								projectionLimits, SpellCapabilityPolicies.experimentalCapabilities());
					}
				}
			}
		} catch (IllegalArgumentException e) {
			// capability policy or hard limits now reject this definition
			return null;
		}
		return plan;
	}

	@Nullable
	private static SpellDefinition resolveLegacyDefinition(ServerPlayer player, ItemStack stack) {
		String hash = getCertifiedHash(stack);
		if (hash == null || hash.isEmpty()) return null;
		SpellCertificate certificate = CertifiedSpellStorage.loadCertificate(player.server, hash);
		if (certificate == null || certificate.healthPlanVersion() >= SpellCertificate.CURRENT_HEALTH_PLAN_VERSION) {
			return null;
		}
		if (certificate.certificateId() == null
				|| !hash.equals(certificate.definitionHash())
				|| certificate.certifiedDuration() < 0
				|| certificate.certifiedDuration() > 1200
				|| !certificate.certificateId().toString().equals(stack.getTag().getString(TAG_CERTIFICATE_ID))
				|| getCertifiedDuration(stack) != certificate.certifiedDuration()
				|| getCertifiedCost(stack) != certificate.costUnits()
				|| certificate.costUnits() < 0) return null;
		SpellDefinition definition = CertifiedSpellStorage.loadDefinition(player.server, hash);
		if (definition == null || !hash.equals(SpellHash.canonicalHash(definition))) return null;
		SpellAnalysisLimits projectionLimits = SpellAnalysisLimits.certification();
		try {
			boolean operatorTest = certificate.operatorTest() || hasLegacyOperatorCapability(certificate);
			if (operatorTest) {
				var analysis = SpellAnalyzer.analyzeOperatorTest(definition, SpellAnalysisLimits.certification());
				boolean requiresOperator = analysis.requiredCapabilities().stream()
						.anyMatch(cap -> SpellCapabilityPolicies.currentPolicy(cap) == SpellCapabilityPolicy.OP_ONLY);
				if (requiresOperator && !player.hasPermissions(2)) return null;
			} else if (certificate.draftBudget() != null) {
				SpellHealthPlan synthetic = SpellHealthPlan.analyze(definition, id -> null);
				CertificationService.validateCertifiedPlan(synthetic, certificate.draftBudget());
			} else {
				if (certificate.certificationRulesVersion() >= SpellCertificate.CURRENT_RULES_VERSION) return null;
				try {
					SpellAnalyzer.analyze(definition, SpellAnalysisProfile.CERTIFICATION, projectionLimits);
				} catch (IllegalArgumentException e) {
					int count = SpecialNodeCounter.count(definition);
					if (count <= 0 || count > certificate.specialNodeQuota()) return null;
					SpellAnalyzer.analyze(definition, SpellAnalysisProfile.CERTIFICATION,
									projectionLimits, SpellCapabilityPolicies.experimentalCapabilities());
				}
			}
		} catch (IllegalArgumentException e) {
			return null;
		}
		return definition;
	}

	private static boolean hasLegacyOperatorCapability(SpellCertificate certificate) {
		return !certificate.operatorTest() && hasOperatorOnlyCapability(certificate);
	}

	private static boolean hasOperatorOnlyCapability(SpellCertificate certificate) {
		if (certificate.capabilities() == null) return false;
		return certificate.capabilities().stream()
				.anyMatch(cap -> SpellCapabilityPolicies.currentPolicy(cap) == SpellCapabilityPolicy.OP_ONLY);
	}
}
