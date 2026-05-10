package com.example.GastroTech.service.impl;

import com.example.GastroTech.dto.request.AuthRequestDTO;
import com.example.GastroTech.dto.request.RegisterRequestDTO;
import com.example.GastroTech.dto.response.AuthResponseDTO;
import com.example.GastroTech.exception.BusinessException;
import com.example.GastroTech.exception.ResourceNotFoundException;
import com.example.GastroTech.model.Entity.Usuario;
import com.example.GastroTech.model.Enum.RolUsuario;
import com.example.GastroTech.repository.UsuarioRepository;
import com.example.GastroTech.security.JwtService;
import com.example.GastroTech.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Override
    public AuthResponseDTO login(AuthRequestDTO request) {
        // Lanza BadCredentialsException si las credenciales son incorrectas
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        Usuario usuario = usuarioRepository.findByEmail(request.username())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        String token = jwtService.generateToken(usuario);
        return new AuthResponseDTO(token, "Bearer", usuario.getEmail(), usuario.getRol().name());
    }

    @Override
    public AuthResponseDTO register(RegisterRequestDTO request) {
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new BusinessException("El email '" + request.email() + "' ya esta registrado");
        }

        Usuario usuario = Usuario.builder()
                .nombre(request.nombre())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .rol(RolUsuario.USER)
                .fechaCreacion(LocalDateTime.now())
                .activo(true)
                .build();

        usuarioRepository.save(usuario);

        String token = jwtService.generateToken(usuario);
        return new AuthResponseDTO(token, "Bearer", usuario.getEmail(), usuario.getRol().name());
    }
}
