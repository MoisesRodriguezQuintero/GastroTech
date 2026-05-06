package com.example.GastroTech.service;

import com.example.GastroTech.model.Entity.Usuario;
import com.example.GastroTech.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public Optional<Usuario> econtrarPorEmail(String email){
        return usuarioRepository.findByEmail(email);
    }
}