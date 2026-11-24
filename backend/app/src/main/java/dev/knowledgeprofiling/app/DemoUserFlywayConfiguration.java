package dev.knowledgeprofiling.app;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Map;

@Configuration
public class DemoUserFlywayConfiguration {
    @Bean FlywayConfigurationCustomizer demoUserHashPlaceholders(
            PasswordEncoder passwordEncoder,
            @Value("${KPF_DEMO_USER_PASSWORD:}") String userPassword,
            @Value("${KPF_DEMO_ADMIN_PASSWORD:}") String adminPassword) {
        String userHash = userPassword.isBlank() ? "" : passwordEncoder.encode(userPassword);
        String adminHash = adminPassword.isBlank() ? "" : passwordEncoder.encode(adminPassword);
        return configuration -> configuration.placeholders(Map.of("demoUserHash", userHash, "demoAdminHash", adminHash));
    }
}
