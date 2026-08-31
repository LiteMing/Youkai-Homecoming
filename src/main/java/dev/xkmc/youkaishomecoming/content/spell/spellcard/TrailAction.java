package dev.xkmc.youkaishomecoming.content.spell.spellcard;

import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

@SerialClass
public class TrailAction {

	private CardHolder cached;

	public void execute(CardHolder holder, Vec3 pos, Vec3 dir) {

	}

	public void executeEntityHit(CardHolder holder, Vec3 pos, Vec3 dir, Entity hitEntity) {
		execute(holder, pos, dir);
	}

	public void executeEntityHit(CardHolder holder, dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext hitContext) {
		executeEntityHit(holder, hitContext.hitPosition(), hitContext.incomingVelocity(), hitContext.hitEntity());
	}

	public void executeBlockHit(CardHolder holder, Vec3 pos, Vec3 dir) {
		execute(holder, pos, dir);
	}

	public void executeBlockHit(CardHolder holder, dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext hitContext) {
		executeBlockHit(holder, hitContext.hitPosition(), hitContext.incomingVelocity());
	}

	public void execute(Vec3 pos, Vec3 dir) {
		if (cached != null) {
			execute(cached, pos, dir);
		}
	}

	public void executeEntityHit(Vec3 pos, Vec3 dir, Entity hitEntity) {
		if (cached != null) {
			executeEntityHit(cached, pos, dir, hitEntity);
		}
	}

	public void executeEntityHit(dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext hitContext) {
		if (cached != null) {
			executeEntityHit(cached, hitContext);
		} else {
			executeEntityHit(hitContext.hitPosition(), hitContext.incomingVelocity(), hitContext.hitEntity());
		}
	}

	public void executeBlockHit(Vec3 pos, Vec3 dir) {
		if (cached != null) {
			executeBlockHit(cached, pos, dir);
		}
	}

	public void executeBlockHit(dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext hitContext) {
		if (cached != null) {
			executeBlockHit(cached, hitContext);
		} else {
			executeBlockHit(hitContext.hitPosition(), hitContext.incomingVelocity());
		}
	}

	public void setup(CardHolder holder) {
		cached = holder;
	}

	protected CardHolder cachedHolder() {
		return cached;
	}

}
