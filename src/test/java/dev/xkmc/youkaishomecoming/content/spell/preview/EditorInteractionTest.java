package dev.xkmc.youkaishomecoming.content.spell.preview;

import com.google.gson.JsonParser;
import dev.xkmc.youkaishomecoming.content.spell.action.FreezeOnTickAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SetInvulnerableAction;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.MultiPackResourceManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/** Headless checks for interaction refresh, the actual editor catalog and bilingual name coverage. */
public final class EditorInteractionTest {

	private static int checks;
	private record RefreshModel(NumberProvider duration, boolean frozen) {}

	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		refreshInteractions();
		actionNames();
		System.out.println("EditorInteractionTest: " + checks + " checks passed");
	}

	private static void refreshInteractions() {
		var model = new AtomicReference<>(new RefreshModel(NumberProvider.constant(100), false));
		var displayed = new AtomicReference<RefreshModel>();
		var press = new AtomicReference<Runnable>();
		var controller = new AtomicReference<EditorViewRefresh>();
		var builds = new AtomicInteger();
		var refresh = new EditorViewRefresh(() -> {
			builds.incrementAndGet();
			var value = model.get();
			displayed.set(value);
			// A rendered button captures its displayed boolean, while the edit preserves the latest duration.
			press.set(() -> controller.get().interact(() -> model.set(
					new RefreshModel(model.get().duration(), !value.frozen()))));
		});
		controller.set(refresh);
		refresh.request();
		refresh.flush();
		press.get().run();
		check("widgets survive until event dispatch has finished", !displayed.get().frozen());
		refresh.flush();
		check("freeze button updates its displayed state", displayed.get().frozen());
		press.get().run();
		refresh.flush();
		check("second press can turn freeze off", !displayed.get().frozen());

		int beforeTyping = builds.get();
		model.set(new RefreshModel(NumberProvider.constant(240), false));
		check("live value edits retain the current widgets", builds.get() == beforeTyping);
		press.get().run();
		refresh.flush();
		check("button uses the latest typed duration", model.get().duration().equals(NumberProvider.constant(240)));
		check("button refresh includes live edits", displayed.get().equals(model.get()));

		int beforeInteraction = builds.get();
		refresh.interact(() -> {
			model.set(new RefreshModel(NumberProvider.constant(50), false));
			refresh.request();
			refresh.request();
			refresh.interact(() -> model.set(new RefreshModel(NumberProvider.constant(60), true)));
			check("nested callbacks do not rebuild intermediate models", builds.get() == beforeInteraction);
		});
		refresh.flush();
		check("multiple refresh requests produce one rebuild", builds.get() == beforeInteraction + 1);
		check("the final linked value is rendered", displayed.get().equals(model.get()));

		try {
			refresh.interact(() -> {
				model.set(new RefreshModel(NumberProvider.constant(0), false));
				throw new IllegalArgumentException("input rejected");
			});
			throw new AssertionError("expected rejected input");
		} catch (IllegalArgumentException expected) {
			refresh.flush();
			check("an interrupted interaction cannot leave an old view", displayed.get().equals(model.get()));
		}
		press.get().run();
		refresh.flush();
		check("refresh continues after rejected input", displayed.get().frozen());
	}

	private static void actionNames() throws Exception {
		Path source = Path.of("src/main/resources");
		Path chineseSource = Path.of("src/test/resources/youkaishomecoming/lang/zh_cn/spell_editor_actions.json");
		var english = JsonParser.parseString(Files.readString(source.resolve("assets/youkaishomecoming/lang/en_us.json"))).getAsJsonObject();
		var chinese = JsonParser.parseString(Files.readString(chineseSource)).getAsJsonObject();
		Path overlay = Files.createTempDirectory(Path.of("build"), "editor-language-check-");
		Path chineseResource = overlay.resolve("assets/youkaishomecoming/lang/zh_cn.json");
		Files.createDirectories(chineseResource.getParent());
		Files.copy(chineseSource, chineseResource);

		var mode = SpellEditorLocalization.class.getDeclaredField("chineseOverride");
		mode.setAccessible(true);
		Object previousMode = mode.get(null);
		// Initializing the Codec registry requires Forge mod loading. Enumerate its registrations without booting a game.
		var registered = new LinkedHashSet<String>();
		var registrations = Pattern.compile("register\\(\"([^\"]+)\"").matcher(Files.readString(
				Path.of("src/main/java/dev/xkmc/youkaishomecoming/content/spell/action/SpellActions.java")));
		while (registrations.find()) registered.add(registrations.group(1));
		check("action registry coverage is nonempty", !registered.isEmpty());
		var groups = ActionEditorPanel.class.getDeclaredField("TYPE_GROUPS");
		groups.setAccessible(true);
		try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(
				new PathPackResources("mod", source, false), new PathPackResources("source translations", overlay, false)))) {
			SpellEditorLocalization.onResourceReload(resources);
			for (boolean zh : new boolean[]{false, true}) {
				mode.set(null, zh);
				var strings = zh ? chinese : english;
				for (String type : registered) {
					String key = SpellEditorLocalization.actionNameKey(type);
					check("registered action has " + (zh ? "Chinese" : "English") + " name: " + type,
							strings.has(key) && strings.get(key).getAsString().equals(SpellEditorLocalization.actionName(type)));
				}
				for (Object group : (List<?>) groups.get(null)) {
					var types = group.getClass().getDeclaredMethod("types");
					types.setAccessible(true);
					for (Object id : (List<?>) types.invoke(group)) {
						String type = (String) id;
						String name = SpellEditorLocalization.actionName(type);
						check("menu action is registered: " + type, registered.contains(type));
						check("menu action uses the language catalog: " + type,
								strings.get(SpellEditorLocalization.actionNameKey(type)).getAsString().equals(name));
					}
				}
			}
			mode.set(null, true);
			check("show card is Chinese", SpellEditorLocalization.actionName("show_spell_card").equals("展示符卡物品"));
			SpellEditorLocalization.toggle();
			check("editor language toggle changes action names", SpellEditorLocalization.actionName("show_spell_card").equals("Show Spell Card"));
			SpellEditorLocalization.toggle();
			check("toggling back restores Chinese names", SpellEditorLocalization.actionName("show_spell_card").equals("展示符卡物品"));
			check("invulnerability is marked operator-only", SpellEditorNodeLabels.actionMarker(
					new SetInvulnerableAction(NumberProvider.constant(100))).equals("[OP] "));
			check("onTick freeze is marked operator-only", SpellEditorNodeLabels.actionMarker(
					new FreezeOnTickAction(NumberProvider.constant(100))).equals("[OP] "));
			var override = chinese.deepCopy();
			override.addProperty(SpellEditorLocalization.actionNameKey("show_spell_card"), "资源包符卡展示");
			Files.writeString(chineseResource, override.toString());
			SpellEditorLocalization.onResourceReload(resources);
			check("resource reload replaces a cached action name", SpellEditorLocalization.actionName("show_spell_card").equals("资源包符卡展示"));
			check("partial overrides retain other action names", SpellEditorLocalization.actionName("fire_danmaku").equals("发射弹幕"));
		} finally {
			mode.set(null, previousMode);
		}
	}

	private static void check(String name, boolean result) {
		if (!result) throw new AssertionError(name);
		checks++;
	}
}
