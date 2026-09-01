package com.diamend.spyglass.inspect;

import java.util.UUID;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.diamend.spyglass.util.Safe;

/**
 * Works out who {@code /spy Notch} means.
 *
 * <p>Exact name first, then a partial match against people online, then a UUID,
 * and finally the players this server has a save for. The last step is why an
 * offline name works at all — and it deliberately avoids Bukkit's name-based
 * offline lookup, which will happily invent a profile for a name nobody has ever
 * used (and block on a web request to do it).
 */
public final class Targets {

    /**
     * Somebody to inspect.
     *
     * @param online  the live player, or null when they are not connected
     * @param offline the profile, always present
     */
    public record Target(Player online, OfflinePlayer offline, UUID uuid, String name) {

        public boolean isOnline() {
            return online != null;
        }

        /**
         * The same target under a name we trust more than the server's.
         *
         * <p>A UUID nobody has logged in as since the last restart has no name
         * as far as Bukkit is concerned, even when the server's own
         * {@code usercache.json} says otherwise.
         */
        public Target named(String better) {
            return better == null || better.isBlank()
                    ? this : new Target(online, offline, uuid, better);
        }

        /** How the report should label them. */
        public String label() {
            return name + (isOnline() ? " (online)" : " (offline)");
        }
    }

    private Targets() {
    }

    /** Resolves a name or UUID, or returns null when nobody matches. */
    public static Target resolve(Server server, String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String wanted = query.trim();

        Player exact = Safe.call(() -> server.getPlayerExact(wanted), null);
        if (exact != null) {
            return of(exact);
        }
        Player partial = Safe.call(() -> server.getPlayer(wanted), null);
        if (partial != null) {
            return of(partial);
        }
        UUID uuid = parseUuid(wanted);
        if (uuid != null) {
            OfflinePlayer offline = Safe.call(() -> server.getOfflinePlayer(uuid), null);
            if (offline != null) {
                Player online = Safe.call(offline::getPlayer, null);
                // A UUID nobody has logged in as has no name to give, and asking
                // for one can go looking further than we want it to.
                String name = Safe.call(offline::getName, uuid.toString());
                return new Target(online, offline, uuid, name);
            }
            return null;
        }
        return known(server, wanted);
    }

    /** Looks through the players this server has actually seen. */
    private static Target known(Server server, String name) {
        OfflinePlayer[] all = Safe.call(server::getOfflinePlayers, new OfflinePlayer[0]);
        for (OfflinePlayer candidate : all) {
            String candidateName = Safe.call(candidate::getName, null);
            if (candidateName != null && candidateName.equalsIgnoreCase(name)) {
                return new Target(candidate.getPlayer(), candidate,
                        candidate.getUniqueId(), candidateName);
            }
        }
        return null;
    }

    private static Target of(Player player) {
        return new Target(player, player, player.getUniqueId(), player.getName());
    }

    private static UUID parseUuid(String text) {
        if (text.length() != 36 || text.indexOf('-') < 0) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
