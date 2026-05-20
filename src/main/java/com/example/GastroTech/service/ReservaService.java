package com.example.GastroTech.service;

import com.example.GastroTech.dto.request.ReservationRequestDTO;
import com.example.GastroTech.dto.response.ReservationResponseDTO;
import com.example.GastroTech.exception.BusinessException;
import com.example.GastroTech.exception.ResourceNotFoundException;
import com.example.GastroTech.exception.UserBannedException;
import com.example.GastroTech.model.Entity.Mesa;
import com.example.GastroTech.model.Entity.Reserva;
import com.example.GastroTech.model.Entity.Usuario;
import com.example.GastroTech.model.Enum.EstadoReserva;
import com.example.GastroTech.model.Enum.EstadoUsuario;
import com.example.GastroTech.model.Enum.RolUsuario;
import com.example.GastroTech.repository.MesaRepository;
import com.example.GastroTech.repository.ReservaRepository;
import com.example.GastroTech.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReservaService {

    private final ReservaRepository reservaRepository;
    private final MesaRepository mesaRepository;
    private final UsuarioRepository usuarioRepository;

    /**
     * Crea una reserva validando:
     * 1. La fecha debe ser futura.
     * 2. La mesa no debe tener otra reserva activa en un margen de ±2 horas.
     */
    @Transactional
    public ReservationResponseDTO saveReservation(ReservationRequestDTO dto, String username) {

        // ── NUEVO: bloquear si el usuario está baneado ───────────────────────
        Usuario usuario = usuarioRepository.findByEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + username));

        if (usuario.getStatus() == EstadoUsuario.BANNED) {
            throw new UserBannedException();
        }
        // ─────────────────────────────────────────────────────────────────────

        // Validacion de negocio: fecha futura
        if (!dto.reservationDate().isAfter(LocalDateTime.now())) {
            throw new BusinessException("La reserva debe ser en una fecha futura");
        }

        // Buscar la mesa
        Mesa mesa = mesaRepository.findById(dto.tableId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Mesa no encontrada con id: " + dto.tableId()));

        // Comprobar franja de 2 horas
        LocalDateTime inicio = dto.reservationDate().minusHours(2);
        LocalDateTime fin    = dto.reservationDate().plusHours(2);

        boolean conflicto = reservaRepository
                .existsByMesaIdAndFechaReservaBetweenAndEstadoNot(
                        dto.tableId(), inicio, fin, EstadoReserva.CANCELADA);

        if (conflicto) {
            throw new BusinessException(
                    "La mesa ya tiene una reserva activa en esa franja horaria (margen de 2 horas)");
        }

        // Construir y guardar la reserva
        Reserva reserva = Reserva.builder()
                .mesa(mesa)
                .usuario(usuario)
                .fechaReserva(dto.reservationDate())
                .numeroPersonas(dto.numberOfGuests())
                .estado(EstadoReserva.PENDIENTE)
                .fechaCreacion(LocalDateTime.now())
                .build();
        if (!usuarioIsVip(usuario,mesa)){
            throw new BusinessException(
                    "Esta mesa es solo para clientes Vip"
            );
        }
        return mapToResponseDTO(reservaRepository.save(reserva));
    }

    /**
     * Devuelve reservas segun el rol:
     * - ADMIN: todas las reservas.
     * - USER: solo las propias.
     *
     * NOTA: @Transactional es obligatorio aqui porque mapToResponseDTO accede a
     * relaciones LAZY (mesa, usuario). Sin transaccion activa, Hibernate cierra la
     * sesion tras el findAll/findByUsuarioId y lanza LazyInitializationException.
     */
    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> findReservations(String username) {
        Usuario usuario = usuarioRepository.findByEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        List<Reserva> reservas = usuario.getRol() == RolUsuario.ADMIN
                ? reservaRepository.findAll()
                : reservaRepository.findByUsuarioId(usuario.getId());

        return reservas.stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Soft delete: cambia el estado a CANCELADA, no elimina el registro.
     */
    @Transactional
    public void cancelReservation(Long id, String username) {
        Reserva reserva = reservaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reserva no encontrada con id: " + id));

        Usuario usuario = usuarioRepository.findByEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        boolean esAdmin = usuario.getRol() == RolUsuario.ADMIN;
        boolean esPropietario = reserva.getUsuario().getId().equals(usuario.getId());

        if (!esAdmin && !esPropietario) {
            throw new BusinessException("No tienes permiso para cancelar esta reserva");
        }

        // ── NUEVO: penalización por cancelación tardía ───────────────────────
        // Solo se penaliza al usuario propietario (no al admin que cancela por él)
        if (esPropietario) {
            aplicarPenalizacionSiEsTardia(reserva, usuario);
        }

        reserva.setEstado(EstadoReserva.CANCELADA);
        reservaRepository.save(reserva);
    }

    // ── NUEVO método privado de apoyo ────────────────────────────────────────────
    private void aplicarPenalizacionSiEsTardia(Reserva reserva, Usuario usuario) {
        LocalDateTime limite = reserva.getFechaReserva().minusHours(2);

        if (LocalDateTime.now().isAfter(limite)) {
            int nuevosPuntos = usuario.getPenalizationPoints() + 2;
            usuario.setPenalizationPoints(nuevosPuntos);

            if (nuevosPuntos > 6) {
                usuario.setStatus(EstadoUsuario.BANNED);
            }

            usuarioRepository.save(usuario);
        }
    }

    // ─── Crear Usuario Vip ─────────────────────────────────────────────────
    private boolean usuarioIsVip(Usuario usuario, Mesa mesa){
        List<Reserva> users = reservaRepository.findByUsuarioId(usuario.getId());
        if (users.size() <3 && mesa.isVip){
            return false;
        }
        return true;
    }

    // ─── Mapeo entidad → DTO ─────────────────────────────────────────────────

    private ReservationResponseDTO mapToResponseDTO(Reserva reserva) {
        return new ReservationResponseDTO(
                reserva.getId(),
                "Mesa " + reserva.getMesa().getNumeroMesa(),
                reserva.getUsuario().getNombre(),
                reserva.getFechaReserva(),
                reserva.getEstado().name()
        );
    }
}
