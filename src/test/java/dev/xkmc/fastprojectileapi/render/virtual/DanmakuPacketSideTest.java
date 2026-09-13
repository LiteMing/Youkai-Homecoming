package dev.xkmc.fastprojectileapi.render.virtual;

import dev.xkmc.l2serial.network.SerialPacketBase;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;

/** Construct and serialize real packets with client classes unavailable, without launching a game. */
public final class DanmakuPacketSideTest {

	private static final String PACKAGE = "dev.xkmc.fastprojectileapi.render.virtual.";
	private static int checks;

	public static void main(String[] args) throws Exception {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
		try (var loader = new ServerPacketLoader()) {
			for (String name : new String[]{"net.minecraft.client.multiplayer.ClientLevel", PACKAGE + "DanmakuClientHandler"}) {
				try {
					loader.loadClass(name);
					throw new AssertionError("Client class was available: " + name);
				} catch (ClassNotFoundException expected) {
					check("server fixture rejects client classes", expected.getMessage().contains("Client class unavailable"));
				}
			}
			int rejectedBefore = loader.rejected;
			Class<? extends SerialPacketBase> packetType = loader.loadClass(PACKAGE + "DanmakuBounceSyncPacket")
					.asSubclass(SerialPacketBase.class);
			check("packet is defined by the server fixture", packetType.getClassLoader() == loader);
			Class<?> kindType = loader.loadClass(PACKAGE + "DanmakuBounceSyncPacket$ResetKind");
			var constructor = packetType.getConstructor(int.class, Vec3.class, Vec3.class, int.class, kindType);
			Vec3 pos = new Vec3(8192.32, 95.38, -25.35);
			Vec3 vel = new Vec3(-0.75, 0.5, 1.25);
			for (Object kind : kindType.getEnumConstants()) {
				var packet = constructor.newInstance(150356, pos, vel, 3, kind);
				var restored = roundTrip(packetType, packet);
				check("entity ID survives " + kind, field(restored, "entityId").equals(150356));
				check("position survives " + kind, vector(restored, "pos").equals(pos));
				check("velocity survives " + kind, vector(restored, "vel").equals(vel));
				check("bounce count survives " + kind, field(restored, "bounceCount").equals(3));
				check("reset kind survives " + kind, field(restored, "resetKind") == kind);
			}
			var legacy = packetType.getConstructor(int.class, Vec3.class, Vec3.class, int.class)
					.newInstance(5, pos, vel, 2);
			check("legacy constructor retains bounce mode", field(roundTrip(packetType, legacy), "resetKind").toString().equals("BOUNCE"));
			var missingKind = constructor.newInstance(5, pos, vel, 2, null);
			check("null constructor kind retains bounce fallback", field(roundTrip(packetType, missingKind), "resetKind").toString().equals("BOUNCE"));
			check("serializer constructor remains usable", field(packetType.getConstructor().newInstance(), "resetKind").toString().equals("BOUNCE"));
			for (String name : new String[]{"DanmakuToClientPacket", "EraseDanmakuToClient", "BatchEraseDanmakuToClient"}) {
				Class<?> type = loader.loadClass(PACKAGE + name);
				check("neighboring packet remains constructible on server: " + name,
						type.getConstructor().newInstance() instanceof SerialPacketBase);
			}
			check("packet construction and serialization never request client classes", loader.rejected == rejectedBefore);
		}
		System.out.println("DanmakuPacketSideTest: " + checks + " checks passed");
	}

	private static SerialPacketBase roundTrip(Class<? extends SerialPacketBase> type, SerialPacketBase packet) {
		var buffer = new FriendlyByteBuf(Unpooled.buffer());
		try {
			packet.write(buffer);
			SerialPacketBase result = SerialPacketBase.serial(type, buffer);
			check("packet consumes its entire encoded payload", buffer.readableBytes() == 0);
			return result;
		} finally { buffer.release(); }
	}

	private static Object field(Object packet, String name) throws ReflectiveOperationException {
		var field = packet.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(packet);
	}

	private static Vec3 vector(Object packet, String prefix) throws ReflectiveOperationException {
		return new Vec3((double) field(packet, prefix + "X"), (double) field(packet, prefix + "Y"), (double) field(packet, prefix + "Z"));
	}

	private static final class ServerPacketLoader extends URLClassLoader {
		private int rejected;

		private ServerPacketLoader() {
			super(Arrays.stream(System.getProperty("java.class.path").split(java.io.File.pathSeparator)).map(path -> {
				try { return Path.of(path).toUri().toURL(); }
				catch (java.net.MalformedURLException error) { throw new IllegalArgumentException(error); }
			}).toArray(URL[]::new), DanmakuPacketSideTest.class.getClassLoader());
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			if (name.startsWith("net.minecraft.client.") || name.startsWith("com.mojang.blaze3d.")
					|| name.equals(PACKAGE + "DanmakuClientHandler") || name.equals(PACKAGE + "ClientDanmakuCache")) {
				rejected++;
				throw new ClassNotFoundException("Client class unavailable on dedicated server: " + name);
			}
			if (!name.startsWith(PACKAGE)) return super.loadClass(name, resolve);
			synchronized (getClassLoadingLock(name)) {
				Class<?> type = findLoadedClass(name);
				if (type == null) type = findClass(name);
				if (resolve) resolveClass(type);
				return type;
			}
		}
	}

	private static void check(String label, boolean pass) { if (!pass) throw new AssertionError(label); checks++; }
}
