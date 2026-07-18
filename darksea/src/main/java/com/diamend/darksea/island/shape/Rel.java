package com.diamend.darksea.island.shape;

/**
 * A block offset relative to an island's origin: y == 0 is sea level. This
 * package deliberately has no Bukkit imports so shapes can be compiled and
 * rendered standalone (previews) — the placer converts to world positions.
 */
public record Rel(int x, int y, int z) {

    public Rel offset(int dx, int dy, int dz) {
        return new Rel(x + dx, y + dy, z + dz);
    }

    public Rel above() {
        return new Rel(x, y + 1, z);
    }

    public Rel below() {
        return new Rel(x, y - 1, z);
    }
}
