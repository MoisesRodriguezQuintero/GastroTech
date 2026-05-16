package com.example.GastroTech.model.Entity;

import com.example.GastroTech.model.Enum.EstadoMesa;
import com.example.GastroTech.model.Enum.UbicacionMesa;
import jakarta.persistence.*;

@Entity
@Table(name="Mesa")
public class Mesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long Id;

    private int Numero_Mesa;

    private int Capacidad;

    @Enumerated(EnumType.STRING)
    private UbicacionMesa Ubicacion;

    @Enumerated(EnumType.STRING)
    private EstadoMesa Estado;
}
