package articulect;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

/** Articulect: speech-recording platform for focus-elicitation experiments. */
public final class Articulect {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            new App().setVisible(true);
        });
    }
}

final class App extends JFrame {
    private final JPanel content = new JPanel(new BorderLayout());
    private Storage.Topolect current;

    App() {
        super("Articulect");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { App.this.guarded(App.this::exit); }
        });
        setSize(1100, 720);
        setLocationRelativeTo(null);
        setJMenuBar(menuBar());
        setContentPane(content);
        java.net.URL logoUrl = App.class.getResource("/articulect-logo.png");
        if (logoUrl != null) setIconImage(new ImageIcon(logoUrl).getImage());
        guarded(this::welcome);
    }

    private JMenuBar menuBar() {
        JMenu file = new JMenu("File");
        file.add(item("Add topolect", this::newTopolect));
        file.add(item("Choose topolect", this::chooseTopolect));
        file.add(item("Speaker database", this::speakerDatabase));

        JButton settings = flatButton("Settings", () -> guarded(this::settings));
        JButton exit = flatButton("Exit", () -> guarded(this::exit));

        JMenuBar bar = new JMenuBar();
        bar.add(homeButton());
        bar.add(file);
        bar.add(settings);
        bar.add(Box.createHorizontalGlue());
        bar.add(exit);
        return bar;
    }

    private static JButton flatButton(String label, Runnable r) {
        JButton b = new JButton(label);
        b.putClientProperty("JButton.buttonType", "toolBarButton");
        b.setFocusable(false);
        b.addActionListener(e -> r.run());
        return b;
    }

    /** The house symbol button at the top left of the window: goes to the start screen. */
    private JButton homeButton() {
        final char house = '\u2302';
        JButton b = new JButton(String.valueOf(house));
        Font f = b.getFont().deriveFont(Font.BOLD, 20f);
        if (!f.canDisplay(house)) {
            // the default UI font has no house glyph: look for an installed font that does
            boolean found = false;
            for (String name : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
                Font c = new Font(name, Font.BOLD, 20);
                if (c.canDisplay(house)) { f = c; found = true; break; }
            }
            if (!found) b.setText("Home");
        }
        if (!"Home".equals(b.getText())) b.setFont(f);
        b.setToolTipText("Home");
        b.putClientProperty("JButton.buttonType", "toolBarButton");
        b.setFocusable(false);
        b.addActionListener(e -> guarded(() -> { if (leaveSession()) welcome(); }));
        return b;
    }

    /** Menu item that first offers to save any unsaved recording before leaving the session screen. */
    private JMenuItem item(String label, Runnable r) {
        JMenuItem i = new JMenuItem(label);
        i.addActionListener(e -> guarded(() -> { if (leaveSession()) r.run(); }));
        return i;
    }

    private SessionPanel session() {
        for (Component c : content.getComponents())
            if (c instanceof SessionPanel s) return s;
        return null;
    }

    /** Asks "Save?" if a take is unsaved. Returns false if the user cancelled. */
    private boolean askSave(SessionPanel s) {
        int c = JOptionPane.showConfirmDialog(this, "Save the current recording before leaving?",
                "Save?", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (c == JOptionPane.YES_OPTION) return s.saveTake();
        return c == JOptionPane.NO_OPTION;
    }

    private boolean leaveSession() {
        SessionPanel s = session();
        return s == null || !s.hasTake() || askSave(s);
    }

    private void exit() {
        SessionPanel s = session();
        if (s != null) {
            if (s.hasTake()) {
                if (!askSave(s)) return;
            } else if (JOptionPane.showConfirmDialog(this,
                    "Exit Articulect? Recordings already finished are saved.", "Exit",
                    JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                return;
            }
            s.dispose();
        }
        dispose();
        System.exit(0);
    }

    private void speakerDatabase() {
        String text;
        try {
            text = Files.exists(Storage.SPEAKERS)
                    ? Files.readString(Storage.SPEAKERS, StandardCharsets.UTF_8)
                    : "No speakers recorded yet.";
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        JTextArea area = new JTextArea(text, 24, 70);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setCaretPosition(0);
        JOptionPane.showMessageDialog(this, new JScrollPane(area),
                "Speaker database – " + Storage.SPEAKERS, JOptionPane.PLAIN_MESSAGE);
    }

    private void guarded(Runnable r) {
        try { r.run(); }
        catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, ex.toString(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void show(JComponent c) {
        for (Component old : content.getComponents())
            if (old instanceof SessionPanel s) s.dispose();
            else if (old instanceof QuestionAudioPanel q) q.dispose();
        content.removeAll();
        content.add(c, BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }

    private static JComponent centered(JComponent c) {
        JPanel p = new JPanel(new GridBagLayout());
        p.add(c);
        return p;
    }

    private static JButton bigButton(String text, Runnable r) {
        JButton b = new JButton(text);
        b.setFont(b.getFont().deriveFont(18f));
        b.setMargin(new Insets(10, 30, 10, 30));
        b.addActionListener(e -> r.run());
        return b;
    }

    // ---- start screen ----

    /** The logo scaled to fit inside a maxSize square, keeping its proportions. */
    private static ImageIcon logoIcon(int maxSize) {
        java.net.URL u = App.class.getResource("/articulect-logo.png");
        if (u == null) return null;
        Image img = new ImageIcon(u).getImage();
        double scale = Math.min((double) maxSize / img.getWidth(null), (double) maxSize / img.getHeight(null));
        int w = Math.max(1, (int) Math.round(img.getWidth(null) * scale));
        int h = Math.max(1, (int) Math.round(img.getHeight(null) * scale));
        return new ImageIcon(img.getScaledInstance(w, h, Image.SCALE_SMOOTH));
    }

    private static String html(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    private static JLabel centeredLabel(String text, float size, int style) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(style, size));
        l.setAlignmentX(CENTER_ALIGNMENT);
        return l;
    }

    private void welcome() {
        List<Storage.Topolect> all;
        try { all = Storage.loadAll(); }
        catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        ImageIcon logo = logoIcon(110);
        if (logo != null) {
            JLabel l = new JLabel(logo);
            l.setAlignmentX(CENTER_ALIGNMENT);
            p.add(l);
            p.add(Box.createVerticalStrut(12));
        }
        p.add(centeredLabel("articulect", 40f, Font.BOLD));
        p.add(Box.createVerticalStrut(4));
        p.add(centeredLabel("a topolect toolkit", 18f, Font.PLAIN));
        p.add(Box.createVerticalStrut(40));
        p.add(centeredLabel("choose topolect", 18f, Font.BOLD));
        p.add(Box.createVerticalStrut(10));

        if (all.isEmpty()) {
            p.add(centeredLabel("No topolects yet. Use File > Add topolect.", 15f, Font.PLAIN));
        } else {
            JList<Storage.Topolect> list = new JList<>(all.toArray(new Storage.Topolect[0]));
            list.setFont(Forms.uiFont(Font.PLAIN, 18));
            list.setFixedCellHeight(44);
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            DefaultListCellRenderer r = new DefaultListCellRenderer();
            r.setHorizontalAlignment(SwingConstants.CENTER);
            list.setCellRenderer(r);
            list.addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    int i = list.locationToIndex(e.getPoint());
                    Rectangle b = i >= 0 ? list.getCellBounds(i, i) : null;
                    if (b != null && b.contains(e.getPoint())) list.setSelectedIndex(i);
                    else list.clearSelection();
                }
            });
            list.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    int i = list.locationToIndex(e.getPoint());
                    Rectangle b = i >= 0 ? list.getCellBounds(i, i) : null;
                    if (b != null && b.contains(e.getPoint())) home(list.getModel().getElementAt(i));
                }
                @Override public void mouseExited(MouseEvent e) { list.clearSelection(); }
            });
            list.addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ENTER && list.getSelectedValue() != null)
                        home(list.getSelectedValue());
                }
            });
            JScrollPane scroll = new JScrollPane(list);
            Dimension d = new Dimension(420, Math.min(all.size(), 6) * 44 + 6);
            scroll.setPreferredSize(d);
            scroll.setMaximumSize(d);
            scroll.setAlignmentX(CENTER_ALIGNMENT);
            p.add(scroll);
        }
        show(centered(p));
    }

    // ---- topolect management ----

    private void newTopolect() {
        try {
            Set<String> codes = new HashSet<>();
            for (Storage.Topolect t : Storage.loadAll()) codes.add(t.code);
            Storage.Topolect t = Forms.newTopolect(this, codes);
            if (t == null) return;
            Storage.save(t);
            home(t);
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    private void chooseTopolect() {
        try {
            List<Storage.Topolect> all = Storage.loadAll();
            if (all.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No topolects yet. Use 'New topolect' first.");
                return;
            }
            Object sel = JOptionPane.showInputDialog(this, "Choose a topolect:", "Choose topolect",
                    JOptionPane.PLAIN_MESSAGE, null, all.toArray(), all.get(0));
            if (sel != null) home((Storage.Topolect) sel);
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    private void home(Storage.Topolect t) {
        current = t;
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(t.toString());
        title.setFont(Forms.uiFont(Font.BOLD, 28));
        title.setAlignmentX(CENTER_ALIGNMENT);
        p.add(title);
        if (!t.description.isBlank()) {
            JLabel desc = new JLabel("<html><div style='width:520px;text-align:center'>"
                    + html(t.description) + "</div></html>");
            desc.setFont(Forms.uiFont(Font.PLAIN, 16));
            desc.setAlignmentX(CENTER_ALIGNMENT);
            p.add(Box.createVerticalStrut(10));
            p.add(desc);
        }
        JLabel sub = new JLabel(t.answers.size() + " target answer(s)");
        sub.setAlignmentX(CENTER_ALIGNMENT);
        p.add(Box.createVerticalStrut(10));
        p.add(sub);
        p.add(Box.createVerticalStrut(24));
        for (JButton b : List.of(
                bigButton("Edit topolect", () -> guarded(this::editTopolect)),
                bigButton("Record question audio", () -> guarded(this::questionAudio)),
                bigButton("New speaker", () -> guarded(this::newSpeaker)),
                bigButton("Choose speaker", () -> guarded(this::chooseSpeaker)))) {
            b.setAlignmentX(CENTER_ALIGNMENT);
            b.setMaximumSize(new Dimension(300, 50));
            p.add(b); p.add(Box.createVerticalStrut(12));
        }
        show(centered(p));
    }

    private void questionAudio() {
        show(new QuestionAudioPanel(current, () -> home(current)));
    }

    private void editTopolect() {
        try {
            List<Object> opts = new ArrayList<>();
            opts.add("Edit name, Chinese name and description");
            for (int i = 0; i < current.answers.size(); i++)
                opts.add("Edit TA" + (i + 1) + ": " + current.answers.get(i).text);
            opts.add("Add additional target answer");
            Object sel = JOptionPane.showInputDialog(this, "What would you like to do?", "Edit topolect",
                    JOptionPane.PLAIN_MESSAGE, null, opts.toArray(), opts.get(0));
            if (sel == null) return;
            int idx = opts.indexOf(sel);
            if (idx == 0) {
                if (!Forms.detailsForm(this, current)) return;
            } else if (idx == opts.size() - 1) {
                int n = current.answers.size() + 1;
                Storage.TargetAnswer a = Forms.answerForm(this, "New target answer TA" + n, null);
                if (a == null) return;
                current.answers.add(a);
            } else {
                int ti = idx - 1;
                Storage.TargetAnswer a = Forms.answerForm(this, "Edit TA" + (ti + 1), current.answers.get(ti));
                if (a == null) return;
                current.answers.set(ti, a);
            }
            Storage.save(current);
            home(current);
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    // ---- speakers ----

    private void newSpeaker() {
        try {
            Storage.Speaker s = Forms.speakerForm(this);
            if (s == null) return;
            String id = Storage.addSpeaker(current, s);
            JOptionPane.showMessageDialog(this, "Speaker profile created: " + id);
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    private void chooseSpeaker() {
        try {
            Map<String, String> sp = Storage.speakers(current.code);
            if (sp.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No speakers yet. Use 'New speaker' first.");
                return;
            }
            List<String> labels = new ArrayList<>();
            for (var e : sp.entrySet()) labels.add(e.getKey() + "  (" + e.getValue() + ")");
            Object sel = JOptionPane.showInputDialog(this, "Choose a speaker:", "Choose speaker",
                    JOptionPane.PLAIN_MESSAGE, null, labels.toArray(), labels.get(0));
            if (sel == null) return;
            String id = ((String) sel).split("\\s")[0];
            JPanel p = new JPanel();
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            JLabel l = new JLabel("Speaker " + id);
            l.setFont(l.getFont().deriveFont(Font.BOLD, 24f));
            l.setAlignmentX(CENTER_ALIGNMENT);
            JButton go = bigButton("Begin recording", () ->
                    show(new SessionPanel(current, id, () -> home(current))));
            go.setAlignmentX(CENTER_ALIGNMENT);
            p.add(l); p.add(Box.createVerticalStrut(20)); p.add(go);
            show(centered(p));
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    // ---- settings ----

    private void settings() {
        // microphone
        List<String> names = new ArrayList<>();
        names.add(Recorder.DEFAULT_LABEL);
        names.addAll(Recorder.inputDevices());
        JComboBox<String> mic = new JComboBox<>(names.toArray(new String[0]));
        String savedMic = Storage.mixer();
        mic.setSelectedItem(savedMic == null ? Recorder.DEFAULT_LABEL : savedMic);

        // question/answer display
        List<String> fonts = new ArrayList<>();
        fonts.add(Forms.DEFAULT_FONT);
        fonts.addAll(Forms.chineseFonts());
        JComboBox<String> fontBox = new JComboBox<>(fonts.toArray(new String[0]));
        fontBox.setMaximumRowCount(16);
        fontBox.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foc) {
                JLabel c = (JLabel) super.getListCellRendererComponent(l, v, i, sel, foc);
                if (v != null && !Forms.DEFAULT_FONT.equals(v)) c.setFont(new Font((String) v, Font.PLAIN, 18));
                return c;
            }
        });
        String savedFont = Storage.fontFamily();
        fontBox.setSelectedItem(savedFont == null ? Forms.DEFAULT_FONT : savedFont);
        JSpinner qSize = new JSpinner(new SpinnerNumberModel(Storage.questionSize(), 16, 160, 2));
        JSpinner aSize = new JSpinner(new SpinnerNumberModel(Storage.answerSize(), 16, 160, 2));

        JLabel qPrev = new JLabel("那个儿吃什么？");
        JLabel aPrev = new JLabel("那个儿吃羊肉。");
        qPrev.setAlignmentX(Component.CENTER_ALIGNMENT);
        aPrev.setAlignmentX(Component.CENTER_ALIGNMENT);
        JPanel preview = new JPanel();
        preview.setLayout(new BoxLayout(preview, BoxLayout.Y_AXIS));
        preview.add(qPrev);
        preview.add(Box.createVerticalStrut(8));
        preview.add(aPrev);
        JScrollPane previewScroll = new JScrollPane(preview);
        previewScroll.setPreferredSize(new Dimension(640, 230));

        Runnable update = () -> {
            String f = (String) fontBox.getSelectedItem();
            String family = Forms.DEFAULT_FONT.equals(f) ? Font.SANS_SERIF : f;
            qPrev.setFont(new Font(family, Font.PLAIN, (Integer) qSize.getValue()));
            aPrev.setFont(new Font(family, Font.PLAIN, (Integer) aSize.getValue()));
            preview.revalidate();
            preview.repaint();
        };
        fontBox.addActionListener(e -> update.run());
        qSize.addChangeListener(e -> update.run());
        aSize.addChangeListener(e -> update.run());
        update.run();

        List<Object> rows = new ArrayList<>(List.of(
                "Microphone", mic,
                "Chinese font (questions and answers)", fontBox,
                "Question font size", qSize,
                "Answer font size", aSize,
                "Preview", previewScroll));
        if (Forms.chineseFonts().isEmpty()) {
            rows.add("");
            rows.add(new JLabel("<html>No installed fonts with Chinese characters were found.<br>"
                    + "On NixOS, add noto-fonts-cjk-sans to fonts.packages and rebuild.</html>"));
        }
        JPanel p = Forms.grid(rows.toArray());
        if (JOptionPane.showConfirmDialog(this, p, "Settings",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;

        String m = (String) mic.getSelectedItem();
        Storage.setMixer(Recorder.DEFAULT_LABEL.equals(m) ? null : m);
        String f = (String) fontBox.getSelectedItem();
        Storage.setDisplay(Forms.DEFAULT_FONT.equals(f) ? null : f,
                (Integer) qSize.getValue(), (Integer) aSize.getValue());
        SessionPanel s = session();
        if (s != null) s.applyFonts();
    }
}
