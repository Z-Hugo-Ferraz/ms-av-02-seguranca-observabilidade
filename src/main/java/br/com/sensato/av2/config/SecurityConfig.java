package br.com.sensato.av2.config;

import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
 @Bean JdbcUserDetailsManager users(DataSource dataSource) {
  var manager = new JdbcUserDetailsManager(dataSource);
  manager.setUsersByUsernameQuery("SELECT u.username, u.senha, (u.enabled AND NOT COALESCE(a.locked, FALSE)) FROM usuarios u LEFT JOIN login_attempts a ON a.usuario_id=u.id WHERE u.username=?");
  manager.setAuthoritiesByUsernameQuery("SELECT u.username, a.authority FROM authorities a JOIN usuarios u ON u.id=a.usuario_id WHERE u.username=?");
  manager.setCreateUserSql("INSERT INTO usuarios(username, senha, enabled) VALUES (?,?,?)");
  manager.setCreateAuthoritySql("INSERT INTO authorities(usuario_id, authority) VALUES ((SELECT id FROM usuarios WHERE username=?), ?)");
  manager.setUserExistsSql("SELECT username FROM usuarios WHERE username=?");
  return manager;
 }
 @Bean AuthenticationManager authenticationManager(JdbcUserDetailsManager users, PasswordEncoder encoder) {
  var provider = new DaoAuthenticationProvider(users); provider.setPasswordEncoder(encoder); return new ProviderManager(provider);
 }
 @Bean SecurityFilterChain security(HttpSecurity http, AuthenticationManager manager) throws Exception {
  return http.csrf(csrf -> csrf.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authenticationManager(manager).httpBasic(b -> b.authenticationEntryPoint((req,res,e) -> {res.setStatus(401); res.setHeader("WWW-Authenticate", "Basic realm=\"AV2\""); res.setContentType("application/json"); res.getWriter().write("{\"mensagem\":\"Autenticação necessária\"}");}))
   .exceptionHandling(e -> e.accessDeniedHandler((req,res,ex) -> {res.setStatus(403); res.setContentType("application/json"); res.getWriter().write("{\"mensagem\":\"Acesso negado\"}");}))
   .authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.POST,"/login").permitAll().requestMatchers(HttpMethod.PUT,"/trocarsenha/**").permitAll()
    .requestMatchers("/swagger-ui/**","/swagger-ui.html","/v3/api-docs/**","/actuator/health","/actuator/prometheus").permitAll()
    .requestMatchers(HttpMethod.GET,"/").hasRole("MANUTENCAO").requestMatchers(HttpMethod.POST,"/").hasRole("MANUTENCAO")
    .requestMatchers(HttpMethod.PUT,"/unlock/**").hasRole("ADMIN").anyRequest().denyAll()).build();
 }
}


