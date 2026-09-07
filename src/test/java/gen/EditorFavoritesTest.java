package gen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xkmc.youkaishomecoming.content.spell.action.ContinueSourceAction;
import dev.xkmc.youkaishomecoming.content.spell.action.DelayAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.preview.ActionEditorValueUpdatesTest;
import dev.xkmc.youkaishomecoming.content.spell.preview.ActionFavoriteStore;
import dev.xkmc.youkaishomecoming.content.spell.preview.ActionListPanel;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Narrow persistence and tree-name regressions; no client launch or action Codec bootstrap. */
public final class EditorFavoritesTest {

	private static int checks;

	public static void main(String[] args) throws Exception {
		testSnapshotsAndPersistence();
		testCollapsedSubtreeNames();
		checks += ActionEditorValueUpdatesTest.runAllTests();
		System.out.println("EditorFavoritesTest: all " + checks + " checks passed");
	}

	private static void testSnapshotsAndPersistence() throws Exception {
		Path directory = Files.createTempDirectory("yh-action-favorites-test-");
		Path config = directory.resolve("config");
		Path file = config.resolve("favorites.json");
		try {
			ActionFavoriteStore store = new ActionFavoriteStore(file);
			check("new collection is empty", store.list().isEmpty());
			JsonObject action = JsonParser.parseString("""
					{"type":"repeat","count":{"type":"caster_power"},"index_variable":"i",
					 "body":[{"type":"set_variable","key":"angle","value":7}]}
					""").getAsJsonObject();
			Map<String, String> names = new HashMap<>(Map.of("", "灵力自机狙", ":body/0", "设置角度"));
			var favorite = new ActionFavoriteStore.Favorite("灵力自机狙", action, names);
			action.addProperty("index_variable", "changed");
			names.put("", "changed");
			check("snapshot owns action content", favorite.action().get("index_variable").getAsString().equals("i"));
			check("snapshot owns custom names", favorite.customNames().get("").equals("灵力自机狙"));
			favorite.action().getAsJsonArray("body").get(0).getAsJsonObject().addProperty("value", 999);
			check("nested copies are independent", favorite.action().getAsJsonArray("body").get(0)
					.getAsJsonObject().get("value").getAsInt() == 7);

			store.save(favorite);
			var reopened = new ActionFavoriteStore(file);
			check("favorites survive reopening", reopened.list().equals(List.of(favorite)));
			check("nested names survive reopening", reopened.list().get(0).customNames().get(":body/0").equals("设置角度"));
			reopened.save(reopened.list().get(0));
			check("identical snapshots are not duplicated", reopened.list().size() == 1);

			JsonObject edited = favorite.action();
			edited.getAsJsonArray("body").get(0).getAsJsonObject().addProperty("value", 21);
			var variant = new ActionFavoriteStore.Favorite(favorite.name(), edited, favorite.customNames());
			reopened.save(variant);
			check("distinct configurations remain distinct", reopened.list().equals(List.of(variant, favorite)));
			reopened.remove(variant);
			check("removing one variant preserves the other", reopened.list().equals(List.of(favorite)));
			reopened.remove(variant);
			check("removing an absent favorite is harmless", reopened.list().equals(List.of(favorite)));

			Files.writeString(file, "[{\"name\":\"legacy\",\"action\":{\"type\":\"continue_source\"}}]");
			check("optional name metadata defaults to empty", store.list().get(0).customNames().isEmpty());
			String damaged = "[{\"name\":\"unfinished";
			Files.writeString(file, damaged);
			expectIOException("damaged favorites are reported", store::list);
			expectIOException("saving refuses a damaged collection", () -> store.save(favorite));
			check("failed save preserves original bytes", Files.readString(file).equals(damaged));

			Files.delete(file);
			Files.createDirectory(file);
			expectIOException("storage errors reach the caller", () -> store.save(favorite));
		} finally {
			// Delete only the exact fixture paths, never a recursively computed tree.
			Files.deleteIfExists(file);
			Files.deleteIfExists(config);
			Files.deleteIfExists(directory);
		}
	}

	private static void testCollapsedSubtreeNames() {
		var leaf = new ContinueSourceAction();
		var delay = new DelayAction(NumberProvider.constant(3), List.of(leaf));
		var repeat = new SpellActions.RepeatAction(NumberProvider.constant(2), "i", List.of(delay));
		PhaseDefinition phase = new PhaseDefinition(new ResourceLocation("dev", "favorites/main"),
				List.of(), List.of(repeat, new ContinueSourceAction()), List.of(), List.of(), List.of());
		ActionListPanel panel = new ActionListPanel((action, path) -> {}, target -> {}, () -> {}, () -> null);
		panel.setPhase(phase);
		Map<String, String> original = Map.of("tick/0", "组合", "tick/0:body/0", "延迟",
				"tick/0:body/0:body/0", "子动作", "tick/1", "旁支", "other/tick/0", "其他阶段");
		panel.loadCustomNames(original);
		panel.collapseAll();
		check("saving retains names in collapsed branches", panel.getCustomNames().equals(original));
		var root = ActionListPanel.ActionPath.topLevel("tick", 0);
		panel.selectPath(root);
		check("favorite captures the complete named subtree", panel.getSelectedActionNames().equals(
				Map.of("", "组合", ":body/0", "延迟", ":body/0:body/0", "子动作")));
		panel.selectPath(root.child("body", 0));
		check("nested favorites use relative paths", panel.getSelectedActionNames().equals(
				Map.of("", "延迟", ":body/0", "子动作")));

		// Reorder without Codec snapshots: name ownership must follow the actual nodes,
		// including invisible descendants and unrelated phase metadata.
		java.util.Collections.swap(phase.onTick, 0, 1);
		panel.markDirty();
		Map<String, String> moved = panel.getCustomNames();
		check("root name follows reorder", "组合".equals(moved.get("tick/1")));
		check("collapsed child name follows reorder", "子动作".equals(moved.get("tick/1:body/0:body/0")));
		check("obsolete child path is removed", !moved.containsKey("tick/0:body/0:body/0"));
		check("other phase metadata is preserved", "其他阶段".equals(moved.get("other/tick/0")));
		panel.selectPath(ActionListPanel.ActionPath.topLevel("tick", 1));
		check("relative favorite names survive relocation", panel.getSelectedActionNames().equals(
				Map.of("", "组合", ":body/0", "延迟", ":body/0:body/0", "子动作")));
	}

	private static void check(String label, boolean condition) {
		if (!condition) throw new AssertionError(label);
		checks++;
	}

	private static void expectIOException(String label, IoAction action) throws Exception {
		try {
			action.run();
			throw new AssertionError(label);
		} catch (IOException expected) {
			checks++;
		}
	}

	@FunctionalInterface
	private interface IoAction {
		void run() throws Exception;
	}
}
