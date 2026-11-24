package dev.knowledgeprofiling.app;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/login")
    public AuthService.TokenResponse login(@Valid @RequestBody LoginRequest request) {
        try { return authService.login(request.username(), request.password()); }
        catch (AuthenticationException e) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"); }
    }

    @PostMapping("/refresh")
    public AuthService.TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        try { return authService.refresh(request.refreshToken()); }
        catch (IllegalArgumentException | AuthenticationException e) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"); }
    }

    public record LoginRequest(@NotBlank @Size(max = 120) String username, @NotBlank @Size(max = 200) String password) { }
    public record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) { }
}
