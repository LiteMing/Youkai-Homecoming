package dev.xkmc.youkaishomecoming.compat.ysm;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Entity request and battle-signal contracts after preset ownership moved to OYSM. */
public final class ModelPresentationTest {
    private static int passed;
    private static final YsmPresentationState.Source SOURCE = YsmPresentationState.Source.COMMAND;
    public static void main(String[] args) {
        stateContracts();
        ownedPresetContracts();
        serializationContracts();
        signalContracts();
        check("different model entries never alias", !YsmModelPaths.same("contract/model/main", "contract/model/arrow"));
        check("pack-only paths do not alias main", !YsmModelPaths.same("contract/model", "contract/model/main"));
        System.out.println("ModelPresentationTest: " + passed + " contracts passed");
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

	private static void ownedPresetContracts() {
		var preset = new YsmPresetBridge.Preset("contract:preview", "test/model/main", "test", "extra5", 20, Map.of("v.face", 1f, "v.mouth", 2f));
		var base = YsmPresentationState.EMPTY.setParameter("v.other", 7, 100, 0, SOURCE, 8);
		var owned = base.applyPreset("test/model/main", preset, 100, 0, SOURCE, 8, "cchat:request-one");
		var released = owned.releaseOwned("cchat:request-one");
		check("owned release clears body", released.animation() == null);
		check("owned release clears preset parameters", !released.parameters().containsKey("v.face") && !released.parameters().containsKey("v.mouth"));
		equal("owned release preserves unrelated request", released.parameters(), base.parameters());
		check("release idempotent", released.releaseOwned("cchat:request-one") == released);
		var overwritten = owned.applyPreset("test/model/main", preset, 100, 0, SOURCE, 8, "cchat:request-two");
		equal("same tick/value body replacement survives old release", overwritten.releaseOwned("cchat:request-one"), overwritten);
		var raw = owned.setParameter("v.face", 1, 100, 0, SOURCE, 8).play("extra5", 100, 0, SOURCE);
		var rawReleased = raw.releaseOwned("cchat:request-one");
		check("unowned identical body survives", rawReleased.animation() != null && rawReleased.animation().owner().isEmpty());
		equal("unowned identical parameter survives", rawReleased.parameters().get("v.face").value(), 1f);
		check("remaining owned parameter released", !rawReleased.parameters().containsKey("v.mouth"));
		var restored = YsmPresentationState.fromTag(owned.toTag());
		equal("owner survives entity NBT reload", restored, owned);
		equal("zero-duration reloaded owned state releases", restored.releaseOwned("cchat:request-one"), released);
		var face = owned.applyPreset("test/model/main", new YsmPresetBridge.Preset("contract:preview", "test/model/main", "face", "", 10, Map.of("v.face", 4f)), 100, 10, SOURCE, 8, "cchat:face");
		equal("face-only owned preset does not restart body", face.sequence(), owned.sequence());
		equal("face-only owned release preserves body", face.releaseOwned("cchat:face").animation(), owned.animation());
		check("finite owner expires at boundary", base.applyPreset("test/model/main", preset, 100, 20, SOURCE, 8, "cchat:finite").expire(120).animation() == null);
		reject("empty owner cannot clear unowned values", () -> raw.releaseOwned(""));
		reject("owner format bound", () -> raw.releaseOwned("bad owner"));
		var legacy = base.toTag();
		equal("legacy NBT still decodes", YsmPresentationState.fromTag(legacy), base);
	}

	private static void serializationContracts() {
		var state = YsmPresentationState.EMPTY.play("extra5", 9000000000L, 100, SOURCE)
				.setParameter("v.roaming.mouth", 2.5f, 9000000000L, 0, YsmPresentationState.Source.SCRIPT, 4);
		CompoundTag tag = state.toTag();
		equal("round trip", YsmPresentationState.fromTag(tag), state);
		CompoundTag entityTag = new CompoundTag();
		entityTag.put(YsmPresentationState.ENTITY_TAG, tag.copy());
		var reloaded = YsmPresentationState.fromTag(entityTag.getCompound(YsmPresentationState.ENTITY_TAG));
		equal("persistent parameter survives entity NBT reload", reloaded.parameters().get("v.roaming.mouth").value(), 2.5f);
		check("zero-duration parameter remains active after world reload", reloaded.parameters().get("v.roaming.mouth").active(9001000000L));
		var finite = YsmPresentationState.EMPTY.setParameter("v.roaming.b", 1, 100, 20, SOURCE, 4);
		var finiteReloaded = YsmPresentationState.fromTag(finite.toTag());
		check("finite persisted parameter resumes before deadline", finiteReloaded.parameters().get("v.roaming.b").active(119));
		check("finite persisted parameter expires on world clock", finiteReloaded.expire(120).parameters().isEmpty());
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

	private static void signalContracts() {
		var empty = YsmPresentationSignals.EMPTY;
		check("unchanged daily state sends no new snapshot", empty.advance(YsmPresentationSignals.Trigger.IDLE, false, 100) == empty);
		var combat = empty.advance(YsmPresentationSignals.Trigger.WALK, true, 100);
		equal("combat starts on server edge", combat.event(YsmPresentationSignals.Trigger.ENTER_COMBAT).at(), 100L);
		check("holding combat does not restart event", combat.advance(YsmPresentationSignals.Trigger.WALK, true, 120) == combat);
		var fly = combat.advance(YsmPresentationSignals.Trigger.FLY, true, 120);
		equal("movement transition leaves combat clock", fly.event(YsmPresentationSignals.Trigger.ENTER_COMBAT).at(), 100L);
		equal("movement transition increments state sequence", fly.stateSequence(), 2L);
		var again = fly.advance(YsmPresentationSignals.Trigger.FLY, false, 130).advance(YsmPresentationSignals.Trigger.FLY, true, 140);
		equal("re-enter combat is a new event", again.event(YsmPresentationSignals.Trigger.ENTER_COMBAT).sequence(), 2L);
		var hurt = again.hurt(141).hurt(141);
		equal("same-tick hits each have a sequence", hurt.event(YsmPresentationSignals.Trigger.HURT).sequence(), 2L);
		equal("signal serialization", YsmPresentationSignals.fromTag(hurt.toTag()), hurt);
		var legacyTag = hurt.toTag();
		legacyTag.remove("combatMode");
		legacyTag.remove("combatModeAt");
		legacyTag.remove("combatModeSequence");
		equal("old signal NBT has no inferred combat condition",
				YsmPresentationSignals.fromTag(legacyTag).combatMode(), YsmPresentationSignals.CombatMode.NONE);
		equal("missing signals degrade to idle", YsmPresentationSignals.fromTag(new CompoundTag()), empty);
		reject("event cannot be persistent state", () -> empty.advance(YsmPresentationSignals.Trigger.HURT, false, 100));
		var cache = new YsmPresentationSignals.Cache();
		var tag = hurt.toTag();
		check("signal decode cache reused", cache.read(tag) == cache.read(tag));
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
