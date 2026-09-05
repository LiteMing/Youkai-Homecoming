package dev.xkmc.youkaishomecoming.compat.ysm;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Standalone state/catalog/restore contracts. Optional OYSM jar checks do not launch Minecraft. */
public final class ModelPresentationTest {

	private static int passed;
	private static final YsmPresentationState.Source SOURCE = YsmPresentationState.Source.COMMAND;

	public static void main(String[] args) throws Exception {
		stateContracts();
		serializationContracts();
		profileContracts();
		signalContracts();
		compositionContracts();
		acknowledgementContracts();
		catalogContracts();
		overlayContracts();
		if (Boolean.getBoolean("yh.test.oysm")) installedOysmContracts();
		else {
			try {
				Class.forName("com.elfmcys.yesstevemodel.geckolib3.core.molang.storage.VariableStorage", false, ModelPresentationTest.class.getClassLoader());
				throw new AssertionError("No-OYSM test accidentally includes an OYSM jar");
			} catch (ClassNotFoundException expected) { passed++; }
		}
		System.out.println("ModelPresentationTest: " + passed + " contracts passed" +
				(Boolean.getBoolean("yh.test.oysm") ? " (includes installed OYSM numeric-storage bridge)" : " (without OYSM)"));
	}

	private static void stateContracts() {
		var played = YsmPresentationState.EMPTY.play("extra5", 100, 40, SOURCE);
		equal("sequence starts at one", played.sequence(), 1L);
		check("active at start", played.animation().active(100));
		check("active before boundary", played.animation().active(139));
		check("inactive at boundary", !played.animation().active(140));
		check("no new snapshot when nothing expired", played.expire(139) == played);
		equal("late tracker gets remaining duration", YsmPresentationState.remaining(played.animation().expiresAt(), 125), 15L);
		check("late tracker cannot replay expired request", played.expire(140).animation() == null);
		var replayed = played.play("extra5", 110, 40, SOURCE);
		equal("same animation is a new request", replayed.sequence(), 2L);
		equal("replay gets new start", replayed.animation().startedAt(), 110L);
		equal("stop retains sequence", replayed.stop().sequence(), replayed.sequence());
		equal("next play after stop is distinct", replayed.stop().play("extra5", 111, 40, SOURCE).sequence(), 3L);
		var base = played.setParameter("variable.roaming.mouth", 2, 100, 10, SOURCE, 2)
				.setParameter("v.ysmemoji", 1, 100, 0, SOURCE, 2);
		equal("parameters do not restart animation", base.sequence(), played.sequence());
		check("canonical variable alias", base.parameters().containsKey("v.roaming.mouth"));
		var replaced = base.setParameter("v.roaming.mouth", 3, 105, 20, SOURCE, 2).expire(110);
		equal("old expiry cannot remove newer override", replaced.parameters().get("v.roaming.mouth").value(), 3f);
		check("own deadline expires parameter", !replaced.expire(125).parameters().containsKey("v.roaming.mouth"));
		check("unrelated permanent parameter survives", replaced.expire(10000).parameters().containsKey("v.ysmemoji"));
		equal("clear parameter keeps body animation", base.clearParameter("v.roaming.mouth").animation(), base.animation());
		equal("stop body keeps parameters", base.stop().parameters(), base.parameters());
		check("preview rewind drops future requests", base.expire(99).animation() == null && base.expire(99).parameters().isEmpty());
		reject("parameter limit", () -> base.setParameter("v.third", 0, 105, 1, SOURCE, 2));
		equal("expired slots can be reused", base.setParameter("v.third", 0, 110, 1, SOURCE, 2).parameters().size(), 2);
		reject("NaN", () -> base.setParameter("v.bad", Float.NaN, 100, 1, SOURCE, 3));
		reject("infinite", () -> base.setParameter("v.bad", Float.POSITIVE_INFINITY, 100, 1, SOURCE, 3));
		reject("finite but outside numeric wire bound", () -> base.setParameter("v.bad", Float.MAX_VALUE, 100, 1, SOURCE, 3));
		reject("negative duration", () -> YsmPresentationState.EMPTY.play("extra5", 0, -1, SOURCE));
		reject("negative clock", () -> YsmPresentationState.EMPTY.play("extra5", -1, 1, SOURCE));
		reject("immutable map", () -> base.parameters().clear());
		for (String input : List.of("v.a=2;", "v.a;v.b", "q.health", "v.roaming", "v.roaming.face.deep", "v.a[0]", "v.x()")) {
			reject("invalid variable " + input, () -> YsmPresentationState.normalizeParameter(input));
		}
		for (String input : List.of("special=extra5", "extra5+idle", "extra5 idle", "extra5;foo", "", "x".repeat(129))) {
			reject("invalid clip " + input, () -> YsmPresentationState.normalizeClip(input));
		}
		equal("case-sensitive clip kept", YsmPresentationState.normalizeClip("CustomAction"), "CustomAction");
		equal("case-insensitive variable canonicalized", YsmPresentationState.normalizeParameter("VARIABLE.Roaming.Mouth"), "v.roaming.mouth");
	}

	private static void serializationContracts() {
		var state = YsmPresentationState.EMPTY.play("extra5", 9000000000L, 100, SOURCE)
				.setParameter("v.roaming.mouth", 2.5f, 9000000000L, 0, YsmPresentationState.Source.SCRIPT, 4);
		CompoundTag tag = state.toTag();
		equal("round trip", YsmPresentationState.fromTag(tag), state);
		var cache = new YsmPresentationState.Cache();
		var cached = cache.read(tag);
		check("reference cache reused", cache.read(tag) == cached);
		check("replacement tag decoded", cache.read(tag.copy()) != cached);
		equal("empty decode", YsmPresentationState.fromTag(new CompoundTag()), YsmPresentationState.EMPTY);
		CompoundTag malformed = state.toTag();
		malformed.getCompound("animation").putString("clip", "special=bad");
		ListTag parameters = malformed.getList("parameters", 10);
		parameters.getCompound(0).putFloat("value", Float.NaN);
		var decoded = YsmPresentationState.fromTag(malformed);
		check("malformed entries dropped", decoded.animation() == null && decoded.parameters().isEmpty());
		equal("malformed entry does not reset sequence", decoded.sequence(), state.sequence());
		ListTag oversized = new ListTag();
		for (int i = 0; i < 140; i++) {
			CompoundTag entry = new CompoundTag();
			entry.putString("name", "v.test" + i);
			entry.putFloat("value", i);
			entry.putString("source", "COMMAND");
			oversized.add(entry);
		}
		CompoundTag bounded = new CompoundTag();
		bounded.put("parameters", oversized);
		equal("wire decode bounded", YsmPresentationState.fromTag(bounded).parameters().size(), YsmPresentationState.WIRE_MAX_PARAMETERS);
	}

	private static void catalogContracts() {
		String variable = "v.roaming.mouth";
		var smile = YsmModelCatalog.choice(variable, "Smile", "v.roaming.mouth=2;");
		equal("simple declared assignment recognized", smile.numericValue(), 2f);
		check("multiple statements not executable", YsmModelCatalog.choice(variable, "Complex", "v.roaming.mouth=2;v.x=1;").numericValue() == null);
		check("other variable not substituted", YsmModelCatalog.choice(variable, "Wrong", "v.ysmemoji=2;").numericValue() == null);
		check("functions not executed", YsmModelCatalog.choice(variable, "Script", "v.roaming.mouth=math.random(0,2)").numericValue() == null);
		var menu = YsmModelCatalog.wheelEntry("", "extra1", "#Face", List.of("extra5"), Map.of("Face", new Object()));
		check("configuration menu is not a clip", !menu.clipAvailable() && menu.configGroup().equals("Face"));
		var both = YsmModelCatalog.wheelEntry("", "extra1", "#Face", List.of("extra1"), Map.of("Face", new Object()));
		check("combined clip/config entry preserves both", both.clipAvailable() && !both.configGroup().isEmpty());
		var submenu = YsmModelCatalog.wheelEntry("", "#Actions", "Actions", List.of("#Actions"), Map.of());
		check("submenu not falsely playable", !submenu.clipAvailable() && submenu.submenu().equals("Actions"));
		var radio = new YsmModelCatalog.Control("face_id", "Face", "Mouth", "", "radio", variable, variable, 0, 1, 1, List.of(smile));
		check("native group id and label remain distinct", radio.group().equals("face_id") && radio.groupLabel().equals("Face"));
		check("radio allows declared value", radio.accepts(2));
		check("radio rejects undeclared value", !radio.accepts(3));
		var range = new YsmModelCatalog.Control("face_id", "Face", "Eyes", "", "range", "v.eyes", "v.eyes", -100, 50, 1, List.of());
		check("range includes endpoints", range.accepts(-100) && range.accepts(50));
		check("range rejects out-of-range values", !range.accepts(51));
		var catalog = new YsmModelCatalog(YsmModelCatalog.Status.READY, "", List.of("extra5"), List.of(menu), List.of(radio));
		check("known parameter validated", !catalog.accepts(variable, 3));
		check("raw numeric variables remain usable", catalog.accepts("v.ysmemoji", 1));
	}

	private static YsmModelProfile exampleProfile() {
		return new YsmModelProfile("test/model", Map.of(
				"idle", new YsmModelProfile.Preset("Daily", "idle", 20, Map.of("v.face", 1f, "v.other", 8f)),
				"walk", new YsmModelProfile.Preset("Walk", "walk", 20, Map.of("v.face", 2f)),
				"combat", new YsmModelProfile.Preset("Combat", "extra5", 40, Map.of("v.face", 3f)),
				"hurt", new YsmModelProfile.Preset("Hurt", "attacked", 10, Map.of("v.face", 4f)),
				"down", new YsmModelProfile.Preset("Down", "beaten_prone", 5, Map.of("v.face", 5f)),
				"face", new YsmModelProfile.Preset("Face only", "", 10, Map.of("v.face", 6f))),
				Map.of(YsmModelProfile.Trigger.IDLE, "idle", YsmModelProfile.Trigger.WALK, "walk", YsmModelProfile.Trigger.ENTER_COMBAT, "combat",
						YsmModelProfile.Trigger.HURT, "hurt", YsmModelProfile.Trigger.PRONE, "down"));
	}

	private static void profileContracts() {
		var profile = exampleProfile();
		equal("profile JSON round trip", YsmModelProfile.fromJson(profile.toJson()), profile);
		equal("empty profile round trip", YsmModelProfile.fromJson(YsmModelProfile.empty("YH内置/remilia").toJson()), YsmModelProfile.empty("YH内置/remilia"));
		reject("profile immutable", () -> profile.presets().clear());
		reject("wrong profile format", () -> YsmModelProfile.fromJson(profile.toJson().replace("\"format\": 1", "\"format\": 2")));
		reject("fractional duration rejected", () -> YsmModelProfile.fromJson(profile.toJson().replace("\"ticks\": 20", "\"ticks\": 1.5")));
		reject("unknown trigger rejected", () -> YsmModelProfile.fromJson(profile.toJson().replace("\"enter_combat\"", "\"combat_typo\"")));
		reject("unknown profile field", () -> YsmModelProfile.fromJson(profile.toJson().replace("\"format\": 1", "\"format\": 1, \"provider\": \"oysm\"")));
		reject("unknown preset target rejected", () -> new YsmModelProfile(profile.model(), profile.presets(), Map.of(YsmModelProfile.Trigger.IDLE, "absent")));
		reject("infinite event rejected", () -> new YsmModelProfile("test/model", Map.of("never", new YsmModelProfile.Preset("", "extra1", 0, Map.of())), Map.of(YsmModelProfile.Trigger.HURT, "never")));
		reject("preset expression rejected", () -> new YsmModelProfile.Preset("", "special=extra5", 20, Map.of()));
		reject("unsafe numeric path rejected", () -> new YsmModelProfile.Preset("", "", 20, Map.of("v.x[0]", 1f)));
		reject("non-finite preset rejected", () -> new YsmModelProfile.Preset("", "", 20, Map.of("v.x", Float.NaN)));
		reject("duplicate normalized parameter rejected", () -> new YsmModelProfile.Preset("", "", 20, Map.of("v.x", 1f, "variable.x", 2f)));
		reject("oversized JSON rejected", () -> YsmModelProfile.fromJson(" ".repeat(YsmModelProfile.MAX_JSON_LENGTH + 1)));
		var data = new YsmProfileData();
		equal("unregistered profile revision", data.entry(profile.model()).revision(), 0L);
		data.replace(profile, 0, 2);
		equal("saved revision increments", data.entry(profile.model()).revision(), 1L);
		reject("stale editor revision rejected", () -> data.replace(YsmModelProfile.empty(profile.model()), 0, 2));
		equal("conflict preserves server data", data.entry(profile.model()).profile(), profile);
		var other = YsmModelProfile.empty("another/model");
		data.replace(other, 0, 2);
		equal("independent model has independent revision", data.entry(other.model()).revision(), 1L);
		reject("profile capacity enforced", () -> data.replace(YsmModelProfile.empty("third/model"), 0, 2));
		data.replace(profile, 1, 1);
		equal("lower capacity still allows existing profile update", data.entry(profile.model()).revision(), 2L);
		var loaded = YsmProfileData.load(data.save(new CompoundTag()));
		equal("SavedData round trip with revisions", loaded.entries(), data.entries());
		var bindings = new YsmOverrideData();
		bindings.setEntity(new java.util.UUID(0, 1), YSMCompatConfig.RenderBinding.enabled("test/model", "default"));
		equal("binding revision increments", bindings.revision(), 1L);
		var savedBindings = YsmOverrideData.load(bindings.save(new CompoundTag()));
		equal("binding revision persisted", savedBindings.revision(), 1L);
		equal("binding data remains compatible", savedBindings.getEntityOverrides(), bindings.getEntityOverrides());
		var scoped = YsmPresentationState.EMPTY.applyPreset(profile.model(), profile.presets().get("hurt"), 100, 40, SOURCE, 32);
		equal("preset snapshot round trip", YsmPresentationState.fromTag(scoped.toTag()), scoped);
		check("preset body scoped to model", scoped.animation().matchesModel(profile.model()) && !scoped.animation().matchesModel("other"));
		check("preset parameter scoped to model", !scoped.parameters().get("v.face").matchesModel("other"));
		var face = scoped.applyPreset(profile.model(), profile.presets().get("face"), 110, 10, SOURCE, 32);
		equal("expression-only preset does not restart body", face.sequence(), scoped.sequence());
		equal("expression-only preset preserves body", face.animation(), scoped.animation());
		var restored = face.expire(120);
		check("expression expires independently from body", restored.parameters().isEmpty() && restored.animation() != null);
		reject("atomic preset parameter bound", () -> scoped.applyPreset(profile.model(), new YsmModelProfile.Preset("", "extra5", 20, Map.of("v.new", 1f)), 110, 20, SOURCE, 1));
		equal("failed preset leaves original body", scoped.animation().clip(), "attacked");
	}

	private static void signalContracts() {
		var empty = YsmPresentationSignals.EMPTY;
		check("unchanged daily state sends no new snapshot", empty.advance(YsmModelProfile.Trigger.IDLE, false, 100) == empty);
		var combat = empty.advance(YsmModelProfile.Trigger.WALK, true, 100);
		equal("combat starts on server edge", combat.event(YsmModelProfile.Trigger.ENTER_COMBAT).at(), 100L);
		check("holding combat does not restart event", combat.advance(YsmModelProfile.Trigger.WALK, true, 120) == combat);
		var fly = combat.advance(YsmModelProfile.Trigger.FLY, true, 120);
		equal("movement transition leaves combat clock", fly.event(YsmModelProfile.Trigger.ENTER_COMBAT).at(), 100L);
		equal("movement transition increments state sequence", fly.stateSequence(), 2L);
		var again = fly.advance(YsmModelProfile.Trigger.FLY, false, 130).advance(YsmModelProfile.Trigger.FLY, true, 140);
		equal("re-enter combat is a new event", again.event(YsmModelProfile.Trigger.ENTER_COMBAT).sequence(), 2L);
		var hurt = again.hurt(141).hurt(141);
		equal("same-tick hits each have a sequence", hurt.event(YsmModelProfile.Trigger.HURT).sequence(), 2L);
		equal("signal serialization", YsmPresentationSignals.fromTag(hurt.toTag()), hurt);
		equal("missing signals degrade to idle", YsmPresentationSignals.fromTag(new CompoundTag()), empty);
		reject("event cannot be persistent state", () -> empty.advance(YsmModelProfile.Trigger.HURT, false, 100));
		var cache = new YsmPresentationSignals.Cache();
		var tag = hurt.toTag();
		check("signal decode cache reused", cache.read(tag) == cache.read(tag));
	}

	private static void acknowledgementContracts() {
		YsmClientProfiles.clear();
		var first = new YsmProfileData.Entry(1, exampleProfile());
		var later = new YsmProfileData.Entry(2, YsmModelProfile.empty(first.profile().model()));
		YsmClientProfiles.receive(new YsmProfileSyncToClient(first, "save-a", true, "saved"));
		YsmClientProfiles.receive(new YsmProfileSyncToClient(later, "", true, ""));
		var response = YsmClientProfiles.takeResponse("save-a");
		equal("late broadcast updates runtime cache", YsmClientProfiles.entry(first.profile().model()).revision(), 2L);
		equal("save acknowledgement retains own revision", response.revision(), 1L);
		equal("save acknowledgement retains own snapshot", YsmModelProfile.fromJson(response.json()), first.profile());
		YsmClientProfiles.response("bind-a", true, "saved", 3, "");
		YsmClientProfiles.response("bind-b", true, "saved", 4, "");
		equal("binding acknowledgements stay correlated", YsmClientProfiles.takeResponse("bind-a").revision(), 3L);
		check("acknowledgement consumed once", YsmClientProfiles.takeResponse("save-a") == null);
		YsmClientProfiles.clear();
	}

	private static void compositionContracts() {
		var profile = exampleProfile();
		var empty = YsmPresentationState.EMPTY;
		var idle = YsmPresentationSignals.EMPTY;
		var result = YsmPresentationResolver.resolve(profile.model(), profile, idle, empty, 1000);
		equal("idle ignores finite preset duration", result.body().clip(), "idle");
		var combat = idle.advance(YsmModelProfile.Trigger.IDLE, true, 100);
		result = YsmPresentationResolver.resolve(profile.model(), profile, combat, empty, 110);
		equal("combat above daily", result.body().clip(), "extra5");
		equal("per-parameter merge retains unrelated daily value", result.parameters().get("v.other"), 8f);
		String replay = result.body().replayKey();
		equal("holding event does not replay per frame", YsmPresentationResolver.resolve(profile.model(), profile, combat, empty, 111).body().replayKey(), replay);
		equal("event ends at its deadline", YsmPresentationResolver.resolve(profile.model(), profile, combat, empty, 140).body().clip(), "idle");
		var hurt = combat.hurt(110);
		result = YsmPresentationResolver.resolve(profile.model(), profile, hurt, empty, 112);
		equal("hurt takes priority over combat", result.body().clip(), "attacked");
		check("accepted hit restarts same clip", !result.body().replayKey().equals(YsmPresentationResolver.resolve(profile.model(), profile, hurt.hurt(110), empty, 112).body().replayKey()));
		equal("hurt expiry restores still-active combat", YsmPresentationResolver.resolve(profile.model(), profile, hurt, empty, 120).body().clip(), "extra5");
		var manual = empty.play("manual", 111, 50, SOURCE).setParameter("v.face", 9, 111, 50, SOURCE, 32).setParameter("v.explicit", 7, 111, 50, SOURCE, 32);
		result = YsmPresentationResolver.resolve(profile.model(), profile, hurt, manual, 112);
		equal("manual body above events", result.body().clip(), "manual");
		equal("manual parameters above events", result.parameters().get("v.face"), 9f);
		var prone = hurt.advance(YsmModelProfile.Trigger.PRONE, false, 113);
		result = YsmPresentationResolver.resolve(profile.model(), profile, prone, manual, 119);
		equal("beaten mapping above manual body", result.body().clip(), "beaten_prone");
		equal("beaten mapping above conflicting manual parameter", result.parameters().get("v.face"), 5f);
		equal("unrelated explicit parameter still applies while beaten", result.parameters().get("v.explicit"), 7f);
		check("unrelated daily parameter stops while beaten", !result.parameters().containsKey("v.other"));
		equal("prone remains long after preset ticks", YsmPresentationResolver.resolve(profile.model(), profile, prone, manual, 10000).body().clip(), "beaten_prone");
		var defeat = prone.advance(YsmModelProfile.Trigger.DEFEAT, false, 114);
		result = YsmPresentationResolver.resolve(profile.model(), profile, defeat, manual, 119);
		check("missing beaten mapping reserves fallback, never manual body", result.beaten() && result.body() == null);
		var heal = prone.advance(YsmModelProfile.Trigger.IDLE, false, 120);
		equal("healing restores active manual body", YsmPresentationResolver.resolve(profile.model(), profile, heal, manual, 120).body().clip(), "manual");
		equal("manual expiry restores current daily state", YsmPresentationResolver.resolve(profile.model(), profile, heal, manual, 161).body().clip(), "idle");
		var scoped = empty.applyPreset(profile.model(), profile.presets().get("hurt"), 100, 30, SOURCE, 32);
		result = YsmPresentationResolver.resolve("different/model", profile, idle, scoped, 101);
		check("model switch suppresses scoped presets and profile", result.body() == null && result.parameters().isEmpty());
		equal("raw clip follows current model", YsmPresentationResolver.resolve("different/model", profile, idle, manual, 112).body().clip(), "manual");
		var faceOnly = new YsmModelProfile(profile.model(), profile.presets(), Map.of(YsmModelProfile.Trigger.IDLE, "idle", YsmModelProfile.Trigger.HURT, "face"));
		result = YsmPresentationResolver.resolve(profile.model(), faceOnly, hurt, empty, 112);
		equal("event expression-only preset retains daily body", result.body().clip(), "idle");
		equal("event expression-only preset overrides parameter", result.parameters().get("v.face"), 6f);
		check("rewind does not replay a future event", !YsmPresentationResolver.resolve(profile.model(), profile, hurt, empty, 99).body().clip().equals("attacked"));
	}

	private static void overlayContracts() throws Exception {
		Slot value = new Slot(7f);
		try (var overlay = new YsmParameterOverlay()) {
			check("numeric overlay applies", overlay.apply(value, 2));
			equal("overlay input visible", value.value, 2f);
		}
		equal("original nonzero value restored", value.value, 7f);
		try (var overlay = new YsmParameterOverlay()) {
			overlay.apply(value, 2);
			value.value = 9f;
		}
		equal("model's newer input survives cleanup", value.value, 9f);
		Slot unset = new Slot(null);
		try (var overlay = new YsmParameterOverlay()) { overlay.apply(unset, 1); }
		check("absent input is restored as absent, not zero", unset.value == null);
		Object struct = new Object();
		Slot nonNumeric = new Slot(struct);
		try (var overlay = new YsmParameterOverlay()) { check("struct cannot be overwritten", !overlay.apply(nonNumeric, 2)); }
		check("struct preserved", nonNumeric.value == struct);
		try (var outer = new YsmParameterOverlay()) {
			outer.apply(value, 3);
			try (var inner = new YsmParameterOverlay()) { inner.apply(value, 4); }
			equal("nested overlay returns to current parent", value.value, 3f);
		}
		equal("nested cleanup returns to base", value.value, 9f);
		var overlay = new YsmParameterOverlay();
		overlay.apply(value, 4);
		overlay.close();
		overlay.close();
		equal("cleanup idempotent", value.value, 9f);
		Slot failsOnRestore = new Slot(10f);
		var partial = new YsmParameterOverlay();
		partial.apply(value, 2);
		partial.apply(failsOnRestore, 2);
		failsOnRestore.failWrites = true;
		try { partial.close(); throw new AssertionError("Expected restore failure"); }
		catch (ReflectiveOperationException expected) { passed++; }
		equal("one restore failure does not block other restores", value.value, 9f);
	}

	private static void installedOysmContracts() throws Exception {
		String base = "com.elfmcys.yesstevemodel.";
		var storageClass = Class.forName(base + "geckolib3.core.molang.storage.VariableStorage");
		equal("exact requested OYSM jar, not developer shadow jar", java.nio.file.Path.of(storageClass.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize(),
				java.nio.file.Path.of(System.getProperty("yh.test.oysm.jar")).toAbsolutePath().normalize());
		check("preview-cache cleanup field matches installed API", Map.class.isAssignableFrom(Class.forName(base + "client.renderer.ExternalLivingRenderer", false,
				ModelPresentationTest.class.getClassLoader()).getDeclaredField("cache").getType()));
		var pool = Class.forName(base + "geckolib3.core.molang.util.StringPool").getMethod("computeIfAbsent", String.class);
		Object storage = storageClass.getConstructor().newInstance();
		int roamingId = (Integer) pool.invoke(null, "roaming");
		Class<?> mapClass = Class.forName("it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap");
		Object roaming = Class.forName(base + "molang.runtime.Int2FloatOpenHashMapStruct").getConstructor(mapClass)
				.newInstance(mapClass.getConstructor().newInstance());
		storageClass.getMethod("setScoped", int.class, Object.class).invoke(storage, roamingId, roaming);
		var access = new YsmClientPresentationBridge.ParameterAccess();
		var mouth = access.slot(storage, "v.roaming.mouth");
		var emoji = access.slot(storage, "v.ysmemoji");
		check("actual OYSM slots found", mouth != null && emoji != null);
		mouth.set(4f);
		emoji.set(7f);
		try (var overlay = new YsmParameterOverlay()) {
			overlay.apply(mouth, 2);
			overlay.apply(emoji, 1);
			equal("OYSM roaming override", mouth.get(), 2f);
			equal("OYSM scoped override", emoji.get(), 1f);
		}
		equal("OYSM roaming restored", mouth.get(), 4f);
		equal("OYSM scoped restored", emoji.get(), 7f);
	}

	private static final class Slot implements YsmParameterOverlay.Slot {
		private Object value;
		private boolean failWrites;
		private Slot(Object value) { this.value = value; }
		@Override public Object get() { return value; }
		@Override public void set(Object value) throws ReflectiveOperationException {
			if (failWrites) throw new ReflectiveOperationException("simulated restore failure");
			this.value = value;
		}
	}

	private static void equal(String name, Object actual, Object expected) {
		check(name + " (actual=" + actual + ", expected=" + expected + ")", Objects.equals(actual, expected));
	}

	private static void check(String name, boolean condition) {
		if (!condition) throw new AssertionError(name);
		passed++;
	}

	private static void reject(String name, Runnable action) {
		try { action.run(); }
		catch (IllegalArgumentException | UnsupportedOperationException expected) { passed++; return; }
		throw new AssertionError(name + " was accepted");
	}
}
