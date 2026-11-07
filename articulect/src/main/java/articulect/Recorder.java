package articulect;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Captures 16-bit mono PCM from the chosen (or default) microphone into memory. */
final class Recorder {
    static final String DEFAULT_LABEL = "System default";
    static final AudioFormat FORMAT = new AudioFormat(44100f, 16, 1, true, false);

    private TargetDataLine line;
    private Thread thread;
    private ByteArrayOutputStream buf;
    private volatile boolean running;

    static List<String> inputDevices() {
        List<String> out = new ArrayList<>();
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        for (Mixer.Info mi : AudioSystem.getMixerInfo())
            if (AudioSystem.getMixer(mi).isLineSupported(info)) out.add(mi.getName());
        return out;
    }

    boolean isRunning() { return running; }

    void start(String mixerName) throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        TargetDataLine l = null;
        if (mixerName != null) {
            for (Mixer.Info mi : AudioSystem.getMixerInfo())
                if (mi.getName().equals(mixerName) && AudioSystem.getMixer(mi).isLineSupported(info)) {
                    l = (TargetDataLine) AudioSystem.getMixer(mi).getLine(info);
                    break;
                }
        }
        if (l == null) l = (TargetDataLine) AudioSystem.getLine(info);
        l.open(FORMAT);
        l.start();
        line = l;
        buf = new ByteArrayOutputStream();
        ByteArrayOutputStream b = buf;
        running = true;
        thread = new Thread(() -> {
            byte[] tmp = new byte[4096];
            while (running) {
                int n = line.read(tmp, 0, tmp.length);
                if (n > 0) b.write(tmp, 0, n);
            }
        }, "recorder");
        thread.start();
    }

    /** Stops capture and returns the recorded PCM bytes. */
    byte[] stop() {
        if (!running) return buf == null ? new byte[0] : buf.toByteArray();
        running = false;
        try { thread.join(1000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        line.stop();
        line.close();
        return buf.toByteArray();
    }

    static void saveWav(byte[] pcm, Path file) throws IOException {
        java.nio.file.Files.createDirectories(file.getParent());
        try (AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(pcm), FORMAT,
                pcm.length / FORMAT.getFrameSize())) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, file.toFile());
        }
    }
}
