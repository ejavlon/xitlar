package uz.xitlar.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import uz.xitlar.entity.User;
import uz.xitlar.enums.Role;
import uz.xitlar.repository.UserRepository;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.default.username}")
    private String adminUsername;

    @Value("${admin.default.password}")
    private String adminPassword;

    private final javax.sql.DataSource dataSource;

    @Override
    public void run(String... args) throws Exception {
        migrateSchema();
        createAdmin();
    }

    private void migrateSchema() {
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE users ALTER COLUMN password DROP NOT NULL");
            stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR(255)");
            stmt.execute("""
                    DO $$
                    BEGIN
                        IF NOT EXISTS (
                            SELECT 1 FROM pg_constraint WHERE conname = 'uk_users_email'
                        ) THEN
                            ALTER TABLE users ADD CONSTRAINT uk_users_email UNIQUE (email);
                        END IF;
                    END $$;
                    """);
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS oauth_accounts (
                        id SERIAL PRIMARY KEY,
                        user_id INTEGER NOT NULL,
                        provider VARCHAR(20) NOT NULL,
                        provider_user_id VARCHAR(255) NOT NULL,
                        created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT fk_oauth_accounts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                        CONSTRAINT uk_oauth_provider_user UNIQUE (provider, provider_user_id)
                    )
                    """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_oauth_accounts_user_id ON oauth_accounts(user_id)");
        } catch (Exception e) {
            // Log warning if already executed or in test environment
        }
    }

    private void createAdmin() {
        if (userRepository.existsByUsername(adminUsername)) {
            return;
        }

        User admin = User.builder()
                .firstName("Javlon")
                .lastName("Ergashev")
                .username(adminUsername)
                .password(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .build();

        userRepository.save(admin);
    }
}
