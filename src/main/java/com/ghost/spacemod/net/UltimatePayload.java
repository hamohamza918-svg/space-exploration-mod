package com.ghost.spacemod.net;

import com.ghost.spacemod.SpaceMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Empty C2S signal: "player pressed the Ultimate key". Server decides start-vs-shatter. */
public record UltimatePayload() implements CustomPayload {

    public static final CustomPayload.Id<UltimatePayload> ID =
            new CustomPayload.Id<>(Identifier.of(SpaceMod.MOD_ID, "ultimate"));

    public static final PacketCodec<PacketByteBuf, UltimatePayload> CODEC =
            PacketCodec.unit(new UltimatePayload());

    @Override
    public CustomPayload.Id<UltimatePayload> getId() {
        return ID;
    }
}
