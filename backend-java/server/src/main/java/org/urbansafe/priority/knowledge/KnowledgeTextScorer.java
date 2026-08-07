package org.urbansafe.priority.knowledge;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 不依赖外部模型的中文双字词与英文词元重排器。
 *
 * <p>它只用于第一版可解释检索，不声称具备向量语义能力。
 */
@Component
public class KnowledgeTextScorer {

    private static final Pattern WORD = Pattern.compile("[a-z0-9]+|[\\p{IsHan}]+");

    public double score(String question, String sectionTitle, String content) {
        String query = normalize(question);
        String title = normalize(sectionTitle);
        String body = normalize(content);
        if (query.isBlank() || (title.isBlank() && body.isBlank())) {
            return 0d;
        }
        String combined = (title + " " + body).trim();
        if (query.length() >= 4 && combined.contains(query)) {
            return 1d;
        }
        Set<String> queryTokens = tokens(query);
        if (queryTokens.isEmpty()) {
            return 0d;
        }
        Set<String> bodyTokens = tokens(combined);
        Set<String> titleTokens = tokens(title);
        long bodyMatches = queryTokens.stream().filter(bodyTokens::contains).count();
        long titleMatches = queryTokens.stream().filter(titleTokens::contains).count();
        double coverage = bodyMatches / (double) queryTokens.size();
        double titleCoverage = titleMatches / (double) queryTokens.size();
        double orderedBonus = longestSharedCjkRun(query, combined) >= 4 ? 0.15d : 0d;
        return clamp(coverage * 0.78d + titleCoverage * 0.12d + orderedBonus);
    }

    private static Set<String> tokens(String text) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            String value = matcher.group();
            if (value.codePoints().allMatch(KnowledgeTextScorer::isHan)) {
                int[] codePoints = value.codePoints().toArray();
                if (codePoints.length == 1) {
                    result.add(value);
                } else {
                    for (int index = 0; index < codePoints.length - 1; index++) {
                        result.add(new String(codePoints, index, 2));
                    }
                }
            } else if (value.length() > 1) {
                result.add(value);
            }
        }
        return result;
    }

    private static int longestSharedCjkRun(String query, String candidate) {
        int[] codePoints = query.codePoints().filter(KnowledgeTextScorer::isHan).toArray();
        int best = 0;
        for (int start = 0; start < codePoints.length; start++) {
            for (int length = codePoints.length - start; length > best; length--) {
                if (candidate.contains(new String(codePoints, start, length))) {
                    best = length;
                    break;
                }
            }
        }
        return best;
    }

    private static boolean isHan(int codePoint) {
        return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\p{IsHan}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static double clamp(double value) {
        return Math.max(0d, Math.min(1d, value));
    }
}
