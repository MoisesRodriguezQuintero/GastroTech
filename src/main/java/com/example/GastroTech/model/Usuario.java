package com.example.GastroTech.model;

import jakarta.persistence.*;

import java.util.Date;

@Entity
@Table(name="Usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long Id;

    private String Nombre;

    private String Email;

    private String Pass;

    @Enumerated(EnumType.STRING)
    private RolUsuario Rol;

    private Date Fecha_Creacion;

    private boolean activo;
}