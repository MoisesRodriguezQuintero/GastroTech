package com.example.GastroTech.dto.request;

import com.example.GastroTech.model.Enum.UbicacionMesa;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MesaRequestDTO(
        @Min(value = 1, message = "El número de mesa debe ser positivo")
        int numeroMesa,

        @Min(value = 1, message = "La capacidad mínima es 1")
        @Max(value = 20, message = "La capacidad máxima es 20")
        int capacidad,

        @NotNull(message = "La ubicación es obligatoria")
        UbicacionMesa ubicacion
) {}
