package articulect;

import javax.swing.*;
import javax.swing.plaf.LayerUI;
import javax.swing.text.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

/**
 * The recording screen. The question is shown in a speech bubble (top) and the sentence to
 * read out loud in a second bubble (bottom), with the record controls beneath it.
 */
final class SessionPanel extends JPanel {
    private record Trial(int ta, String focus, int rep) {}
    private enum State { READY, RECORDING, STOPPED }

    /** javax.swing.JLayer decoration: red frame and REC badge while the microphone is live. */
    private static final class RecordingLayerUI extends LayerUI<JComponent> {
        private boolean recording;

        void setRecording(boolean r) {
            boolean old = recording;
            recording = r;
            firePropertyChange("recording", old, r);
        }

        @Override public void paint(Graphics g, JComponent c) {
            super.paint(g, c);
            if (!recording) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(220, 30, 30));
            g2.setStroke(new BasicStroke(6f));
            g2.drawRect(3, 3, c.getWidth() - 6, c.getHeight() - 6);
            g2.fillOval(c.getWidth() - 100, 18, 18, 18);
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, 16f));
            g2.drawString("REC", c.getWidth() - 74, 33);
            g2.dispose();
        }

        @Override public void applyPropertyChange(PropertyChangeEvent e, JLayer<? extends JComponent> l) {
            if ("recording".equals(e.getPropertyName())) l.repaint();
        }
    }

    /** Rounded speech bubble with a tail. TOP_LEFT/BOTTOM_RIGHT say where the tail points. */
    private static final class SpeechBubble extends JPanel {
        enum Corner { TOP_LEFT, BOTTOM_RIGHT }

        private static final int TAIL = 22;
        private final Corner corner;
        private final Color outline; // null = theme accent colour

        SpeechBubble(JComponent content, Corner corner, Color outline) {
            super(new BorderLayout());
            this.corner = corner;
            this.outline = outline;
            setOpaque(false);
            boolean top = corner == Corner.TOP_LEFT;
            setBorder(BorderFactory.createEmptyBorder(18 + (top ? TAIL : 0), 32, 18 + (top ? 0 : TAIL), 32));
            add(content, BorderLayout.CENTER);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            Area shape;
            Path2D tail = new Path2D.Float();
            if (corner == Corner.TOP_LEFT) {
                shape = new Area(new RoundRectangle2D.Float(3, TAIL + 3, w - 6, h - TAIL - 6, 44, 44));
                tail.moveTo(70, TAIL + 8);
                tail.lineTo(118, TAIL + 8);
                tail.lineTo(58, 3);
            } else {
                int bh = h - TAIL;
                shape = new Area(new RoundRectangle2D.Float(3, 3, w - 6, bh - 6, 44, 44));
                tail.moveTo(w - 118, bh - 8);
                tail.lineTo(w - 70, bh - 8);
                tail.lineTo(w - 58, h - 3);
            }
            tail.closePath();
            shape.add(new Area(tail));
            Color fill = UIManager.getColor("TextField.background");
            Color line = outline != null ? outline : UIManager.getColor("Component.accentColor");
            if (line == null) line = UIManager.getColor("Component.borderColor");
            g2.setColor(fill != null ? fill : Color.WHITE);
            g2.fill(shape);
            g2.setColor(line != null ? line : Color.GRAY);
            g2.setStroke(new BasicStroke(3f));
            g2.draw(shape);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Draws a red line under the focused part of the answer sentence. */
    private static final class UnderlinePainter implements Highlighter.HighlightPainter {
        @Override public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent c) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(new Color(210, 40, 40));
            try {
                for (int i = p0; i < p1; i++) {
                    Rectangle2D a = c.modelToView2D(i);
                    Rectangle2D b = c.modelToView2D(i + 1);
                    if (a == null || b == null) continue;
                    double w = Math.abs(b.getY() - a.getY()) < 1 ? b.getX() - a.getX() : a.getHeight() * 0.9;
                    if (w <= 0) w = a.getHeight() * 0.9;
                    double t = Math.max(3, a.getHeight() / 14);
                    g2.fill(new Rectangle2D.Double(a.getX(), a.getMaxY() - t - 1, w + 0.5, t));
                }
            } catch (BadLocationException ignored) {
            } finally {
                g2.dispose();
            }
        }
    }

    private final Storage.Topolect topolect;
    private final String speakerId;
    private final Runnable onExit;
    private final Recorder recorder = new Recorder();
    private final RecordingLayerUI layerUI = new RecordingLayerUI();

    private final JLabel progress = new JLabel(" ", SwingConstants.CENTER);
    private final JTextPane question = bigText();
    private final JTextPane answer = bigText();
    private final JButton record = new JButton("Record answer");
    private final JButton stop = new JButton("Stop recording");
    private final JButton again = new JButton("Record again");
    private final JButton next = new JButton("Next");
    private final JButton repeat = new JButton("Repeat audio");
    private Path audioFile;
    private String answerMarked = "", focusKey = "neutral";

    private List<Trial> trials;
    private int setIndex = 0, pos = 0;
    private State state = State.READY;
    private byte[] pending;

    SessionPanel(Storage.Topolect topolect, String speakerId, Runnable onExit) {
        super(new BorderLayout());
        this.topolect = topolect;
        this.speakerId = speakerId;
        this.onExit = onExit;

        record.setBackground(new Color(200, 30, 30));
        record.setForeground(Color.WHITE);
        for (JButton b : List.of(record, stop, again, next, repeat)) {
            b.setFont(b.getFont().deriveFont(Font.BOLD, 20f));
            b.setMargin(new Insets(12, 28, 12, 28));
        }
        record.addActionListener(e -> startRecording());
        stop.addActionListener(e -> { pending = recorder.stop(); setState(State.STOPPED); });
        again.addActionListener(e -> {
            if (recorder.isRunning()) recorder.stop();
            pending = null;
            startRecording();
        });
        next.addActionListener(e -> advance());
        repeat.addActionListener(e -> { if (audioFile != null) Playback.play(audioFile); });

        JPanel recordButtons = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 8));
        recordButtons.add(record); recordButtons.add(stop); recordButtons.add(again); recordButtons.add(next);

        JComponent questionBox = new SpeechBubble(scroll(question), SpeechBubble.Corner.TOP_LEFT, null);

        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.setBorder(BorderFactory.createEmptyBorder(24, 40, 24, 40));
        progress.setFont(progress.getFont().deriveFont(14f));
        top.add(progress, BorderLayout.NORTH);
        top.add(questionBox, BorderLayout.CENTER);
        JPanel repeatRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 8));
        repeatRow.add(repeat);
        top.add(repeatRow, BorderLayout.SOUTH);

        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.setBorder(BorderFactory.createEmptyBorder(24, 40, 24, 40));
        JComponent answerBubble = new SpeechBubble(scroll(answer), SpeechBubble.Corner.BOTTOM_RIGHT, new Color(210, 40, 40));
        bottom.add(answerBubble, BorderLayout.CENTER);
        bottom.add(recordButtons, BorderLayout.SOUTH);

        JPanel split = new JPanel(new GridLayout(2, 1));
        split.add(top);
        split.add(bottom);
        JLayer<JComponent> layer = new JLayer<>(split, layerUI);
        add(layer, BorderLayout.CENTER);

        newSet();
        refresh();
    }

    private static JTextPane bigText() {
        JTextPane p = new JTextPane();
        p.setEditable(false);
        p.setOpaque(false);
        p.setFocusable(false);
        return p;
    }

    private static JScrollPane scroll(JTextPane p) {
        JScrollPane sp = new JScrollPane(p, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        return sp;
    }

    /** Sets the text, centred, in the font family and size chosen in Settings. */
    private static void render(JTextPane p, String text, int size) {
        String family = Storage.fontFamily();
        p.setText(text);
        StyledDocument d = p.getStyledDocument();
        SimpleAttributeSet para = new SimpleAttributeSet();
        StyleConstants.setAlignment(para, StyleConstants.ALIGN_CENTER);
        d.setParagraphAttributes(0, d.getLength(), para, false);
        SimpleAttributeSet chars = new SimpleAttributeSet();
        StyleConstants.setFontFamily(chars, family == null ? Font.SANS_SERIF : family);
        StyleConstants.setFontSize(chars, size);
        d.setCharacterAttributes(0, d.getLength(), chars, false);
        p.setCaretPosition(0);
    }

    /** Re-applies the font settings to the text currently on screen. */
    void applyFonts() {
        render(question, question.getText(), Storage.questionSize());
        showAnswerText();
        revalidate();
        repaint();
    }

    /** Shows the answer without its brackets; the focused part gets a red underline (none for neutral). */
    private void showAnswerText() {
        FocusText ft = FocusText.parse(answerMarked);
        render(answer, ft.plain(), Storage.answerSize());
        answer.getHighlighter().removeAllHighlights();
        int[] span = ft.span(focusKey);
        if (span != null) {
            try { answer.getHighlighter().addHighlight(span[0], span[1], new UnderlinePainter()); }
            catch (BadLocationException ignored) {}
        }
    }

    /** 4 focus conditions x 4 repetitions = 16 trials, freshly shuffled. */
    private void newSet() {
        trials = new ArrayList<>();
        for (String f : Storage.FOCI)
            for (int rep = 1; rep <= 4; rep++) trials.add(new Trial(setIndex, f, rep));
        Collections.shuffle(trials);
        pos = 0;
        pending = null;
        state = State.READY;
    }

    /** Loads the current trial: question, answer and the Record answer button. */
    private void refresh() {
        Trial t = trials.get(pos);
        Storage.TargetAnswer ta = topolect.answers.get(t.ta());
        render(question, ta.questions.get(t.focus()), Storage.questionSize());
        answerMarked = ta.text;
        focusKey = t.focus();
        showAnswerText();
        String set = topolect.answers.size() > 1
                ? String.format("Sentence set %d of %d,  ", setIndex + 1, topolect.answers.size()) : "";
        progress.setText(String.format("%sRecording %d of %d", set, pos + 1, trials.size()));
        audioFile = Storage.questionAudio(topolect.code, t.ta(), t.focus());
        boolean hasAudio = Files.isRegularFile(audioFile);
        repeat.setVisible(hasAudio);
        setState(State.READY);
        if (hasAudio) Playback.play(audioFile); else Playback.stop();
    }

    private void setState(State s) {
        state = s;
        record.setVisible(s == State.READY);
        stop.setVisible(s == State.RECORDING);
        again.setVisible(s == State.RECORDING || s == State.STOPPED);
        next.setVisible(s == State.RECORDING || s == State.STOPPED);
        layerUI.setRecording(s == State.RECORDING);
        repeat.setEnabled(s != State.RECORDING);
        revalidate();
        repaint();
    }

    private void startRecording() {
        Playback.stop();
        try {
            recorder.start(Storage.mixer());
            setState(State.RECORDING);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not open the microphone:\n" + ex.getMessage(),
                    "Recording error", JOptionPane.ERROR_MESSAGE);
            setState(State.READY);
        }
    }

    private Path currentFile() {
        Trial t = trials.get(pos);
        return Storage.speakerDir(topolect.code, speakerId).resolve(
                String.format("%s-TA%d-%s-%d.wav", speakerId, t.ta() + 1, t.focus(), t.rep()));
    }

    /** True while a take exists that has not been written to disk (recording, or stopped but not "Next"). */
    boolean hasTake() {
        return state == State.RECORDING || state == State.STOPPED;
    }

    /** Stops the microphone if live and writes the current take. Returns false if saving failed. */
    boolean saveTake() {
        if (recorder.isRunning()) pending = recorder.stop();
        if (pending == null) return true;
        try {
            Recorder.saveWav(pending, currentFile());
            pending = null;
            return true;
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save recording:\n" + ex.getMessage(),
                    "Save error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void advance() {
        if (!saveTake()) { setState(State.STOPPED); return; }
        pos++;
        if (pos < trials.size()) { refresh(); return; }

        boolean more = setIndex + 1 < topolect.answers.size();
        if (!more) {
            JOptionPane.showMessageDialog(this, "The experiment is over. Thank you for taking part!",
                    "Finished", JOptionPane.INFORMATION_MESSAGE);
            onExit.run();
            return;
        }
        int c = JOptionPane.showOptionDialog(this, "You have finished this sentence set.", "Sentence set complete",
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
                new Object[]{"Continue to next sentence set", "End experiment"}, "Continue to next sentence set");
        if (c == 0) { setIndex++; newSet(); refresh(); }
        else onExit.run();
    }

    /** Releases the microphone, discarding any unsaved take. */
    void dispose() {
        Playback.stop();
        if (recorder.isRunning()) recorder.stop();
    }
}
