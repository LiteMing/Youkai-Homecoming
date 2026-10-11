package gen;

import com.electronwill.nightconfig.core.CommentedConfig;
import dev.xkmc.youkaishomecoming.content.entity.goal.CompanionFlightSettings;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import static dev.xkmc.youkaishomecoming.content.entity.goal.CompanionFlightMath.*;

/** Real JSON parser/configuration validation without creating a Minecraft world. */
public final class CompanionFlightSettingsTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
    private static void reject(String json) { boolean rejected = false; try { CompanionFlightSettings.parse(json); } catch (RuntimeException expected) { rejected = true; } check(rejected); }
    public static void main(String[] args) {
        var config = CommentedConfig.inMemory();
        YHModConfig.COMMON_SPEC.correct(config); YHModConfig.COMMON_SPEC.setConfig(config);
        var defaults = CompanionFlightSettings.parse("{}");
        check(defaults.targetMode() == TargetMode.LEGACY && defaults.maxSpeed() == .85 && defaults.updateIntervalTicks() == 1);
        check(YHModConfig.COMMON.companionAutomaticSlots.get() && YHModConfig.COMMON.companionLocalAvoidance.get());
        check(YHModConfig.COMMON.companionSlotSpacing.get() == 2.4 && YHModConfig.COMMON.companionLookahead.get() == 1.5);
        check(YHModConfig.COMMON.companionDetourSpeed.get() == .4 && YHModConfig.COMMON.companionDetourHoldTicks.get() == 4);
        check(YHModConfig.COMMON.companionTargetRetraction.get() == 1.5 && YHModConfig.COMMON.companionTargetRecoveryTicks.get() == 20);
        var lock = CompanionFlightSettings.parse("{\"target_mode\":\"PLAYER_LOCAL\",\"x\":0,\"y\":-10,\"z\":0,\"movement_mode\":\"LOCK_TO_TARGET\",\"facing_mode\":\"PLAYER_BODY\"}");
        check(lock.y() == -10 && lock.movementMode() == MovementMode.LOCK_TO_TARGET && lock.facingMode() == FacingMode.PLAYER_BODY);
        check(CompanionFlightSettings.parse(lock.json()).equals(lock));
        YHModConfig.COMMON.companionMaxSpeed.set(.42);
        check(CompanionFlightSettings.parse("{}").maxSpeed() == .42);
        check(CompanionFlightSettings.parse("{\"max_speed\":0.7}").maxSpeed() == .7);
        for (String json : new String[]{"[]", "null", "{\"unknown\":1}", "{\"max_speed\":0}", "{\"max_speed\":5}",
                "{\"max_speed\":\"0.3\"}", "{\"y\":true}", "{\"y\":null}", "{\"target_mode\":true}",
                "{\"target_mode\":\"UNKNOWN\"}", "{\"movement_mode\":\"UNKNOWN\"}", "{\"facing_mode\":\"UNKNOWN\"}",
                "{\"update_interval_ticks\":0}", "{\"update_interval_ticks\":201}", "{\"update_interval_ticks\":1.5}", "{\"update_interval_ticks\":1e100}",
                "{\"x\":129}", "{\"y\":-129}", "{\"z\":1e1000}", "{\"target_mode\":\"ABSOLUTE\",\"x\":1,\"y\":64,\"z\":1}"}) reject(json);
        var absolute = CompanionFlightSettings.parse("{\"target_mode\":\"ABSOLUTE\",\"x\":5000,\"y\":64,\"z\":-5000,\"dimension\":\"minecraft:overworld\"}");
        check(absolute.x() == 5000 && absolute.targetMode() == TargetMode.ABSOLUTE);
        check(!dailyFollow(absolute.targetMode(), absolute.movementMode()));
        check(!dailyFollow(lock.targetMode(), lock.movementMode()));
        var explicitZero = CompanionFlightSettings.parse("{\"target_mode\":\"PLAYER_LOCAL\",\"x\":0,\"y\":0,\"z\":0}");
        check(!dailyFollow(explicitZero.targetMode(), explicitZero.movementMode()));
        System.out.println("CompanionFlightSettingsTest: " + checks + " checks passed");
    }
}
