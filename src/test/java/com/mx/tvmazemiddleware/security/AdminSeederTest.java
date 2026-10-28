package com.mx.tvmazemiddleware.security;

import com.mx.tvmazemiddleware.dto.User;
import com.mx.tvmazemiddleware.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AdminSeederTest {

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin-secret";

    @Mock
    private UserRepository userRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void creaAdminConContrasenaEncriptadaSiNoExiste() {
        when(userRepository.findByUsername(ADMIN_USERNAME)).thenReturn(Optional.empty());

        AdminSeeder seeder = new AdminSeeder(userRepository, passwordEncoder, ADMIN_USERNAME, ADMIN_PASSWORD);
        seeder.run();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(savedUser.username()).isEqualTo(ADMIN_USERNAME);
        assertThat(savedUser.passwordHash()).isNotEqualTo(ADMIN_PASSWORD);
        assertThat(passwordEncoder.matches(ADMIN_PASSWORD, savedUser.passwordHash())).isTrue();
        assertThat(savedUser.roles()).containsExactly("ADMIN");
        assertThat(savedUser.enabled()).isTrue();
    }

    @Test
    void noGuardaAdminSiYaExiste() {
        User existingAdmin = new User("1", ADMIN_USERNAME, "hash", List.of("ADMIN"), true);
        when(userRepository.findByUsername(ADMIN_USERNAME)).thenReturn(Optional.of(existingAdmin));

        AdminSeeder seeder = new AdminSeeder(userRepository, passwordEncoder, ADMIN_USERNAME, ADMIN_PASSWORD);
        seeder.run();

        verify(userRepository, never()).save(any(User.class));
    }
}
