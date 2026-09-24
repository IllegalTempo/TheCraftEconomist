package com.jedts.theeconomist.citizen.info;

import com.jedts.theeconomist.TheEconomistMod;
import com.jedts.theeconomist.citizen.behavior.decision.CitizenDecisionFactor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Server-authoritative job and contract details for the citizen info screen. */
public record CitizenInfoPayload(int entityId, String occupation, String employer, int wage, String workHours,
                                 String contractStatus, String contractTarget, int contractBounty,
                                 long contractDeadline, CitizenOverview overview,
                                 CitizenInfoDetails details, List<CitizenDecisionView> decisions,
                                 CitizenCraftingPreview craftingPreview) implements CustomPacketPayload {
    private static final int MAX_DECISIONS = 16;
    public static final Type<CitizenInfoPayload> TYPE = CustomPacketPayload.createType(TheEconomistMod.MOD_ID + "/citizen_info");
    public static final StreamCodec<RegistryFriendlyByteBuf, CitizenInfoPayload> CODEC =
            StreamCodec.of(CitizenInfoPayload::write, CitizenInfoPayload::read);

    public CitizenInfoPayload {
        if (contractTarget.length() > 256) contractTarget = contractTarget.substring(0, 256);
        decisions = List.copyOf(decisions);
        if (decisions.size() > MAX_DECISIONS)
            throw new IllegalArgumentException("at most " + MAX_DECISIONS + " citizen decisions are supported");
    }

    public CitizenInfoPayload(int entityId, String occupation, String employer, int wage, String workHours,
                              String contractStatus, String contractTarget, int contractBounty,
                              long contractDeadline, CitizenOverview overview, CitizenInfoDetails details) {
        this(entityId, occupation, employer, wage, workHours, contractStatus, contractTarget, contractBounty,
                contractDeadline, overview, details, List.of(), null);
    }

    public CitizenInfoPayload(int entityId, String occupation, String employer, int wage, String workHours,
                              String contractStatus, String contractTarget, int contractBounty,
                              long contractDeadline, CitizenOverview overview, CitizenInfoDetails details,
                              List<CitizenDecisionView> decisions) {
        this(entityId, occupation, employer, wage, workHours, contractStatus, contractTarget, contractBounty,
                contractDeadline, overview, details, decisions, null);
    }

    private static void write(RegistryFriendlyByteBuf buf, CitizenInfoPayload payload) {
        buf.writeVarInt(payload.entityId);
        buf.writeUtf(payload.occupation, 32);
        buf.writeUtf(payload.employer, 128);
        buf.writeVarInt(payload.wage);
        buf.writeUtf(payload.workHours, 32);
        buf.writeUtf(payload.contractStatus, 32);
        buf.writeUtf(payload.contractTarget, 256);
        buf.writeVarInt(payload.contractBounty);
        buf.writeVarLong(payload.contractDeadline);
        CitizenOverview overview = payload.overview;
        buf.writeUtf(overview.name(), 128);
        buf.writeUtf(overview.lifeStage(), 32);
        buf.writeUtf(overview.gender(), 16);
        buf.writeUtf(overview.familyRole(), 16);
        buf.writeUtf(overview.familyMembers(), 512);
        buf.writeUtf(overview.activeBehavior(), 32);
        buf.writeUtf(overview.behaviorStatus(), 128);
        buf.writeUtf(overview.model(), 32);
        buf.writeUtf(overview.profile(), 128);
        buf.writeUtf(overview.health(), 32);
        buf.writeUtf(overview.movementSpeed(), 32);
        buf.writeUtf(overview.followRange(), 32);
        buf.writeUtf(overview.position(), 64);
        buf.writeVarInt(overview.hunger());
        buf.writeVarInt(overview.energy());
        buf.writeVarInt(overview.safety());
        buf.writeVarInt(overview.morale());
        buf.writeVarInt(overview.intelligence());
        buf.writeVarInt(overview.anger());
        buf.writeVarInt(overview.education());
        buf.writeVarInt(payload.details.inventory().size());
        for (ItemStack stack : payload.details.inventory()) ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
        buf.writeUtf(payload.details.farmStatus(), 128);
        buf.writeUtf(payload.details.home(), 64);
        buf.writeVarInt(payload.details.claimedFarmland());
        buf.writeVarInt(payload.details.sellableWheat());
        buf.writeVarLong(payload.details.crownBalance());
        buf.writeVarInt(payload.details.wheatPrice());
        buf.writeVarInt(payload.details.seedPrice());
        buf.writeVarInt(payload.details.hoePrice());
        buf.writeVarInt(payload.decisions.size());
        for (CitizenDecisionView decision : payload.decisions) {
            buf.writeUtf(decision.actionId(), 32);
            buf.writeUtf(decision.displayName(), 64);
            buf.writeBoolean(decision.eligible());
            buf.writeDouble(decision.score());
            buf.writeBoolean(decision.emergencyOverride());
            buf.writeUtf(decision.explanation(), 128);
            buf.writeVarInt(decision.factors().size());
            for (CitizenDecisionFactor factor : decision.factors()) {
                buf.writeUtf(factor.id(), 32);
                buf.writeDouble(factor.value());
                buf.writeDouble(factor.scoreContribution());
            }
            buf.writeBoolean(decision.selected());
            buf.writeBoolean(decision.active());
        }
        buf.writeBoolean(payload.craftingPreview != null);
        if (payload.craftingPreview != null) {
            for (ItemStack stack : payload.craftingPreview.ingredients()) ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.craftingPreview.result());
        }
    }

    private static CitizenInfoPayload read(RegistryFriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        String occupation = buf.readUtf(32);
        String employer = buf.readUtf(128);
        int wage = buf.readVarInt();
        String workHours = buf.readUtf(32);
        String contractStatus = buf.readUtf(32);
        String contractTarget = buf.readUtf(256);
        int contractBounty = buf.readVarInt();
        long contractDeadline = buf.readVarLong();
        CitizenOverview overview = new CitizenOverview(buf.readUtf(128), buf.readUtf(32), buf.readUtf(16),
                buf.readUtf(16), buf.readUtf(512), buf.readUtf(32), buf.readUtf(128), buf.readUtf(32), buf.readUtf(128), buf.readUtf(32),
                buf.readUtf(32), buf.readUtf(32), buf.readUtf(64),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt());
        int count = buf.readVarInt();
        if (count != 27) throw new IllegalArgumentException("invalid Citizen inventory size");
        List<ItemStack> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) items.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
        CitizenInfoDetails details = new CitizenInfoDetails(items, buf.readUtf(128), buf.readUtf(64), buf.readVarInt(),
                buf.readVarInt(), buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        int decisionCount = buf.readVarInt();
        if (decisionCount < 0 || decisionCount > MAX_DECISIONS)
            throw new IllegalArgumentException("invalid citizen decision count");
        List<CitizenDecisionView> decisions = new ArrayList<>(decisionCount);
        for (int i = 0; i < decisionCount; i++) {
            String actionId = buf.readUtf(32);
            String displayName = buf.readUtf(64);
            boolean eligible = buf.readBoolean();
            double score = buf.readDouble();
            boolean emergency = buf.readBoolean();
            String explanation = buf.readUtf(128);
            int factorCount = buf.readVarInt();
            if (factorCount < 0 || factorCount > 6) throw new IllegalArgumentException("invalid decision factor count");
            List<CitizenDecisionFactor> factors = new ArrayList<>(factorCount);
            for (int factor = 0; factor < factorCount; factor++)
                factors.add(new CitizenDecisionFactor(buf.readUtf(32), buf.readDouble(), buf.readDouble()));
            decisions.add(new CitizenDecisionView(actionId, displayName, eligible, score, emergency, explanation,
                    factors, buf.readBoolean(), buf.readBoolean()));
        }
        CitizenCraftingPreview preview = null;
        if (buf.readBoolean()) {
            List<ItemStack> ingredients = new ArrayList<>(9);
            for (int i = 0; i < 9; i++) ingredients.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
            preview = new CitizenCraftingPreview(ingredients, ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
        }
        return new CitizenInfoPayload(entityId, occupation, employer, wage, workHours,
                contractStatus, contractTarget, contractBounty, contractDeadline, overview, details, decisions, preview);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
