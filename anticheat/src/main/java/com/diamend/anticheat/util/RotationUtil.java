package com.diamend.anticheat.util;

/**
 * Pure helpers for turning a Minecraft yaw/pitch into a look direction and
 * measuring the angle between vectors. Used by the Scaffold raytrace-face check
 * and unit-testable without a server.
 */
public final class RotationUtil {

    private RotationUtil() {
    }

    /**
     * Unit look-direction vector for a given yaw/pitch (degrees), using
     * Minecraft's convention (yaw 0 = +Z, +90 = -X; pitch down positive).
     */
    public static double[] direction(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRad);
        double x = -cosPitch * Math.sin(yawRad);
        double y = -Math.sin(pitchRad);
        double z = cosPitch * Math.cos(yawRad);
        return new double[] { x, y, z };
    }

    /** Angle in degrees between two 3-vectors. Returns 0 if either has zero length. */
    public static double angleBetween(double[] a, double[] b) {
        double dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        double la = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        double lb = Math.sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2]);
        if (la < 1.0e-9 || lb < 1.0e-9) {
            return 0.0D;
        }
        double cos = MathUtil.clamp(dot / (la * lb), -1.0D, 1.0D);
        return Math.toDegrees(Math.acos(cos));
    }

    /**
     * Angle (degrees) between the player's look direction and the direction from
     * the eye to the centre of the clicked block face. A scaffold that places a
     * block it isn't looking at (e.g. sprinting forward, or staring at the sky)
     * produces a large angle here.
     *
     * @param faceNormal unit normal of the clicked face, e.g. {0,-1,0} for the
     *                   bottom face
     */
    public static double lookToFaceAngle(double eyeX, double eyeY, double eyeZ,
                                         float yaw, float pitch,
                                         double blockX, double blockY, double blockZ,
                                         double[] faceNormal) {
        double faceX = blockX + 0.5D + faceNormal[0] * 0.5D;
        double faceY = blockY + 0.5D + faceNormal[1] * 0.5D;
        double faceZ = blockZ + 0.5D + faceNormal[2] * 0.5D;
        double[] toFace = { faceX - eyeX, faceY - eyeY, faceZ - eyeZ };
        return angleBetween(direction(yaw, pitch), toFace);
    }
}
