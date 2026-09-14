package com.ghost.spacemod.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** One S2C event per accepted cast, never a per-frame particle stream. */
public record CryoCastPayload(Identifier dimension, int caster, String animation,
        double x, double y, double z, long startTick, int duration) implements CustomPayload {
    public static final Id<CryoCastPayload> ID = new Id<>(Identifier.of("spacemod", "cryo_cast"));
    public static final PacketCodec<RegistryByteBuf, CryoCastPayload> CODEC = new PacketCodec<>() {
        @Override public CryoCastPayload decode(RegistryByteBuf buf) {
            return new CryoCastPayload(buf.readIdentifier(), buf.readVarInt(), buf.readString(32),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readLong(), buf.readVarInt());
        }
        @Override public void encode(RegistryByteBuf buf, CryoCastPayload value) {
            buf.writeIdentifier(value.dimension()); buf.writeVarInt(value.caster());
            buf.writeString(value.animation(), 32);
            buf.writeDouble(value.x()); buf.writeDouble(value.y()); buf.writeDouble(value.z());
            buf.writeLong(value.startTick()); buf.writeVarInt(value.duration());
        }
    };
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
