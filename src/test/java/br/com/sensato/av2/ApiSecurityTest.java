package br.com.sensato.av2;
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
class ApiSecurityTest {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired PasswordEncoder encoder;
 long userId;
 @BeforeEach void setup() {
  jdbc.update("DELETE FROM authorities"); jdbc.update("DELETE FROM login_attempts"); jdbc.update("DELETE FROM usuarios");
  userId=add("cliente","ROLE_USER"); add("gestor","ROLE_ADMIN"); add("operador","ROLE_MANUTENCAO");
 }
 long add(String name,String role) { jdbc.update("INSERT INTO usuarios(username,senha) VALUES (?,?)",name,encoder.encode("senha")); long id=jdbc.queryForObject("SELECT id FROM usuarios WHERE username=?",Long.class,name); jdbc.update("INSERT INTO authorities(usuario_id,authority) VALUES (?,?)",id,role); return id; }
 @Test void duasFalhasPersistemEBloqueiam() throws Exception {
  for(int i=1;i<=2;i++) {
   mvc.perform(post("/login").contentType("application/json").content("{\"username\":\"cliente\",\"senha\":\"errada\"}")).andExpect(status().isUnauthorized());
   assertEquals(i,jdbc.queryForObject("SELECT failed_attempts FROM login_attempts WHERE usuario_id=?",Integer.class,userId));
  }
  assertTrue(jdbc.queryForObject("SELECT locked FROM login_attempts WHERE usuario_id=?",Boolean.class,userId));
  mvc.perform(post("/login").contentType("application/json").content("{\"username\":\"cliente\",\"senha\":\"senha\"}")).andExpect(status().isUnauthorized());
  mvc.perform(get("/").with(httpBasic("cliente","senha"))).andExpect(status().isUnauthorized());
  mvc.perform(put("/unlock/"+userId).with(httpBasic("gestor","senha"))).andExpect(status().isNoContent());
  mvc.perform(post("/login").contentType("application/json").content("{\"username\":\"cliente\",\"senha\":\"senha\"}")).andExpect(status().isOk());
 }
 @Test void restricaoDeRoles() throws Exception {
  mvc.perform(get("/")).andExpect(status().isUnauthorized());
  mvc.perform(get("/").with(httpBasic("gestor","senha"))).andExpect(status().isForbidden());
  mvc.perform(get("/").with(httpBasic("operador","senha"))).andExpect(status().isOk());
  mvc.perform(put("/unlock/"+userId).with(httpBasic("operador","senha"))).andExpect(status().isForbidden());
  mvc.perform(post("/").with(httpBasic("operador","senha")).contentType("application/json").content("{\"username\":\"novo\",\"senha\":\"segredo\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.username").value("novo"));
 }
 @Test void contratosComplementares() throws Exception {
  String corpo="{\"username\":\"novo\",\"senha\":\"segredo\"}";
  mvc.perform(post("/").with(httpBasic("operador","senha")).contentType("application/json").content(corpo)).andExpect(status().isCreated());
  mvc.perform(post("/").with(httpBasic("operador","senha")).contentType("application/json").content(corpo)).andExpect(status().isConflict());
  mvc.perform(put("/trocarsenha/"+userId).contentType("application/json").content("{\"atual\":\"senha\",\"nova\":\"outra\"}")).andExpect(status().isNoContent());
  mvc.perform(post("/login").contentType("application/json").content("{\"username\":\"cliente\",\"senha\":\"outra\"}")).andExpect(status().isOk());
  mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.components.securitySchemes.basicAuth.scheme").value("basic"));
  mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
 }
 @AfterEach void cleanup() { jdbc.update("DELETE FROM authorities"); jdbc.update("DELETE FROM login_attempts"); jdbc.update("DELETE FROM usuarios"); }
}
