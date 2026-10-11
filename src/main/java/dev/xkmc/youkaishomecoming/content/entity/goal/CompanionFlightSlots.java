package dev.xkmc.youkaishomecoming.content.entity.goal;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Stable level-local formation leases; existing companions never compact. */
public final class CompanionFlightSlots {
    private static final class Lease {
        final int slot;
        long tick;
        Lease(int slot, long tick) { this.slot = slot; this.tick = tick; }
    }
    private static final class Group {
        final Map<UUID, Lease> members = new HashMap<>();
        final BitSet occupied = new BitSet();
    }
    private final Map<UUID, Group> groups = new HashMap<>();
    private long lastSweep = Long.MIN_VALUE;

    public int claim(UUID player, UUID entity, long tick) {
        if (tick != lastSweep) {
            var iterator = groups.values().iterator();
            while (iterator.hasNext()) {
                Group group = iterator.next();
                group.members.entrySet().removeIf(entry -> {
                    if (entry.getValue().tick >= tick - 1 && entry.getValue().tick <= tick) return false;
                    group.occupied.clear(entry.getValue().slot);
                    return true;
                });
                if (group.members.isEmpty()) iterator.remove();
            }
            lastSweep = tick;
        }
        Group group = groups.computeIfAbsent(player, unused -> new Group());
        Lease previous = group.members.get(entity);
        if (previous != null) {
            previous.tick = tick;
            return previous.slot;
        }
        int slot = group.occupied.nextClearBit(0);
        group.occupied.set(slot);
        group.members.put(entity, new Lease(slot, tick));
        return slot;
    }

    public void release(UUID player, UUID entity) {
        Group group = groups.get(player);
        if (group == null) return;
        Lease lease = group.members.remove(entity);
        if (lease != null) group.occupied.clear(lease.slot);
        if (group.members.isEmpty()) groups.remove(player);
    }
}
