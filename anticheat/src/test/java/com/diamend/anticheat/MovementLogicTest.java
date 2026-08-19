package com.diamend.anticheat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import com.diamend.anticheat.check.movement.FlightCheck;
import com.diamend.anticheat.check.movement.NoFallCheck;
import com.diamend.anticheat.check.movement.TimerCheck;
import com.diamend.anticheat.check.world.ScaffoldCheck;
import com.diamend.anticheat.config.AntiCheatConfig;
import com.diamend.anticheat.player.TimerState;
import com.diamend.anticheat.util.PhysicsUtil;
import com.diamend.anticheat.util.RotationUtil;

/**
 * Pure-logic tests for the v2 movement / world checks. All of these run without
 * a server: the checks take primitives and the physics is arithmetic.
 */
class MovementLogicTest {

    /** A config where every check reads its built-in defaults. */
    private AntiCheatConfig defaults() {
        return AntiCheatConfig.load(new YamlConfiguration());
    }

    // ---- Physics ---------------------------------------------------------

    @Test
    void hoveringExceedsGravityPrediction() {
        // Holding altitude: prev motionY 0, observed 0 -> excess > 0 (gravity says fall).
        double excess = PhysicsUtil.verticalExcess(0.0D, 0.0D);
        assertTrue(excess > 0.05D, "hover should read as positive vertical excess");
    }

    @Test
    void normalFallDoesNotExceedPrediction() {
        double prev = -0.4D;
        double predicted = PhysicsUtil.predictNextMotionY(prev);
        // A player falling exactly on the curve has ~zero excess.
        assertTrue(Math.abs(PhysicsUtil.verticalExcess(prev, predicted)) < 1.0e-9);
    }

    // ---- Flight ----------------------------------------------------------

    @Test
    void flightFlagsInstantBigLiftAndSustainedHover() {
        FlightCheck flight = new FlightCheck(this::defaults);
        assertTrue(flight.inspect(0.8D, 1).failed(), "big single lift should flag");
        assertTrue(flight.inspect(0.1D, 6).failed(), "sustained lift streak should flag");
        assertFalse(flight.inspect(0.1D, 2).failed(), "short lift streak should pass");
    }

    // ---- NoFall ----------------------------------------------------------

    @Test
    void noFallCatchesGroundClaimWhileFalling() {
        NoFallCheck nofall = new NoFallCheck(this::defaults);
        // Claims onGround while dropping fast: impossible regardless of cache.
        assertTrue(nofall.inspect(true, false, false, -0.6D, 0.0D).failed());
        // Claims onGround, no block below, meaningful fall: cache-proven.
        assertTrue(nofall.inspect(true, false, true, -0.1D, 3.0D).failed());
        // Admits it's airborne: NoFall never does this.
        assertFalse(nofall.inspect(false, false, true, -0.6D, 3.0D).failed());
        // Genuinely standing on a block.
        assertFalse(nofall.inspect(true, true, true, 0.0D, 0.0D).failed());
    }

    // ---- Timer -----------------------------------------------------------

    @Test
    void timerGoesNegativeUnderPacketFlood() {
        TimerState state = new TimerState();
        TimerCheck timer = new TimerCheck(this::defaults);
        long t = 1_000_000L;
        state.onFlyingPacket(t); // prime
        double balance = 0.0D;
        // Fire 20 packets only 10 ms apart -> way faster than 50 ms/tick.
        for (int i = 1; i <= 20; i++) {
            balance = state.onFlyingPacket(t + i * 10L);
        }
        assertTrue(balance <= -5.0D, "flooding packets should drive the balance negative");
        assertTrue(timer.inspect(balance).failed());
    }

    @Test
    void timerToleratesVanillaCadence() {
        TimerState state = new TimerState();
        TimerCheck timer = new TimerCheck(this::defaults);
        long t = 2_000_000L;
        state.onFlyingPacket(t);
        double balance = 0.0D;
        for (int i = 1; i <= 40; i++) {
            balance = state.onFlyingPacket(t + i * 50L); // exactly one tick apart
        }
        assertFalse(timer.inspect(balance).failed(), "vanilla 20 tps must not flag");
    }

    // ---- Scaffold --------------------------------------------------------

    @Test
    void scaffoldFlagsInstantPlacementsAndBadAngles() {
        ScaffoldCheck scaffold = new ScaffoldCheck(this::defaults);
        assertTrue(scaffold.inspectDelay(new long[] { 40, 45, 3 }).failed(),
                "a near-zero placement gap should flag");
        assertTrue(scaffold.inspectAngle(120.0D).failed(), "placing away from the look flags");
        assertFalse(scaffold.inspectAngle(20.0D).failed(), "looking at the face passes");
    }

    // ---- Rotation geometry ----------------------------------------------

    @Test
    void lookingAwayFromFaceIsWideAngle() {
        // Eye above a block, looking straight up (pitch -90), placing on the
        // block's bottom face beneath the feet: the angle must be large.
        double angle = RotationUtil.lookToFaceAngle(
                0.5D, 5.0D, 0.5D, 0.0F, -90.0F,
                0, 0, 0, new double[] { 0, 1, 0 });
        assertTrue(angle > 90.0D, "staring at the sky while bridging down is a wide angle");
    }
}
