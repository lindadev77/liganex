package tech.liganex.studio.module.rag.splitter;

import java.util.ArrayList;
import java.util.List;

/** Recursive semantic splitter with bounded character overlap. */
public class RecursiveTextSplitter {
    private static final String[] SEPARATORS = {"\n\n", "\n", "。", "；", "！", "？", ". ", " ", ""};

    private final int maxSize;
    private final int overlap;

    public RecursiveTextSplitter(int maxSize, int overlap) {
        if (maxSize <= 0 || overlap < 0 || overlap >= maxSize) {
            throw new IllegalArgumentException("invalid split size or overlap");
        }
        this.maxSize = maxSize;
        this.overlap = overlap;
    }

    public List<Span> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        int contentSize = Math.max(1, maxSize - overlap);
        List<String> pieces = merge(splitRecursively(text, contentSize, 0), contentSize);
        List<Span> spans = new ArrayList<>(pieces.size());
        int searchFrom = 0;
        String previous = "";
        for (String piece : pieces) {
            String prefix = previous.length() <= overlap ? previous
                    : previous.substring(previous.length() - overlap);
            String value = spans.isEmpty() ? piece : prefix + piece;
            int pieceStart = text.indexOf(piece, searchFrom);
            if (pieceStart < 0) {
                pieceStart = searchFrom;
            }
            int start = Math.max(0, pieceStart - (spans.isEmpty() ? 0 : prefix.length()));
            int end = Math.min(text.length(), pieceStart + piece.length());
            spans.add(new Span(value, start, end));
            searchFrom = Math.max(pieceStart + piece.length(), searchFrom);
            previous = value;
        }
        return spans;
    }

    private List<String> splitRecursively(String text, int target, int separatorIndex) {
        if (text.length() <= target) {
            return List.of(text);
        }
        for (int i = separatorIndex; i < SEPARATORS.length; i++) {
            String separator = SEPARATORS[i];
            if (separator.isEmpty()) {
                break;
            }
            List<String> parts = splitKeepingSeparator(text, separator);
            if (parts.size() <= 1) {
                continue;
            }
            List<String> output = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String part : parts) {
                if (current.length() + part.length() > target) {
                    if (!current.isEmpty()) {
                        output.add(current.toString());
                        current.setLength(0);
                    }
                    if (part.length() > target) {
                        output.addAll(splitRecursively(part, target, i + 1));
                    } else {
                        current.append(part);
                    }
                } else {
                    current.append(part);
                }
            }
            if (!current.isEmpty()) {
                output.add(current.toString());
            }
            return output;
        }
        List<String> output = new ArrayList<>();
        for (int i = 0; i < text.length(); i += target) {
            output.add(text.substring(i, Math.min(text.length(), i + target)));
        }
        return output;
    }

    private static List<String> merge(List<String> pieces, int target) {
        List<String> output = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String piece : pieces) {
            if (current.length() + piece.length() <= target) {
                current.append(piece);
            } else {
                if (!current.isEmpty()) {
                    output.add(current.toString());
                    current.setLength(0);
                }
                current.append(piece);
            }
        }
        if (!current.isEmpty()) {
            output.add(current.toString());
        }
        return output;
    }

    private static List<String> splitKeepingSeparator(String text, String separator) {
        List<String> result = new ArrayList<>();
        int start = 0;
        int index;
        while ((index = text.indexOf(separator, start)) >= 0) {
            int end = index + separator.length();
            result.add(text.substring(start, end));
            start = end;
        }
        if (start < text.length()) {
            result.add(text.substring(start));
        }
        return result;
    }

    public record Span(String text, int startOffset, int endOffset) {
    }
}
