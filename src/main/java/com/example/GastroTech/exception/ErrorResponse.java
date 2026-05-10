package com.example.GastroTech.exception;

import java.time.LocalDateTime;

public record ErrorResponse(
        String codigo,
        String mensaje,
        LocalDateTime timestamp
) {}
