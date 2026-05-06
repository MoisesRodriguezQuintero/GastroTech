package com.example.GastroTech.model.Entity;

import com.example.GastroTech.model.Enum.EstadoReserva;
import jakarta.persistence.*;

import java.time.LocalTime;
import java.util.Date;

@Entity
@Table(name="Reserva")
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long Id;

    private Date Fecha;

    private LocalTime Hora_inicio;

    private int num_personas;

    @Enumerated(EnumType.STRING)
    private EstadoReserva Estado;

    private Date Fecha_Creacion;

    private String Observaciones;

    @ManyToOne
    @JoinColumn(name = "Cliente_id", nullable = false)
    private Cliente paciente;

    @ManyToOne
    @JoinColumn(name = "Usuario_id", nullable = false)
    private Usuario medico;

}