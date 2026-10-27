package com.mx.tvmazemiddleware.security;

import com.mx.tvmazemiddleware.controller.TVMazeController;
import com.mx.tvmazemiddleware.service.TVMazeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TVMazeController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TVMazeService tvMazeService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @WithMockUser(roles = "USER")
    void actuatorConRolUserDevuelve403ProblemJson() throws Exception {
        mockMvc.perform(get("/actuator/beans"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:forbidden"))
                .andExpect(jsonPath("$.status").value(403));
    }
}
