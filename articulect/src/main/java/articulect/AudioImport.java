package articulect;

import javax.sound.sampled.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Converts an uploaded audio clip (WAV, AIFF, AU or MP3) to a 16-bit PCM WAV file. */
final class AudioImport {
    private AudioImport() {}

    static void toWav(Path src, Path dest) throws IOException, UnsupportedAudioFileException {
        Files.createDirectories(dest.getParent());
        Path tmp = dest.resolveSibling(dest.getFileName() + ".tmp");
        try (AudioInputStream in = AudioSystem.getAudioInputStream(src.toFile())) {
            AudioFormat f = in.getFormat();
            AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, f.getSampleRate(), 16,
                    f.getChannels(), f.getChannels() * 2, f.getSampleRate(), false);
            AudioInputStream out;
            try {
                out = AudioSystem.getAudioInputStream(pcm, in);
            } catch (IllegalArgumentException e) {
                throw new UnsupportedAudioFileException("Cannot convert " + f);
            }
            try (out) {
                AudioSystem.write(out, AudioFileFormat.Type.WAVE, tmp.toFile());
            }
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
