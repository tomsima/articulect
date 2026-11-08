<img width="886" height="247" alt="articulect-banner" src="https://github.com/user-attachments/assets/f8f98c83-7648-41ad-a704-e8dc8f0dbc8c" />

Articulect is a specialised software package for researchers collecting speech data on post-focus compression in Chinese topolects. The application allows for the easy capture of data from speech experiment participants who speak various Chinese topolects and may not be confident with technology. Audio files are automatically named and saved to file ready for processing in Praat. 

Articulect is written in java for Linux machines, and is built from source using maven. git clone the repo, then run mvn package from the root folder.

```bash
git clone https://github.com/tomsima/articulect.git
cd articulect

mvn package
```

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
