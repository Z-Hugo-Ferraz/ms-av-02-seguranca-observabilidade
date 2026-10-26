package br.com.sensato.av2;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class AcceptanceTest {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate db; @Autowired PasswordEncoder encoder; @Autowired MeterRegistry meters;
 long id;
 @BeforeEach void setup() {
  db.update("DELETE FROM authorities"); db.update("DELETE FROM login_attempts"); db.update("DELETE FROM usuarios");
  id=add("cliente","ROLE_USER"); add("admin-teste","ROLE_ADMIN"); add("manut-teste","ROLE_MANUTENCAO");
 }
 long add(String name,String role) { db.update("INSERT INTO usuarios(username,senha) VALUES (?,?)",name,encoder.encode("senha")); long n=db.queryForObject("SELECT id FROM usuarios WHERE username=?",Long.class,name); db.update("INSERT INTO authorities(usuario_id,authority) VALUES (?,?)",n,role); return n; }
 org.springframework.test.web.servlet.ResultActions login(String name,String password) throws Exception {return mvc.perform(post("/login").contentType("application/json").content("{\"username\":\""+name+"\",\"senha\":\""+password+"\"}"));}
 int attempts(){return db.queryForObject("SELECT failed_attempts FROM login_attempts WHERE usuario_id=?",Integer.class,id);}
 boolean locked(){return db.queryForObject("SELECT locked FROM login_attempts WHERE usuario_id=?",Boolean.class,id);}
 double count(String name){return meters.get(name).counter().count();}
 @Test void sequenciaMetricasEUnlock() throws Exception {
  double valid=count("seguranca.logins.validos"), invalid=count("seguranca.logins.invalidos");
  login("ausente","senha").andExpect(status().isUnauthorized());
  assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM login_attempts",Integer.class));
  login("cliente","errada").andExpect(status().isUnauthorized()); assertEquals(1,attempts());
  mvc.perform(get("/").with(httpBasic("cliente","senha"))).andExpect(status().isForbidden()); assertEquals(1,attempts());
  login("cliente","senha").andExpect(status().isOk()).andExpect(content().string("")); assertEquals(0,attempts());
  login("cliente","errada").andExpect(status().isUnauthorized()); assertFalse(locked());
  login("cliente","errada").andExpect(status().isUnauthorized()); assertTrue(locked());
  login("cliente","senha").andExpect(status().isUnauthorized());
  mvc.perform(get("/").with(httpBasic("cliente","senha"))).andExpect(status().isUnauthorized());
  mvc.perform(put("/unlock/"+id).with(httpBasic("manut-teste","senha"))).andExpect(status().isForbidden());
  assertEquals(1.0,meters.get("seguranca.usuarios.bloqueados").gauge().value());
  mvc.perform(put("/unlock/"+id).with(httpBasic("admin-teste","senha"))).andExpect(status().isNoContent());
  assertEquals(0,attempts()); assertFalse(locked());
  assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM login_attempts WHERE usuario_id=? AND (last_failed_at IS NOT NULL OR locked_at IS NOT NULL)",Integer.class,id));
  mvc.perform(put("/unlock/"+id).with(httpBasic("admin-teste","senha"))).andExpect(status().isNoContent());
  login("cliente","senha").andExpect(status().isOk());
  assertEquals(2.0,count("seguranca.logins.validos")-valid); assertEquals(5.0,count("seguranca.logins.invalidos")-invalid);
  assertEquals(0.0,meters.get("seguranca.usuarios.bloqueados").gauge().value());
 }
 @Test void cadastroTrocaEValidacao() throws Exception {
  String json="{\"username\":\"criado\",\"senha\":\"segredo\"}";
  mvc.perform(post("/").contentType("application/json").content(json)).andExpect(status().isUnauthorized());
  mvc.perform(post("/").with(httpBasic("admin-teste","senha")).contentType("application/json").content(json)).andExpect(status().isForbidden());
  mvc.perform(post("/").with(httpBasic("manut-teste","senha")).contentType("application/json").content(json)).andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNumber()).andExpect(jsonPath("$.senha").doesNotExist());
  long created=db.queryForObject("SELECT id FROM usuarios WHERE username='criado'",Long.class); assertTrue(created>0);
  assertEquals("ROLE_USER",db.queryForObject("SELECT authority FROM authorities WHERE usuario_id=?",String.class,created));
  assertTrue(encoder.matches("segredo",db.queryForObject("SELECT senha FROM usuarios WHERE id=?",String.class,created)));
  mvc.perform(post("/").with(httpBasic("manut-teste","senha")).contentType("application/json").content(json)).andExpect(status().isConflict());
  String list=mvc.perform(get("/").with(httpBasic("manut-teste","senha"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(); assertFalse(list.contains("senha")); assertFalse(list.contains("segredo")); assertFalse(list.contains("$2"));
  mvc.perform(put("/trocarsenha/"+id).contentType("application/json").content("{\"atual\":\"errada\",\"nova\":\"nova\"}")).andExpect(status().isUnauthorized());
  mvc.perform(put("/trocarsenha/99999999").contentType("application/json").content("{\"atual\":\"senha\",\"nova\":\"nova\"}")).andExpect(status().isNotFound());
  login("cliente","errada").andExpect(status().isUnauthorized()); login("cliente","errada").andExpect(status().isUnauthorized());
  mvc.perform(put("/trocarsenha/"+id).contentType("application/json").content("{\"atual\":\"senha\",\"nova\":\"nova\"}")).andExpect(status().isNoContent());
  assertTrue(locked()); login("cliente","nova").andExpect(status().isUnauthorized());
  mvc.perform(put("/unlock/"+id).with(httpBasic("admin-teste","senha"))).andExpect(status().isNoContent());
  login("cliente","senha").andExpect(status().isUnauthorized()); login("cliente","nova").andExpect(status().isOk());
  mvc.perform(put("/unlock/99999999").with(httpBasic("admin-teste","senha"))).andExpect(status().isNotFound());
  mvc.perform(put("/unlock/0").with(httpBasic("admin-teste","senha"))).andExpect(status().isBadRequest());
  double invalid=count("seguranca.logins.invalidos");
  mvc.perform(post("/login").contentType("application/json").content("{}")).andExpect(status().isBadRequest()); assertEquals(invalid,count("seguranca.logins.invalidos"));
 }
 @Test void concorrenciaNaoPerdeFalhas() throws Exception {
  var pool=Executors.newFixedThreadPool(2); var gate=new CountDownLatch(1);
  try { var a=pool.submit(() -> {gate.await(); return login("cliente","errada").andReturn().getResponse().getStatus();}); var b=pool.submit(() -> {gate.await(); return login("cliente","errada").andReturn().getResponse().getStatus();}); gate.countDown(); assertEquals(401,a.get()); assertEquals(401,b.get()); assertEquals(2,attempts()); assertTrue(locked()); } finally {pool.shutdownNow();}
 }
 @Test void endpointsPublicos() throws Exception {
  mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()); mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
  String p=mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertTrue(p.contains("seguranca_logins_validos_total")); assertTrue(p.contains("seguranca_logins_invalidos_total")); assertTrue(p.contains("seguranca_usuarios_bloqueados"));
 }
 @AfterEach void cleanup() {db.update("DELETE FROM authorities"); db.update("DELETE FROM login_attempts"); db.update("DELETE FROM usuarios");}
}
