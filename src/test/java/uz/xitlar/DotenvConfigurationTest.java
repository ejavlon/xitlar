package uz.xitlar;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class DotenvConfigurationTest {

    @Autowired
    private Environment environment;

    @Test
    void shouldLoadPropertiesFromDotenv() {
        assertNotNull(environment.getProperty("DB_URL"), "DB_URL should be resolved from .env or environment");
        assertNotNull(environment.getProperty("DB_USERNAME"), "DB_USERNAME should be resolved from .env or environment");
        assertNotNull(environment.getProperty("DB_PASSWORD"), "DB_PASSWORD should be resolved from .env or environment");
        assertNotNull(environment.getProperty("JWT_SECRET_KEY"), "JWT_SECRET_KEY should be resolved from .env or environment");
        assertNotNull(environment.getProperty("ADMIN_DEFAULT_USERNAME"), "ADMIN_DEFAULT_USERNAME should be resolved from .env or environment");
        assertNotNull(environment.getProperty("ADMIN_DEFAULT_PASSWORD"), "ADMIN_DEFAULT_PASSWORD should be resolved from .env or environment");
    }
}
