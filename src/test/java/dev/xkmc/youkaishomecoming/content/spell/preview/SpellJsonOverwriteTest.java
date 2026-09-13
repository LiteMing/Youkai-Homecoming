package dev.xkmc.youkaishomecoming.content.spell.preview;

import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDisplay;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellItemForm;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyProfile;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Selection/content decisions only; does not claim to drive a real ConfirmScreen. */
public final class SpellJsonOverwriteTest {
	private static int checks;
	public static void main(String[] args) {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
		var a = definition("a", "original A");
		var b = definition("b", "original B");
		SpellRegistry.register(a);
		SpellRegistry.register(b);
		try {
			var editor = new SpellEditorController(a, false, null, () -> {});
			check("selected ID changes without repeated confirmation", !editor.needsJsonOverwriteConfirmation(definition("a", "modified A")));
			check("new IDs do not overwrite a registered spell", !editor.needsJsonOverwriteConfirmation(definition("new", "new spell")));
			check("another existing ID requires confirmation when changed", editor.needsJsonOverwriteConfirmation(definition("b", "modified B")));
			var roundTrip = SpellDefinition.CODEC.parse(JsonOps.INSTANCE, SpellDefinition.CODEC.encodeStart(JsonOps.INSTANCE, b).getOrThrow(false, s -> {})).getOrThrow(false, s -> {});
			check("importing identical content does not prompt", !editor.needsJsonOverwriteConfirmation(roundTrip));
			check("the decision leaves the registered definition intact", SpellRegistry.get(b.id) == b);
			editor.setDefinition(definition("b", "accepted B"));
			check("accepted target becomes the selected ID and stops prompting", !editor.needsJsonOverwriteConfirmation(definition("b", "next edit")));
			var draft = new SpellEditorController(SpellEditorController.createDraftDefinition(), true, null, () -> {});
			check("an empty workspace asks before changing an existing ID", draft.needsJsonOverwriteConfirmation(definition("a", "replaced A")));
			check("an empty workspace accepts an unused ID", !draft.needsJsonOverwriteConfirmation(definition("new", "new spell")));
		} finally {
			SpellRegistry.remove(a.id);
			SpellRegistry.remove(b.id);
		}
		System.out.println("SpellJsonOverwriteTest: " + checks + " checks passed");
	}

	private static SpellDefinition definition(String path, String title) {
		var id = new ResourceLocation("test", path);
		var phaseId = new ResourceLocation("test", path + "/main");
		return new SpellDefinition(id, new SpellDisplay(title, "", Optional.empty(), Optional.empty()), SpellItemForm.NONE,
				phaseId, Map.of(phaseId, new PhaseDefinition(phaseId, List.of(), List.of(), List.of(), List.of(), List.of())), DifficultyProfile.DEFAULT);
	}
	private static void check(String label, boolean pass) { if (!pass) throw new AssertionError(label); checks++; }
}
