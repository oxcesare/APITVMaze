package com.mx.tvmazemiddleware.controller;

import com.mx.tvmazemiddleware.security.AuthService;
import com.mx.tvmazemiddleware.dto.auth.LoginRequest;
import com.mx.tvmazemiddleware.dto.auth.LoginResponse;
import com.mx.tvmazemiddleware.security.JwtConstants;
import com.mx.tvmazemiddleware.util.ApiConstants;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping(ApiConstants.V1 + "/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request.username(), request.password());
        long expiresIn = JwtConstants.TTL.toSeconds();
        return new LoginResponse(token, "Bearer", expiresIn);
    }
}
