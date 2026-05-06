package com.example.GastroTech.service;

import com.example.GastroTech.model.Entity.Mesa;
import com.example.GastroTech.model.Enum.EstadoMesa;
import com.example.GastroTech.model.Enum.UbicacionMesa;
import com.example.GastroTech.repository.MesaRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
public class MesaService {
    private final MesaRepository mesaRepository;

    public MesaService(MesaRepository mesaRepository) {
        this.mesaRepository = mesaRepository;
    }

    public List<Mesa> buscarMesasDisponibles(EstadoMesa estado){
        return mesaRepository.findByEstado(estado);
    }
    public List<Mesa> buscarPorUbicacion(UbicacionMesa ubicacion){
        return mesaRepository.findByUbicacion(ubicacion);
    }

    public List<Mesa> buscarPorCapacidad(int capacidad){
        return mesaRepository.findByCapacidadGreaterThanEqual(capacidad);
    }
}