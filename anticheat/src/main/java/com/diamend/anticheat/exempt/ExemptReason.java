package com.diamend.anticheat.exempt;

/** Why a player was skipped by the checks on a given tick. {@code null} means not exempt. */
public enum ExemptReason {
    BYPASS_PERMISSION,
    GAMEMODE,
    GLIDING,
    RIPTIDE,
    VEHICLE,
    DEAD,
    LOGIN_GRACE,
    RECENT_TELEPORT,
    HIGH_PING,
    SERVER_LAG
}
