package br.com.sensato.av2;

import br.com.sensato.av2.config.DemoDataInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:demotest;DB_CLOSE_DELAY=-1")
@ActiveProfiles({"test", "demo"})
class DemoDataInitializerTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DemoDataInitializer initializer;

    @Test
    void createsDemoAccountsOnlyOnceAndPreservesChanges() {
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM usuarios", Integer.class));
        for (String[] account : new String[][] {{"admin", "Admin123!", "ROLE_ADMIN"}, {"manutencao", "Manut123!", "ROLE_MANUTENCAO"}}) {
            String hash = jdbc.queryForObject("SELECT senha FROM usuarios WHERE username=?", String.class, account[0]);
            assertTrue(new BCryptPasswordEncoder().matches(account[1], hash));
            assertEquals(account[2], jdbc.queryForObject("SELECT a.authority FROM authorities a JOIN usuarios u ON a.usuario_id=u.id WHERE u.username=?", String.class, account[0]));
        }
        Long adminId = jdbc.queryForObject("SELECT id FROM usuarios WHERE username='admin'", Long.class);
        jdbc.update("UPDATE usuarios SET senha='changed' WHERE id=?", adminId);
        jdbc.update("INSERT INTO login_attempts(usuario_id, locked) VALUES (?, TRUE)", adminId);
        initializer.run(new DefaultApplicationArguments(new String[0]));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM usuarios", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM authorities", Integer.class));
        assertEquals("changed", jdbc.queryForObject("SELECT senha FROM usuarios WHERE id=?", String.class, adminId));
        assertTrue(jdbc.queryForObject("SELECT locked FROM login_attempts WHERE usuario_id=?", Boolean.class, adminId));
    }
}
