package com.eventmanagement.cli;

import java.util.List;

/**
 * Small fixed-width table renderer. Column widths take East Asian wide
 * characters into account so names typed in Chinese stay aligned.
 */
public final class TablePrinter {

    private static final int MAX_COLUMN_WIDTH = 32;

    private TablePrinter() {
    }

    public static void print(ConsoleIO io, List<String> headers, List<List<String>> rows) {
        int[] widths = new int[headers.size()];
        for (int column = 0; column < headers.size(); column++) {
            widths[column] = Math.min(MAX_COLUMN_WIDTH, textWidth(headers.get(column)));
        }
        for (List<String> row : rows) {
            for (int column = 0; column < headers.size(); column++) {
                String cell = column < row.size() ? row.get(column) : "";
                widths[column] = Math.min(MAX_COLUMN_WIDTH,
                        Math.max(widths[column], textWidth(cell)));
            }
        }

        io.println(formatRow(headers, widths));
        StringBuilder separator = new StringBuilder();
        for (int column = 0; column < widths.length; column++) {
            if (column > 0) {
                separator.append("  ");
            }
            separator.append("-".repeat(widths[column]));
        }
        io.println(separator.toString());

        for (List<String> row : rows) {
            io.println(formatRow(row, widths));
        }
    }

    private static String formatRow(List<String> cells, int[] widths) {
        StringBuilder line = new StringBuilder();
        for (int column = 0; column < widths.length; column++) {
            if (column > 0) {
                line.append("  ");
            }
            String cell = column < cells.size() ? cells.get(column) : "";
            String trimmed = truncate(cell, widths[column]);
            line.append(trimmed);
            if (column < widths.length - 1) {
                line.append(" ".repeat(Math.max(0, widths[column] - textWidth(trimmed))));
            }
        }
        return line.toString();
    }

    private static String truncate(String value, int maxWidth) {
        if (textWidth(value) <= maxWidth) {
            return value;
        }
        int available = Math.max(1, maxWidth - 3);
        StringBuilder result = new StringBuilder();
        int used = 0;
        for (int index = 0; index < value.length(); ) {
            int codePoint = value.codePointAt(index);
            int width = codePointWidth(codePoint);
            if (used + width > available) {
                break;
            }
            result.appendCodePoint(codePoint);
            used += width;
            index += Character.charCount(codePoint);
        }
        return result.append("...").toString();
    }

    private static int textWidth(String value) {
        int width = 0;
        for (int index = 0; index < value.length(); ) {
            int codePoint = value.codePointAt(index);
            width += codePointWidth(codePoint);
            index += Character.charCount(codePoint);
        }
        return width;
    }

    private static int codePointWidth(int codePoint) {
        int type = Character.getType(codePoint);
        if (type == Character.NON_SPACING_MARK
                || type == Character.ENCLOSING_MARK
                || codePoint == 0x200D
                || codePoint == 0xFE0F) {
            return 0;
        }
        return isWide(codePoint) ? 2 : 1;
    }

    private static boolean isWide(int codePoint) {
        return (codePoint >= 0x1100 && codePoint <= 0x115F)
                || (codePoint >= 0x2E80 && codePoint <= 0xA4CF)
                || (codePoint >= 0xAC00 && codePoint <= 0xD7A3)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
                || (codePoint >= 0xFE10 && codePoint <= 0xFE19)
                || (codePoint >= 0xFE30 && codePoint <= 0xFE6F)
                || (codePoint >= 0xFF00 && codePoint <= 0xFF60)
                || (codePoint >= 0xFFE0 && codePoint <= 0xFFE6)
                || (codePoint >= 0x1F300 && codePoint <= 0x1FAFF)
                || (codePoint >= 0x20000 && codePoint <= 0x3FFFD);
    }
}
