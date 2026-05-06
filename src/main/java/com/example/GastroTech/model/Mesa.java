package com.example.GastroTech.model;

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
    private String Ubicacion;

    @Enumerated(EnumType.STRING)
    private String Estado;
}
