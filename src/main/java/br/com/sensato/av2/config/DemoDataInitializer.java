package br.com.sensato.av2.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
public class DemoDataInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public DemoDataInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createIfMissing("admin", "Admin123!", "ROLE_ADMIN");
        createIfMissing("manutencao", "Manut123!", "ROLE_MANUTENCAO");
    }

    private void createIfMissing(String username, String password, String role) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE username = ?", Integer.class, username);
        if (count != null && count == 0) {
            jdbc.update("INSERT INTO usuarios (username, senha) VALUES (?, ?)", username, encoder.encode(password));
            jdbc.update("INSERT INTO authorities (usuario_id, authority) SELECT id, ? FROM usuarios WHERE username = ?", role, username);
        }
    }
}
