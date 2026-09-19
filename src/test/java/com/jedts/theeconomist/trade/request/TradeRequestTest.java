package com.jedts.theeconomist.trade.request;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TradeRequestTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test void bothParticipantsMustAccept() {
        TradeRequest request = TradeRequest.create(UUID.randomUUID(), A, B, 1_000L);
        assertEquals(TradeRequestResult.WAITING, request.accept(A, 1_010L));
        assertEquals(TradeRequestResult.READY_TO_OPEN, request.accept(B, 1_011L));
        assertEquals(TradeRequestStatus.ACCEPTED, request.status());
    }

    @Test void nonparticipantAndExpiryAreRejected() {
        TradeRequest request = TradeRequest.create(UUID.randomUUID(), A, B, 1_000L);
        assertEquals(TradeRequestResult.REJECTED, request.accept(UUID.randomUUID(), 1_001L));
        assertEquals(TradeRequestResult.EXPIRED, request.expire(1_300L));
        assertEquals(TradeRequestStatus.EXPIRED, request.status());
    }
}
