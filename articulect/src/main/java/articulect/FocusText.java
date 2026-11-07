package articulect;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A target answer written with its three focus areas in square brackets, in the order
 * initial, medial, final. Example: [他][巴都]他[哥哥]。
 */
final class FocusText {
    static final String HINT = "Mark the three focus areas with square brackets, in the order initial, medial, final.\n"
            + "Example: [他][巴都]他[哥哥]。";

    private static final String NB = "[^\\[\\]]*";
    private static final String GROUP = "\\[[^\\[\\]]+\\]";
    private static final Pattern THREE_GROUPS =
            Pattern.compile(NB + GROUP + NB + GROUP + NB + GROUP + NB);

    private final String plain;
    private final List<int[]> spans;

    private FocusText(String plain, List<int[]> spans) {
        this.plain = plain;
        this.spans = spans;
    }

    /** Accepts the full-width brackets a Chinese input method produces. */
    static String normalise(String s) {
        return s.replace('【', '[').replace('】', ']').replace('［', '[').replace('］', ']').trim();
    }

    /** True if the text contains exactly three non-empty, non-nested bracketed areas. */
    static boolean valid(String marked) {
        return THREE_GROUPS.matcher(normalise(marked)).matches();
    }

    static FocusText parse(String marked) {
        StringBuilder sb = new StringBuilder();
        List<int[]> spans = new ArrayList<>();
        int start = -1;
        for (char c : normalise(marked).toCharArray()) {
            if (c == '[') {
                start = sb.length();
            } else if (c == ']') {
                if (start >= 0 && sb.length() > start) spans.add(new int[]{start, sb.length()});
                start = -1;
            } else {
                sb.append(c);
            }
        }
        return new FocusText(sb.toString(), spans);
    }

    /** The sentence without brackets. */
    String plain() { return plain; }

    /** {start, end} of the focused part for a focus condition, or null (neutral or not marked). */
    int[] span(String focus) {
        int i = switch (focus) {
            case "initial" -> 0;
            case "medial" -> 1;
            case "final" -> 2;
            default -> -1;
        };
        return (i >= 0 && i < spans.size()) ? spans.get(i) : null;
    }
}
