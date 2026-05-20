package com.example.GastroTech.repository;

import com.example.GastroTech.dto.response.MesaResponseDTO;
import com.example.GastroTech.model.Entity.Mesa;
import com.example.GastroTech.model.Enum.EstadoMesa;
import com.example.GastroTech.model.Enum.UbicacionMesa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MesaRepository extends JpaRepository<Mesa, Long> {

    List<MesaResponseDTO> findByCapacidadGreaterThanEqual(int capacidad);

    MesaResponseDTO findByEstado(EstadoMesa estado);

    List<Mesa> findByUbicacion(UbicacionMesa ubicacion);
}
