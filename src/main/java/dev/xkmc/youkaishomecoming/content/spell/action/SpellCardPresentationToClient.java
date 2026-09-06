package dev.xkmc.youkaishomecoming.content.spell.action;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.spell.client.SpellCardPresentationClient;
import net.minecraftforge.network.NetworkEvent;

/** Server-to-client cue for the generic spell card presentation action. */
@SerialClass
public class SpellCardPresentationToClient extends SerialPacketBase {
	@SerialClass.SerialField public int entityId;
	@SerialClass.SerialField public String spellId = "";
	@SerialClass.SerialField public long startedAt;
	@SerialClass.SerialField public int holdTicks = 10;
	@SerialClass.SerialField public int throwTicks = 8;
	@SerialClass.SerialField public int floatTicks = 22;
	@SerialClass.SerialField public boolean rightHand;
	@SerialClass.SerialField public double offsetRight;
	@SerialClass.SerialField public double offsetUp;
	@SerialClass.SerialField public double offsetForward;
	@SerialClass.SerialField public double heldScale = 0.55;
	@SerialClass.SerialField public double displayScale = 0.92;
	@SerialClass.SerialField public long sequence;

	@Deprecated
	public SpellCardPresentationToClient() {
	}

	public SpellCardPresentationToClient(int entityId, String spellId, long startedAt,
			int holdTicks, int throwTicks, int floatTicks, boolean rightHand,
			double offsetRight, double offsetUp, double offsetForward,
			double heldScale, double displayScale, long sequence) {
		this.entityId = entityId;
		this.spellId = spellId == null ? "" : spellId;
		this.startedAt = startedAt;
		this.holdTicks = holdTicks;
		this.throwTicks = throwTicks;
		this.floatTicks = floatTicks;
		this.rightHand = rightHand;
		this.offsetRight = offsetRight;
		this.offsetUp = offsetUp;
		this.offsetForward = offsetForward;
		this.heldScale = heldScale;
		this.displayScale = displayScale;
		this.sequence = sequence;
	}

	public static SpellCardPresentationToClient clear(int entityId, long startedAt, long sequence) {
		return new SpellCardPresentationToClient(entityId, "", startedAt,
				0, 0, 0, false, 0, 0, 0, 0.55, 0.92, sequence);
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		context.enqueueWork(() -> SpellCardPresentationClient.accept(entityId, spellId, startedAt,
				holdTicks, throwTicks, floatTicks, rightHand,
				offsetRight, offsetUp, offsetForward, heldScale, displayScale, sequence));
		context.setPacketHandled(true);
	}
}
