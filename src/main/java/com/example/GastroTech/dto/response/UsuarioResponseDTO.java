package com.example.GastroTech.dto.response;

public record UsuarioResponseDTO(
        Long id,
        String nombre,
        String email,
        String rol,
        int penaliztionPoints,
        String status
) {}