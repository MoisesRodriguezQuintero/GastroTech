package com.example.GastroTech.repository;

import com.example.GastroTech.model.Entity.Cliente;
import com.example.GastroTech.model.Entity.Mesa;
import com.example.GastroTech.model.Entity.Reserva;
import com.example.GastroTech.model.Enum.EstadoReserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReservaRepository extends JpaRepository<Reserva,Long> {

    List<Reserva> findByFecha(LocalDate fecha);

    List<Reserva> findByMesaAndFecha(
            Mesa mesa,
            LocalDate fecha
    );

    List<Reserva> findByClienteId(Long cliente);

    List<Reserva> findByEstado(EstadoReserva estado);
}