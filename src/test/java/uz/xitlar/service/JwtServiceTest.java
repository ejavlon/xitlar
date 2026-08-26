package uz.xitlar.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uz.xitlar.entity.User;
import uz.xitlar.enums.Role;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
class JwtServiceTest {

    @Autowired
    private JwtService jwtService;

    @Test
    void generatesAndParsesToken() {
        User user = User.builder()
                .firstName("Test")
                .lastName("User")
                .username("testuser")
                .password("encoded-password")
                .role(Role.USER)
                .build();

        String jwt = jwtService.generateToken(user);

        assertEquals("testuser", jwtService.extractUsername(jwt));
        assertFalse(jwtService.isTokenExpired(jwt));
    }
}
