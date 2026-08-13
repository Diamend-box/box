package com.diamend.spyglass.report;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.CommandSender;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * A block of report lines, built once and then either sent to whoever asked or
 * written to a file.
 *
 * <p>Reports are held as structured lines rather than as finished strings so the
 * same report can come out coloured in a console, coloured in chat, and plain in
 * a dump file, without three renderers drifting apart.
 */
public final class Report {

    /** Label column width — wide enough for the longest field name in a section. */
    private static final int LABEL_WIDTH = 20;

    public enum Kind {
        /** The one line naming who this report is about. */
        TITLE,
        /** A section heading inside the report. */
        HEADER,
        /** A name and its value. */
        FIELD,
        /** A line that is all value — a list entry, a raw NBT line. */
        TEXT,
        /** An aside: an explanation, a warning, an empty-section note. */
        NOTE,
        BLANK
    }

    public record Line(Kind kind, String label, String value) {
    }

    private final List<Line> lines = new ArrayList<>();

    public Report title(String text) {
        lines.add(new Line(Kind.TITLE, null, text));
        return this;
    }

    public Report header(String text) {
        if (!lines.isEmpty()) {
            lines.add(new Line(Kind.BLANK, null, ""));
        }
        lines.add(new Line(Kind.HEADER, null, text));
        return this;
    }

    public Report field(String label, Object value) {
        lines.add(new Line(Kind.FIELD, label, value == null ? "n/a" : String.valueOf(value)));
        return this;
    }

    /** A field only worth a line when it has something to say. */
    public Report fieldIf(boolean condition, String label, Object value) {
        return condition ? field(label, value) : this;
    }

    public Report text(String value) {
        lines.add(new Line(Kind.TEXT, null, value == null ? "" : value));
        return this;
    }

    public Report note(String value) {
        lines.add(new Line(Kind.NOTE, null, value == null ? "" : value));
        return this;
    }

    public Report blank() {
        lines.add(new Line(Kind.BLANK, null, ""));
        return this;
    }

    public Report append(Report other) {
        if (other != null) {
            lines.addAll(other.lines);
        }
        return this;
    }

    public List<Line> lines() {
        return List.copyOf(lines);
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public int size() {
        return lines.size();
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /** The whole report as plain text, one string per line — for dump files. */
    public List<String> plain() {
        List<String> out = new ArrayList<>(lines.size());
        for (Line line : lines) {
            out.add(plain(line));
        }
        return out;
    }

    private static String plain(Line line) {
        return switch (line.kind()) {
            case TITLE -> "=== " + line.value() + " ===";
            case HEADER -> "-- " + line.value() + " --";
            case FIELD -> "  " + pad(line.label()) + line.value();
            case TEXT -> "  " + line.value();
            case NOTE -> "  " + line.value();
            case BLANK -> "";
        };
    }

    private static String pad(String label) {
        String name = label == null ? "" : label;
        if (name.length() >= LABEL_WIDTH) {
            return name + "  ";
        }
        return name + " ".repeat(LABEL_WIDTH - name.length());
    }

    private static Component render(Line line) {
        return switch (line.kind()) {
            case TITLE -> Component.text("=== " + line.value() + " ===", NamedTextColor.GOLD)
                    .decorate(TextDecoration.BOLD);
            case HEADER -> Component.text("-- " + line.value() + " --", NamedTextColor.AQUA);
            case FIELD -> Component.text("  " + pad(line.label()), NamedTextColor.GRAY)
                    .append(Component.text(line.value(), NamedTextColor.WHITE));
            case TEXT -> Component.text("  " + line.value(), NamedTextColor.WHITE);
            case NOTE -> Component.text("  " + line.value(), NamedTextColor.GRAY);
            case BLANK -> Component.empty();
        };
    }

    /** Sends the whole report, however long it is. */
    public void send(CommandSender sender) {
        for (Line line : lines) {
            sender.sendMessage(render(line));
        }
    }

    /**
     * Sends one page of the report, keeping the title on every page.
     *
     * @param page     1-based; clamped into range
     * @param pageSize lines per page, ignoring the title
     * @param moreHint the command to repeat for the next page, e.g.
     *                 {@code /spy Notch stats}; null to leave the hint off
     * @return the page actually shown
     */
    public int send(CommandSender sender, int page, int pageSize, String moreHint) {
        List<Line> titles = new ArrayList<>();
        List<Line> body = new ArrayList<>(lines);
        while (!body.isEmpty() && body.get(0).kind() == Kind.TITLE) {
            titles.add(body.remove(0));
        }
        int size = Math.max(1, pageSize);
        int pages = Math.max(1, (body.size() + size - 1) / size);
        int shown = Math.min(Math.max(1, page), pages);
        int from = (shown - 1) * size;
        int to = Math.min(body.size(), from + size);

        for (Line line : titles) {
            sender.sendMessage(render(line));
        }
        if (pages > 1) {
            sender.sendMessage(Component.text(
                    "  page " + shown + "/" + pages + " (" + body.size() + " lines)",
                    NamedTextColor.DARK_GRAY));
        }
        for (Line line : body.subList(from, to)) {
            sender.sendMessage(render(line));
        }
        if (pages > 1 && shown < pages && moreHint != null) {
            sender.sendMessage(Component.text(
                    "  more: " + moreHint + " " + (shown + 1), NamedTextColor.DARK_GRAY));
        }
        return shown;
    }
}
