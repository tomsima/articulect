package articulect;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/** All on-disk state lives under ~/articulect. */
final class Storage {
    private Storage() {}

    static final Path ROOT = Path.of(System.getProperty("user.home"), "articulect");
    static final Path SPEAKERS = ROOT.resolve("speaker-database.org");
    static final Path SETTINGS = ROOT.resolve("settings.properties");
    static final List<String> FOCI = List.of("neutral", "initial", "medial", "final");

    static final class TargetAnswer {
        String text;
        final Map<String, String> questions = new LinkedHashMap<>();
        TargetAnswer(String text) { this.text = text; }
    }

    static final class Topolect {
        String name, code;
        String chineseName = "", description = "";
        final List<TargetAnswer> answers = new ArrayList<>();
        Topolect(String name, String code) { this.name = name; this.code = code; }
        /** The Chinese-character name if one was entered, otherwise the name. */
        String displayName() { return chineseName.isBlank() ? name : chineseName; }
        @Override public String toString() { return displayName() + " (" + code + ")"; }
    }

    record Speaker(String name, String email, String hometown, int yearsSpoken,
                   boolean firstLanguage, Integer yearsStudied) {}

    // ---- topolects ----

    static Path topolectFile(String code) { return ROOT.resolve(code).resolve("topolect.properties"); }

    static void save(Topolect t) throws IOException {
        Files.createDirectories(ROOT.resolve(t.code));
        Properties p = new Properties();
        p.setProperty("name", t.name);
        p.setProperty("chinese.name", t.chineseName);
        p.setProperty("description", t.description);
        p.setProperty("count", String.valueOf(t.answers.size()));
        for (int i = 0; i < t.answers.size(); i++) {
            TargetAnswer a = t.answers.get(i);
            p.setProperty("ta." + (i + 1) + ".text", a.text);
            for (String f : FOCI) p.setProperty("ta." + (i + 1) + "." + f, a.questions.getOrDefault(f, ""));
        }
        try (Writer w = Files.newBufferedWriter(topolectFile(t.code), StandardCharsets.UTF_8)) {
            p.store(w, "articulect topolect");
        }
    }

    static List<Topolect> loadAll() throws IOException {
        List<Topolect> out = new ArrayList<>();
        if (!Files.isDirectory(ROOT)) return out;
        try (Stream<Path> dirs = Files.list(ROOT)) {
            for (Path d : (Iterable<Path>) dirs.sorted()::iterator) {
                Path f = d.resolve("topolect.properties");
                if (!Files.isRegularFile(f)) continue;
                Properties p = new Properties();
                try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) { p.load(r); }
                Topolect t = new Topolect(p.getProperty("name", d.getFileName().toString()), d.getFileName().toString());
                t.chineseName = p.getProperty("chinese.name", "");
                t.description = p.getProperty("description", "");
                int n = Integer.parseInt(p.getProperty("count", "0"));
                for (int i = 1; i <= n; i++) {
                    TargetAnswer a = new TargetAnswer(p.getProperty("ta." + i + ".text", ""));
                    for (String focus : FOCI) a.questions.put(focus, p.getProperty("ta." + i + "." + focus, ""));
                    t.answers.add(a);
                }
                out.add(t);
            }
        }
        return out;
    }

    // ---- elicitation question audio ----

    static Path questionAudioDir(String code) { return ROOT.resolve(code).resolve("elicitation-sentence-audio"); }

    /** e.g. ~/articulect/ZJH/elicitation-sentence-audio/TA1-neutral.wav */
    static Path questionAudio(String code, int taIndex, String focus) {
        return questionAudioDir(code).resolve(String.format("TA%d-%s.wav", taIndex + 1, focus));
    }

    // ---- speakers (org-mode database) ----

    /** speaker-id -> name, for one topolect code. */
    static Map<String, String> speakers(String code) throws IOException {
        Map<String, String> m = new LinkedHashMap<>();
        if (!Files.exists(SPEAKERS)) return m;
        String cur = null;
        for (String l : Files.readAllLines(SPEAKERS, StandardCharsets.UTF_8)) {
            if (l.startsWith("* ")) {
                cur = l.substring(2).trim();
                if (cur.startsWith(code + "-")) m.put(cur, "anon"); else cur = null;
            } else if (cur != null && l.startsWith("| Name |")) {
                m.put(cur, l.substring(8, l.lastIndexOf('|')).trim());
            }
        }
        return m;
    }

    static String addSpeaker(Topolect t, Speaker s) throws IOException {
        Files.createDirectories(ROOT);
        int max = 0;
        for (String id : speakers(t.code).keySet()) {
            try { max = Math.max(max, Integer.parseInt(id.substring(t.code.length() + 1))); }
            catch (NumberFormatException ignored) {}
        }
        String id = String.format("%s-%02d", t.code, max + 1);
        StringBuilder sb = new StringBuilder();
        sb.append("* ").append(id).append('\n');
        sb.append("| Field | Value |\n|-------+-------|\n");
        row(sb, "Name", s.name().isBlank() ? "anon" : s.name());
        row(sb, "Email", s.email());
        row(sb, "Hometown", s.hometown());
        row(sb, "Topolect", t.name + " (" + t.code + ")");
        row(sb, "Years topolect spoken", String.valueOf(s.yearsSpoken()));
        row(sb, "First language", s.firstLanguage() ? "yes" : "no");
        if (!s.firstLanguage()) row(sb, "Years actively studied", String.valueOf(s.yearsStudied()));
        row(sb, "Created", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        sb.append('\n');
        Files.writeString(SPEAKERS, sb.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        Files.createDirectories(speakerDir(t.code, id));
        return id;
    }

    private static void row(StringBuilder sb, String k, String v) {
        sb.append("| ").append(k).append(" | ").append(v.replace('|', '/').trim()).append(" |\n");
    }

    static Path speakerDir(String code, String speakerId) { return ROOT.resolve(code).resolve(speakerId); }

    // ---- settings ----

    static final int DEFAULT_QUESTION_SIZE = 40, DEFAULT_ANSWER_SIZE = 56;

    private static Properties loadSettings() {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(SETTINGS, StandardCharsets.UTF_8)) { p.load(r); }
        catch (IOException ignored) {}
        return p;
    }

    private static void storeSettings(Properties p) {
        try {
            Files.createDirectories(ROOT);
            try (Writer w = Files.newBufferedWriter(SETTINGS, StandardCharsets.UTF_8)) { p.store(w, "articulect settings"); }
        } catch (IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    private static String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s; }

    private static int intSetting(Properties p, String key, int def) {
        try { return Integer.parseInt(p.getProperty(key, "")); } catch (NumberFormatException e) { return def; }
    }

    /** Selected microphone name, or null for the system default. */
    static String mixer() { return blankToNull(loadSettings().getProperty("mixer")); }

    static void setMixer(String name) {
        Properties p = loadSettings();
        if (name == null) p.remove("mixer"); else p.setProperty("mixer", name);
        storeSettings(p);
    }

    /** Font family for questions and answers, or null for the automatic default. */
    static String fontFamily() { return blankToNull(loadSettings().getProperty("font.family")); }
    static int questionSize() { return intSetting(loadSettings(), "font.question.size", DEFAULT_QUESTION_SIZE); }
    static int answerSize() { return intSetting(loadSettings(), "font.answer.size", DEFAULT_ANSWER_SIZE); }

    static void setDisplay(String family, int questionSize, int answerSize) {
        Properties p = loadSettings();
        if (family == null) p.remove("font.family"); else p.setProperty("font.family", family);
        p.setProperty("font.question.size", String.valueOf(questionSize));
        p.setProperty("font.answer.size", String.valueOf(answerSize));
        storeSettings(p);
    }
}
