package com.jedts.theeconomist.citizen.info;

import com.jedts.theeconomist.TheEconomistMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server-authoritative job and contract details for the citizen info screen. */
public record CitizenInfoPayload(int entityId, String occupation, String employer, int wage, String workHours,
                                 String contractStatus, String contractTarget, int contractBounty,
                                 long contractDeadline) implements CustomPacketPayload {
    public static final Type<CitizenInfoPayload> TYPE = CustomPacketPayload.createType(TheEconomistMod.MOD_ID + "/citizen_info");
    public static final StreamCodec<RegistryFriendlyByteBuf, CitizenInfoPayload> CODEC =
            StreamCodec.of(CitizenInfoPayload::write, CitizenInfoPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, CitizenInfoPayload payload) {
        buf.writeVarInt(payload.entityId);
        buf.writeUtf(payload.occupation, 32);
        buf.writeUtf(payload.employer, 128);
        buf.writeVarInt(payload.wage);
        buf.writeUtf(payload.workHours, 32);
        buf.writeUtf(payload.contractStatus, 32);
        buf.writeUtf(payload.contractTarget, 128);
        buf.writeVarInt(payload.contractBounty);
        buf.writeVarLong(payload.contractDeadline);
    }

    private static CitizenInfoPayload read(RegistryFriendlyByteBuf buf) {
        return new CitizenInfoPayload(buf.readVarInt(), buf.readUtf(32), buf.readUtf(128), buf.readVarInt(),
                buf.readUtf(32), buf.readUtf(32), buf.readUtf(128), buf.readVarInt(), buf.readVarLong());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
