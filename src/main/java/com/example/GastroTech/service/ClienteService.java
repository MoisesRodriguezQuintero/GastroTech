package com.example.GastroTech.service;

import com.example.GastroTech.exception.BusinessException;
import com.example.GastroTech.model.Entity.Cliente;
import com.example.GastroTech.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;

    public Cliente registrarCliente(Cliente cliente) {
        if (clienteRepository.existsByEmail(cliente.getEmail())) {
            throw new BusinessException("El email ya esta registrado como cliente");
        }
        cliente.setFechaRegistro(LocalDateTime.now());
        cliente.setActivo(true);
        return clienteRepository.save(cliente);
    }

    public Optional<Cliente> buscarPorEmail(String email) {
        return clienteRepository.findByEmail(email);
    }
}
