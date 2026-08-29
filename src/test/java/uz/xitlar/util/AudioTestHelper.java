package uz.xitlar.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class AudioTestHelper {

    /**
     * Generates a minimal valid MP3 byte array with an ID3v2 tag and several MPEG-1 Layer 3 frames.
     */
    public static byte[] createMinimalValidMp3() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            // ID3v2.3 header (10 bytes)
            baos.write(new byte[]{
                    'I', 'D', '3',
                    0x03, 0x00, // Version 2.3
                    0x00,       // Flags
                    0x00, 0x00, 0x00, 0x00 // Tag size (0 bytes)
            });

            // 10 valid MPEG-1 Layer III audio frames (128 kbps, 44100 Hz, stereo)
            // Frame size = 144 * 128000 / 44100 = 417 bytes
            byte[] frame = new byte[417];
            frame[0] = (byte) 0xFF;
            frame[1] = (byte) 0xFB; // MPEG-1, Layer 3, no CRC
            frame[2] = (byte) 0x90; // 128 kbps, 44100 Hz
            frame[3] = (byte) 0x00; // Stereo

            for (int i = 0; i < 10; i++) {
                baos.write(frame);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }
}
