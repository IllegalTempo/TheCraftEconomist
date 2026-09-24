package com.jedts.theeconomist.trade.request;

import com.jedts.theeconomist.trade.domain.TradePair;
import com.jedts.theeconomist.trade.domain.TradeRules;
import java.util.Objects;
import java.util.UUID;

public final class TradeRequest {
    private final UUID requestId;
    private final TradePair pair;
    private final UUID initiator;
    private final UUID recipient;
    private final long expiresAtTick;
    private boolean initiatorAccepted;
    private boolean recipientAccepted;
    private TradeRequestStatus status = TradeRequestStatus.PENDING;

    private TradeRequest(UUID requestId, UUID initiator, UUID recipient, long createdAtTick) {
        this.requestId = Objects.requireNonNull(requestId);
        this.pair = TradePair.of(initiator, recipient);
        this.initiator = Objects.requireNonNull(initiator);
        this.recipient = Objects.requireNonNull(recipient);
        this.expiresAtTick = createdAtTick + TradeRules.REQUEST_TIMEOUT_TICKS;
    }

    public static TradeRequest create(UUID requestId, UUID initiator, UUID recipient, long createdAtTick) {
        return new TradeRequest(requestId, initiator, recipient, createdAtTick);
    }

    public synchronized TradeRequestResult accept(UUID participant, long nowTick) {
        if (status == TradeRequestStatus.EXPIRED) return TradeRequestResult.EXPIRED;
        if (status != TradeRequestStatus.PENDING || nowTick >= expiresAtTick) {
            status = TradeRequestStatus.EXPIRED;
            return TradeRequestResult.EXPIRED;
        }
        if (initiator.equals(participant)) initiatorAccepted = true;
        else if (recipient.equals(participant)) recipientAccepted = true;
        else return TradeRequestResult.REJECTED;
        if (initiatorAccepted && recipientAccepted) {
            status = TradeRequestStatus.ACCEPTED;
            return TradeRequestResult.READY_TO_OPEN;
        }
        return TradeRequestResult.WAITING;
    }

    public synchronized TradeRequestResult expire(long nowTick) {
        if (status == TradeRequestStatus.PENDING && nowTick >= expiresAtTick) {
            status = TradeRequestStatus.EXPIRED;
            return TradeRequestResult.EXPIRED;
        }
        return status == TradeRequestStatus.EXPIRED ? TradeRequestResult.EXPIRED : TradeRequestResult.WAITING;
    }

    public UUID requestId() { return requestId; }
    public TradePair pair() { return pair; }
    public long expiresAtTick() { return expiresAtTick; }
    public synchronized TradeRequestStatus status() { return status; }
    public synchronized boolean accepted(UUID participant) {
        if (initiator.equals(participant)) return initiatorAccepted;
        if (recipient.equals(participant)) return recipientAccepted;
        return false;
    }
}
