package com.example.GastroTech.dto.response;

public record AuthResponseDTO(
        String token,
        String tipo,
        String email,
        String rol
) {
    /** Constructor de conveniencia para devolver solo el token. */
    public AuthResponseDTO(String token) {
        this(token, "Bearer", null, null);
    }
}
