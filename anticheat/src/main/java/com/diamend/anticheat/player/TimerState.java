package com.diamend.anticheat.player;

/**
 * A token-bucket clock for the Timer check.
 *
 * <p>Real time hands the player one token every 50 ms (one server tick). Every
 * movement ({@code PlayerFlying}) packet spends one token. A vanilla client
 * sends ~20 such packets a second, so the balance hovers around zero. A client
 * running a sped-up game tick sends 30-40+ packets a second, spending tokens
 * faster than time can mint them, and the balance goes sharply negative — that
 * is the Timer signal. A laggy client that bursts packets after a stall is
 * caught by the opposite bound and simply has its balance clamped.
 */
public final class TimerState {

    private static final double MS_PER_TOKEN = 50.0D;
    /** Don't let the balance bank more than this many tokens of "saved" time. */
    private static final double MAX_BALANCE = 10.0D;

    private double balance;
    private long lastRefill;
    private boolean started;

    /**
     * Mint tokens for the time elapsed, spend one for this packet, and return
     * the new balance. A balance well below zero means packets are arriving
     * faster than real time allows.
     */
    public synchronized double onFlyingPacket(long now) {
        if (!started) {
            started = true;
            lastRefill = now;
            // First packet just primes the clock; treat as neutral.
            return 0.0D;
        }
        double minted = (now - lastRefill) / MS_PER_TOKEN;
        lastRefill = now;
        balance += minted;
        if (balance > MAX_BALANCE) {
            balance = MAX_BALANCE;
        }
        balance -= 1.0D; // this packet spends a tick of time
        return balance;
    }

    /** Pull the balance back toward zero once a violation has been counted. */
    public synchronized void relax(double toward) {
        if (balance < toward) {
            balance = toward;
        }
    }

    public synchronized double balance() {
        return balance;
    }
}
