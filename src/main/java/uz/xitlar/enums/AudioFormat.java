package uz.xitlar.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum AudioFormat {
    MP3("mp3");

    @Getter
    private final String formatName;
}
