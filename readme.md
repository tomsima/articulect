<img width="886" height="247" alt="articulect-banner" src="https://github.com/user-attachments/assets/f8f98c83-7648-41ad-a704-e8dc8f0dbc8c" />

Articulect is a specialised software package for researchers collecting speech data on post-focus compression in Chinese topolects. The application allows for the easy capture of data from speech experiment participants who speak various Chinese topolects and may not be confident with technology. Audio files are automatically named and saved to file ready for processing in Praat. 

Articulect is written in java for Linux machines, and is built from source using maven. git clone the repo, then run mvn package from the root folder.

```bash
git clone https://github.com/tomsima/articulect.git
cd articulect

mvn package
```

Where files live

Code and data share ~/articulect.

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

Recording file name: [speakerid]-TA[n]-[focus]-[repetition].wav (focus is neutral / initial / medial / final, repetition is 1-4).

Avoid a topolect code that matches a project folder name (e.g. SRC) on case-insensitive file systems.
How an experiment runs

Start screen: logo, "articulect / a topolect toolkit", and a clickable choose topolect list (click a topolect to open it; the ⌂ button at the top left of the window returns here).

Top bar: the ⌂ Home button (far left), File (Add topolect, Choose topolect, Speaker database), Settings (microphone and question/answer fonts), and Exit on the right. Exit asks "Save the current recording?" (Yes / No / Cancel) if a take is unsaved; the window close button does the same.

File -> Speaker database shows speaker-database.org read-only. Using the File menu mid-session also offers to save an unsaved take first.

Original design: 4 sentences x 4 focus conditions x 4 repetitions = 64 recordings per speaker (12 speakers = 768 recordings). In Articulect that is one topolect with TA1-TA4.
Source layout (src/main/java/articulect/)
File 	Purpose
Articulect.java 	main, and the App window: menu bar, exit/save prompt, navigation, settings dialog
Storage.java 	All disk I/O: topolects, org speaker database, settings, paths
Forms.java 	Input dialogs (new topolect, target answer, speaker)
SessionPanel.java 	Recording screen, trial randomisation, JLayer recording overlay
Recorder.java 	Microphone capture to memory, WAV writing
Playback.java 	Plays question audio (one clip at a time)
AudioImport.java 	Converts uploaded clips (WAV/MP3/AIFF/AU) to 16-bit WAV
QuestionAudioPanel.java 	Screen for recording the elicitation-question audio
FocusText.java 	Parses/validates [focus] bracket markup in target answers
Theme.java 	FlatLaf light/dark, following the OS
Fonts (Settings)

Settings -> choose the Chinese font and the question and answer font sizes (defaults 40 and 56). This only affects the question and answer text on the recording screen, not the rest of the application (start screen, topolect page, forms). A live preview shows the result, and changes apply immediately if a session is open. The font list only offers installed fonts that can draw Chinese characters.

On NixOS, Java finds fonts through fontconfig, so install CJK fonts system-wide:

fonts.packages = with pkgs; [ noto-fonts-cjk-sans noto-fonts-cjk-serif ];

then rebuild and check with fc-list :lang=zh. Some rarer characters (e.g. dialect characters such as 嘎, 尬) may be missing from some fonts; the preview text only shows common characters, so check a real target sentence too.

Notes on focus markup and audio

    Target answers must contain exactly three bracketed areas when entered or edited. Topolects created before this feature (no brackets) still run, but show no underline; edit each target answer to add the brackets.
    Question audio is optional: a question with no recording shows no Repeat audio button.
    Audio is played with Java's Clip, so it needs a working audio output (on NixOS with PipeWire, keep services.pipewire.alsa.enable = true;). Playback errors are logged to launcher.log, not shown.

Known gaps / ideas

    No in-app way to delete or edit a speaker (edit speaker-database.org by hand; the Speaker database view is read-only).
    No audio level meter or playback to check takes.
    Researcher can't mark a take as unnatural other than pressing Record again.
    No migration from the old ~/topolecture data folder: copy speaker-database.org and the topolect folders into ~/articulect by hand.
    Export of metadata (CSV) for Praat/R analysis.

Dependencies

    Java 17+
    FlatLaf 3.5.4 (pulled in by Maven)
    mp3spi 1.9.5.4 (with JLayer), for decoding uploaded MP3 clips
    Maven plugins: exec, shade (builds the runnable jar)
