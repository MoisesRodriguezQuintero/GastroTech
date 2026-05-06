package com.example.GastroTech.model.Entity;

import jakarta.persistence.*;

import java.util.Date;

@Entity
@Table(name="Cliente")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long Id;

    private String Nombre;

    private String Apellidos;

    private String Email;

    private int telefono;

    private Date Fecha_Registro;

    private boolean activo;
}
