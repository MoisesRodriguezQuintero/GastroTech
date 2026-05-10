package com.example.GastroTech.service;

import com.example.GastroTech.dto.request.AuthRequestDTO;
import com.example.GastroTech.dto.request.RegisterRequestDTO;
import com.example.GastroTech.dto.response.AuthResponseDTO;

public interface AuthService {
    AuthResponseDTO login(AuthRequestDTO request);
    AuthResponseDTO register(RegisterRequestDTO request);
}
