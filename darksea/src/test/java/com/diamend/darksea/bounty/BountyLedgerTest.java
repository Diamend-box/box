package com.diamend.darksea.bounty;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BountyLedgerTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test
    void placeAccumulatesAndPoolsAcrossSponsors() {
        BountyLedger ledger = new BountyLedger();
        assertEquals(50, ledger.place(a, 50), "first sponsor sets the pool");
        assertEquals(80, ledger.place(a, 30), "a second sponsor stacks onto the same head");
        assertEquals(80, ledger.get(a));
        assertTrue(ledger.has(a));
        assertEquals(0, ledger.get(b), "an unbountied head is worth nothing");
        assertFalse(ledger.has(b));
    }

    @Test
    void claimPaysTheWholePurseOnceThenZero() {
        BountyLedger ledger = new BountyLedger();
        ledger.place(a, 120);
        assertEquals(120, ledger.claim(a), "the killer takes the whole pool");
        assertEquals(0, ledger.get(a), "the head is clear after a claim");
        assertEquals(0, ledger.claim(a), "no double payout on a cleared head");
        assertEquals(0, ledger.claim(b), "claiming an unbountied head pays nothing");
    }

    @Test
    void nonPositiveBountiesAreRejected() {
        BountyLedger ledger = new BountyLedger();
        assertThrows(IllegalArgumentException.class, () -> ledger.place(a, 0));
        assertThrows(IllegalArgumentException.class, () -> ledger.place(a, -10));
        assertThrows(IllegalArgumentException.class, () -> ledger.place(null, 10));
    }

    @Test
    void topRanksRichestFirstAndRespectsLimit() {
        BountyLedger ledger = new BountyLedger();
        ledger.place(a, 30);
        ledger.place(b, 90);
        ledger.place(c, 60);
        List<Map.Entry<UUID, Integer>> top2 = ledger.top(2);
        assertEquals(2, top2.size());
        assertEquals(b, top2.get(0).getKey(), "richest head first");
        assertEquals(c, top2.get(1).getKey());
        assertEquals(3, ledger.top(99).size(), "asking for more than exist returns all");
    }

    @Test
    void snapshotRoundTripsAndLoadDropsBadRows() {
        BountyLedger ledger = new BountyLedger();
        ledger.place(a, 40);
        ledger.place(b, 75);
        Map<String, Integer> snap = ledger.snapshot();

        BountyLedger restored = new BountyLedger();
        restored.load(snap);
        assertEquals(40, restored.get(a));
        assertEquals(75, restored.get(b));
        assertEquals(2, restored.size());

        // A file hand-edited with junk must not poison the load.
        restored.load(Map.of("not-a-uuid", 10, b.toString(), -5, a.toString(), 20));
        assertEquals(20, restored.get(a), "the one valid, positive row survives");
        assertEquals(0, restored.get(b), "a non-positive row is dropped");
        assertEquals(1, restored.size(), "malformed UUID and bad amount are skipped");
    }
}
