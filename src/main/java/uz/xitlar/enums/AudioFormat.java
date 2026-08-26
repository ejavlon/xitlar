package uz.xitlar.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum AudioFormat {
    MP3("mp3"),
    FLAC("flac"),
    WAV("wav"),
    AAC("aac");

    @Getter
    private final String formatName;
}
