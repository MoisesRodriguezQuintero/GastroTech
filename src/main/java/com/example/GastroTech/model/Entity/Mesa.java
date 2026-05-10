package com.example.GastroTech.model.Entity;

import com.example.GastroTech.model.Enum.EstadoMesa;
import com.example.GastroTech.model.Enum.UbicacionMesa;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "mesa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Mesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private int numeroMesa;

    @Column(nullable = false)
    private int capacidad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UbicacionMesa ubicacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoMesa estado;
}
