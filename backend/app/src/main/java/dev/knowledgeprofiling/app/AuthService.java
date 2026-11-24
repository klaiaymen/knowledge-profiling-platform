package dev.knowledgeprofiling.app;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

@Service
public class AuthService {
    private static final long ACCESS_TOKEN_SECONDS = 900;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtEncoder jwtEncoder;
    private final RefreshTokenService refreshTokens;
    private final AuditService audit;
    private final String issuer;

    public AuthService(AuthenticationManager authenticationManager, UserDetailsService userDetailsService,
                       JwtEncoder jwtEncoder, RefreshTokenService refreshTokens, AuditService audit,
                       @Value("${app.jwt.issuer:knowledge-profiling-platform}") String issuer) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtEncoder = jwtEncoder;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.issuer = issuer;
    }

    @Transactional
    public TokenResponse login(String username, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));
        return issueTokens(authentication, "AUTH_LOGIN");
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        RefreshTokenService.RotationResult rotation = refreshTokens.rotate(refreshToken);
        UserDetails user = userDetailsService.loadUserByUsername(rotation.username());
        Authentication authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        return issueTokens(authentication, rotation.refreshToken(), "AUTH_REFRESH");
    }

    private TokenResponse issueTokens(Authentication authentication, String action) {
        return issueTokens(authentication, refreshTokens.issue(authentication.getName()), action);
    }

    private TokenResponse issueTokens(Authentication authentication, String refresh, String action) {
        Instant now = Instant.now();
        List<String> authorities = authentication.getAuthorities().stream().map(a -> a.getAuthority()).toList();
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(authentication.getName()).issuedAt(now)
                .expiresAt(now.plusSeconds(ACCESS_TOKEN_SECONDS)).claim("authorities", authorities).build();
        String access = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        audit.record(authentication.getName(), action, "session");
        return new TokenResponse(access, refresh, "Bearer", ACCESS_TOKEN_SECONDS);
    }

    public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) { }
}
