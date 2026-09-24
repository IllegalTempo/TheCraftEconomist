package com.jedts.theeconomist.citizen.trade;

import java.util.Optional;

/** Selects only coins that physically exist on each side. */
public final class CoinExchange {
    private CoinExchange() { }

    public static Optional<CoinPlan> plan(CoinStock payer, CoinStock receiver, int price) {
        if (price <= 0) return Optional.empty();
        long maxPayment = Math.min(payer.value(), (long) price + 99);
        for (long paid = price; paid <= maxPayment; paid++) {
            Optional<CoinStock> payment = exact(payer, paid);
            if (payment.isEmpty()) continue;
            Optional<CoinStock> change = exact(receiver, paid - price);
            if (change.isPresent()) return Optional.of(new CoinPlan(payment.get(), change.get()));
        }
        return Optional.empty();
    }

    private static Optional<CoinStock> exact(CoinStock source, long target) {
        CoinStock best = null;
        int maxGold = (int) Math.min(source.gold(), target / 100);
        for (int gold = 0; gold <= maxGold; gold++) {
            long afterGold = target - 100L * gold;
            int maxSilver = (int) Math.min(source.silver(), afterGold / 10);
            for (int silver = 0; silver <= maxSilver; silver++) {
                long copper = afterGold - 10L * silver;
                if (copper > source.copper()) continue;
                CoinStock candidate = new CoinStock((int) copper, silver, gold);
                if (best == null || candidate.coins() < best.coins()) best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }
}
