package dev.xkmc.youkaishomecoming.content.spell.preview;

import net.minecraftforge.fml.loading.FMLPaths;

import java.util.List;

/** Client-only command presets shared by worlds and servers. */
public final class CommandPresetStore {
	private static final ClientStringPresetStore STORE = new ClientStringPresetStore(
			FMLPaths.CONFIGDIR.get().resolve("youkaishomecoming_command_presets.json"), 64, 512);

	private CommandPresetStore() {
	}

	public static synchronized List<String> list() {
		return STORE.list();
	}

	public static synchronized void save(String command) {
		STORE.save(command);
	}

	public static synchronized void remove(String command) {
		STORE.remove(command);
	}
}
