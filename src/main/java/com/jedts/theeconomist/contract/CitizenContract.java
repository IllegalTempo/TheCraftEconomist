package com.jedts.theeconomist.contract;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class CitizenContract {
    private final UUID id;
    private final UUID requesterId;
    private final ContractKind kind;
    private final String target;
    private final int quantity;
    private final int bounty;
    private final long deadlineTick;
    private final int requiredSkill;
    private UUID workerId;
    private ContractStatus status = ContractStatus.OPEN;
    private boolean bountyReserved;

    private CitizenContract(UUID id, UUID requesterId, ContractKind kind, String target, int quantity,
                            int bounty, long deadlineTick, int requiredSkill) {
        this.id = Objects.requireNonNull(id);
        this.requesterId = Objects.requireNonNull(requesterId);
        this.kind = Objects.requireNonNull(kind);
        this.target = Objects.requireNonNull(target);
        if (target.isBlank() || quantity <= 0 || bounty < 0 || deadlineTick < 0 || requiredSkill < 0 || requiredSkill > 100) {
            throw new IllegalArgumentException("invalid contract terms");
        }
        this.quantity = quantity;
        this.bounty = bounty;
        this.deadlineTick = deadlineTick;
        this.requiredSkill = requiredSkill;
    }

    public static CitizenContract service(UUID id, UUID requesterId, String target, int bounty, long deadlineTick, int requiredSkill) {
        return new CitizenContract(id, requesterId, ContractKind.SERVICE, target, 1, bounty, deadlineTick, requiredSkill);
    }

    public static CitizenContract resource(UUID id, UUID requesterId, String target, int quantity, int bounty, long deadlineTick, int requiredSkill) {
        return new CitizenContract(id, requesterId, ContractKind.RESOURCE, target, quantity, bounty, deadlineTick, requiredSkill);
    }

    static CitizenContract restore(UUID id, UUID requesterId, ContractKind kind, String target, int quantity,
                                   int bounty, long deadlineTick, int requiredSkill, UUID workerId,
                                   ContractStatus status, boolean bountyReserved) {
        CitizenContract contract = new CitizenContract(id, requesterId, kind, target, quantity, bounty, deadlineTick, requiredSkill);
        contract.workerId = workerId;
        contract.status = Objects.requireNonNull(status);
        contract.bountyReserved = bountyReserved;
        return contract;
    }

    public synchronized ContractResult accept(UUID worker, int skill, long nowTick) {
        if (status != ContractStatus.OPEN || worker.equals(requesterId)) return ContractResult.REJECTED;
        if (nowTick >= deadlineTick) {
            status = ContractStatus.FAILED;
            return ContractResult.EXPIRED;
        }
        if (skill < requiredSkill) return ContractResult.INSUFFICIENT_SKILL;
        workerId = Objects.requireNonNull(worker);
        bountyReserved = true;
        status = ContractStatus.ACCEPTED;
        return ContractResult.ACCEPTED;
    }

    public synchronized ContractResult start(UUID worker) {
        if (status != ContractStatus.ACCEPTED || !worker.equals(workerId)) return ContractResult.REJECTED;
        status = ContractStatus.IN_PROGRESS;
        return ContractResult.STARTED;
    }

    public synchronized ContractResult complete(UUID worker) {
        if (status != ContractStatus.IN_PROGRESS || !worker.equals(workerId)) return ContractResult.REJECTED;
        status = ContractStatus.COMPLETED;
        bountyReserved = false;
        return ContractResult.COMPLETED;
    }

    public synchronized ContractResult cancel(UUID requester) {
        if (!requester.equals(requesterId) || status == ContractStatus.COMPLETED || status == ContractStatus.CANCELLED || status == ContractStatus.FAILED) {
            return ContractResult.REJECTED;
        }
        status = ContractStatus.CANCELLED;
        bountyReserved = false;
        return ContractResult.CANCELLED;
    }

    public UUID id() { return id; }
    public UUID requesterId() { return requesterId; }
    public ContractKind kind() { return kind; }
    public String target() { return target; }
    public int quantity() { return quantity; }
    public int bounty() { return bounty; }
    public long deadlineTick() { return deadlineTick; }
    public int requiredSkill() { return requiredSkill; }
    public synchronized Optional<UUID> workerId() { return Optional.ofNullable(workerId); }
    public synchronized ContractStatus status() { return status; }
    public synchronized boolean bountyReserved() { return bountyReserved; }
    public synchronized int payout() { return status == ContractStatus.COMPLETED ? bounty : 0; }
}
