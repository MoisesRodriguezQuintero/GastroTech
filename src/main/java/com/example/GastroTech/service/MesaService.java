package com.example.GastroTech.service;

import com.example.GastroTech.dto.request.MesaRequestDTO;
import com.example.GastroTech.dto.response.MesaResponseDTO;
import com.example.GastroTech.exception.BusinessException;
import com.example.GastroTech.exception.ResourceNotFoundException;
import com.example.GastroTech.model.Entity.Mesa;
import com.example.GastroTech.model.Enum.EstadoMesa;
import com.example.GastroTech.repository.MesaRepository;
import com.example.GastroTech.repository.ReservaRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MesaService {

    private final ReservaRepository reservaRepository;
    private final MesaRepository mesaRepository;

    public List<MesaResponseDTO> findAllMesas() {
        return mesaRepository.findAll().stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    public MesaResponseDTO findMesaById(Long id) {
        Mesa mesa = mesaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa no encontrada con id: " + id));
        return mapToResponseDTO(mesa);
    }

    public MesaResponseDTO createMesa(MesaRequestDTO dto) {
        Mesa mesa = Mesa.builder()
                .numeroMesa(dto.numeroMesa())
                .capacidad(dto.capacidad())
                .ubicacion(dto.ubicacion())
                .estado(EstadoMesa.DISPONIBLE)
                .build();
        return mapToResponseDTO(mesaRepository.save(mesa));
    }

    public List<MesaResponseDTO> findMesasDisponibles(MesaRequestDTO mesa, int capacidad) {
        if (capacidad > mesa.capacidad()){
            throw new BusinessException(
                    "Cantidad de comensales superior a la capacidad de la mesa"
            );
        }
        return mesaRepository.findByEstado(EstadoMesa.DISPONIBLE).stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    // ─── Mapeo entidad → DTO (las entidades nunca salen del Service) ─────────

    private MesaResponseDTO mapToResponseDTO(Mesa mesa) {
        return new MesaResponseDTO(
                mesa.getId(),
                mesa.getNumeroMesa(),
                mesa.getCapacidad(),
                mesa.getUbicacion().name(),
                mesa.getEstado().name(),
                mesa.isVip()
        );
    }
}
