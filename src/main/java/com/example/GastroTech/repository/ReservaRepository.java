package com.example.GastroTech.repository;

import com.example.GastroTech.model.Entity.Reserva;
import com.example.GastroTech.model.Enum.EstadoReserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    /** Todas las reservas de un usuario concreto. */
    List<Reserva> findByUsuarioId(Long usuarioId);

    /** Reservas en un estado determinado. */
    List<Reserva> findByEstado(EstadoReserva estado);

    /**
     * Comprueba si ya existe una reserva NO cancelada para una mesa
     * dentro de una franja de ±2 horas.
     */
    boolean existsByMesaIdAndFechaReservaBetweenAndEstadoNot(
            Long mesaId,
            LocalDateTime inicio,
            LocalDateTime fin,
            EstadoReserva estado
    );
}
