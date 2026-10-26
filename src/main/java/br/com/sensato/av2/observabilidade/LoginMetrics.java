package br.com.sensato.av2.observabilidade;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class LoginMetrics {
    private final Counter validos;
    private final Counter invalidos;

    public LoginMetrics(MeterRegistry registry, JdbcTemplate jdbc) {
        validos = registry.counter("seguranca.logins.validos");
        invalidos = registry.counter("seguranca.logins.invalidos");
        Gauge.builder("seguranca.usuarios.bloqueados", jdbc,
                db -> db.queryForObject("SELECT COUNT(*) FROM login_attempts WHERE locked = TRUE", Long.class).doubleValue())
                .register(registry);
    }

    public void loginValido() { validos.increment(); }
    public void loginInvalido() { invalidos.increment(); }
}
