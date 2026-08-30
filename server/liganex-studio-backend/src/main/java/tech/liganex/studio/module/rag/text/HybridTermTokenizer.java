package tech.liganex.studio.module.rag.text;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Produces identical persistent lexical terms for indexing and querying. */
@Component
public class HybridTermTokenizer {
    public String terms(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        Set<String> terms = new LinkedHashSet<>();
        StringBuilder latin = new StringBuilder();
        List<String> hanRun = new ArrayList<>();
        for (int offset = 0; offset < normalized.length();) {
            int cp = normalized.codePointAt(offset);
            offset += Character.charCount(cp);
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) {
                flushLatin(latin, terms);
                hanRun.add(new String(Character.toChars(cp)));
            } else {
                flushHan(hanRun, terms);
                if (Character.isLetterOrDigit(cp) || cp == '_' || cp == '-') {
                    latin.appendCodePoint(cp);
                } else {
                    flushLatin(latin, terms);
                }
            }
        }
        flushHan(hanRun, terms);
        flushLatin(latin, terms);
        return String.join(" ", terms);
    }

    private static void flushHan(List<String> run, Set<String> output) {
        for (int i = 0; i < run.size(); i++) {
            output.add(run.get(i));
            if (i + 1 < run.size()) {
                output.add(run.get(i) + run.get(i + 1));
            }
        }
        run.clear();
    }

    private static void flushLatin(StringBuilder value, Set<String> output) {
        if (!value.isEmpty()) {
            output.add(value.toString());
            value.setLength(0);
        }
    }
}
