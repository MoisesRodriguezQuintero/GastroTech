package com.example.GastroTech.dto.response;

public record MesaResponseDTO(
        Long id,
        int numeroMesa,
        int capacidad,
        String ubicacion,
        String estado
) {}
