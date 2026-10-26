package br.com.sensato.av2.controller;
import br.com.sensato.av2.dto.*;
import br.com.sensato.av2.service.UsuarioService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController
public class UsuarioController {
 private final UsuarioService service;
 public UsuarioController(UsuarioService service) { this.service=service; }
 @Operation(summary="Validar login", responses={@ApiResponse(responseCode="200",description="Credenciais válidas"),@ApiResponse(responseCode="400",description="Entrada inválida"),@ApiResponse(responseCode="401",description="Credenciais inválidas ou conta bloqueada")})
 @PostMapping("/login") ResponseEntity<?> login(@Valid @RequestBody Credenciais c) { return service.login(c) ? ResponseEntity.ok().build() : ResponseEntity.status(401).body(Map.of("mensagem","Credenciais inválidas")); }
 @Operation(summary="Trocar senha pelo ID numérico", responses={@ApiResponse(responseCode="204",description="Senha alterada"),@ApiResponse(responseCode="400",description="Entrada inválida"),@ApiResponse(responseCode="401",description="Senha incorreta"),@ApiResponse(responseCode="404",description="Usuário inexistente")})
 @PutMapping("/trocarsenha/{userid}") ResponseEntity<?> trocar(@PathVariable long userid,@Valid @RequestBody TrocaSenha c) { service.trocar(userid,c); return ResponseEntity.noContent().build(); }
 @Operation(summary="Cadastrar usuário", security=@SecurityRequirement(name="basicAuth"), responses={@ApiResponse(responseCode="201",description="Criado"),@ApiResponse(responseCode="400",description="Entrada inválida"),@ApiResponse(responseCode="401",description="Não autenticado"),@ApiResponse(responseCode="403",description="Sem permissão"),@ApiResponse(responseCode="409",description="Username já cadastrado")})
 @PostMapping("/") ResponseEntity<UsuarioResposta> criar(@Valid @RequestBody Credenciais c) { return ResponseEntity.status(201).body(service.criar(c)); }
 @Operation(summary="Listar usuários", security=@SecurityRequirement(name="basicAuth"), responses={@ApiResponse(responseCode="200",description="Lista"),@ApiResponse(responseCode="401",description="Não autenticado"),@ApiResponse(responseCode="403",description="Sem permissão")})
 @GetMapping("/") List<UsuarioResposta> listar() { return service.listar(); }
 @Operation(summary="Desbloquear usuário pelo ID numérico", security=@SecurityRequirement(name="basicAuth"), responses={@ApiResponse(responseCode="204",description="Desbloqueado"),@ApiResponse(responseCode="400",description="ID inválido"),@ApiResponse(responseCode="401",description="Não autenticado"),@ApiResponse(responseCode="403",description="Sem permissão"),@ApiResponse(responseCode="404",description="Usuário inexistente")})
 @PutMapping("/unlock/{userid}") ResponseEntity<?> unlock(@PathVariable long userid) { service.unlock(userid); return ResponseEntity.noContent().build(); }
}
