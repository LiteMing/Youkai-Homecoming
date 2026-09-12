package dev.xkmc.youkaishomecoming.compat.ysm;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;
import java.util.function.Function;

/** Pure hint grammar/expiry plus optional reflection signatures from the installed OpenYSM jar. */
public final class SpellHintTest {

	private static int checks;

	public static void main(String[] args) throws Exception {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
		var state = YsmSpellHintState.create(" special=missing+cast ", 1000, 20, 1);
		check("hints are trimmed", state.hint().equals("special=missing+cast"));
		check("duration boundary is exclusive", state.active(1019) && !state.active(1020));
		check("late tracking retains the deadline", new YsmSpellHintState(state.hint(), state.expiresAt(), state.sequence()).active(1019));
		check("late tracking never extends expired hints", !new YsmSpellHintState(state.hint(), state.expiresAt(), state.sequence()).active(1030));
		check("zero duration lasts for the spell scope", YsmSpellHintState.create("cast", 1000, 0, 1).active(1_000_000));
		check("empty hint clears immediately", !YsmSpellHintState.create(" ", 1000, 0, 2).active(1000));
		check("deadline saturates", YsmSpellHintState.create("cast", Long.MAX_VALUE - 5, 20, 1).expiresAt() == Long.MAX_VALUE);

		Set<String> clips = Set.of("cast", "attack", "CustomAction", "idle", "fly");
		Function<String, String> mappings = key -> key.equals("cast") ? "cast=missing+cast" : key;
		check("bare clip is routed through special", normalize("CustomAction", mappings).equals("special=CustomAction"));
		check("semantic mapping is shared", normalize("cast", mappings).equals("cast=missing+cast"));
		check("authored fallback is preserved", normalize("special=missing+CustomAction", mappings).equals("special=missing+CustomAction"));
		check("mixed hint separators normalize identically", normalize("fly,cast;walk|calm", mappings).equals("fly cast=missing+cast walk calm"));
		check("first existing clip wins", "CustomAction".equals(YsmAnimationHints.resolve("special=missing+CustomAction+cast", clips::contains)));
		check("explicit clip remains case-sensitive", YsmAnimationHints.resolve("special=customaction", clips::contains) == null);
		check("missing clips fall back to native animation", YsmAnimationHints.resolve("special=missing", clips::contains) == null);
		check("cap priority matches boss even when token order differs", "attack".equals(YsmAnimationHints.resolve("special=cast angry=attack", clips::contains)));
		check("a missing higher-priority group does not promote a lower one", YsmAnimationHints.resolve("special=cast angry=missing", clips::contains) == null);
		check("default cap fallback is available", "cast".equals(YsmAnimationHints.resolve("cast", clips::contains)));
		check("movement hint works on a native player", "fly".equals(YsmAnimationHints.resolve("fly", clips::contains)));
		check("unknown groups remain unsupported", YsmAnimationHints.resolve("defeat=cast", clips::contains) == null);
		check("bare actions suppress automatic angry expression", YsmAnimationHints.overridesPassiveExpression("CustomAction"));
		check("movement keeps automatic expression", !YsmAnimationHints.overridesPassiveExpression("fly walk calm"));
		casterRouting();

		if (args.length > 0) {
			try (var loader = new URLClassLoader(new java.net.URL[]{Path.of(args[0]).toUri().toURL()}, SpellHintTest.class.getClassLoader())) {
				new YsmPlayerHintBridge.Access(loader);
				check("installed native player bridge signatures are available", true);
				var predicate = Class.forName("com.elfmcys.yesstevemodel.client.animation.predicate.PlayerBaseAnimationPredicate", false, loader);
				var event = Class.forName("com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent", false, loader);
				var evaluator = Class.forName("com.elfmcys.yesstevemodel.molang.runtime.ExpressionEvaluator", false, loader);
				check("optional mixin targets the installed predicate descriptor", predicate.getDeclaredMethod("predicate", event, evaluator)
						.getReturnType().getName().equals("com.elfmcys.yesstevemodel.geckolib3.core.enums.PlayState"));
			}
		} else {
			check("absent OpenYSM does not break the adapter", YsmPlayerHintBridge.playHint(new Object()) == null);
		}
		System.out.println("SpellHintTest: all " + checks + " checks passed");
	}

	private static String normalize(String hint, Function<String, String> mapping) {
		return YsmAnimationHints.normalize(hint, mapping);
	}

	private static void casterRouting() {
		var called = new ArrayList<String>();
		var host = new TestHost(called);
		YsmSpellHints.caster(host);
		check("proxy presentation resolves through the host's displayed owner", called.equals(java.util.List.of("spellCircleDisplayEntity")));
		var trail = new dev.xkmc.youkaishomecoming.content.spell.action.TrailCardHolder(host,
				net.minecraft.world.phys.Vec3.ZERO, new net.minecraft.world.phys.Vec3(0, 0, 1));
		check("projectile callbacks retain the original caster", YsmSpellHints.source(trail) == host);
		called.clear();
		var holder = new TestHolder(called);
		YsmSpellHints.caster(holder);
		check("legacy player spells resolve their shooter", called.equals(java.util.List.of("shooter")));
	}

	private static class TestHolder implements dev.xkmc.youkaishomecoming.content.spell.spellcard.LivingCardHolder {
		protected final ArrayList<String> called;
		TestHolder(ArrayList<String> called) { this.called = called; }
		@Override public net.minecraft.world.entity.LivingEntity self() { called.add("self"); return null; }
		@Override public net.minecraft.world.entity.LivingEntity shooter() { called.add("shooter"); return null; }
		@Override public net.minecraft.world.entity.LivingEntity targetEntity() { return null; }
		@Override public float getDamage(dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku.IDanmakuType type) { return 0; }
	}

	private static final class TestHost extends TestHolder implements dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntimeHost {
		TestHost(ArrayList<String> called) { super(called); }
		@Override public net.minecraft.world.entity.LivingEntity spellCircleDisplayEntity() { called.add("spellCircleDisplayEntity"); return null; }
		@Override public net.minecraft.world.entity.LivingEntity owner() { return null; }
		@Override public dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime getSpellRuntime() { return null; }
		@Override public void setSpellRuntime(dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime runtime) { }
		@Override public void eraseDanmaku(net.minecraft.world.entity.player.Player player) { }
		@Override public void syncSpellState() { }
		@Override public boolean isBossHost() { return false; }
	}

	private static void check(String message, boolean condition) {
		if (!condition) throw new AssertionError(message);
		checks++;
	}
}
