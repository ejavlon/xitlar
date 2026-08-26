package uz.xitlar.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import uz.xitlar.enums.Role;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class UserTest {

    @Test
    void getPasswordReturnsStoredPassword() {
        User user = User.builder()
                .firstName("Test")
                .lastName("User")
                .username("testuser")
                .password("encoded-password")
                .role(Role.USER)
                .build();

        assertEquals("encoded-password", user.getPassword());
    }

    @Test
    void passwordIsNotSerializedToJson() throws Exception {
        User user = User.builder()
                .firstName("Test")
                .lastName("User")
                .username("testuser")
                .password("secret-bcrypt-hash")
                .role(Role.USER)
                .build();

        String json = new ObjectMapper().writeValueAsString(user);

        assertFalse(json.contains("secret-bcrypt-hash"));
    }
}
