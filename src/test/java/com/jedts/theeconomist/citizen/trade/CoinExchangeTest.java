package com.jedts.theeconomist.citizen.trade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoinExchangeTest {
    @Test void exactCopperPaymentNeedsNoChange() {
        CoinPlan plan = CoinExchange.plan(new CoinStock(3, 0, 0), new CoinStock(0, 0, 0), 3).orElseThrow();
        assertEquals(new CoinStock(3, 0, 0), plan.payment());
        assertEquals(0, plan.change().value());
    }

    @Test void silverCanPayWhenSellerHasSevenCopperChange() {
        CoinPlan plan = CoinExchange.plan(new CoinStock(0, 1, 0), new CoinStock(7, 0, 0), 3).orElseThrow();
        assertEquals(10, plan.payment().value());
        assertEquals(7, plan.change().value());
        assertEquals(3, plan.payment().value() - plan.change().value());
    }

    @Test void missingChangeRejectsThePayment() {
        assertTrue(CoinExchange.plan(new CoinStock(0, 1, 0), new CoinStock(6, 0, 0), 3).isEmpty());
    }

    @Test void payerAndReceiverCannotCreateValue() {
        CoinStock payer = new CoinStock(5, 2, 1);
        CoinStock seller = new CoinStock(9, 1, 0);
        CoinPlan plan = CoinExchange.plan(payer, seller, 17).orElseThrow();
        assertTrue(payer.contains(plan.payment()));
        assertTrue(seller.contains(plan.change()));
        assertEquals(17, plan.payment().value() - plan.change().value());
    }
}
