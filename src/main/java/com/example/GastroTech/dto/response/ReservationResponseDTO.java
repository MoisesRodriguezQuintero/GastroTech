package com.example.GastroTech.dto.response;

import java.time.LocalDateTime;

public record ReservationResponseDTO(
        Long id,
        String tableName,
        String customerName,
        LocalDateTime reservationDate,
        String status
) {
}
