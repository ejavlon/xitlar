package uz.xitlar.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Genre {
    POP("Pop"),
    ROCK("Rock"),
    HIP_HOP("Hip-Hop"),
    RAP("Rap"),
    JAZZ("Jazz"),
    CLASSICAL("Classical"),
    ELECTRONIC("Electronic"),
    R_AND_B("R&B"),
    K_POP("K-Pop"),
    OTHER("Boshqa");

    private final String displayName;
}
