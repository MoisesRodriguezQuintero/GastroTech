package com.example.GastroTech.service;

import com.example.GastroTech.dto.response.UsuarioResponseDTO;
import com.example.GastroTech.exception.ResourceNotFoundException;
import com.example.GastroTech.model.Entity.Reserva;
import com.example.GastroTech.model.Entity.Usuario;
import com.example.GastroTech.model.Enum.EstadoUsuario;
import com.example.GastroTech.repository.ReservaRepository;
import com.example.GastroTech.repository.UsuarioRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public Optional<Usuario> buscarPorEmail(String email) {
        return usuarioRepository.findByEmail(email);
    }

    public Usuario buscarPorEmailOExcepcion(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + email));
    }

    @Transactional
    public UsuarioResponseDTO resetPenalization(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario no encontrado con id: " + id));

        usuario.setPenalizationPoints(0);
        usuario.setStatus(EstadoUsuario.ACTIVE);
        usuarioRepository.save(usuario);

        return mapToResponseDTO(usuario);
    }

    private UsuarioResponseDTO mapToResponseDTO(Usuario usuario) {
        return new UsuarioResponseDTO(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.getRol().name(),
                usuario.getPenalizationPoints(),
                usuario.getStatus().name()
        );
    }
}
