package com.example.GastroTech.repository;

import com.example.GastroTech.model.Entity.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReservaRepository extends JpaRepository<Reserva,Long> {

    List<Reserva> findByClienteId(Long ClienteId);
}