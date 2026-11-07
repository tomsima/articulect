package articulect;

import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Lets a native speaker record each elicitation question; the audio plays during the experiment. */
final class QuestionAudioPanel extends JPanel {
    private final Storage.Topolect topolect;
    private final Recorder recorder = new Recorder();
    private final List<Row> rows = new ArrayList<>();
    private Row active;
    private static java.io.File lastDir;

    private final class Row {
        final Path file;
        final JButton record = new JButton("Record");
        final JButton upload = new JButton("Upload");
        final JButton play = new JButton("Play");
        final JLabel status = new JLabel();

        Row(int ta, String focus) {
            file = Storage.questionAudio(topolect.code, ta, focus);
            record.addActionListener(e -> toggleRecord(this));
            upload.addActionListener(e -> uploadClip(this));
            play.addActionListener(e -> Playback.play(file));
        }

        void refresh() {
            boolean has = Files.isRegularFile(file);
            record.setEnabled(active == null || active == this);
            record.setText(active == this ? "Stop" : (has ? "Record again" : "Record"));
            upload.setEnabled(active == null);
            play.setEnabled(has && active == null);
            status.setText(active == this ? "recording" : (has ? "recorded" : "not recorded"));
            status.setForeground(active == this ? new Color(210, 40, 40) : UIManager.getColor("Label.foreground"));
        }
    }

    QuestionAudioPanel(Storage.Topolect topolect, Runnable onDone) {
        super(new BorderLayout(0, 12));
        this.topolect = topolect;
        setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        JLabel title = new JLabel("Record question audio: " + topolect);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel help = new JLabel("<html>Ask a native speaker to read each question aloud and record it, "
                + "or upload an existing clip (WAV, MP3, AIFF or AU). "
                + "The recording plays automatically when the question appears during the experiment. "
                + "If you change a question's wording, record it again.</html>");
        JPanel north = new JPanel(new BorderLayout(0, 6));
        north.add(title, BorderLayout.NORTH);
        north.add(help, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);

        Font qFont = Forms.uiFont(Font.PLAIN, 20);

        JPanel list = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 8, 5, 8);
        c.anchor = GridBagConstraints.WEST;
        int y = 0;
        for (int ta = 0; ta < topolect.answers.size(); ta++) {
            for (String focus : Storage.FOCI) {
                Row row = new Row(ta, focus);
                rows.add(row);
                JLabel which = new JLabel("TA" + (ta + 1) + "  " + focus);
                which.setFont(which.getFont().deriveFont(Font.BOLD));
                JLabel text = new JLabel(topolect.answers.get(ta).questions.get(focus));
                text.setFont(qFont);
                c.gridy = y++;
                c.gridx = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE; list.add(which, c);
                c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; list.add(text, c);
                c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE; list.add(row.record, c);
                c.gridx = 3; list.add(row.upload, c);
                c.gridx = 4; list.add(row.play, c);
                c.gridx = 5; list.add(row.status, c);
            }
        }
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);

        JButton done = new JButton("Done");
        done.setFont(done.getFont().deriveFont(Font.BOLD, 16f));
        done.addActionListener(e -> { dispose(); onDone.run(); });
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(done);
        add(south, BorderLayout.SOUTH);

        rows.forEach(Row::refresh);
    }

    private void uploadClip(Row r) {
        if (active != null) return;
        JFileChooser fc = new JFileChooser(lastDir);
        fc.setDialogTitle("Choose an audio clip");
        fc.setFileFilter(new FileNameExtensionFilter("Audio files (wav, mp3, aiff, au)",
                "wav", "mp3", "aif", "aiff", "au"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.io.File chosen = fc.getSelectedFile();
        lastDir = chosen.getParentFile();
        if (Files.exists(r.file) && JOptionPane.showConfirmDialog(this,
                "Replace the existing recording?", "Upload audio",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        Playback.stop();
        try {
            AudioImport.toWav(chosen.toPath(), r.file);
        } catch (UnsupportedAudioFileException ex) {
            JOptionPane.showMessageDialog(this, "That audio format is not supported.\nUse a WAV, MP3, AIFF or AU file.",
                    "Upload error", JOptionPane.ERROR_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not import the clip:\n" + ex.getMessage(),
                    "Upload error", JOptionPane.ERROR_MESSAGE);
        }
        rows.forEach(Row::refresh);
    }

    private void toggleRecord(Row r) {
        if (active == r) {
            byte[] pcm = recorder.stop();
            active = null;
            if (pcm.length > 0) {
                try { Recorder.saveWav(pcm, r.file); }
                catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Could not save recording:\n" + ex.getMessage(),
                            "Save error", JOptionPane.ERROR_MESSAGE);
                }
            }
            rows.forEach(Row::refresh);
            return;
        }
        if (active != null) return;
        if (Files.exists(r.file) && JOptionPane.showConfirmDialog(this,
                "Replace the existing recording?", "Record again",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        Playback.stop();
        try {
            recorder.start(Storage.mixer());
            active = r;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not open the microphone:\n" + ex.getMessage(),
                    "Recording error", JOptionPane.ERROR_MESSAGE);
        }
        rows.forEach(Row::refresh);
    }

    /** Stops the microphone and playback; an unfinished recording is discarded. */
    void dispose() {
        if (recorder.isRunning()) recorder.stop();
        active = null;
        Playback.stop();
    }
}
