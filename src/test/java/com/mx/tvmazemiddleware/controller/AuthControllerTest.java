package com.mx.tvmazemiddleware.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.tvmazemiddleware.dto.auth.LoginRequest;
import com.mx.tvmazemiddleware.exception.InvalidCredentialsException;
import com.mx.tvmazemiddleware.security.AuthService;
import com.mx.tvmazemiddleware.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void loginExitoso() throws Exception {
        LoginRequest request = new LoginRequest("ana", "clave");

        when(authService.login("ana", "clave")).thenReturn("el-token");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("el-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void loginCredencialesInvalidasDevuelve401() throws Exception {
        LoginRequest request = new LoginRequest("ana", "clave-mala");

        when(authService.login("ana", "clave-mala"))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:invalid-credentials"))
                .andExpect(jsonPath("$.title").value("Credenciales inválidas"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(content().string(not(containsString("usuario"))))
                .andExpect(content().string(not(containsString("contraseña"))));
    }

    @ParameterizedTest
    @CsvSource({
            " , clave",
            "'', clave",
            "ana, ",
            "ana, ''"
    })
    void loginCuerpoInvalidoDevuelve400(String username, String password) throws Exception {
        LoginRequest request = new LoginRequest(username, password);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:validation-error"))
                .andExpect(jsonPath("$.title").value("Error de validación"))
                .andExpect(jsonPath("$.errors").isArray());

        verifyNoInteractions(authService);
    }
}