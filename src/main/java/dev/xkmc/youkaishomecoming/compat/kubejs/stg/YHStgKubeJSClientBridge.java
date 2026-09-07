package dev.xkmc.youkaishomecoming.compat.kubejs.stg;

import dev.latvian.mods.kubejs.script.ScriptType;
import dev.xkmc.youkaishomecoming.compat.stg.event.ClassicControlModeEvent;
import net.minecraftforge.common.MinecraftForge;

public final class YHStgKubeJSClientBridge {

	private YHStgKubeJSClientBridge() {
	}

	public static void register() {
		MinecraftForge.EVENT_BUS.addListener(YHStgKubeJSClientBridge::onClassicControlChanged);
	}

	private static void onClassicControlChanged(ClassicControlModeEvent event) {
		if (YHStgKubeJSEvents.CLASSIC_CONTROL_CHANGED.hasListeners()) {
			YHStgKubeJSEvents.CLASSIC_CONTROL_CHANGED.post(
					ScriptType.CLIENT, new ClassicControlModeEventJS(event));
		}
	}
}
