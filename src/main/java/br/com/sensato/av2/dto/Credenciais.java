package br.com.sensato.av2.dto;
import jakarta.validation.constraints.*;
public record Credenciais(@NotBlank @Size(max=100) String username, @NotBlank String senha) { }
