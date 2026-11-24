package dev.knowledgeprofiling.app;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final JdbcTemplate jdbcTemplate;
    public DatabaseUserDetailsService(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return jdbcTemplate.query("SELECT username, password_hash, role, enabled FROM app_user WHERE username = ?",
                result -> {
                    if (!result.next()) throw new UsernameNotFoundException("User not found");
                    return User.withUsername(result.getString("username"))
                            .password(result.getString("password_hash"))
                            .roles(result.getString("role"))
                            .disabled(!result.getBoolean("enabled"))
                            .build();
                }, username);
    }
}
