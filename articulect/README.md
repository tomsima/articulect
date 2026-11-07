# Articulect

A Java desktop app for collecting randomised sentence recordings from participants,
built for my PhD on focus marking (Post Focus Compression) in Chinese topolects.
It replaces the manual setup of the Zhijiang experiment (laptop + Audacity + a
randomised list of sentences) with a guided recording screen.

(Formerly called "Topolecture"; the name was taken. Inside the app, "topolect"
is still the word for a dialect/language variety being studied.)

## Quick start (NixOS)

With a full (non-headless) JDK 17+ and Maven installed system-wide, no nix-shell is
needed: `mvn package`, then `java -jar target/articulect-1.0.0.jar`. The nix-shell
route below is only for machines without them.

Maven's own JDK on NixOS is headless (no fonts/AWT), so **do not** use
`mvn exec:java`. Build with Maven, run the jar with the full JDK:

```
cd ~/articulect
nix-shell -p maven jdk17
mvn package
java -jar target/articulect-1.0.0.jar
```

Optional `shell.nix` in the project root, so `nix-shell` alone is enough:

```nix
{ pkgs ? import <nixpkgs> {} }:
pkgs.mkShell {
  packages = [ pkgs.maven pkgs.jdk17 ];
}
```

Use `jdk17`, not `jdk17_headless`.

On other systems: JDK 17+ and Maven, then `mvn package` and
`java -jar target/articulect-1.0.0.jar` from the folder containing `pom.xml`.

## Desktop launcher

Same layout as the Piyingxi launcher: the `.desktop` file runs a wrapper in
`~/.local/bin`, which here is a symlink to `articulect.sh` in the project.
The script runs the jar with the system `java` (building it first with the system
`mvn` if `target/articulect-1.0.0.jar` doesn't exist) and writes any errors to
`launcher.log` in the project folder. `articulect-logo.png` is the icon.

Install (paths inside `articulect.desktop` assume `/home/tomsima/articulect`;
edit them if the project lives elsewhere):

```
chmod +x ~/articulect/articulect.sh
mkdir -p ~/.local/bin ~/.local/share/applications
ln -sf ~/articulect/articulect.sh ~/.local/bin/articulect
cp ~/articulect/articulect.desktop ~/.local/share/applications/
```

If the launcher doesn't start the app, run `articulect` in a terminal and read
`~/articulect/launcher.log`. `StartupWMClass` is `articulect-Articulect`
(what Java derives from the main class `articulect.Articulect`); if the
taskbar doesn't group the window with the launcher, check the real class with
`xprop WM_CLASS` and click the window.

## Logo

The logo is `articulect-logo.png` in the project root. Maven bundles that same file into
the jar (see `<resources>` in `pom.xml`) and the desktop launcher uses it as its icon, so
there is one file to replace. After replacing it, rebuild with `mvn clean package`
(a plain `mvn package` can keep an older bundled copy).

## Microphone / audio on NixOS

Java Sound talks to ALSA. With PipeWire, enable the ALSA layer in the NixOS config:

```
services.pipewire.alsa.enable = true;
```

If no devices appear in System settings, check this first.

## Where files live

Code and data share `~/articulect`.

```
~/articulect/
  pom.xml, src/, target/              the project
  speaker-database.org                one org heading per speaker, details in an org-table
  settings.properties                 microphone, Chinese font, question/answer font sizes
  ZJH/                                one folder per topolect code
    topolect.properties               name, Chinese name, description, target answers, elicitation questions
    elicitation-sentence-audio/       native-speaker question audio: TA1-neutral.wav, TA1-initial.wav, ...
    ZJH-01/                           one folder per speaker
      ZJH-01-TA1-neutral-1.wav
      ZJH-01-TA1-initial-3.wav
      ...
```

Recording file name: `[speakerid]-TA[n]-[focus]-[repetition].wav`
(focus is neutral / initial / medial / final, repetition is 1-4).

Avoid a topolect code that matches a project folder name (e.g. `SRC`) on
case-insensitive file systems.

## How an experiment runs

Start screen: logo, "articulect / a topolect toolkit", and a clickable **choose topolect** list
(click a topolect to open it; the ⌂ button at the top left of the window returns here).

Top bar: the **⌂ Home** button (far left), **File** (Add topolect, Choose topolect, Speaker database), **Settings**
(microphone and question/answer fonts), and **Exit** on the right. Exit asks "Save the current recording?"
(Yes / No / Cancel) if a take is unsaved; the window close button does the same.

1. **File -> Add topolect**: name, name in Chinese characters (shown in the topolect list instead of the name, e.g.
   枝江话 (ZJH)), a description (shown under the name when the topolect is opened), 3-letter
   code, TA1 sentence, and
   the four elicitation questions (neutral, initial, medial, final).
   **Mark the three focus areas of the target answer with square brackets**, in
   the order initial, medial, final, e.g. `[他][巴都]他[哥哥]。` (full-width 【】
   from a Chinese input method are accepted). The brackets are never shown to
   participants.
2. **File -> Choose topolect**, then:
   - **Edit topolect**: edit the name, Chinese name and description, edit a target answer, or
     add TA2, TA3...
   - **Record question audio**: a native speaker records each elicitation question
     (one row per target answer and focus condition: Record / Stop, **Upload** an existing
     clip as WAV, MP3, AIFF or AU - converted to WAV - and Play). Files go to
     `~/articulect/[CODE]/elicitation-sentence-audio/TA1-neutral.wav` etc. Re-record a
     question if you change its wording.
   - **New speaker**: name (blank = anon), email, hometown, years spoken,
     first language yes/no (and years studied if no). Gets an id like `ZJH-01`.
   - **Choose speaker -> Begin recording**.
3. Recording screen, per recording:
   - The **question** is shown at the top in a speech bubble with its tail pointing
     up-left (someone else is speaking). If question audio was recorded, it plays
     automatically when the question appears, and a **Repeat audio** button beneath the
     bubble plays it again (it is disabled while recording).
   - The **sentence to read out loud** is shown in a red-outlined speech bubble below
     (tail pointing down-right) with the red **Record answer** button beneath it. The
     sentence matches the question's focus condition: the focused part has a red line
     underneath (initial / medial / final); neutral has no underline.
   - **Record answer**, then **Stop recording**, **Record again** (discards the take
     and immediately starts a new one) or **Next** (saves, and also stops the
     recording if still running).
   - A red frame and REC badge show while the microphone is live.
4. Per target answer: 4 focus conditions x 4 repetitions = 16 recordings, in a
   fresh random order each time a speaker starts. After the 16th, either
   "experiment over", or Continue to next sentence set / End experiment if more
   target answers exist.

**File -> Speaker database** shows `speaker-database.org` read-only.
Using the File menu mid-session also offers to save an unsaved take first.

Original design: 4 sentences x 4 focus conditions x 4 repetitions = 64
recordings per speaker (12 speakers = 768 recordings). In Articulect that is one
topolect with TA1-TA4.

## Source layout (`src/main/java/articulect/`)

| File | Purpose |
|------|---------|
| `Articulect.java` | `main`, and the `App` window: menu bar, exit/save prompt, navigation, settings dialog |
| `Storage.java` | All disk I/O: topolects, org speaker database, settings, paths |
| `Forms.java` | Input dialogs (new topolect, target answer, speaker) |
| `SessionPanel.java` | Recording screen, trial randomisation, `JLayer` recording overlay |
| `Recorder.java` | Microphone capture to memory, WAV writing |
| `Playback.java` | Plays question audio (one clip at a time) |
| `AudioImport.java` | Converts uploaded clips (WAV/MP3/AIFF/AU) to 16-bit WAV |
| `QuestionAudioPanel.java` | Screen for recording the elicitation-question audio |
| `FocusText.java` | Parses/validates `[focus]` bracket markup in target answers |
| `Theme.java` | FlatLaf light/dark, following the OS |

## Fonts (Settings)

Settings -> choose the **Chinese font** and the **question** and **answer** font
sizes (defaults 40 and 56). This only affects the question and answer text on the
recording screen, not the rest of the application (start screen, topolect page, forms). A live preview shows the result, and changes apply
immediately if a session is open. The font list only offers installed fonts that
can draw Chinese characters.

On NixOS, Java finds fonts through fontconfig, so install CJK fonts system-wide:

```
fonts.packages = with pkgs; [ noto-fonts-cjk-sans noto-fonts-cjk-serif ];
```

then rebuild and check with `fc-list :lang=zh`. Some rarer characters
(e.g. dialect characters such as 嘎, 尬) may be missing from some fonts; the
preview text only shows common characters, so check a real target sentence too.

## Design decisions and assumptions

- **Audio format**: 44.1 kHz, 16-bit, mono WAV (constant `FORMAT` in
  `Recorder.java`). The original manual setup was 32 kHz stereo, but many
  microphones don't offer 32 kHz through Java Sound. Change it there if needed.
- **Recordings are held in memory** and written only when pressing Next, so
  "Record again" never leaves files behind.
- **Folder name** uses the 3-letter code (`ZJH`), not the full topolect name.
- **Dark mode**: Swing has no OS theme detection, so `Theme.java` polls the OS
  every 5 s (macOS `defaults`, Windows `reg`, GNOME `gsettings`). On other Linux
  desktops it falls back to light.
- **"JLayer"** was interpreted as Swing's `javax.swing.JLayer` (used for the
  recording overlay), not the JLayer MP3 library.
- Speaker numbers are the highest existing number for that code + 1, read from
  the org file. Don't delete headings from the middle if you want ids to stay
  unique and stable.
- The code was written without being compiled or run in the authoring
  environment, so expect small fixes on first real use.

## Notes on focus markup and audio

- Target answers must contain exactly three bracketed areas when entered or edited.
  Topolects created before this feature (no brackets) still run, but show no underline;
  edit each target answer to add the brackets.
- Question audio is optional: a question with no recording shows no Repeat audio button.
- Audio is played with Java's Clip, so it needs a working audio output (on NixOS with
  PipeWire, keep `services.pipewire.alsa.enable = true;`). Playback errors are logged
  to `launcher.log`, not shown.

## Known gaps / ideas

- No in-app way to delete or edit a speaker (edit `speaker-database.org` by hand; the Speaker database view is read-only).
- No audio level meter or playback to check takes.
- Researcher can't mark a take as unnatural other than pressing Record again.
- No migration from the old `~/topolecture` data folder: copy
  `speaker-database.org` and the topolect folders into `~/articulect` by hand.
- Export of metadata (CSV) for Praat/R analysis.

## Dependencies

- Java 17+
- FlatLaf 3.5.4 (pulled in by Maven)
- mp3spi 1.9.5.4 (with JLayer), for decoding uploaded MP3 clips
- Maven plugins: exec, shade (builds the runnable jar)
