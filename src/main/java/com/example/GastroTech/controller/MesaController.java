package com.example.GastroTech.controller;

import com.example.GastroTech.dto.request.MesaRequestDTO;
import com.example.GastroTech.dto.response.MesaResponseDTO;
import com.example.GastroTech.service.MesaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tables")
@RequiredArgsConstructor
@Tag(name = "Mesas", description = "Gestion de mesas (solo ADMIN)")
@SecurityRequirement(name = "BearerAuth")
public class MesaController {

    private final MesaService mesaService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar todas las mesas")
    public ResponseEntity<List<MesaResponseDTO>> getAllMesas() {
        return ResponseEntity.ok(mesaService.findAllMesas());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear una nueva mesa")
    public ResponseEntity<MesaResponseDTO> createMesa(@Valid @RequestBody MesaRequestDTO dto) {
        return new ResponseEntity<>(mesaService.createMesa(dto), HttpStatus.CREATED);
    }

    @GetMapping("api/v1/tables/available?guests=x")
    public List<MesaResponseDTO> getDisponibles(@RequestBody MesaRequestDTO dto, int guests){
        return mesaService.findMesasDisponibles(dto, guests);
    }
}
