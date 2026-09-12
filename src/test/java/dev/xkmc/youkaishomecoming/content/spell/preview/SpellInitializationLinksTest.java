package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.youkaishomecoming.content.spell.action.DelayAction;
import dev.xkmc.youkaishomecoming.content.spell.action.FreezeOnTickAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SetInvulnerableAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SetSpellHealthAction;
import dev.xkmc.youkaishomecoming.content.spell.action.ShowSpellCardAction;
import dev.xkmc.youkaishomecoming.content.spell.action.ShowSpellTitleAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellTitleStyle;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;

/** Declaration grouping and lossless toggles without a client or the Forge action registry. */
public final class SpellInitializationLinksTest {

	private static int checks;
	private static final SpellInitializationLinks.Kind TITLE = SpellInitializationLinks.Kind.TITLE;
	private static final SpellInitializationLinks.Kind CARD = SpellInitializationLinks.Kind.CARD;
	private static final SpellInitializationLinks.Kind INVULNERABILITY = SpellInitializationLinks.Kind.INVULNERABILITY;

	public static void main(String[] args) {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
		var init = new SetSpellHealthAction(SetSpellHealthAction.Mode.SET, NumberProvider.constant(600), NumberProvider.constant(1200));
		var title = new ShowSpellTitleAction("", "Declaration", 80, 96,
				SpellTitleStyle.DEFAULT.withBackground(Optional.of(new ResourceLocation("kubejs", "textures/portraits/remilia.png")))
						.withGradientDirection(SpellTitleStyle.GradientDirection.TOP_TO_BOTTOM).withBackgroundX(-32));
		var card = ShowSpellCardAction.defaults().withHand(ShowSpellCardAction.Hand.LEFT).withTimeline(8, 12, 32);
		var protection = new SetInvulnerableAction(NumberProvider.constant(100));
		var freeze = new FreezeOnTickAction(NumberProvider.constant(100));
		var barrier = new DelayAction(NumberProvider.constant(100), List.of());
		var actions = new ArrayList<SpellAction>(List.of(init, card, barrier));
		var siblings = siblings(actions);

		check("unlinked title is optional", SpellInitializationLinks.findIndex(siblings, 0, TITLE) == -1);
		check("an existing card is recognized", SpellInitializationLinks.findIndex(siblings, 0, CARD) == 1);
		actions.add(SpellInitializationLinks.insertionIndex(siblings, 0, TITLE), title);
		actions.add(SpellInitializationLinks.insertionIndex(siblings, 0, INVULNERABILITY), protection);
		check("new links precede the attack delay in declaration order", actions.equals(List.of(init, protection, title, card, barrier)));
		check("existing presentation parameters survive adding links", actions.get(2) == title && actions.get(3) == card);
		check("each link resolves independently", SpellInitializationLinks.findIndex(siblings, 0, TITLE) == 2
				&& SpellInitializationLinks.findIndex(siblings, 0, CARD) == 3
				&& SpellInitializationLinks.findIndex(siblings, 0, INVULNERABILITY) == 1);
		for (SpellAction configured : List.of(title, card, protection)) {
			SpellAction disabled = SpellInitializationLinks.withEnabled(configured, false, () -> { throw new AssertionError("must not recreate configured actions"); });
			check("turning a link off disables execution", !SpellInitializationLinks.isEnabled(disabled));
			check("turning a link off retains its configured action", SpellInitializationLinks.unwrap(disabled) == configured);
			check("a disabled link remains discoverable", SpellInitializationLinks.kindOf(disabled) == SpellInitializationLinks.kindOf(configured));
			check("turning a link back on restores the same settings", SpellInitializationLinks.withEnabled(disabled, true,
					() -> { throw new AssertionError("must not recreate configured actions"); }) == configured);
			check("turning an already disabled link off is a no-op", SpellInitializationLinks.withEnabled(disabled, false, () -> null) == disabled);
		}
		check("turning an absent link off creates nothing", SpellInitializationLinks.withEnabled(null, false,
				() -> { throw new AssertionError("must not create disabled defaults"); }) == null);
		check("turning an absent link on uses the requested defaults", SpellInitializationLinks.withEnabled(null, true, () -> protection) == protection);
		check("a delay is a declaration boundary", SpellInitializationLinks.findIndex(siblings(List.of(init, barrier, title)), 0, TITLE) == -1);
		check("a second initialization owns its own title", SpellInitializationLinks.findIndex(siblings(List.of(init, card, init, title)), 0, TITLE) == -1);
		var clear = new SetSpellHealthAction(SetSpellHealthAction.Mode.CLEAR, NumberProvider.constant(0), NumberProvider.constant(0));
		check("clearing spell health does not start a declaration", SpellInitializationLinks.findIndex(siblings(List.of(clear, title)), 0, TITLE) == -1);
		check("disabled initialization does not expose active declaration controls", !SpellInitializationLinks.isInitializer(new SpellActions.DisabledAction(init)));
		check("noncanonical existing order remains discoverable", SpellInitializationLinks.findIndex(siblings(List.of(init, card, protection, title)), 0, TITLE) == 3);
		check("freeze is linked only when immediately after invulnerability",
				InvulnerabilityFreezeLinks.findIndex(siblings(List.of(protection, freeze)), 0) == 1
						&& InvulnerabilityFreezeLinks.findIndex(siblings(List.of(protection, barrier, freeze)), 0) == -1);
		SpellAction disabledFreeze = InvulnerabilityFreezeLinks.withEnabled(freeze, false, () -> null);
		check("freeze shortcut preserves settings while disabled", !InvulnerabilityFreezeLinks.isEnabled(disabledFreeze)
				&& InvulnerabilityFreezeLinks.unwrap(disabledFreeze) == freeze);
		check("disabled freeze remains linked", InvulnerabilityFreezeLinks.findIndex(
				siblings(List.of(protection, disabledFreeze)), 0) == 1);
		checkNestedLinks(init, title, card, protection, freeze);
		System.out.println("SpellInitializationLinksTest: all " + checks + " checks passed");
	}

	private static void checkNestedLinks(SetSpellHealthAction init, ShowSpellTitleAction title,
			ShowSpellCardAction card, SetInvulnerableAction protection, FreezeOnTickAction freeze) {
		SpellAction disabledTitle = SpellInitializationLinks.withEnabled(title, false, () -> null);
		var nested = new SpellActions.RepeatAction(NumberProvider.constant(2), "i",
				List.of(init, protection, freeze, disabledTitle, card));
		var delay = new DelayAction(NumberProvider.constant(10), List.of(nested));
		var phase = new PhaseDefinition(new ResourceLocation("yh_test", "main"), List.of(delay, init, title), List.of(), List.of(), List.of(), List.of());
		var panel = new ActionListPanel((action, path) -> {}, target -> {}, () -> {}, () -> null);
		panel.setPhase(phase);
		var initPath = ActionListPanel.ActionPath.topLevel("enter", 0).child("body", 0).child("body", 0);
		check("nested title path stays in the same branch", panel.linkedInitializationPath(initPath, TITLE).equals(initPath.sibling(3)));
		check("nested title resolves with its disabled state", panel.linkedInitializationAction(initPath, TITLE) == disabledTitle);
		check("nested card resolves independently", panel.linkedInitializationAction(initPath, CARD) == card);
		check("nested invulnerability resolves independently", panel.linkedInitializationAction(initPath, INVULNERABILITY) == protection);
		check("top-level declaration remains independent", panel.linkedInitializationAction(ActionListPanel.ActionPath.topLevel("enter", 1), TITLE) == title);
		check("invalid selection has no linked declaration", panel.linkedInitializationAction(null, TITLE) == null);
		check("edit navigation selects the linked card", panel.selectPath(panel.linkedInitializationPath(initPath, CARD)) && panel.getSelectedAction() == card);
		panel.selectPath(initPath);
		check("enabling an already enabled link does not duplicate it", !panel.setLinkedInitializationAction(initPath, CARD, card));
		check("wrong action types cannot replace another option", !panel.setLinkedInitializationAction(initPath, TITLE, card));
		check("no-op edits preserve the initializer selection", panel.getSelectedPath().equals(initPath));

		var protectionPath = initPath.sibling(1);
		check("invulnerability links to the adjacent freeze sibling",
				panel.linkedInvulnerabilityFreezePath(protectionPath).equals(protectionPath.sibling(2))
						&& panel.linkedInvulnerabilityFreezeAction(protectionPath) == freeze);
		SpellAction disabledFreeze = InvulnerabilityFreezeLinks.withEnabled(freeze, false, () -> null);
		check("disabled freeze retains its duration", InvulnerabilityFreezeLinks.unwrap(
				disabledFreeze) == freeze);
		check("the shortcut restores the same freeze", InvulnerabilityFreezeLinks.withEnabled(
				disabledFreeze, true, () -> null) == freeze);
	}

	private static IntFunction<SpellAction> siblings(List<SpellAction> actions) {
		return index -> index < 0 || index >= actions.size() ? null : actions.get(index);
	}

	private static void check(String label, boolean condition) {
		if (!condition) throw new AssertionError(label);
		checks++;
	}
}
