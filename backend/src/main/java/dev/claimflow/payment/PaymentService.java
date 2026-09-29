package dev.claimflow.payment;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stand-in for a payment rail and a general ledger. The payout is idempotent on its key, so a job worker
 * retry after a timeout returns the original payment instead of paying twice.
 */
@Service
public class PaymentService {

    private final PayoutRepository payouts;
    private final Clock clock;
    private final BigDecimal ledgerRejectAbove;

    public PaymentService(PayoutRepository payouts, Clock clock,
                          @Value("${claimflow.ledger.reject-above}") BigDecimal ledgerRejectAbove) {
        this.payouts = payouts;
        this.clock = clock;
        this.ledgerRejectAbove = ledgerRejectAbove;
    }

    @Transactional
    public Payout execute(UUID claimId, String idempotencyKey, BigDecimal amount, String account) {
        return payouts.findByIdempotencyKey(idempotencyKey).orElseGet(() -> payouts.save(
                new Payout(claimId, idempotencyKey, amount, account,
                        "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), clock.instant())));
    }

    public Payout get(String idempotencyKey) {
        return payouts.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("No payout for " + idempotencyKey));
    }

    /** Compensation handler. Reversing twice is a no-op. */
    @Transactional
    public Payout reverse(String idempotencyKey) {
        Payout payout = payouts.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("No payout for " + idempotencyKey));
        if (payout.getReversedAt() == null) {
            payout.reverse(clock.instant());
        }
        return payout;
    }

    /** Posts the payout to the ledger. The mock ledger rejects amounts over a configurable limit. */
    public void postToLedger(Payout payout) {
        if (payout.getAmount().compareTo(ledgerRejectAbove) > 0) {
            throw new LedgerRejectedException("Ledger rejected " + payout.getReference()
                    + ": amount above posting limit " + ledgerRejectAbove);
        }
    }

    public static class LedgerRejectedException extends RuntimeException {
        public LedgerRejectedException(String message) {
            super(message);
        }
    }
}
