package com.devhub.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class LoginController {
    private final LoginService loginService;

    public LoginController(LoginService loginService) { this.loginService = loginService; }

    @PostMapping("/login")
    public AuthTokenResponse login(@Valid @RequestBody LoginRequests.PasswordLoginRequest request) {
        return loginService.loginWithPassword(request);
    }

    @PostMapping("/login/google")
    public AuthTokenResponse googleLogin(@Valid @RequestBody LoginRequests.SocialLoginRequest request) {
        return loginService.loginWithProvider(RegistrationMethod.GOOGLE, request.providerToken());
    }

    @PostMapping("/refresh")
    public AuthTokenResponse refresh(@Valid @RequestBody LoginRequests.RefreshRequest request) {
        return loginService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LoginRequests.RefreshRequest request) {
        loginService.logout(request.refreshToken());
    }
}
