// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp.cli.interactive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * FZF-like interactive command picker.
 *
 * <p>Renders a single-screen list that updates as the user types.  Arrow keys
 * move the selection, Enter confirms, Esc/q cancels.  Spaces in the query are
 * normalised to underscores so that queries like {@code jira get ticket} match
 * tool names.</p>
 *
 * <p>The picker deliberately avoids the alternate screen buffer to prevent the
 * blinking/flickering reported with the previous Python implementation.  It
 * redraws in place by moving the cursor and clearing lines.</p>
 */
public class CommandPicker {

    private final Terminal terminal;
    private final List<CommandItem> allCommands;
    private final int height;
    private final int width;

    private String query = "";
    private int selected = 0;
    private List<CommandItem> filtered;

    public CommandPicker(Terminal terminal, List<CommandItem> allCommands, int height, int width) {
        this.terminal = terminal;
        this.allCommands = Collections.unmodifiableList(new ArrayList<>(allCommands));
        this.filtered = new ArrayList<>(allCommands);
        this.height = Math.max(5, height);
        this.width = Math.max(20, width);
    }

    /**
     * Runs the picker and returns the selected command, or {@code null} if cancelled.
     */
    public CommandItem pick() {
        terminal.clearScreen();
        draw();

        while (true) {
            int code = terminal.read();
            if (code < 0) {
                return null;
            }
            char ch = (char) code;

            if (ch == '\u001b') {
                String seq = terminal.readEscapeSequence(50);
                if ("\u001b".equals(seq) || seq.length() == 1) {
                    return null; // bare Esc
                }
                switch (seq) {
                    case "\u001b[A":
                    case "\u001bOA":
                        selected = Math.max(0, selected - 1);
                        draw();
                        break;
                    case "\u001b[B":
                    case "\u001bOB":
                        selected = Math.min(filtered.size() - 1, selected + 1);
                        draw();
                        break;
                    case "\u001b[5~":
                        selected = Math.max(0, selected - visibleCount());
                        draw();
                        break;
                    case "\u001b[6~":
                        selected = Math.min(filtered.size() - 1, selected + visibleCount());
                        draw();
                        break;
                    case "\u001b[H":
                    case "\u001bOH":
                        selected = 0;
                        draw();
                        break;
                    case "\u001b[F":
                    case "\u001bOF":
                        selected = Math.max(0, filtered.size() - 1);
                        draw();
                        break;
                    default:
                        // ignore unknown escape sequences
                }
            } else if (code == 127 || code == 8) { // Backspace / Ctrl-H
                if (!query.isEmpty()) {
                    query = query.substring(0, query.length() - 1);
                    selected = 0;
                    applyFilter();
                    draw();
                }
            } else if (code == 10 || code == 13) { // Enter
                if (selected >= 0 && selected < filtered.size()) {
                    return filtered.get(selected);
                }
                return null;
            } else if (code == 3 || code == 4 || ch == 'q') { // Ctrl-C / Ctrl-D / q
                return null;
            } else if (code >= 32 && code <= 126) { // printable ASCII
                query += (ch == ' ') ? "_" : ch;
                selected = 0;
                applyFilter();
                draw();
            }
        }
    }

    private void applyFilter() {
        String q = query.toLowerCase();
        filtered = new ArrayList<>();
        for (CommandItem item : allCommands) {
            if (q.isEmpty() || item.getSearchableText().contains(q)) {
                filtered.add(item);
            }
        }
        selected = Math.min(selected, Math.max(0, filtered.size() - 1));
    }

    private void draw() {
        terminal.moveCursor(1, 1);
        terminal.clearLine();
        terminal.write("DMTools interactive command picker  |  type to filter, ↑↓ to move, Enter to select, Esc/q to quit");
        terminal.moveCursor(2, 1);
        terminal.clearLine();
        terminal.write("> " + query);
        terminal.moveCursor(3, 1);
        terminal.clearLine();
        terminal.write(repeat('-', width));

        List<String> rendered = renderVisibleItems();
        int row = 4;
        for (String line : rendered) {
            terminal.moveCursor(row, 1);
            terminal.clearLine();
            terminal.write(line);
            row++;
        }
        while (row < height) {
            terminal.moveCursor(row, 1);
            terminal.clearLine();
            row++;
        }

        terminal.moveCursor(height, 1);
        terminal.clearLine();
        terminal.write(filtered.size() + " / " + allCommands.size() + " commands");
    }

    private List<String> renderVisibleItems() {
        if (filtered.isEmpty()) {
            return Collections.singletonList("No matches.");
        }

        List<RenderedItem> blocks = new ArrayList<>();
        for (int i = 0; i < filtered.size(); i++) {
            blocks.add(new RenderedItem(formatItem(filtered.get(i), i == selected)));
        }

        int visible = visibleCount();
        int start = Math.max(0, Math.min(selected, filtered.size() - 1));

        int first = 0;
        int rowsUsed = 0;
        for (int i = start; i >= 0; i--) {
            int itemRows = blocks.get(i).rowCount();
            if (rowsUsed + itemRows > visible) {
                first = i + 1;
                break;
            }
            rowsUsed += itemRows;
            first = i;
        }

        List<String> result = new ArrayList<>();
        rowsUsed = 0;
        for (int i = first; i < filtered.size(); i++) {
            for (String line : blocks.get(i).lines) {
                if (rowsUsed >= visible) {
                    return result;
                }
                result.add(line.substring(0, Math.min(line.length(), width)));
                rowsUsed++;
            }
        }
        return result;
    }

    private List<String> formatItem(CommandItem item, boolean selected) {
        String prefix = selected ? "> " : "  ";
        String kind = pad(item.getKind().getId(), 4);
        String integration = pad(item.getIntegration(), 12);

        int overhead = prefix.length() + kind.length() + 2 + integration.length() + 2;
        int available = Math.max(0, width - overhead);
        int nameW;
        int descW;
        if (available < 30) {
            nameW = available;
            descW = 0;
        } else {
            nameW = Math.min(40, Math.max(20, available / 2));
            descW = Math.max(0, available - nameW - 2);
        }

        String namePart = truncate(item.getName(), nameW);
        List<String> lines = new ArrayList<>();
        lines.add(prefix + kind + "  " + integration + "  " + pad(namePart, nameW));
        if (descW > 0) {
            String indent = spaces(overhead + nameW + 2);
            String desc = item.getDescription() == null ? "" : item.getDescription();
            for (String chunk : wrap(desc, descW)) {
                lines.add(indent + chunk);
            }
        }
        return lines;
    }

    private int visibleCount() {
        return Math.max(1, height - 4);
    }

    private static List<String> wrap(String text, int width) {
        if (text == null || text.isEmpty() || width <= 0) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<>();
        String remaining = text;
        while (!remaining.isEmpty()) {
            if (remaining.length() <= width) {
                lines.add(remaining);
                break;
            }
            String chunk = remaining.substring(0, width);
            int split = chunk.lastIndexOf(' ');
            if (split <= 0) {
                split = width;
            }
            lines.add(remaining.substring(0, split));
            remaining = remaining.substring(split).trim();
        }
        return lines;
    }

    private static String pad(String s, int width) {
        if (s.length() >= width) {
            return s.substring(0, width);
        }
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static String truncate(String s, int width) {
        if (s.length() <= width) {
            return s;
        }
        if (width <= 3) {
            return s.substring(0, width);
        }
        return s.substring(0, width - 3) + "...";
    }

    private static String spaces(int count) {
        if (count <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static String repeat(char c, int count) {
        if (count <= 0) {
            return "";
        }
        char[] arr = new char[count];
        java.util.Arrays.fill(arr, c);
        return new String(arr);
    }

    private static final class RenderedItem {
        final List<String> lines;

        RenderedItem(List<String> lines) {
            this.lines = lines;
        }

        int rowCount() {
            return lines.size();
        }
    }
}
