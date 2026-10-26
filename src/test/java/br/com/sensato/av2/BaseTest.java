package br.com.sensato.av2;

import br.com.sensato.av2.observabilidade.LoginMetrics;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class BaseTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PrometheusMeterRegistry registry;
    @Autowired LoginMetrics metrics;

    @Test
    void schemaEnforcesKeysAndUniqueness() {
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM usuarios", Integer.class));
        jdbc.update("INSERT INTO usuarios(username, senha) VALUES ('teste', 'hash')");
        Long id = jdbc.queryForObject("SELECT id FROM usuarios WHERE username='teste'", Long.class);
        assertNotNull(id);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO usuarios(username, senha) VALUES ('teste', 'hash')"));
        jdbc.update("INSERT INTO login_attempts(usuario_id) VALUES (?)", id);
        jdbc.update("INSERT INTO authorities(usuario_id, authority) VALUES (?, 'ROLE_USER')", id);
        assertNotNull(jdbc.queryForObject("SELECT id FROM login_attempts WHERE usuario_id=?", Long.class, id));
        assertNotNull(jdbc.queryForObject("SELECT id FROM authorities WHERE usuario_id=?", Long.class, id));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO login_attempts(usuario_id) VALUES (?)", id));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO authorities(usuario_id, authority) VALUES (?, 'ROLE_USER')", id));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO login_attempts(usuario_id) VALUES (-1)"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO authorities(usuario_id, authority) VALUES (-1, 'ROLE_USER')"));
        jdbc.update("UPDATE login_attempts SET locked=TRUE WHERE usuario_id=?", id);
        String before = registry.scrape();
        assertTrue(before.contains("seguranca_logins_validos_total 0.0"));
        assertTrue(before.contains("seguranca_logins_invalidos_total 0.0"));
        assertTrue(before.contains("seguranca_usuarios_bloqueados 1.0"));
        metrics.loginValido(); metrics.loginInvalido();
        String after = registry.scrape();
        assertTrue(after.contains("seguranca_logins_validos_total 1.0"));
        assertTrue(after.contains("seguranca_logins_invalidos_total 1.0"));
        jdbc.update("UPDATE login_attempts SET locked=FALSE WHERE usuario_id=?", id);
        assertTrue(registry.scrape().contains("seguranca_usuarios_bloqueados 0.0"));
    }
}
