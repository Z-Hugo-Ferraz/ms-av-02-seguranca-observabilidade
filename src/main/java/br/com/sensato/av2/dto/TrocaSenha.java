package br.com.sensato.av2.dto;
import jakarta.validation.constraints.NotBlank;
public record TrocaSenha(@NotBlank String atual, @NotBlank String nova) { }
