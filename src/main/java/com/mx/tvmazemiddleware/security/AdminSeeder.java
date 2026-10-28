package com.mx.tvmazemiddleware.security;

import com.mx.tvmazemiddleware.dto.User;
import com.mx.tvmazemiddleware.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("dev") // 1. Restringido estrictamente al perfil dev
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;

    public AdminSeeder(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       @Value("${app.admin.username:admin}") String adminUsername,
                       @Value("${app.admin.password:#{null}}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException(
                    "Error crítico de configuración: La propiedad o variable para la contraseña del administrador es obligatoria en el entorno de desarrollo."
            );
        }

        if (userRepository.findByUsername(adminUsername).isPresent()) {
            return;
        }

        User admin = new User(
                null,
                adminUsername,
                passwordEncoder.encode(adminPassword),
                List.of("ADMIN"),
                true
        );
        userRepository.save(admin);
    }
}