package articulect;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/** Input dialogs. */
final class Forms {
    private Forms() {}

    static final String DEFAULT_FONT = "Default (automatic)";

    /** The application's normal UI font at a given style and size (not the question/answer font). */
    static Font uiFont(int style, float size) {
        return UIManager.getFont("Label.font").deriveFont(style, size);
    }

    private static JTextField cjkField(String text) {
        JTextField f = new JTextField(text, 34);
        f.setFont(uiFont(Font.PLAIN, 16));
        return f;
    }

    private static JTextArea cjkArea(String text) {
        JTextArea a = new JTextArea(text, 4, 34);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setFont(uiFont(Font.PLAIN, 15));
        return a;
    }

    /** Edits name, Chinese name and description in place. Returns false if cancelled. */
    static boolean detailsForm(Component parent, Storage.Topolect t) {
        JTextField name = new JTextField(t.name, 34);
        JTextField zh = cjkField(t.chineseName);
        JTextArea desc = cjkArea(t.description);
        JPanel p = grid(
                "Name of the topolect", name,
                "Name in Chinese characters", zh,
                "Topolect description", new JScrollPane(desc));
        while (true) {
            if (JOptionPane.showConfirmDialog(parent, p, "Edit topolect details",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return false;
            if (name.getText().isBlank()) {
                JOptionPane.showMessageDialog(parent, "Please enter a name for the topolect.");
                continue;
            }
            t.name = name.getText().trim();
            t.chineseName = zh.getText().trim();
            t.description = desc.getText().trim();
            return true;
        }
    }
    private static List<String> cjkFonts;

    /** Installed font families that can draw Chinese characters (computed once). */
    static synchronized List<String> chineseFonts() {
        if (cjkFonts == null) {
            List<String> out = new ArrayList<>();
            for (String name : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
                if (new Font(name, Font.PLAIN, 12).canDisplayUpTo("他那个儿我奶炒赵艳尬") == -1) out.add(name);
            cjkFonts = out;
        }
        return cjkFonts;
    }

    static JPanel grid(Object... rows) {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < rows.length; i += 2) {
            c.gridy = i / 2;
            c.gridx = 0; c.fill = GridBagConstraints.NONE; c.weightx = 0;
            p.add(new JLabel((String) rows[i]), c);
            c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
            p.add((Component) rows[i + 1], c);
        }
        return p;
    }

    private static final class AnswerFields {
        final JTextField text = new JTextField(34);
        final Map<String, JTextField> q = new LinkedHashMap<>();

        AnswerFields(Storage.TargetAnswer init) {
            for (String f : Storage.FOCI) q.put(f, new JTextField(34));
            if (init != null) {
                text.setText(init.text);
                init.questions.forEach((k, v) -> q.get(k).setText(v));
            }
        }

        void rows(List<Object> rows, String taLabel) {
            rows.add(taLabel); rows.add(text);
            rows.add("");
            rows.add(new JLabel("<html>" + FocusText.HINT.replace("\n", "<br>") + "</html>"));
            for (String f : Storage.FOCI) { rows.add("Elicitation question: " + f); rows.add(q.get(f)); }
        }

        /** null if the fields are fine, otherwise a message for the user. */
        String error() {
            if (text.getText().isBlank() || q.values().stream().anyMatch(t -> t.getText().isBlank()))
                return "Please fill in every field.";
            if (!FocusText.valid(text.getText()))
                return FocusText.HINT;
            return null;
        }

        Storage.TargetAnswer read() {
            Storage.TargetAnswer a = new Storage.TargetAnswer(FocusText.normalise(text.getText()));
            q.forEach((k, v) -> a.questions.put(k, v.getText().trim()));
            return a;
        }
    }

    static Storage.Topolect newTopolect(Component parent, Set<String> existingCodes) {
        JTextField name = new JTextField(34), code = new JTextField(5);
        JTextField zh = cjkField("");
        JTextArea desc = cjkArea("");
        AnswerFields af = new AnswerFields(null);
        List<Object> rows = new ArrayList<>(List.of(
                "Name of the topolect", name,
                "Name in Chinese characters", zh,
                "Topolect description", new JScrollPane(desc),
                "3-letter topolect code", code));
        af.rows(rows, "Target answer 1 (TA1)");
        JPanel p = grid(rows.toArray());
        while (true) {
            if (JOptionPane.showConfirmDialog(parent, p, "New topolect",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return null;
            String c = code.getText().trim().toUpperCase(Locale.ROOT);
            if (name.getText().isBlank() || !c.matches("[A-Z]{3}")) {
                JOptionPane.showMessageDialog(parent,
                        "Please fill in the name and a topolect code of exactly 3 letters.");
            } else if (af.error() != null) {
                JOptionPane.showMessageDialog(parent, af.error());
            } else if (existingCodes.contains(c)) {
                JOptionPane.showMessageDialog(parent, "A topolect with code " + c + " already exists.");
            } else {
                Storage.Topolect t = new Storage.Topolect(name.getText().trim(), c);
                t.chineseName = zh.getText().trim();
                t.description = desc.getText().trim();
                t.answers.add(af.read());
                return t;
            }
        }
    }

    /** init == null creates a new answer; otherwise edits. Returns null if cancelled. */
    static Storage.TargetAnswer answerForm(Component parent, String title, Storage.TargetAnswer init) {
        AnswerFields af = new AnswerFields(init);
        List<Object> rows = new ArrayList<>();
        af.rows(rows, "Target answer");
        JPanel p = grid(rows.toArray());
        while (true) {
            if (JOptionPane.showConfirmDialog(parent, p, title,
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return null;
            String err = af.error();
            if (err == null) return af.read();
            JOptionPane.showMessageDialog(parent, err);
        }
    }

    static Storage.Speaker speakerForm(Component parent) {
        JTextField name = new JTextField(24), email = new JTextField(24), town = new JTextField(24);
        JSpinner spoken = new JSpinner(new SpinnerNumberModel(0, 0, 120, 1));
        JRadioButton yes = new JRadioButton("Yes", true), no = new JRadioButton("No");
        ButtonGroup g = new ButtonGroup(); g.add(yes); g.add(no);
        JPanel yn = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); yn.add(yes); yn.add(no);
        JSpinner studied = new JSpinner(new SpinnerNumberModel(0, 0, 120, 1));
        studied.setEnabled(false);
        no.addActionListener(e -> studied.setEnabled(true));
        yes.addActionListener(e -> studied.setEnabled(false));
        JPanel p = grid(
                "Name (blank = anon)", name,
                "Email", email,
                "Hometown", town,
                "Years topolect has been spoken", spoken,
                "Is this topolect your first language?", yn,
                "If no: years actively studied", studied);
        if (JOptionPane.showConfirmDialog(parent, p, "New speaker",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return null;
        boolean first = yes.isSelected();
        return new Storage.Speaker(name.getText().trim(), email.getText().trim(), town.getText().trim(),
                (Integer) spoken.getValue(), first, first ? null : (Integer) studied.getValue());
    }
}
