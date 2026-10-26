package br.com.sensato.av2.service;
import br.com.sensato.av2.dto.*;
import br.com.sensato.av2.exception.ApiException;
import br.com.sensato.av2.observabilidade.LoginMetrics;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {
 private final JdbcTemplate jdbc; private final JdbcUserDetailsManager users; private final AuthenticationManager auth; private final PasswordEncoder encoder; private final LoginMetrics metrics;
 public UsuarioService(JdbcTemplate jdbc, JdbcUserDetailsManager users, AuthenticationManager auth, PasswordEncoder encoder, LoginMetrics metrics) { this.jdbc=jdbc; this.users=users; this.auth=auth; this.encoder=encoder; this.metrics=metrics; }
 public void senhaValida(String senha) { if (senha == null || senha.isBlank() || senha.getBytes(StandardCharsets.UTF_8).length > 72) throw new ApiException(HttpStatus.BAD_REQUEST,"Senha inválida"); }
 public void idValido(long id) { if (id <= 0) throw new ApiException(HttpStatus.BAD_REQUEST,"ID inválido"); }
 @Transactional
 public boolean login(Credenciais c) {
  senhaValida(c.senha());
  var ids=jdbc.query("SELECT id FROM usuarios WHERE username=? FOR UPDATE", (rs,n)->rs.getLong(1), c.username());
  boolean valid=false;
  if (!ids.isEmpty()) {
   long id=ids.getFirst();
   var locked=jdbc.query("SELECT locked FROM login_attempts WHERE usuario_id=?",(rs,n)->rs.getBoolean(1),id);
   if (locked.isEmpty() || !locked.getFirst()) {
    try { auth.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(c.username(),c.senha())); valid=true; } catch (AuthenticationException ignored) { }
    if (locked.isEmpty()) jdbc.update("INSERT INTO login_attempts(usuario_id) VALUES (?)",id);
    if (valid) jdbc.update("UPDATE login_attempts SET failed_attempts=0, last_failed_at=NULL, locked_at=NULL, updated_at=CURRENT_TIMESTAMP WHERE usuario_id=?",id);
    else jdbc.update("UPDATE login_attempts SET failed_attempts=failed_attempts+1, last_failed_at=CURRENT_TIMESTAMP, locked=CASE WHEN failed_attempts+1>=2 THEN TRUE ELSE FALSE END, locked_at=CASE WHEN failed_attempts+1>=2 THEN CURRENT_TIMESTAMP ELSE NULL END, updated_at=CURRENT_TIMESTAMP WHERE usuario_id=?",id);
   }
  }
  if(valid) metrics.loginValido(); else metrics.loginInvalido();
  return valid;
 }
 @Transactional
 public UsuarioResposta criar(Credenciais c) {
  senhaValida(c.senha());
  try { users.createUser(User.withUsername(c.username()).password(encoder.encode(c.senha())).roles("USER").build()); }
  catch (DataIntegrityViolationException e) { throw new ApiException(HttpStatus.CONFLICT,"Usuário já cadastrado"); }
  return new UsuarioResposta(jdbc.queryForObject("SELECT id FROM usuarios WHERE username=?",Long.class,c.username()),c.username());
 }
 public List<UsuarioResposta> listar() { return jdbc.query("SELECT id, username FROM usuarios ORDER BY id",(rs,n)->new UsuarioResposta(rs.getLong(1),rs.getString(2))); }
 @Transactional
 public void trocar(long id, TrocaSenha c) {
  idValido(id); senhaValida(c.atual()); senhaValida(c.nova());
  var hashes=jdbc.query("SELECT senha FROM usuarios WHERE id=? FOR UPDATE",(rs,n)->rs.getString(1),id);
  if(hashes.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"Usuário não encontrado");
  if(!encoder.matches(c.atual(),hashes.getFirst())) throw new ApiException(HttpStatus.UNAUTHORIZED,"Credenciais inválidas");
  jdbc.update("UPDATE usuarios SET senha=? WHERE id=?",encoder.encode(c.nova()),id);
 }
 @Transactional
 public void unlock(long id) {
  idValido(id); var ids=jdbc.query("SELECT id FROM usuarios WHERE id=? FOR UPDATE",(rs,n)->rs.getLong(1),id);
  if(ids.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"Usuário não encontrado");
  jdbc.update("UPDATE login_attempts SET failed_attempts=0, locked=FALSE, last_failed_at=NULL, locked_at=NULL, updated_at=CURRENT_TIMESTAMP WHERE usuario_id=?",id);
 }
}

