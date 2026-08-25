package com.xiaou.web.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Test
    void loginReturnsStatusAndTokenData() throws Exception {
        when(authService.login("demo-pilot-pm", "demo-password"))
                .thenReturn(new AuthLoginResult("opaque-token", 3600, "demo-pilot-pm"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo-pilot-pm\",\"password\":\"demo-password\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.token").value("opaque-token"))
                .andExpect(jsonPath("$.data.expires_in_seconds").value(3600));
    }

    @Test
    void loginRejectsBlankCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(authService, never()).login(eq(""), eq(""));
    }

    @Test
    void loginMapsInvalidCredentialsToUnauthorized() throws Exception {
        when(authService.login("demo-pilot-pm", "wrong-password"))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo-pilot-pm\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void logoutPassesBearerTokenAndReturnsInvalidationResult() throws Exception {
        when(authService.logout("opaque-token")).thenReturn(new AuthLogoutResult(true));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer opaque-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.invalidated").value(true));

        verify(authService).logout("opaque-token");
    }

    @Test
    void logoutWithoutTokenIsIdempotent() throws Exception {
        when(authService.logout("")).thenReturn(new AuthLogoutResult(false));

        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.invalidated").value(false));

        verify(authService).logout("");
    }
}
