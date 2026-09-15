package com.ghost.spacemod.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;

/** One bounded packet per eruption ring, containing server-approved surface positions. */
public record CryoSpikesPayload(Identifier dimension, int caster, long startTick, long endTick,
                               List<Spike> spikes) implements CustomPayload {
    public record Spike(double x, double y, double z, float height, float angle) {}
    public CryoSpikesPayload { spikes = List.copyOf(spikes); }
    public static final Id<CryoSpikesPayload> ID = new Id<>(Identifier.of("spacemod", "cryo_spikes"));
    public static final PacketCodec<RegistryByteBuf, CryoSpikesPayload> CODEC = new PacketCodec<>() {
        @Override public CryoSpikesPayload decode(RegistryByteBuf b) {
            Identifier dimension = b.readIdentifier(); int caster = b.readVarInt();
            long start = b.readLong(), end = b.readLong(); int count = b.readVarInt();
            if (count < 0 || count > 18) throw new IllegalArgumentException("Invalid crystal ring size");
            List<Spike> spikes = new ArrayList<>(count);
            for (int i=0;i<count;i++) spikes.add(new Spike(b.readDouble(),b.readDouble(),b.readDouble(),b.readFloat(),b.readFloat()));
            return new CryoSpikesPayload(dimension,caster,start,end,spikes);
        }
        @Override public void encode(RegistryByteBuf b, CryoSpikesPayload p) {
            b.writeIdentifier(p.dimension()); b.writeVarInt(p.caster());
            b.writeLong(p.startTick()); b.writeLong(p.endTick()); b.writeVarInt(p.spikes().size());
            for (Spike s:p.spikes()) { b.writeDouble(s.x()); b.writeDouble(s.y()); b.writeDouble(s.z()); b.writeFloat(s.height()); b.writeFloat(s.angle()); }
        }
    };
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
