package dev.xkmc.youkaishomecoming.content.spell.editor;

import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class SpellEditorIO {

	public record SaveResult(Path projectPath, Path datapackPath) {
	}

	private SpellEditorIO() {
	}

	public static Optional<SpellEditorData> loadProject(ResourceLocation spellId) throws IOException {
		Path path = getProjectPath(spellId);
		if (!Files.exists(path)) {
			return Optional.empty();
		}
		String json = Files.readString(path, StandardCharsets.UTF_8);
		return Optional.of(SpellEditorCodec.decodeProjectJson(json));
	}

	public static boolean hasProject(ResourceLocation spellId) {
		return Files.exists(getProjectPath(spellId));
	}

	public static SaveResult saveProject(SpellEditorData data) throws IOException {
		ResourceLocation spellId = data.definition().id;
		Path projectPath = getProjectPath(spellId);
		Path datapackPath = getDatapackPath(spellId);
		writeJson(projectPath, SpellEditorCodec.encodeProjectJson(data));
		writeJson(datapackPath, SpellEditorCodec.encodeDefinitionJson(data.definition()));
		return new SaveResult(projectPath, datapackPath);
	}

	public static Path getProjectPath(ResourceLocation spellId) {
		return getRoot().resolve(spellId.getNamespace()).resolve(spellId.getPath() + ".editor.json");
	}

	public static Path getDatapackPath(ResourceLocation spellId) {
		return getRoot()
				.resolve("exported_datapack")
				.resolve("data")
				.resolve(spellId.getNamespace())
				.resolve("spell_definitions")
				.resolve(spellId.getPath() + ".json");
	}

	public static String toClipboardJson(SpellDefinition definition) {
		return SpellEditorCodec.encodeDefinitionJson(definition);
	}

	public static String toClipboardJson(PhaseDefinition phase) {
		return SpellEditorCodec.encodePhaseJson(phase);
	}

	private static Path getRoot() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("yh_editor");
	}

	private static void writeJson(Path path, String json) throws IOException {
		Files.createDirectories(path.getParent());
		Files.writeString(path, json, StandardCharsets.UTF_8);
	}
}
