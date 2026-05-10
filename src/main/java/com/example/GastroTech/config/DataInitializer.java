package com.example.GastroTech.config;

import com.example.GastroTech.model.Entity.Mesa;
import com.example.GastroTech.model.Entity.Usuario;
import com.example.GastroTech.model.Enum.EstadoMesa;
import com.example.GastroTech.model.Enum.RolUsuario;
import com.example.GastroTech.model.Enum.UbicacionMesa;
import com.example.GastroTech.repository.MesaRepository;
import com.example.GastroTech.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final MesaRepository mesaRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        seedAdminUser();
        seedMesas();
    }

    private void seedAdminUser() {
        if (usuarioRepository.existsByEmail("admin@gastrotech.com")) return;

        Usuario admin = Usuario.builder()
                .nombre("Administrador")
                .email("admin@gastrotech.com")
                .password(passwordEncoder.encode("admin123"))
                .rol(RolUsuario.ADMIN)
                .fechaCreacion(LocalDateTime.now())
                .activo(true)
                .build();

        usuarioRepository.save(admin);
        log.info(">>> Admin creado: admin@gastrotech.com / admin123");
    }

    private void seedMesas() {
        if (mesaRepository.count() > 0) return;

        UbicacionMesa[] ubicaciones = {
                UbicacionMesa.INTERIOR, UbicacionMesa.INTERIOR, UbicacionMesa.INTERIOR,
                UbicacionMesa.TERRAZA, UbicacionMesa.TERRAZA,
                UbicacionMesa.VIP
        };
        int[] capacidades = {2, 4, 4, 6, 6, 8};

        for (int i = 0; i < ubicaciones.length; i++) {
            Mesa mesa = Mesa.builder()
                    .numeroMesa(i + 1)
                    .capacidad(capacidades[i])
                    .ubicacion(ubicaciones[i])
                    .estado(EstadoMesa.DISPONIBLE)
                    .build();
            mesaRepository.save(mesa);
        }
        log.info(">>> 6 mesas de ejemplo creadas");
    }
}
