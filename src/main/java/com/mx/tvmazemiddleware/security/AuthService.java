package com.mx.tvmazemiddleware.security;

import com.mx.tvmazemiddleware.dto.User;
import com.mx.tvmazemiddleware.exception.InvalidCredentialsException;
import com.mx.tvmazemiddleware.repository.UserRepository;
import com.mx.tvmazemiddleware.service.TokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    public String login(String username, String password) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        if (!user.enabled()) {
            throw new InvalidCredentialsException();
        }
        return tokenService.issue(user);
    }
}
