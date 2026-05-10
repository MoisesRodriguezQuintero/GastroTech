package com.example.GastroTech.controller;

import com.example.GastroTech.dto.request.ReservationRequestDTO;
import com.example.GastroTech.dto.response.ReservationResponseDTO;
import com.example.GastroTech.service.ReservaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
@Tag(name = "Reservas", description = "Gestion de reservas")
@SecurityRequirement(name = "BearerAuth")
public class ReservaController {

    private final ReservaService reservaService;

    @GetMapping
    @Operation(summary = "Mis reservas (USER) o todas (ADMIN)")
    public ResponseEntity<List<ReservationResponseDTO>> getReservations() {
        String username = getCurrentUsername();
        return ResponseEntity.ok(reservaService.findReservations(username));
    }

    @PostMapping
    @Operation(summary = "Crear una reserva")
    public ResponseEntity<ReservationResponseDTO> createReservation(
            @Valid @RequestBody ReservationRequestDTO dto) {
        String username = getCurrentUsername();
        return new ResponseEntity<>(reservaService.saveReservation(dto, username), HttpStatus.CREATED);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancelar una reserva (soft delete)")
    public ResponseEntity<Void> cancelReservation(@PathVariable Long id) {
        String username = getCurrentUsername();
        reservaService.cancelReservation(id, username);
        return ResponseEntity.noContent().build();
    }

    private String getCurrentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
