package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.youkaishomecoming.compat.ysm.*;
import org.objectweb.asm.*;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Real controller drafts/save/acknowledgement logic with only the game and packet transport replaced. */
public final class YsmEditorSaveTest {

	private static final String MODEL = "test/editor";
	private static final String TARGET = "00000000-0000-0000-0000-000000000001";
	private static final List<Object> packets = new ArrayList<>();
	public static boolean allowed = true;
	private static int checks;

	public static void main(String[] args) throws Exception {
		if (!Arrays.asList(args).contains("--headless")) { headless(); return; }
		var config = com.electronwill.nightconfig.core.CommentedConfig.inMemory();
		dev.xkmc.youkaishomecoming.init.data.YHModConfig.COMMON_SPEC.correct(config);
		dev.xkmc.youkaishomecoming.init.data.YHModConfig.COMMON_SPEC.setConfig(config);
		browsingAndBinding();
		deletion();
		combinedSave(false);
		combinedSave(true);
		failedProfile();
		rawAndPermission();
		rawProfileOnlyAfterBrowsing();
		System.out.println("YsmEditorSaveTest: " + checks + " checks passed (headless controller; no game/network)");
	}

	public static void send(Object packet) { packets.add(packet); }

	private static YsmEditorController editor(boolean preset) {
		YsmClientProfiles.clear();
		packets.clear();
		allowed = true;
		var profile = preset ? new YsmModelProfile(MODEL,
				Map.of("happy", new YsmModelProfile.Preset("Happy", "extra6", 20, Map.of("v.face", 1f))),
				Map.of(YsmModelProfile.Trigger.IDLE, "happy")) : YsmModelProfile.empty(MODEL);
		YsmClientProfiles.receive(new YsmProfileSyncToClient(new YsmProfileData.Entry(7, profile), "", true, ""));
		var editor = new YsmEditorController(() -> {});
		editor.selectModel(MODEL);
		check("browsing an existing library is clean", !editor.isDirty());
		return editor;
	}

	private static void browsingAndBinding() {
		var editor = editor(false);
		editor.saveChanges();
		check("Ctrl+S on an untouched preview sends nothing", packets.isEmpty() && !editor.isDirty());
		editor.target(TARGET);
		check("explicit binding edits are dirty", editor.bindingDirty() && !editor.profileDirty());
		editor.saveChanges();
		var binding = last(YsmOverrideRequestToServer.class);
		check("binding-only Ctrl+S does not need a new preset", packets.size() == 1 && binding.action.equals("entity_set"));
		check("selected model and target are sent", binding.modelId.equals(MODEL) && binding.uuidList.equals(TARGET));
		check("binding remains dirty while waiting", editor.waiting() && editor.bindingDirty());
		editor.saveChanges();
		editor.texture("during_request");
		check("repeated save and edits cannot change an in-flight request", packets.size() == 1 && editor.texture().equals("default"));
		bindingReply(editor, binding, true, 4);
		check("only the binding acknowledgement clears its marker", !editor.isDirty());
		editor.texture("alternate");
		editor.texture("default");
		check("reverting a binding edit clears its marker", !editor.isDirty());
		editor.selectModel("test/another_model");
		editor.saveChanges();
		check("browsing with a selected entity does not bind it", !editor.isDirty() && packets.size() == 1);
	}

	private static void deletion() {
		var editor = editor(true);
		editor.selectPreset("happy");
		editor.deletePreset();
		check("deletion removes the preset and its routes in the draft", editor.profile().presets().isEmpty() && editor.profile().triggers().isEmpty());
		check("deletion remains dirty before saving", editor.profileDirty());
		editor.saveChanges();
		var request = last(YsmProfileRequestToServer.class);
		check("Ctrl+S saves deletion without capturing a blank preview", YsmModelProfile.fromJson(request.json).presets().isEmpty());
		check("profile marker waits for server confirmation", editor.profileDirty());
		profileReply(editor, request, true, 8);
		check("confirmed deletion clears the marker", !editor.isDirty());
		editor.saveChanges();
		check("repeated Ctrl+S cannot recreate a deleted preset", packets.size() == 1 && editor.profile().presets().isEmpty());
	}

	private static void combinedSave(boolean failBinding) {
		var editor = editor(true);
		editor.target(TARGET);
		editor.route(YsmModelProfile.Trigger.WALK, "happy");
		editor.saveChanges();
		var profile = last(YsmProfileRequestToServer.class);
		check("combined save first sends only the profile", packets.size() == 1 && editor.profileDirty() && editor.bindingDirty());
		profileReply(editor, profile, true, 8);
		var binding = last(YsmOverrideRequestToServer.class);
		check("profile acknowledgement queues the binding and clears only profile dirty", packets.size() == 2 && !editor.profileDirty() && editor.bindingDirty() && editor.waiting());
		bindingReply(editor, binding, !failBinding, 5);
		if (failBinding) {
			check("a binding rejection preserves the remaining draft", !editor.waiting() && editor.bindingDirty() && !editor.profileDirty());
			editor.saveChanges();
			var retry = last(YsmOverrideRequestToServer.class);
			check("retry saves only the failed binding", packets.size() == 3 && !retry.requestId.equals(binding.requestId));
			bindingReply(editor, retry, true, 6);
		}
		check("all successful acknowledgements clear the combined marker", !editor.isDirty() && !editor.waiting());
	}

	private static void failedProfile() {
		var editor = editor(true);
		editor.target(TARGET);
		editor.route(YsmModelProfile.Trigger.WALK, "happy");
		editor.saveChanges();
		var profile = last(YsmProfileRequestToServer.class);
		profileReply(editor, profile, false, 8);
		check("profile conflict cannot send a binding", packets.size() == 1 && !editor.waiting());
		check("profile conflict preserves both unsaved scopes", editor.profileDirty() && editor.bindingDirty());
	}

	private static void rawAndPermission() {
		var editor = editor(true);
		var edited = new YsmModelProfile(MODEL, editor.profile().presets(), Map.of());
		editor.rawJson(new YsmEditorDocument(edited, new YsmEditorDocument.Binding(false, TARGET, MODEL, "alternate", Map.of())).toJson());
		allowed = false;
		editor.saveChanges();
		check("permission rejection preserves raw text and sends nothing", editor.rawDirty() && editor.isDirty() && packets.isEmpty());
		allowed = true;
		editor.saveChanges();
		var request = last(YsmProfileRequestToServer.class);
		check("raw Ctrl+S validates the document without requiring a rendered snapshot", !editor.rawDirty() && YsmModelProfile.fromJson(request.json).equals(edited));
		profileReply(editor, request, true, 8);
		var binding = last(YsmOverrideRequestToServer.class);
		check("raw binding edits use the same save chain", binding.textureName.equals("alternate"));
		bindingReply(editor, binding, true, 6);
		check("raw save confirmation clears the marker", !editor.isDirty());
	}

	private static void profileReply(YsmEditorController editor, YsmProfileRequestToServer request, boolean success, long revision) {
		YsmClientProfiles.receive(new YsmProfileSyncToClient(new YsmProfileData.Entry(revision,
				success ? YsmModelProfile.fromJson(request.json) : YsmClientProfiles.entry(request.model).profile()),
				request.requestId, success, success ? "saved" : "revision_conflict"));
		editor.tick();
	}

	private static void rawProfileOnlyAfterBrowsing() {
		var editor = editor(true);
		editor.rawJson("  " + editor.rawJson());
		editor.saveChanges();
		check("formatting-only JSON changes do not create a binding edit", packets.isEmpty() && !editor.isDirty());
		var edited = new YsmModelProfile(MODEL, editor.profile().presets(), Map.of());
		editor.rawJson(new YsmEditorDocument(edited,
				new YsmEditorDocument.Binding(false, "", MODEL, editor.texture(), Map.of())).toJson());
		editor.saveChanges();
		var request = last(YsmProfileRequestToServer.class);
		check("editing only raw presets after browsing does not require a binding target", !editor.bindingDirty() && editor.waiting());
		profileReply(editor, request, true, 8);
		check("raw profile-only save clears its marker without sending a binding", !editor.isDirty() && packets.size() == 1);
	}

	private static void bindingReply(YsmEditorController editor, YsmOverrideRequestToServer request, boolean success, long revision) {
		YsmClientProfiles.response(request.requestId, success, success ? "saved" : "Unsupported or unloaded entity UUID", revision, "");
		editor.tick();
	}

	private static <T> T last(Class<T> type) {
		check("expected " + type.getSimpleName(), !packets.isEmpty() && type.isInstance(packets.get(packets.size() - 1)));
		return type.cast(packets.get(packets.size() - 1));
	}

	private static void check(String name, boolean condition) {
		if (!condition) throw new AssertionError(name);
		checks++;
	}

	/** Stub only rendering, permission lookup, initial target lookup and packet transport; leave state logic intact. */
	private static void headless() throws Exception {
		var urls = Arrays.stream(System.getProperty("java.class.path").split(java.io.File.pathSeparator)).map(path -> {
			try { return Path.of(path).toUri().toURL(); }
			catch (Exception ex) { throw new IllegalArgumentException(ex); }
		}).toArray(java.net.URL[]::new);
		String controller = YsmEditorController.class.getName(), entrypoint = "dev.xkmc.youkaishomecoming.init.YoukaisHomecoming";
		String test = YsmEditorSaveTest.class.getName().replace('.', '/');
		try (var loader = new URLClassLoader(urls, YsmEditorSaveTest.class.getClassLoader()) {
			@Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
				if (!name.startsWith("dev.xkmc.")) return super.loadClass(name, resolve);
				synchronized (getClassLoadingLock(name)) {
					Class<?> type = findLoadedClass(name);
					if (type == null) type = findClass(name);
					if (resolve) resolveClass(type);
					return type;
				}
			}
			@Override protected Class<?> findClass(String name) throws ClassNotFoundException {
				if (!name.equals(controller) && !name.equals(entrypoint)) return super.findClass(name);
				try (var input = getResourceAsStream(name.replace('.', '/') + ".class")) {
					var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
					new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9, writer) {
						@Override public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
							if (name.equals(entrypoint) && method.equals("<clinit>")) return null;
							var next = super.visitMethod(access, method, descriptor, signature, exceptions);
							if (!name.equals(controller)) return next;
							if (method.equals("preview") || method.equals("pickTarget") || method.equals("mayWriteWorld")) {
								next.visitCode();
								if (method.equals("preview")) { next.visitInsn(Opcodes.ACONST_NULL); next.visitInsn(Opcodes.ARETURN); }
								else if (method.equals("mayWriteWorld")) { next.visitFieldInsn(Opcodes.GETSTATIC, test, "allowed", "Z"); next.visitInsn(Opcodes.IRETURN); }
								else next.visitInsn(Opcodes.RETURN);
								next.visitMaxs(1, 1); next.visitEnd();
								return null;
							}
							return new MethodVisitor(Opcodes.ASM9, next) {
								@Override public void visitFieldInsn(int opcode, String owner, String field, String desc) {
									if (opcode == Opcodes.GETSTATIC && owner.equals(entrypoint.replace('.', '/')) && field.equals("HANDLER")) return;
									super.visitFieldInsn(opcode, owner, field, desc);
								}
								@Override public void visitMethodInsn(int opcode, String owner, String called, String desc, boolean itf) {
									if (called.equals("toServer")) { super.visitMethodInsn(Opcodes.INVOKESTATIC, test, "send", "(Ljava/lang/Object;)V", false); return; }
									if (owner.equals("net/minecraft/client/Minecraft") && called.equals("getInstance")) { super.visitInsn(Opcodes.ACONST_NULL); return; }
									if (owner.equals("net/minecraft/client/Minecraft") && called.equals("getConnection")) { super.visitInsn(Opcodes.POP); super.visitInsn(Opcodes.ACONST_NULL); return; }
									if (owner.equals("dev/xkmc/youkaishomecoming/compat/ysm/YSMClientCompat") && called.equals("bindingRevision")) { super.visitInsn(Opcodes.LCONST_0); return; }
									super.visitMethodInsn(opcode, owner, called, desc, itf);
								}
							};
						}
					}, 0);
					byte[] bytes = writer.toByteArray();
					return defineClass(name, bytes, 0, bytes.length);
				} catch (java.io.IOException ex) { throw new ClassNotFoundException(name, ex); }
			}
		}) {
			loader.loadClass(YsmEditorSaveTest.class.getName()).getMethod("main", String[].class).invoke(null, (Object) new String[]{"--headless"});
		}
	}
}
