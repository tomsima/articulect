package articulect;

import javax.sound.sampled.*;
import java.nio.file.Path;

/** Plays one WAV file at a time. Failures (e.g. no audio output) are logged, not shown. */
final class Playback {
    private static Clip clip;

    private Playback() {}

    static synchronized void play(Path file) {
        stop();
        try (AudioInputStream in = AudioSystem.getAudioInputStream(file.toFile())) {
            Clip c = AudioSystem.getClip();
            c.open(in);
            c.addLineListener(ev -> { if (ev.getType() == LineEvent.Type.STOP) c.close(); });
            clip = c;
            c.start();
        } catch (Exception e) {
            System.err.println("Could not play " + file + ": " + e);
            clip = null;
        }
    }

    static synchronized void stop() {
        if (clip != null) {
            clip.stop();
            clip.close();
            clip = null;
        }
    }
}
