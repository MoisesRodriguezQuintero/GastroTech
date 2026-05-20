package com.example.GastroTech.controller;

import com.example.GastroTech.dto.response.UsuarioResponseDTO;
import com.example.GastroTech.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "Gestion de usuarios (solo ADMIN)")
@SecurityRequirement(name = "BearerAuth")
public class UsuarioController {

    private final UsuarioService usuarioService;

    @PatchMapping("/{id}/reset-penalization")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Resetear penalizacion de un usuario (pone puntos a 0 y reactiva la cuenta)")
    public ResponseEntity<UsuarioResponseDTO> resetPenalization(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.resetPenalization(id));
    }
}
