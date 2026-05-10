package com.example.GastroTech.service;

import com.example.GastroTech.dto.request.ReservationRequestDTO;
import com.example.GastroTech.exception.BusinessException;
import com.example.GastroTech.exception.ResourceNotFoundException;
import com.example.GastroTech.model.Entity.Mesa;
import com.example.GastroTech.model.Entity.Reserva;
import com.example.GastroTech.model.Entity.Usuario;
import com.example.GastroTech.model.Enum.EstadoMesa;
import com.example.GastroTech.model.Enum.EstadoReserva;
import com.example.GastroTech.model.Enum.RolUsuario;
import com.example.GastroTech.model.Enum.UbicacionMesa;
import com.example.GastroTech.repository.MesaRepository;
import com.example.GastroTech.repository.ReservaRepository;
import com.example.GastroTech.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservaService - Tests unitarios")
class ReservaServiceTest {

    @Mock private ReservaRepository reservaRepository;
    @Mock private MesaRepository mesaRepository;
    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks
    private ReservaService reservaService;

    private Mesa mesaEjemplo;
    private Usuario usuarioEjemplo;

    @BeforeEach
    void setUp() {
        mesaEjemplo = Mesa.builder()
                .id(1L)
                .numeroMesa(1)
                .capacidad(4)
                .ubicacion(UbicacionMesa.INTERIOR)
                .estado(EstadoMesa.DISPONIBLE)
                .build();

        usuarioEjemplo = Usuario.builder()
                .id(1L)
                .nombre("Usuario Test")
                .email("test@gastrotech.com")
                .password("hashed")
                .rol(RolUsuario.USER)
                .activo(true)
                .build();
    }

    // ─── Test principal del enunciado ─────────────────────────────────────────

    @Test
    @DisplayName("DEBE lanzar BusinessException si la fecha de reserva es en el pasado")
    void saveReservation_lanzaExcepcion_cuandoFechaEsEnElPasado() {
        // Given - fecha en el pasado (viola la regla de negocio del servicio)
        ReservationRequestDTO dto = new ReservationRequestDTO(
                1L,
                LocalDateTime.now().minusDays(1),
                2
        );

        // When & Then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> reservaService.saveReservation(dto, "test@gastrotech.com"));

        assertTrue(ex.getMessage().contains("fecha futura"),
                "El mensaje debe indicar que se requiere una fecha futura");

        // El repositorio no debe consultarse si la fecha es invalida
        verifyNoInteractions(mesaRepository, reservaRepository, usuarioRepository);
    }

    // ─── Tests adicionales ────────────────────────────────────────────────────

    @Test
    @DisplayName("DEBE lanzar ResourceNotFoundException si la mesa no existe")
    void saveReservation_lanzaExcepcion_cuandoMesaNoExiste() {
        ReservationRequestDTO dto = new ReservationRequestDTO(
                99L, LocalDateTime.now().plusDays(1), 2);

        when(mesaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> reservaService.saveReservation(dto, "test@gastrotech.com"));
    }

    @Test
    @DisplayName("DEBE lanzar BusinessException si la mesa ya esta reservada en esa franja")
    void saveReservation_lanzaExcepcion_cuandoMesaOcupadaEnFranja() {
        LocalDateTime fechaFutura = LocalDateTime.now().plusDays(1);
        ReservationRequestDTO dto = new ReservationRequestDTO(1L, fechaFutura, 2);

        when(mesaRepository.findById(1L)).thenReturn(Optional.of(mesaEjemplo));
        when(reservaRepository.existsByMesaIdAndFechaReservaBetweenAndEstadoNot(
                eq(1L), any(LocalDateTime.class), any(LocalDateTime.class),
                eq(EstadoReserva.CANCELADA))
        ).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reservaService.saveReservation(dto, "test@gastrotech.com"));

        assertTrue(ex.getMessage().contains("franja horaria"));
    }

    @Test
    @DisplayName("DEBE crear la reserva correctamente cuando todos los datos son validos")
    void saveReservation_creaReserva_cuandoDatosSonValidos() {
        LocalDateTime fechaFutura = LocalDateTime.now().plusDays(1);
        ReservationRequestDTO dto = new ReservationRequestDTO(1L, fechaFutura, 2);

        when(mesaRepository.findById(1L)).thenReturn(Optional.of(mesaEjemplo));
        when(reservaRepository.existsByMesaIdAndFechaReservaBetweenAndEstadoNot(
                anyLong(), any(), any(), any()))
                .thenReturn(false);
        when(usuarioRepository.findByEmail("test@gastrotech.com"))
                .thenReturn(Optional.of(usuarioEjemplo));

        Reserva reservaGuardada = Reserva.builder()
                .id(1L)
                .mesa(mesaEjemplo)
                .usuario(usuarioEjemplo)
                .fechaReserva(fechaFutura)
                .numeroPersonas(2)
                .estado(EstadoReserva.PENDIENTE)
                .fechaCreacion(LocalDateTime.now())
                .build();

        when(reservaRepository.save(any(Reserva.class))).thenReturn(reservaGuardada);

        var response = reservaService.saveReservation(dto, "test@gastrotech.com");

        assertNotNull(response);
        assertEquals(1L, response.id());
        assertEquals(EstadoReserva.PENDIENTE.name(), response.status());
        assertEquals("Mesa 1", response.tableName());
        assertEquals("Usuario Test", response.customerName());

        verify(reservaRepository, times(1)).save(any(Reserva.class));
    }

    @Test
    @DisplayName("DEBE hacer soft delete (CANCELADA) en lugar de borrar fisicamente")
    void cancelReservation_cambiaEstadoACancelada_noEliminaRegistro() {
        Reserva reserva = Reserva.builder()
                .id(1L)
                .mesa(mesaEjemplo)
                .usuario(usuarioEjemplo)
                .estado(EstadoReserva.PENDIENTE)
                .fechaReserva(LocalDateTime.now().plusDays(1))
                .numeroPersonas(2)
                .build();

        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reserva));
        when(usuarioRepository.findByEmail("test@gastrotech.com"))
                .thenReturn(Optional.of(usuarioEjemplo));
        when(reservaRepository.save(any(Reserva.class))).thenReturn(reserva);

        reservaService.cancelReservation(1L, "test@gastrotech.com");

        assertEquals(EstadoReserva.CANCELADA, reserva.getEstado());
        verify(reservaRepository, times(1)).save(reserva);
        verify(reservaRepository, never()).deleteById(anyLong());
    }
}
