package com.example.GastroTech.service;

import com.example.GastroTech.model.Entity.Cliente;
import com.example.GastroTech.repository.ClienteRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;

    public ClienteService(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    public Cliente crearCliente(Cliente cliente){
        return clienteRepository.save(cliente);
    }

    public Optional<Cliente> encontrarPorEmail(String email){
        return clienteRepository.findByEmail(email);
    }

    public boolean existeElEmail(String email){
        return clienteRepository.existsByEmail(email);
    }
}