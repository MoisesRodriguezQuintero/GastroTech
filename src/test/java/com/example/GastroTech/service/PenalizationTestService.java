package com.example.GastroTech.service;

import com.example.GastroTech.model.Entity.Reserva;
import com.example.GastroTech.model.Enum.EstadoReserva;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.util.AssertionErrors.assertEquals;

public class PenalizationTestService {

    @ExtendWith(MockitoExtension.class)
    @DisplayName("Sistema de penalizacion - Tests unitarios")
    class PenalizacionServiceTest {

        @Mock private ReservaRepository reservaRepository;
        @Mock private MesaRepository mesaRepository;
        @Mock private UsuarioRepository usuarioRepository;

        @InjectMocks private ReservaService reservaService;

        @Test
        @DisplayName("DEBE lanzar UserBannedException si el usuario esta BANNED")
        void saveReservation_lanzaExcepcion_siUsuarioEstaBaneado() {
            Usuario baneado = Usuario.builder()
                    .email("banned@test.com")
                    .status(EstadoUsuario.BANNED)
                    .penalizationPoints(8)
                    .activo(true)
                    .rol(RolUsuario.USER)
                    .build();

            when(usuarioRepository.findByEmail("banned@test.com"))
                    .thenReturn(Optional.of(baneado));

            ReservationRequestDTO dto = new ReservationRequestDTO(
                    1L, LocalDateTime.now().plusDays(1), 2);

            assertThrows(UserBannedException.class,
                    () -> reservaService.saveReservation(dto, "banned@test.com"));
        }

        @Test
        @DisplayName("DEBE sumar 2 puntos y banear si supera 6 al cancelar tarde")
        void cancelReservation_baneaUsuario_siSuperaLimiteDePuntos() {
            Usuario usuario = Usuario.builder()
                    .id(1L).email("user@test.com")
                    .penalizationPoints(6)
                    .status(EstadoUsuario.ACTIVE)
                    .rol(RolUsuario.USER).activo(true).build();

            Reserva reserva = Reserva.builder()
                    .id(1L).usuario(usuario)
                    // la reserva es dentro de 1h → cancelación tardía (< 2h)
                    .fechaReserva(LocalDateTime.now().plusHours(1))
                    .estado(EstadoReserva.PENDIENTE).build();

            when(reservaRepository.findById(1L)).thenReturn(Optional.of(reserva));
            when(usuarioRepository.findByEmail("user@test.com")).thenReturn(Optional.of(usuario));

            reservaService.cancelReservation(1L, "user@test.com");

            assertEquals(8, usuario.getPenalizationPoints());
            assertEquals(EstadoUsuario.BANNED, usuario.getStatus());
            verify(usuarioRepository).save(usuario);
        }

        @Test
        @DisplayName("NO debe penalizar si cancela con mas de 2 horas de antelacion")
        void cancelReservation_noPenaliza_siCancelaConTiempoSuficiente() {
            Usuario usuario = Usuario.builder()
                    .id(1L).email("user@test.com")
                    .penalizationPoints(0)
                    .status(EstadoUsuario.ACTIVE)
                    .rol(RolUsuario.USER).activo(true).build();

            Reserva reserva = Reserva.builder()
                    .id(1L).usuario(usuario)
                    // la reserva es dentro de 5h → cancelación a tiempo
                    .fechaReserva(LocalDateTime.now().plusHours(5))
                    .estado(EstadoReserva.PENDIENTE).build();

            when(reservaRepository.findById(1L)).thenReturn(Optional.of(reserva));
            when(usuarioRepository.findByEmail("user@test.com")).thenReturn(Optional.of(usuario));

            reservaService.cancelReservation(1L, "user@test.com");

            assertEquals(0, usuario.getPenalizationPoints());
            assertEquals(EstadoUsuario.ACTIVE, usuario.getStatus());
            // el usuario no se guarda si no hay penalización
            verify(usuarioRepository, never()).save(usuario);
        }
    }
}
