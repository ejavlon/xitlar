package uz.xitlar.config;

import lombok.RequiredArgsConstructor;
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

    @Override
    public void run(String... args) throws Exception {
        createAdmin();
    }

    private void createAdmin() {
        if (userRepository.existsByUsername("ejavlon")) {
            return;
        }

        User admin = User.builder()
                .firstName("Javlon")
                .lastName("Ergashev")
                .username("ejavlon")
                .password(passwordEncoder.encode("root"))
                .role(Role.ADMIN)
                .build();

        userRepository.save(admin);
    }
}
