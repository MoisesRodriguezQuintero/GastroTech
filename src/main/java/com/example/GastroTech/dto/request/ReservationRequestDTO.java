package com.example.GastroTech.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record ReservationRequestDTO(
        @NotNull(message = "La mesa es obligatoria")
        Long tableId,

        @NotNull(message = "La fecha y hora es obligatoria")
        @Future(message = "La reserva debe ser en una fecha futura")
        LocalDateTime reservationDate,

        @Min(value = 1, message = "Mínimo 1 comensal")
        @Max(value = 12, message = "Máximo 12 comensales por mesa")
        int numberOfGuests
) {}
