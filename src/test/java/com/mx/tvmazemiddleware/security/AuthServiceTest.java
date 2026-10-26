package com.mx.tvmazemiddleware.security;

import com.mx.tvmazemiddleware.dto.User;
import com.mx.tvmazemiddleware.exception.InvalidCredentialsException;
import com.mx.tvmazemiddleware.repository.UserRepository;
import com.mx.tvmazemiddleware.service.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private AuthService authService;


    @Test
    void loginUsuarioNoEncontradoCredencialesInvalidas() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("unknown", "secret")).isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(passwordEncoder, tokenService);
    }

    @Test
    void loginUsuarioContrasenaIncorrecta() {

        User user = new User("1", "ana", "hash", List.of("USER"), true);

        when(userRepository.findByUsername("ana")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("mala", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("ana", "mala")).isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(tokenService);
    }


    @Test
    void loginUsuarioDeshabilitadoLanzaCredencialesInvalidas() {

        User user = new User("1", "ana", "hash", List.of("USER"), false);

        when(userRepository.findByUsername("ana")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("clave", "hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login("ana", "clave")).isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(tokenService);
        verify(passwordEncoder).matches("clave", "hash");
    }

    @Test
    void loginUsuarioValidoDevuelveToken() {
        User user = new User("1", "ana", "hash", List.of("USER"), true);

        when(userRepository.findByUsername("ana")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("clave", "hash")).thenReturn(true);
        when(tokenService.issue(user)).thenReturn("el-token");

        assertThat(authService.login("ana", "clave")).isEqualTo("el-token");
    }
}
