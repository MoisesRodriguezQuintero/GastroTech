# GastroTech API — Catálogo de Features Opcionales

Documento de referencia con las 12 modificaciones opcionales ordenadas de menor a mayor dificultad.
Cada feature sigue la arquitectura en capas `Controller → Service → Repository → Entity` y respeta las leyes de oro (DTOs, Exception Handling, sin entidades fuera del Service).

---

## Índice

| # | Feature | Dificultad |
|---|---------|-----------|
| 1 | [Límite de reservas activas por usuario](#1-límite-de-reservas-activas-por-usuario) | ⭐⭐ |
| 2 | [Sugerencia automática de mesa](#2-sugerencia-automática-de-mesa) | ⭐⭐ |
| 3 | [Notas internas del ADMIN sobre una reserva](#3-notas-internas-del-admin-sobre-una-reserva) | ⭐⭐⭐ |
| 4 | [Aforo máximo simultáneo](#4-aforo-máximo-simultáneo) | ⭐⭐⭐ |
| 5 | [Caducidad automática de puntos de penalización](#5-caducidad-automática-de-puntos-de-penalización) | ⭐⭐⭐ |
| 6 | [Bloqueo temporal de mesa por mantenimiento](#6-bloqueo-temporal-de-mesa-por-mantenimiento) | ⭐⭐⭐ |
| 7 | [Valoración post-reserva](#7-valoración-post-reserva) | ⭐⭐⭐ |
| 8 | [Transferencia de reserva entre usuarios](#8-transferencia-de-reserva-entre-usuarios) | ⭐⭐⭐⭐ |
| 9 | [Historial de cambios de estado de una reserva](#9-historial-de-cambios-de-estado-de-una-reserva) | ⭐⭐⭐⭐ |
| 10 | [Reserva recurrente semanal](#10-reserva-recurrente-semanal) | ⭐⭐⭐⭐ |
| 11 | [Sistema de fidelización](#11-sistema-de-fidelización) | ⭐⭐⭐⭐ |
| 12 | [Lista de espera](#12-lista-de-espera) | ⭐⭐⭐⭐⭐ |

---

## Flujo Git obligatorio para cualquier feature

```bash
git checkout develop
git checkout -b feature/<nombre-de-la-feature>

# desarrollo con commits progresivos...

git checkout develop
git merge --no-ff feature/<nombre-de-la-feature> -m "feat: merge <nombre> into develop"
```

---

---

## 1. Límite de reservas activas por usuario

**Dificultad:** ⭐⭐  
**Rama Git:** `feature/reservation-limit`  
**Resumen:** Un usuario no puede tener más de N reservas activas simultáneas. Evita que un cliente acapare mesas.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Modificar | `model/Entity/Usuario.java` |
| Modificar | `repository/ReservaRepository.java` |
| Modificar | `service/ReservaService.java` |
| Crear | `exception/ReservationLimitExceededException.java` |
| Modificar | `exception/GlobalExceptionHandler.java` |
| Modificar | `application.properties` |

---

### `application.properties` — añadir al final

```properties
gastrotech.reservas.max-activas-por-usuario=3
```

---

### `model/Entity/Usuario.java` — añadir campo después de `activo`

```java
// Importar al inicio del fichero:
import org.springframework.beans.factory.annotation.Value;

@Column(nullable = false)
@Builder.Default
private int maxReservasPermitidas = 3;
```

---

### `repository/ReservaRepository.java` — añadir método

```java
// Importar al inicio del fichero:
import java.util.List;

// Contar reservas PENDIENTE o CONFIRMADA de un usuario
long countByUsuarioIdAndEstadoIn(Long usuarioId, List<EstadoReserva> estados);
```

---

### `exception/ReservationLimitExceededException.java` — archivo nuevo completo

```java
package com.example.GastroTech.exception;

public class ReservationLimitExceededException extends RuntimeException {
    public ReservationLimitExceededException(int limite) {
        super("Has alcanzado el limite de " + limite + " reservas activas simultaneas");
    }
}
```

---

### `exception/GlobalExceptionHandler.java` — añadir handler junto al resto

```java
@ExceptionHandler(ReservationLimitExceededException.class)
public ResponseEntity<ErrorResponse> handleReservationLimit(ReservationLimitExceededException ex) {
    return new ResponseEntity<>(
            new ErrorResponse("RESERVATION_LIMIT", ex.getMessage(), LocalDateTime.now()),
            HttpStatus.CONFLICT);   // 409
}
```

---

### `service/ReservaService.java` — añadir dentro de `saveReservation()`, justo después de la comprobación de ban

```java
// Importar al inicio del fichero:
import com.example.GastroTech.exception.ReservationLimitExceededException;
import java.util.List;

// Dentro de saveReservation(), después de comprobar que el usuario no está BANNED:
long reservasActivas = reservaRepository.countByUsuarioIdAndEstadoIn(
        usuario.getId(),
        List.of(EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA)
);

if (reservasActivas >= usuario.getMaxReservasPermitidas()) {
    throw new ReservationLimitExceededException(usuario.getMaxReservasPermitidas());
}
```

---

### Commits sugeridos

```bash
git commit -m "feat(model): add maxReservasPermitidas field to Usuario"
git commit -m "feat(repository): add countByUsuarioIdAndEstadoIn query"
git commit -m "feat(exception): add ReservationLimitExceededException mapped to 409"
git commit -m "feat(service): block reservation if user exceeds active reservation limit"
```

---

---

## 2. Sugerencia automática de mesa

**Dificultad:** ⭐⭐  
**Rama Git:** `feature/table-suggestion`  
**Resumen:** El cliente indica cuántos comensales son y la hora deseada; la API devuelve la mesa libre más ajustada (menor capacidad suficiente). Endpoint público, sin JWT.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Crear | `dto/request/SugerenciaMesaRequestDTO.java` |
| Modificar | `service/MesaService.java` |
| Modificar | `repository/ReservaRepository.java` |
| Modificar | `controller/MesaController.java` |

---

### `dto/request/SugerenciaMesaRequestDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.request;

import com.example.GastroTech.model.Enum.UbicacionMesa;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record SugerenciaMesaRequestDTO(
        @NotNull(message = "La fecha y hora es obligatoria")
        @Future(message = "La fecha debe ser futura")
        LocalDateTime fechaDeseada,

        @Min(value = 1, message = "Minimo 1 comensal")
        @Max(value = 12, message = "Maximo 12 comensales")
        int numberOfGuests,

        // Opcional: null significa cualquier ubicacion
        UbicacionMesa ubicacionPreferida
) {}
```

---

### `service/MesaService.java` — añadir método e inyectar ReservaRepository

```java
// Nuevos imports:
import com.example.GastroTech.dto.request.SugerenciaMesaRequestDTO;
import com.example.GastroTech.exception.BusinessException;
import com.example.GastroTech.model.Enum.EstadoReserva;
import com.example.GastroTech.repository.ReservaRepository;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

// Añadir dependencia en la clase (Lombok @RequiredArgsConstructor la inyecta):
private final ReservaRepository reservaRepository;

// Nuevo método al final de la clase:
public MesaResponseDTO sugerirMesa(SugerenciaMesaRequestDTO dto) {
    List<Mesa> candidatas = dto.ubicacionPreferida() != null
            ? mesaRepository.findByUbicacion(dto.ubicacionPreferida())
            : mesaRepository.findAll();

    return candidatas.stream()
            .filter(m -> m.getCapacidad() >= dto.numberOfGuests())
            .filter(m -> !hayConflictoEnFranja(m.getId(), dto.fechaDeseada()))
            .min(Comparator.comparingInt(Mesa::getCapacidad))
            .map(this::mapToResponseDTO)
            .orElseThrow(() -> new BusinessException(
                    "No hay mesas disponibles para esa franja horaria con esa capacidad"));
}

private boolean hayConflictoEnFranja(Long mesaId, java.time.LocalDateTime fecha) {
    return reservaRepository.existsByMesaIdAndFechaReservaBetweenAndEstadoNot(
            mesaId,
            fecha.minusHours(2),
            fecha.plusHours(2),
            EstadoReserva.CANCELADA
    );
}
```

---

### `controller/MesaController.java` — añadir endpoint (sin @PreAuthorize, es público)

```java
// Nuevos imports:
import com.example.GastroTech.dto.request.SugerenciaMesaRequestDTO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;

@GetMapping("/suggest")
@Operation(summary = "Sugerir la mesa mas adecuada para el numero de comensales y hora deseada")
public ResponseEntity<MesaResponseDTO> sugerirMesa(
        @Valid @RequestBody SugerenciaMesaRequestDTO dto) {
    return ResponseEntity.ok(mesaService.sugerirMesa(dto));
}
```

---

### `security/SecurityConfig.java` — permitir el endpoint de sugerencia sin JWT

```java
// Dentro de authorizeHttpRequests(), añadir antes de anyRequest().authenticated():
.requestMatchers(HttpMethod.GET, "/api/v1/tables/suggest").permitAll()
```

---

### Commits sugeridos

```bash
git commit -m "feat(dto): add SugerenciaMesaRequestDTO with optional ubicacion filter"
git commit -m "feat(service): add sugerirMesa method with best-fit table algorithm"
git commit -m "feat(controller): add public GET /api/v1/tables/suggest endpoint"
git commit -m "fix(security): permit GET /api/v1/tables/suggest without JWT"
```

---

---

## 3. Notas internas del ADMIN sobre una reserva

**Dificultad:** ⭐⭐⭐  
**Rama Git:** `feature/internal-notes`  
**Resumen:** El ADMIN puede añadir notas internas a una reserva (alergias, peticiones VIP). El cliente nunca las ve.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Crear | `model/Entity/NotaInterna.java` |
| Crear | `repository/NotaInternaRepository.java` |
| Crear | `dto/request/NotaInternaRequestDTO.java` |
| Crear | `dto/response/NotaInternaResponseDTO.java` |
| Crear | `service/NotaInternaService.java` |
| Crear | `controller/NotaInternaController.java` |

---

### `model/Entity/NotaInterna.java` — archivo nuevo completo

```java
package com.example.GastroTech.model.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "nota_interna")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotaInterna {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String contenido;

    @Column(nullable = false)
    private String autor;

    @Column(nullable = false)
    private LocalDateTime fechaCreacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserva_id", nullable = false)
    private Reserva reserva;
}
```

---

### `repository/NotaInternaRepository.java` — archivo nuevo completo

```java
package com.example.GastroTech.repository;

import com.example.GastroTech.model.Entity.NotaInterna;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NotaInternaRepository extends JpaRepository<NotaInterna, Long> {
    List<NotaInterna> findByReservaIdOrderByFechaCreacionDesc(Long reservaId);
}
```

---

### `dto/request/NotaInternaRequestDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NotaInternaRequestDTO(
        @NotBlank(message = "El contenido no puede estar vacio")
        @Size(max = 500, message = "Maximo 500 caracteres")
        String contenido
) {}
```

---

### `dto/response/NotaInternaResponseDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.response;

import java.time.LocalDateTime;

public record NotaInternaResponseDTO(
        Long id,
        String contenido,
        String autor,
        LocalDateTime fechaCreacion
) {}
```

---

### `service/NotaInternaService.java` — archivo nuevo completo

```java
package com.example.GastroTech.service;

import com.example.GastroTech.dto.request.NotaInternaRequestDTO;
import com.example.GastroTech.dto.response.NotaInternaResponseDTO;
import com.example.GastroTech.exception.ResourceNotFoundException;
import com.example.GastroTech.model.Entity.NotaInterna;
import com.example.GastroTech.model.Entity.Reserva;
import com.example.GastroTech.repository.NotaInternaRepository;
import com.example.GastroTech.repository.ReservaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotaInternaService {

    private final NotaInternaRepository notaRepository;
    private final ReservaRepository reservaRepository;

    @Transactional
    public NotaInternaResponseDTO addNota(Long reservaId, NotaInternaRequestDTO dto, String autor) {
        Reserva reserva = reservaRepository.findById(reservaId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reserva no encontrada con id: " + reservaId));

        NotaInterna nota = NotaInterna.builder()
                .contenido(dto.contenido())
                .autor(autor)
                .fechaCreacion(LocalDateTime.now())
                .reserva(reserva)
                .build();

        return mapToResponseDTO(notaRepository.save(nota));
    }

    @Transactional(readOnly = true)
    public List<NotaInternaResponseDTO> getNotas(Long reservaId) {
        if (!reservaRepository.existsById(reservaId)) {
            throw new ResourceNotFoundException("Reserva no encontrada con id: " + reservaId);
        }
        return notaRepository.findByReservaIdOrderByFechaCreacionDesc(reservaId)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    private NotaInternaResponseDTO mapToResponseDTO(NotaInterna nota) {
        return new NotaInternaResponseDTO(
                nota.getId(),
                nota.getContenido(),
                nota.getAutor(),
                nota.getFechaCreacion()
        );
    }
}
```

---

### `controller/NotaInternaController.java` — archivo nuevo completo

```java
package com.example.GastroTech.controller;

import com.example.GastroTech.dto.request.NotaInternaRequestDTO;
import com.example.GastroTech.dto.response.NotaInternaResponseDTO;
import com.example.GastroTech.service.NotaInternaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reservations/{reservaId}/notes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Notas internas", description = "Notas internas sobre reservas (solo ADMIN)")
@SecurityRequirement(name = "BearerAuth")
public class NotaInternaController {

    private final NotaInternaService notaService;

    @PostMapping
    @Operation(summary = "Añadir nota interna a una reserva")
    public ResponseEntity<NotaInternaResponseDTO> addNota(
            @PathVariable Long reservaId,
            @Valid @RequestBody NotaInternaRequestDTO dto) {
        String autor = SecurityContextHolder.getContext().getAuthentication().getName();
        return new ResponseEntity<>(notaService.addNota(reservaId, dto, autor), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Listar notas internas de una reserva")
    public ResponseEntity<List<NotaInternaResponseDTO>> getNotas(@PathVariable Long reservaId) {
        return ResponseEntity.ok(notaService.getNotas(reservaId));
    }
}
```

---

### Commits sugeridos

```bash
git commit -m "feat(model): add NotaInterna entity linked to Reserva"
git commit -m "feat(repository): add NotaInternaRepository with ordered query"
git commit -m "feat(dto): add NotaInternaRequestDTO and NotaInternaResponseDTO"
git commit -m "feat(service): add NotaInternaService with add and list operations"
git commit -m "feat(controller): add NotaInternaController under /reservations/{id}/notes"
```

---

---

## 4. Aforo máximo simultáneo

**Dificultad:** ⭐⭐⭐  
**Rama Git:** `feature/max-capacity`  
**Resumen:** El local tiene un aforo total. Si las reservas activas en una franja ya lo alcanzan, no se puede crear más reservas aunque haya mesas libres.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Modificar | `application.properties` |
| Modificar | `repository/ReservaRepository.java` |
| Crear | `exception/AforoExcedidoException.java` |
| Modificar | `exception/GlobalExceptionHandler.java` |
| Modificar | `service/ReservaService.java` |
| Modificar | `controller/MesaController.java` |

---

### `application.properties` — añadir al final

```properties
gastrotech.aforo.maximo=30
```

---

### `repository/ReservaRepository.java` — añadir query JPQL

```java
// Importar al inicio del fichero:
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Query("""
    SELECT COALESCE(SUM(r.numeroPersonas), 0) FROM Reserva r
    WHERE r.estado != 'CANCELADA'
    AND r.fechaReserva BETWEEN :inicio AND :fin
    """)
int sumPersonasEnFranja(@Param("inicio") LocalDateTime inicio,
                         @Param("fin") LocalDateTime fin);
```

---

### `exception/AforoExcedidoException.java` — archivo nuevo completo

```java
package com.example.GastroTech.exception;

public class AforoExcedidoException extends RuntimeException {
    public AforoExcedidoException(int disponibles) {
        super("Aforo maximo alcanzado. Solo quedan " + disponibles
                + " plazas disponibles en esa franja horaria");
    }
}
```

---

### `exception/GlobalExceptionHandler.java` — añadir handler

```java
@ExceptionHandler(AforoExcedidoException.class)
public ResponseEntity<ErrorResponse> handleAforo(AforoExcedidoException ex) {
    return new ResponseEntity<>(
            new ErrorResponse("AFORO_EXCEDIDO", ex.getMessage(), LocalDateTime.now()),
            HttpStatus.CONFLICT);   // 409
}
```

---

### `service/ReservaService.java` — añadir comprobación en `saveReservation()` e inyectar propiedad

```java
// Nuevos imports:
import com.example.GastroTech.exception.AforoExcedidoException;
import org.springframework.beans.factory.annotation.Value;

// Añadir propiedad inyectada en la clase (encima de los campos del constructor):
@Value("${gastrotech.aforo.maximo:30}")
private int aforoMaximo;

// Dentro de saveReservation(), ANTES de la comprobación de conflicto de mesa individual:
LocalDateTime inicioFranja = dto.reservationDate().minusHours(2);
LocalDateTime finFranja    = dto.reservationDate().plusHours(2);

int personasEnFranja = reservaRepository.sumPersonasEnFranja(inicioFranja, finFranja);
int disponibles = aforoMaximo - personasEnFranja;

if (disponibles < dto.numberOfGuests()) {
    throw new AforoExcedidoException(disponibles);
}
```

---

### `controller/MesaController.java` — añadir endpoint de consulta de aforo disponible

```java
// Nuevos imports:
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;

@GetMapping("/availability")
@Operation(summary = "Consultar plazas disponibles en una franja horaria")
public ResponseEntity<Map<String, Object>> checkAvailability(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime dateTime) {
    // Delegar al service para respetar la arquitectura en capas
    return ResponseEntity.ok(mesaService.consultarDisponibilidad(dateTime));
}
```

```java
// En MesaService — añadir método:
// Nuevos imports en MesaService:
import org.springframework.beans.factory.annotation.Value;
import java.util.Map;

@Value("${gastrotech.aforo.maximo:30}")
private int aforoMaximo;

public Map<String, Object> consultarDisponibilidad(LocalDateTime dateTime) {
    int ocupadas = reservaRepository.sumPersonasEnFranja(
            dateTime.minusHours(2), dateTime.plusHours(2));
    int disponibles = Math.max(0, aforoMaximo - ocupadas);
    return Map.of(
            "aforoMaximo", aforoMaximo,
            "plazasOcupadas", ocupadas,
            "plazasDisponibles", disponibles,
            "franja", dateTime.toString()
    );
}
```

---

### Commits sugeridos

```bash
git commit -m "feat(config): add gastrotech.aforo.maximo property"
git commit -m "feat(repository): add sumPersonasEnFranja JPQL query"
git commit -m "feat(exception): add AforoExcedidoException mapped to 409"
git commit -m "feat(service): validate total capacity before creating reservation"
git commit -m "feat(controller): add GET /api/v1/tables/availability endpoint"
```

---

---

## 5. Caducidad automática de puntos de penalización

**Dificultad:** ⭐⭐⭐  
**Rama Git:** `feature/penalty-expiry`  
**Resumen:** Un scheduler nocturno reduce en 1 punto la penalización de los usuarios que llevan 30 días sin cancelar tarde. Si bajan de 6 puntos, se reactivan automáticamente.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Modificar | `model/Entity/Usuario.java` |
| Modificar | `GastroTechApplication.java` |
| Modificar | `repository/UsuarioRepository.java` |
| Crear | `config/PenalizacionScheduler.java` |
| Modificar | `service/ReservaService.java` |

---

### `GastroTechApplication.java` — añadir @EnableScheduling

```java
// Importar:
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling   // <- añadir esta anotación
public class GastroTechApplication {
    public static void main(String[] args) {
        SpringApplication.run(GastroTechApplication.class, args);
    }
}
```

---

### `model/Entity/Usuario.java` — añadir campo después de `status`

```java
// Fecha de la última cancelación tardía (null si nunca ha tenido)
private LocalDateTime ultimaPenalizacion;
```

---

### `repository/UsuarioRepository.java` — añadir query

```java
// Importar al inicio del fichero:
import java.time.LocalDateTime;
import java.util.List;

// Usuarios con puntos > 0 y cuya ultima penalizacion fue hace más de N días
List<Usuario> findByPenalizationPointsGreaterThanAndUltimaPenalizacionBefore(
        int puntos,
        LocalDateTime fecha
);
```

---

### `service/ReservaService.java` — actualizar `ultimaPenalizacion` cuando se penaliza

```java
// Dentro del método privado aplicarPenalizacionSiEsTardia(), añadir:
// (debajo de usuario.setPenalizationPoints(nuevosPuntos))

usuario.setUltimaPenalizacion(LocalDateTime.now());   // <- línea nueva
```

---

### `config/PenalizacionScheduler.java` — archivo nuevo completo

```java
package com.example.GastroTech.config;

import com.example.GastroTech.model.Entity.Usuario;
import com.example.GastroTech.model.Enum.EstadoUsuario;
import com.example.GastroTech.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PenalizacionScheduler {

    private final UsuarioRepository usuarioRepository;

    /**
     * Cada noche a las 03:00 reduce en 1 punto a los usuarios que llevan
     * mas de 30 dias sin cancelacion tardia. Si bajan de 6, se reactivan.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void reducirPuntosCaducados() {
        LocalDateTime hace30dias = LocalDateTime.now().minusDays(30);

        List<Usuario> candidatos = usuarioRepository
                .findByPenalizationPointsGreaterThanAndUltimaPenalizacionBefore(0, hace30dias);

        if (candidatos.isEmpty()) {
            log.info("[Scheduler] No hay puntos de penalizacion caducados hoy");
            return;
        }

        for (Usuario usuario : candidatos) {
            int puntosNuevos = usuario.getPenalizationPoints() - 1;
            usuario.setPenalizationPoints(puntosNuevos);

            if (puntosNuevos <= 6 && usuario.getStatus() == EstadoUsuario.BANNED) {
                usuario.setStatus(EstadoUsuario.ACTIVE);
                log.info("[Scheduler] Usuario {} reactivado (puntos: {})",
                        usuario.getEmail(), puntosNuevos);
            }

            usuarioRepository.save(usuario);
        }

        log.info("[Scheduler] Puntos reducidos en {} usuarios", candidatos.size());
    }
}
```

---

### Commits sugeridos

```bash
git commit -m "feat(app): enable @EnableScheduling in GastroTechApplication"
git commit -m "feat(model): add ultimaPenalizacion field to Usuario"
git commit -m "feat(repository): add query to find users with expired penalty points"
git commit -m "feat(service): track ultimaPenalizacion timestamp on late cancellation"
git commit -m "feat(scheduler): add nightly job to reduce expired penalty points"
```

---

---

## 6. Bloqueo temporal de mesa por mantenimiento

**Dificultad:** ⭐⭐⭐  
**Rama Git:** `feature/table-block`  
**Resumen:** El ADMIN puede bloquear una mesa en una franja horaria concreta (limpieza, avería). Esa franja queda bloqueada para reservas de clientes.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Crear | `model/Entity/BloqueoMesa.java` |
| Crear | `repository/BloqueoMesaRepository.java` |
| Crear | `dto/request/BloqueoMesaRequestDTO.java` |
| Crear | `dto/response/BloqueoMesaResponseDTO.java` |
| Crear | `service/BloqueoMesaService.java` |
| Crear | `controller/BloqueoMesaController.java` |
| Modificar | `service/ReservaService.java` |

---

### `model/Entity/BloqueoMesa.java` — archivo nuevo completo

```java
package com.example.GastroTech.model.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bloqueo_mesa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BloqueoMesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime inicio;

    @Column(nullable = false)
    private LocalDateTime fin;

    @Column(nullable = false, length = 200)
    private String motivo;

    @Column(nullable = false)
    private String creadoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mesa_id", nullable = false)
    private Mesa mesa;
}
```

---

### `repository/BloqueoMesaRepository.java` — archivo nuevo completo

```java
package com.example.GastroTech.repository;

import com.example.GastroTech.model.Entity.BloqueoMesa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BloqueoMesaRepository extends JpaRepository<BloqueoMesa, Long> {

    // Hay bloqueo si inicio <= fechaReserva <= fin
    boolean existsByMesaIdAndInicioLessThanEqualAndFinGreaterThanEqual(
            Long mesaId,
            java.time.LocalDateTime fechaReserva,
            java.time.LocalDateTime fechaReserva2
    );
}
```

---

### `dto/request/BloqueoMesaRequestDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record BloqueoMesaRequestDTO(
        @NotNull(message = "La hora de inicio es obligatoria")
        LocalDateTime inicio,

        @NotNull(message = "La hora de fin es obligatoria")
        LocalDateTime fin,

        @NotBlank(message = "El motivo es obligatorio")
        @Size(max = 200, message = "Maximo 200 caracteres")
        String motivo
) {}
```

---

### `dto/response/BloqueoMesaResponseDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.response;

import java.time.LocalDateTime;

public record BloqueoMesaResponseDTO(
        Long id,
        Long mesaId,
        int numeroMesa,
        LocalDateTime inicio,
        LocalDateTime fin,
        String motivo,
        String creadoPor
) {}
```

---

### `service/BloqueoMesaService.java` — archivo nuevo completo

```java
package com.example.GastroTech.service;

import com.example.GastroTech.dto.request.BloqueoMesaRequestDTO;
import com.example.GastroTech.dto.response.BloqueoMesaResponseDTO;
import com.example.GastroTech.exception.BusinessException;
import com.example.GastroTech.exception.ResourceNotFoundException;
import com.example.GastroTech.model.Entity.BloqueoMesa;
import com.example.GastroTech.model.Entity.Mesa;
import com.example.GastroTech.repository.BloqueoMesaRepository;
import com.example.GastroTech.repository.MesaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BloqueoMesaService {

    private final BloqueoMesaRepository bloqueoRepository;
    private final MesaRepository mesaRepository;

    @Transactional
    public BloqueoMesaResponseDTO crearBloqueo(Long mesaId, BloqueoMesaRequestDTO dto,
                                               String creadoPor) {
        if (!dto.fin().isAfter(dto.inicio())) {
            throw new BusinessException("La hora de fin debe ser posterior a la de inicio");
        }

        Mesa mesa = mesaRepository.findById(mesaId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Mesa no encontrada con id: " + mesaId));

        BloqueoMesa bloqueo = BloqueoMesa.builder()
                .mesa(mesa)
                .inicio(dto.inicio())
                .fin(dto.fin())
                .motivo(dto.motivo())
                .creadoPor(creadoPor)
                .build();

        return mapToResponseDTO(bloqueoRepository.save(bloqueo));
    }

    @Transactional
    public void eliminarBloqueo(Long bloqueoId) {
        if (!bloqueoRepository.existsById(bloqueoId)) {
            throw new ResourceNotFoundException("Bloqueo no encontrado con id: " + bloqueoId);
        }
        bloqueoRepository.deleteById(bloqueoId);
    }

    private BloqueoMesaResponseDTO mapToResponseDTO(BloqueoMesa b) {
        return new BloqueoMesaResponseDTO(
                b.getId(),
                b.getMesa().getId(),
                b.getMesa().getNumeroMesa(),
                b.getInicio(),
                b.getFin(),
                b.getMotivo(),
                b.getCreadoPor()
        );
    }
}
```

---

### `service/ReservaService.java` — añadir comprobación en `saveReservation()`

```java
// Nuevos imports:
import com.example.GastroTech.repository.BloqueoMesaRepository;

// Añadir dependencia (Lombok la inyecta):
private final BloqueoMesaRepository bloqueoRepository;

// Dentro de saveReservation(), DESPUÉS de encontrar la mesa y ANTES de comprobar conflictos:
boolean mesaBloqueada = bloqueoRepository
        .existsByMesaIdAndInicioLessThanEqualAndFinGreaterThanEqual(
                dto.tableId(),
                dto.reservationDate(),
                dto.reservationDate()
        );

if (mesaBloqueada) {
    throw new BusinessException(
            "La mesa no esta disponible en ese horario por mantenimiento o cierre temporal");
}
```

---

### `controller/BloqueoMesaController.java` — archivo nuevo completo

```java
package com.example.GastroTech.controller;

import com.example.GastroTech.dto.request.BloqueoMesaRequestDTO;
import com.example.GastroTech.dto.response.BloqueoMesaResponseDTO;
import com.example.GastroTech.service.BloqueoMesaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tables/{mesaId}/blocks")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Bloqueos de mesa", description = "Bloqueos por mantenimiento (solo ADMIN)")
@SecurityRequirement(name = "BearerAuth")
public class BloqueoMesaController {

    private final BloqueoMesaService bloqueoService;

    @PostMapping
    @Operation(summary = "Bloquear una mesa en una franja horaria")
    public ResponseEntity<BloqueoMesaResponseDTO> crearBloqueo(
            @PathVariable Long mesaId,
            @Valid @RequestBody BloqueoMesaRequestDTO dto) {
        String admin = SecurityContextHolder.getContext().getAuthentication().getName();
        return new ResponseEntity<>(bloqueoService.crearBloqueo(mesaId, dto, admin),
                HttpStatus.CREATED);
    }

    @DeleteMapping("/{bloqueoId}")
    @Operation(summary = "Eliminar un bloqueo de mesa")
    public ResponseEntity<Void> eliminarBloqueo(@PathVariable Long bloqueoId) {
        bloqueoService.eliminarBloqueo(bloqueoId);
        return ResponseEntity.noContent().build();
    }
}
```

---

### Commits sugeridos

```bash
git commit -m "feat(model): add BloqueoMesa entity"
git commit -m "feat(repository): add BloqueoMesaRepository with overlap query"
git commit -m "feat(dto): add BloqueoMesaRequestDTO and BloqueoMesaResponseDTO"
git commit -m "feat(service): add BloqueoMesaService with create and delete"
git commit -m "feat(service): check maintenance blocks before creating reservation"
git commit -m "feat(controller): add BloqueoMesaController under /tables/{id}/blocks"
```

---

---

## 7. Valoración post-reserva

**Dificultad:** ⭐⭐⭐  
**Rama Git:** `feature/ratings`  
**Resumen:** El usuario valora su reserva (1-5 estrellas + comentario) una vez que ha sido completada. Las valoraciones son públicas y la mesa muestra su media.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Crear | `model/Entity/Valoracion.java` |
| Crear | `repository/ValoracionRepository.java` |
| Crear | `dto/request/ValoracionRequestDTO.java` |
| Crear | `dto/response/ValoracionResponseDTO.java` |
| Modificar | `dto/response/MesaResponseDTO.java` |
| Crear | `exception/AlreadyRatedException.java` |
| Modificar | `exception/GlobalExceptionHandler.java` |
| Crear | `service/ValoracionService.java` |
| Crear | `controller/ValoracionController.java` |
| Modificar | `service/MesaService.java` |

---

### `model/Entity/Valoracion.java` — archivo nuevo completo

```java
package com.example.GastroTech.model.Entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "valoracion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Valoracion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Min(1) @Max(5)
    @Column(nullable = false)
    private int puntuacion;

    @Column(length = 300)
    private String comentario;

    @Column(nullable = false)
    private LocalDateTime fechaValoracion;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserva_id", nullable = false, unique = true)
    private Reserva reserva;
}
```

---

### `repository/ValoracionRepository.java` — archivo nuevo completo

```java
package com.example.GastroTech.repository;

import com.example.GastroTech.model.Entity.Valoracion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ValoracionRepository extends JpaRepository<Valoracion, Long> {

    boolean existsByReservaId(Long reservaId);

    List<Valoracion> findByReservaMesaId(Long mesaId);

    @Query("SELECT AVG(v.puntuacion) FROM Valoracion v WHERE v.reserva.mesa.id = :mesaId")
    Optional<Double> findAverageByMesaId(@Param("mesaId") Long mesaId);
}
```

---

### `dto/request/ValoracionRequestDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ValoracionRequestDTO(
        @Min(value = 1, message = "La puntuacion minima es 1")
        @Max(value = 5, message = "La puntuacion maxima es 5")
        int puntuacion,

        @Size(max = 300, message = "El comentario no puede superar los 300 caracteres")
        String comentario
) {}
```

---

### `dto/response/ValoracionResponseDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.response;

import java.time.LocalDateTime;

public record ValoracionResponseDTO(
        Long id,
        int puntuacion,
        String comentario,
        LocalDateTime fechaValoracion,
        String nombreUsuario
) {}
```

---

### `dto/response/MesaResponseDTO.java` — modificar para incluir media

```java
// Reemplazar el record actual por este (añade averageRating):
public record MesaResponseDTO(
        Long id,
        int numeroMesa,
        int capacidad,
        String ubicacion,
        String estado,
        Double averageRating   // null si no tiene valoraciones
) {}
```

---

### `exception/AlreadyRatedException.java` — archivo nuevo completo

```java
package com.example.GastroTech.exception;

public class AlreadyRatedException extends RuntimeException {
    public AlreadyRatedException() {
        super("Esta reserva ya ha sido valorada");
    }
}
```

---

### `exception/GlobalExceptionHandler.java` — añadir handler

```java
@ExceptionHandler(AlreadyRatedException.class)
public ResponseEntity<ErrorResponse> handleAlreadyRated(AlreadyRatedException ex) {
    return new ResponseEntity<>(
            new ErrorResponse("ALREADY_RATED", ex.getMessage(), LocalDateTime.now()),
            HttpStatus.CONFLICT);   // 409
}
```

---

### `service/ValoracionService.java` — archivo nuevo completo

```java
package com.example.GastroTech.service;

import com.example.GastroTech.dto.request.ValoracionRequestDTO;
import com.example.GastroTech.dto.response.ValoracionResponseDTO;
import com.example.GastroTech.exception.AlreadyRatedException;
import com.example.GastroTech.exception.BusinessException;
import com.example.GastroTech.exception.ResourceNotFoundException;
import com.example.GastroTech.model.Entity.Reserva;
import com.example.GastroTech.model.Entity.Valoracion;
import com.example.GastroTech.model.Enum.EstadoReserva;
import com.example.GastroTech.repository.ReservaRepository;
import com.example.GastroTech.repository.ValoracionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ValoracionService {

    private final ValoracionRepository valoracionRepository;
    private final ReservaRepository reservaRepository;

    @Transactional
    public ValoracionResponseDTO valorar(Long reservaId, ValoracionRequestDTO dto,
                                         String username) {
        Reserva reserva = reservaRepository.findById(reservaId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reserva no encontrada con id: " + reservaId));

        if (!reserva.getUsuario().getUsername().equals(username)) {
            throw new BusinessException("Solo puedes valorar tus propias reservas");
        }

        if (reserva.getEstado() != EstadoReserva.COMPLETADA) {
            throw new BusinessException("Solo se pueden valorar reservas completadas");
        }

        if (valoracionRepository.existsByReservaId(reservaId)) {
            throw new AlreadyRatedException();
        }

        Valoracion valoracion = Valoracion.builder()
                .puntuacion(dto.puntuacion())
                .comentario(dto.comentario())
                .fechaValoracion(LocalDateTime.now())
                .reserva(reserva)
                .build();

        return mapToResponseDTO(valoracionRepository.save(valoracion));
    }

    @Transactional(readOnly = true)
    public List<ValoracionResponseDTO> getValoracionesByMesa(Long mesaId) {
        return valoracionRepository.findByReservaMesaId(mesaId)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    private ValoracionResponseDTO mapToResponseDTO(Valoracion v) {
        return new ValoracionResponseDTO(
                v.getId(),
                v.getPuntuacion(),
                v.getComentario(),
                v.getFechaValoracion(),
                v.getReserva().getUsuario().getNombre()
        );
    }
}
```

---

### `service/MesaService.java` — inyectar ValoracionRepository y actualizar mapToResponseDTO

```java
// Añadir dependencia:
private final ValoracionRepository valoracionRepository;

// Reemplazar mapToResponseDTO:
private MesaResponseDTO mapToResponseDTO(Mesa mesa) {
    Double media = valoracionRepository
            .findAverageByMesaId(mesa.getId())
            .orElse(null);
    return new MesaResponseDTO(
            mesa.getId(),
            mesa.getNumeroMesa(),
            mesa.getCapacidad(),
            mesa.getUbicacion().name(),
            mesa.getEstado().name(),
            media
    );
}
```

---

### `controller/ValoracionController.java` — archivo nuevo completo

```java
package com.example.GastroTech.controller;

import com.example.GastroTech.dto.request.ValoracionRequestDTO;
import com.example.GastroTech.dto.response.ValoracionResponseDTO;
import com.example.GastroTech.service.ValoracionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Valoraciones")
public class ValoracionController {

    private final ValoracionService valoracionService;

    @PostMapping("/api/v1/reservations/{reservaId}/rate")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Valorar una reserva completada")
    public ResponseEntity<ValoracionResponseDTO> valorar(
            @PathVariable Long reservaId,
            @Valid @RequestBody ValoracionRequestDTO dto) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return new ResponseEntity<>(valoracionService.valorar(reservaId, dto, username),
                HttpStatus.CREATED);
    }

    @GetMapping("/api/v1/tables/{mesaId}/ratings")
    @Operation(summary = "Ver valoraciones de una mesa (publico)")
    public ResponseEntity<List<ValoracionResponseDTO>> getRatings(@PathVariable Long mesaId) {
        return ResponseEntity.ok(valoracionService.getValoracionesByMesa(mesaId));
    }
}
```

---

### Commits sugeridos

```bash
git commit -m "feat(model): add Valoracion entity with OneToOne relation to Reserva"
git commit -m "feat(repository): add ValoracionRepository with average rating query"
git commit -m "feat(dto): add ValoracionRequestDTO, ValoracionResponseDTO and update MesaResponseDTO"
git commit -m "feat(exception): add AlreadyRatedException mapped to 409"
git commit -m "feat(service): add ValoracionService and inject average rating into MesaService"
git commit -m "feat(controller): add rate reservation and get mesa ratings endpoints"
```

---

---

## 8. Transferencia de reserva entre usuarios

**Dificultad:** ⭐⭐⭐⭐  
**Rama Git:** `feature/reservation-transfer`  
**Resumen:** Un usuario puede ceder su reserva a otro usuario registrado siempre que falten más de 2 horas y el destinatario no esté baneado.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Crear | `dto/request/TransferenciaRequestDTO.java` |
| Modificar | `service/ReservaService.java` |
| Modificar | `controller/ReservaController.java` |

---

### `dto/request/TransferenciaRequestDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record TransferenciaRequestDTO(
        @Email(message = "El email del destinatario no es valido")
        @NotBlank(message = "El email del destinatario es obligatorio")
        String emailDestinatario
) {}
```

---

### `service/ReservaService.java` — añadir método `transferirReserva()`

```java
// Importar al inicio del fichero:
import com.example.GastroTech.dto.request.TransferenciaRequestDTO;

@Transactional
public ReservationResponseDTO transferirReserva(Long reservaId,
                                                 TransferenciaRequestDTO dto,
                                                 String emailOrigen) {
    Reserva reserva = reservaRepository.findById(reservaId)
            .orElseThrow(() -> new ResourceNotFoundException(
                    "Reserva no encontrada con id: " + reservaId));

    Usuario origen = usuarioRepository.findByEmail(emailOrigen)
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

    if (!reserva.getUsuario().getId().equals(origen.getId())) {
        throw new BusinessException("Solo puedes transferir tus propias reservas");
    }

    if (reserva.getEstado() != EstadoReserva.PENDIENTE
            && reserva.getEstado() != EstadoReserva.CONFIRMADA) {
        throw new BusinessException("Solo se pueden transferir reservas activas");
    }

    // Verificar que faltan mas de 2 horas
    LocalDateTime limite = reserva.getFechaReserva().minusHours(2);
    if (LocalDateTime.now().isAfter(limite)) {
        throw new BusinessException(
                "No se puede transferir con menos de 2 horas de antelacion");
    }

    Usuario destinatario = usuarioRepository.findByEmail(dto.emailDestinatario())
            .orElseThrow(() -> new ResourceNotFoundException(
                    "El usuario destinatario no existe: " + dto.emailDestinatario()));

    if (destinatario.getStatus() == EstadoUsuario.BANNED) {
        throw new BusinessException("No puedes transferir una reserva a un usuario baneado");
    }

    if (destinatario.getId().equals(origen.getId())) {
        throw new BusinessException("No puedes transferirte una reserva a ti mismo");
    }

    reserva.setUsuario(destinatario);
    return mapToResponseDTO(reservaRepository.save(reserva));
}
```

---

### `controller/ReservaController.java` — añadir endpoint

```java
// Importar al inicio del fichero:
import com.example.GastroTech.dto.request.TransferenciaRequestDTO;

@PatchMapping("/{id}/transfer")
@Operation(summary = "Transferir una reserva a otro usuario registrado")
public ResponseEntity<ReservationResponseDTO> transferirReserva(
        @PathVariable Long id,
        @Valid @RequestBody TransferenciaRequestDTO dto) {
    String username = getCurrentUsername();
    return ResponseEntity.ok(reservaService.transferirReserva(id, dto, username));
}
```

---

### Commits sugeridos

```bash
git commit -m "feat(dto): add TransferenciaRequestDTO"
git commit -m "feat(service): add transferirReserva with ban and time validations"
git commit -m "feat(controller): add PATCH /reservations/{id}/transfer endpoint"
```

---

---

## 9. Historial de cambios de estado de una reserva

**Dificultad:** ⭐⭐⭐⭐  
**Rama Git:** `feature/reservation-audit`  
**Resumen:** Cada cambio de estado de una reserva queda registrado. El ADMIN puede consultar el historial completo de cualquier reserva.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Crear | `model/Entity/ReservaAuditoria.java` |
| Crear | `repository/ReservaAuditoriaRepository.java` |
| Crear | `dto/response/ReservaAuditoriaResponseDTO.java` |
| Modificar | `service/ReservaService.java` |
| Modificar | `controller/ReservaController.java` |

---

### `model/Entity/ReservaAuditoria.java` — archivo nuevo completo

```java
package com.example.GastroTech.model.Entity;

import com.example.GastroTech.model.Enum.EstadoReserva;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reserva_auditoria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservaAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reservaId;

    @Enumerated(EnumType.STRING)
    private EstadoReserva estadoAnterior;   // null si es la creacion

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoReserva estadoNuevo;

    @Column(nullable = false)
    private LocalDateTime fechaCambio;

    @Column(nullable = false)
    private String modificadoPor;
}
```

---

### `repository/ReservaAuditoriaRepository.java` — archivo nuevo completo

```java
package com.example.GastroTech.repository;

import com.example.GastroTech.model.Entity.ReservaAuditoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReservaAuditoriaRepository extends JpaRepository<ReservaAuditoria, Long> {
    List<ReservaAuditoria> findByReservaIdOrderByFechaCambioAsc(Long reservaId);
}
```

---

### `dto/response/ReservaAuditoriaResponseDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.response;

import java.time.LocalDateTime;

public record ReservaAuditoriaResponseDTO(
        Long id,
        String estadoAnterior,
        String estadoNuevo,
        LocalDateTime fechaCambio,
        String modificadoPor
) {}
```

---

### `service/ReservaService.java` — inyectar repositorio y registrar en cada cambio de estado

```java
// Añadir dependencia:
private final ReservaAuditoriaRepository auditoriaRepository;

// Método privado de apoyo — añadir al final de la clase:
private void registrarCambioEstado(Reserva reserva, EstadoReserva anterior,
                                    EstadoReserva nuevo, String modificadoPor) {
    ReservaAuditoria auditoria = ReservaAuditoria.builder()
            .reservaId(reserva.getId())
            .estadoAnterior(anterior)
            .estadoNuevo(nuevo)
            .fechaCambio(LocalDateTime.now())
            .modificadoPor(modificadoPor)
            .build();
    auditoriaRepository.save(auditoria);
}

// En saveReservation(), justo antes del return final:
registrarCambioEstado(saved, null, EstadoReserva.PENDIENTE, username);

// En cancelReservation(), justo antes del return final (o del final del método):
registrarCambioEstado(reserva, EstadoReserva.PENDIENTE, EstadoReserva.CANCELADA, username);
```

---

### `service/ReservaService.java` — añadir método para consultar historial

```java
// Importar al inicio:
import com.example.GastroTech.dto.response.ReservaAuditoriaResponseDTO;

@Transactional(readOnly = true)
public List<ReservaAuditoriaResponseDTO> getHistorial(Long reservaId) {
    if (!reservaRepository.existsById(reservaId)) {
        throw new ResourceNotFoundException("Reserva no encontrada con id: " + reservaId);
    }
    return auditoriaRepository.findByReservaIdOrderByFechaCambioAsc(reservaId)
            .stream()
            .map(a -> new ReservaAuditoriaResponseDTO(
                    a.getId(),
                    a.getEstadoAnterior() != null ? a.getEstadoAnterior().name() : "CREACION",
                    a.getEstadoNuevo().name(),
                    a.getFechaCambio(),
                    a.getModificadoPor()
            ))
            .collect(Collectors.toList());
}
```

---

### `controller/ReservaController.java` — añadir endpoint de historial

```java
// Importar al inicio:
import com.example.GastroTech.dto.response.ReservaAuditoriaResponseDTO;
import org.springframework.security.access.prepost.PreAuthorize;

@GetMapping("/{id}/history")
@PreAuthorize("hasRole('ADMIN')")
@Operation(summary = "Ver historial de cambios de estado de una reserva (solo ADMIN)")
public ResponseEntity<List<ReservaAuditoriaResponseDTO>> getHistorial(@PathVariable Long id) {
    return ResponseEntity.ok(reservaService.getHistorial(id));
}
```

---

### Commits sugeridos

```bash
git commit -m "feat(model): add ReservaAuditoria entity to track state changes"
git commit -m "feat(repository): add ReservaAuditoriaRepository ordered by date"
git commit -m "feat(dto): add ReservaAuditoriaResponseDTO"
git commit -m "feat(service): inject audit repository and register state changes"
git commit -m "feat(service): add getHistorial method for ADMIN"
git commit -m "feat(controller): add GET /reservations/{id}/history endpoint"
```

---

---

## 10. Reserva recurrente semanal

**Dificultad:** ⭐⭐⭐⭐  
**Rama Git:** `feature/recurring-reservations`  
**Resumen:** El usuario puede marcar una reserva como recurrente y el sistema genera automáticamente las siguientes N semanas, parando si hay conflicto.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Crear | `model/Entity/GrupoRecurrencia.java` |
| Modificar | `model/Entity/Reserva.java` |
| Crear | `repository/GrupoRecurrenciaRepository.java` |
| Modificar | `dto/request/ReservationRequestDTO.java` |
| Crear | `dto/response/RecurringReservationResponseDTO.java` |
| Modificar | `service/ReservaService.java` |
| Modificar | `controller/ReservaController.java` |

---

### `model/Entity/GrupoRecurrencia.java` — archivo nuevo completo

```java
package com.example.GastroTech.model.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "grupo_recurrencia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrupoRecurrencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int semanassolicitadas;

    @Column(nullable = false)
    private int semanasGeneradas;

    @Column(nullable = false)
    private LocalDateTime fechaCreacion;
}
```

---

### `model/Entity/Reserva.java` — añadir campo

```java
// Añadir campo (puede ser null si no es recurrente):
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "grupo_recurrencia_id")
private GrupoRecurrencia grupoRecurrencia;
```

---

### `dto/request/ReservationRequestDTO.java` — añadir campos opcionales

```java
public record ReservationRequestDTO(
        @NotNull(message = "La mesa es obligatoria")
        Long tableId,

        @NotNull(message = "La fecha y hora es obligatoria")
        @Future(message = "La reserva debe ser en una fecha futura")
        LocalDateTime reservationDate,

        @Min(value = 1, message = "Minimo 1 comensal")
        @Max(value = 12, message = "Maximo 12 comensales por mesa")
        int numberOfGuests,

        // Campos nuevos opcionales para recurrencia
        boolean esRecurrente,

        @Min(value = 1, message = "Minimo 1 semana")
        @Max(value = 8, message = "Maximo 8 semanas")
        int semanasTotales   // ignorado si esRecurrente=false
) {}
```

---

### `dto/response/RecurringReservationResponseDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.response;

import java.util.List;

public record RecurringReservationResponseDTO(
        int semanassolicitadas,
        int semanasCreadas,
        String mensaje,
        List<ReservationResponseDTO> reservasCreadas
) {}
```

---

### `service/ReservaService.java` — añadir método para reservas recurrentes

```java
// Importar:
import com.example.GastroTech.dto.response.RecurringReservationResponseDTO;
import com.example.GastroTech.model.Entity.GrupoRecurrencia;
import com.example.GastroTech.repository.GrupoRecurrenciaRepository;

// Añadir dependencia:
private final GrupoRecurrenciaRepository grupoRepository;

// Nuevo método:
@Transactional
public RecurringReservationResponseDTO saveRecurringReservation(
        ReservationRequestDTO dto, String username) {

    // Reutilizar las mismas validaciones de usuario
    Usuario usuario = usuarioRepository.findByEmail(username)
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

    if (usuario.getStatus() == EstadoUsuario.BANNED) throw new UserBannedException();

    Mesa mesa = mesaRepository.findById(dto.tableId())
            .orElseThrow(() -> new ResourceNotFoundException(
                    "Mesa no encontrada con id: " + dto.tableId()));

    GrupoRecurrencia grupo = GrupoRecurrencia.builder()
            .semanassolicitadas(dto.semanasTotales())
            .semanasGeneradas(0)
            .fechaCreacion(LocalDateTime.now())
            .build();
    grupo = grupoRepository.save(grupo);

    List<ReservationResponseDTO> creadas = new ArrayList<>();

    for (int semana = 0; semana < dto.semanasTotales(); semana++) {
        LocalDateTime fechaSemana = dto.reservationDate().plusWeeks(semana);
        boolean conflicto = reservaRepository
                .existsByMesaIdAndFechaReservaBetweenAndEstadoNot(
                        dto.tableId(),
                        fechaSemana.minusHours(2),
                        fechaSemana.plusHours(2),
                        EstadoReserva.CANCELADA
                );

        if (conflicto) break;  // Parar si hay conflicto, no es un error

        Reserva reserva = Reserva.builder()
                .mesa(mesa).usuario(usuario)
                .fechaReserva(fechaSemana)
                .numeroPersonas(dto.numberOfGuests())
                .estado(EstadoReserva.PENDIENTE)
                .fechaCreacion(LocalDateTime.now())
                .grupoRecurrencia(grupo)
                .build();

        creadas.add(mapToResponseDTO(reservaRepository.save(reserva)));
    }

    grupo.setSemanasGeneradas(creadas.size());
    grupoRepository.save(grupo);

    String mensaje = creadas.size() == dto.semanasTotales()
            ? "Todas las semanas reservadas correctamente"
            : "Se generaron " + creadas.size() + " de " + dto.semanasTotales()
              + " semanas (conflicto en la semana " + (creadas.size() + 1) + ")";

    return new RecurringReservationResponseDTO(
            dto.semanasTotales(), creadas.size(), mensaje, creadas);
}
```

---

### `controller/ReservaController.java` — añadir endpoint

```java
// Importar:
import com.example.GastroTech.dto.response.RecurringReservationResponseDTO;

@PostMapping("/recurring")
@Operation(summary = "Crear reserva recurrente semanal (hasta 8 semanas)")
public ResponseEntity<RecurringReservationResponseDTO> createRecurringReservation(
        @Valid @RequestBody ReservationRequestDTO dto) {
    String username = getCurrentUsername();
    return new ResponseEntity<>(
            reservaService.saveRecurringReservation(dto, username), HttpStatus.CREATED);
}
```

---

### Commits sugeridos

```bash
git commit -m "feat(model): add GrupoRecurrencia entity and link to Reserva"
git commit -m "feat(dto): add esRecurrente and semanasTotales to ReservationRequestDTO"
git commit -m "feat(dto): add RecurringReservationResponseDTO"
git commit -m "feat(service): add saveRecurringReservation with week-by-week generation"
git commit -m "feat(controller): add POST /reservations/recurring endpoint"
```

---

---

## 11. Sistema de fidelización

**Dificultad:** ⭐⭐⭐⭐  
**Rama Git:** `feature/loyalty-system`  
**Resumen:** Cada reserva completada suma puntos de fidelización. Al acumular suficientes, el usuario sube de nivel (BRONZE → SILVER → GOLD). El endpoint `/users/me` muestra el perfil completo.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Crear | `model/Enum/LoyaltyTier.java` |
| Modificar | `model/Entity/Usuario.java` |
| Crear | `dto/response/UserProfileResponseDTO.java` |
| Modificar | `GastroTechApplication.java` |
| Modificar | `config/ReservaScheduler.java` (o crearlo) |
| Modificar | `service/UsuarioService.java` |
| Modificar | `controller/UsuarioController.java` |

---

### `model/Enum/LoyaltyTier.java` — archivo nuevo completo

```java
package com.example.GastroTech.model.Enum;

public enum LoyaltyTier {
    BRONZE,   // 0-49 puntos
    SILVER,   // 50-99 puntos
    GOLD      // 100+ puntos
}
```

---

### `model/Entity/Usuario.java` — añadir campos después de `status`

```java
// Importar al inicio:
import com.example.GastroTech.model.Enum.LoyaltyTier;

@Column(nullable = false)
@Builder.Default
private int loyaltyPoints = 0;

@Enumerated(EnumType.STRING)
@Column(nullable = false)
@Builder.Default
private LoyaltyTier loyaltyTier = LoyaltyTier.BRONZE;
```

---

### `dto/response/UserProfileResponseDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.response;

public record UserProfileResponseDTO(
        Long id,
        String nombre,
        String email,
        String rol,
        String status,
        int penalizationPoints,
        int loyaltyPoints,
        String loyaltyTier
) {}
```

---

### `GastroTechApplication.java` — añadir @EnableScheduling si no está

```java
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GastroTechApplication { ... }
```

---

### `config/ReservaScheduler.java` — archivo nuevo (o añadir método si ya existe)

```java
package com.example.GastroTech.config;

import com.example.GastroTech.model.Entity.Reserva;
import com.example.GastroTech.model.Entity.Usuario;
import com.example.GastroTech.model.Enum.EstadoReserva;
import com.example.GastroTech.model.Enum.LoyaltyTier;
import com.example.GastroTech.repository.ReservaRepository;
import com.example.GastroTech.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservaScheduler {

    private final ReservaRepository reservaRepository;
    private final UsuarioRepository usuarioRepository;

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void completarReservasCaducadas() {
        LocalDateTime limite = LocalDateTime.now().minusHours(2);
        List<Reserva> caducadas = reservaRepository
                .findReservasActivasCaducadas(EstadoReserva.CONFIRMADA, limite);

        for (Reserva reserva : caducadas) {
            reserva.setEstado(EstadoReserva.COMPLETADA);
            reservaRepository.save(reserva);

            // Sumar puntos de fidelizacion al usuario
            actualizarFidelizacion(reserva.getUsuario());

            log.info("[Scheduler] Reserva {} completada. Puntos fidelizacion de {}: {}",
                    reserva.getId(),
                    reserva.getUsuario().getEmail(),
                    reserva.getUsuario().getLoyaltyPoints());
        }
    }

    private void actualizarFidelizacion(Usuario usuario) {
        int nuevosPuntos = usuario.getLoyaltyPoints() + 10;
        usuario.setLoyaltyPoints(nuevosPuntos);

        if (nuevosPuntos >= 100)     usuario.setLoyaltyTier(LoyaltyTier.GOLD);
        else if (nuevosPuntos >= 50) usuario.setLoyaltyTier(LoyaltyTier.SILVER);

        usuarioRepository.save(usuario);
    }
}
```

> **Nota:** `findReservasActivasCaducadas` es la misma query de la feature del scheduler descrita en el enunciado del proyecto.

---

### `service/UsuarioService.java` — añadir método `getMyProfile()`

```java
// Importar al inicio:
import com.example.GastroTech.dto.response.UserProfileResponseDTO;

@Transactional(readOnly = true)
public UserProfileResponseDTO getMyProfile(String email) {
    Usuario usuario = usuarioRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    return mapToProfileDTO(usuario);
}

private UserProfileResponseDTO mapToProfileDTO(Usuario usuario) {
    return new UserProfileResponseDTO(
            usuario.getId(),
            usuario.getNombre(),
            usuario.getEmail(),
            usuario.getRol().name(),
            usuario.getStatus().name(),
            usuario.getPenalizationPoints(),
            usuario.getLoyaltyPoints(),
            usuario.getLoyaltyTier().name()
    );
}
```

---

### `controller/UsuarioController.java` — añadir endpoint de perfil

```java
@GetMapping("/me")
@Operation(summary = "Ver mi perfil con puntos de fidelizacion y estado")
public ResponseEntity<UserProfileResponseDTO> getMyProfile() {
    String email = SecurityContextHolder.getContext().getAuthentication().getName();
    return ResponseEntity.ok(usuarioService.getMyProfile(email));
}
```

---

### Commits sugeridos

```bash
git commit -m "feat(enum): add LoyaltyTier enum (BRONZE, SILVER, GOLD)"
git commit -m "feat(model): add loyaltyPoints and loyaltyTier fields to Usuario"
git commit -m "feat(dto): add UserProfileResponseDTO"
git commit -m "feat(scheduler): add loyalty points on reservation completion"
git commit -m "feat(service): add getMyProfile method to UsuarioService"
git commit -m "feat(controller): add GET /api/v1/users/me endpoint"
```

---

---

## 12. Lista de espera

**Dificultad:** ⭐⭐⭐⭐⭐  
**Rama Git:** `feature/waitlist`  
**Resumen:** Si una mesa está ocupada en esa franja, el cliente puede unirse a la lista de espera. Cuando se cancela una reserva, el sistema notifica automáticamente al primero de la lista.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Crear | `model/Enum/EstadoEspera.java` |
| Crear | `model/Entity/ListaEspera.java` |
| Crear | `repository/ListaEsperaRepository.java` |
| Crear | `dto/request/WaitlistRequestDTO.java` |
| Crear | `dto/response/WaitlistResponseDTO.java` |
| Crear | `exception/AlreadyOnWaitlistException.java` |
| Modificar | `exception/GlobalExceptionHandler.java` |
| Crear | `service/ListaEsperaService.java` |
| Modificar | `service/ReservaService.java` |
| Crear | `controller/ListaEsperaController.java` |

---

### `model/Enum/EstadoEspera.java` — archivo nuevo completo

```java
package com.example.GastroTech.model.Enum;

public enum EstadoEspera {
    WAITING,    // en espera
    NOTIFIED,   // se le ha notificado que hay hueco
    EXPIRED,    // la fecha pasó sin que se liberara
    CANCELLED   // el propio usuario se retiró de la lista
}
```

---

### `model/Entity/ListaEspera.java` — archivo nuevo completo

```java
package com.example.GastroTech.model.Entity;

import com.example.GastroTech.model.Enum.EstadoEspera;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "lista_espera")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListaEspera {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime fechaSolicitada;  // la fecha/hora de la reserva deseada

    @Column(nullable = false)
    private int posicion;

    @Column(nullable = false)
    private int numberOfGuests;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoEspera estado;

    private LocalDateTime fechaNotificacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mesa_id", nullable = false)
    private Mesa mesa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
}
```

---

### `repository/ListaEsperaRepository.java` — archivo nuevo completo

```java
package com.example.GastroTech.repository;

import com.example.GastroTech.model.Entity.ListaEspera;
import com.example.GastroTech.model.Enum.EstadoEspera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ListaEsperaRepository extends JpaRepository<ListaEspera, Long> {

    // Posicion maxima actual para calcular la siguiente
    @Query("SELECT COALESCE(MAX(l.posicion), 0) FROM ListaEspera l " +
           "WHERE l.mesa.id = :mesaId AND l.fechaSolicitada = :fecha " +
           "AND l.estado = 'WAITING'")
    int findMaxPosicion(@Param("mesaId") Long mesaId,
                        @Param("fecha") LocalDateTime fecha);

    // Primero de la lista para una mesa y fecha
    Optional<ListaEspera> findFirstByMesaIdAndFechaSolicitadaAndEstadoOrderByPosicionAsc(
            Long mesaId, LocalDateTime fecha, EstadoEspera estado);

    // Lista de espera de un usuario concreto
    List<ListaEspera> findByUsuarioIdOrderByFechaSolicitadaAsc(Long usuarioId);

    // Comprobar si el usuario ya está en la lista para esa mesa y fecha
    boolean existsByMesaIdAndFechaSolicitadaAndUsuarioIdAndEstado(
            Long mesaId, LocalDateTime fecha, Long usuarioId, EstadoEspera estado);
}
```

---

### `dto/request/WaitlistRequestDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record WaitlistRequestDTO(
        @NotNull(message = "La mesa es obligatoria")
        Long tableId,

        @NotNull(message = "La fecha y hora es obligatoria")
        @Future(message = "La fecha debe ser futura")
        LocalDateTime fechaDeseada,

        @Min(value = 1, message = "Minimo 1 comensal")
        @Max(value = 12, message = "Maximo 12 comensales")
        int numberOfGuests
) {}
```

---

### `dto/response/WaitlistResponseDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.response;

import java.time.LocalDateTime;

public record WaitlistResponseDTO(
        Long id,
        int posicion,
        String mesaNombre,
        LocalDateTime fechaDeseada,
        String estado,
        LocalDateTime fechaNotificacion
) {}
```

---

### `exception/AlreadyOnWaitlistException.java` — archivo nuevo completo

```java
package com.example.GastroTech.exception;

public class AlreadyOnWaitlistException extends RuntimeException {
    public AlreadyOnWaitlistException() {
        super("Ya estas en la lista de espera para esa mesa y fecha");
    }
}
```

---

### `exception/GlobalExceptionHandler.java` — añadir handler

```java
@ExceptionHandler(AlreadyOnWaitlistException.class)
public ResponseEntity<ErrorResponse> handleWaitlist(AlreadyOnWaitlistException ex) {
    return new ResponseEntity<>(
            new ErrorResponse("ALREADY_ON_WAITLIST", ex.getMessage(), LocalDateTime.now()),
            HttpStatus.CONFLICT);
}
```

---

### `service/ListaEsperaService.java` — archivo nuevo completo

```java
package com.example.GastroTech.service;

import com.example.GastroTech.dto.request.WaitlistRequestDTO;
import com.example.GastroTech.dto.response.WaitlistResponseDTO;
import com.example.GastroTech.exception.AlreadyOnWaitlistException;
import com.example.GastroTech.exception.ResourceNotFoundException;
import com.example.GastroTech.model.Entity.ListaEspera;
import com.example.GastroTech.model.Entity.Mesa;
import com.example.GastroTech.model.Entity.Usuario;
import com.example.GastroTech.model.Enum.EstadoEspera;
import com.example.GastroTech.repository.ListaEsperaRepository;
import com.example.GastroTech.repository.MesaRepository;
import com.example.GastroTech.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ListaEsperaService {

    private final ListaEsperaRepository listaEsperaRepository;
    private final MesaRepository mesaRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional
    public WaitlistResponseDTO unirseAListaEspera(WaitlistRequestDTO dto, String username) {
        Mesa mesa = mesaRepository.findById(dto.tableId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Mesa no encontrada con id: " + dto.tableId()));

        Usuario usuario = usuarioRepository.findByEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // Comprobar que no está ya en la lista para esta mesa y fecha
        boolean yaEnLista = listaEsperaRepository
                .existsByMesaIdAndFechaSolicitadaAndUsuarioIdAndEstado(
                        dto.tableId(), dto.fechaDeseada(),
                        usuario.getId(), EstadoEspera.WAITING);

        if (yaEnLista) throw new AlreadyOnWaitlistException();

        int siguientePosicion = listaEsperaRepository
                .findMaxPosicion(dto.tableId(), dto.fechaDeseada()) + 1;

        ListaEspera entrada = ListaEspera.builder()
                .mesa(mesa)
                .usuario(usuario)
                .fechaSolicitada(dto.fechaDeseada())
                .numberOfGuests(dto.numberOfGuests())
                .posicion(siguientePosicion)
                .estado(EstadoEspera.WAITING)
                .build();

        log.info("Usuario {} añadido a lista de espera para mesa {} en posicion {}",
                username, mesa.getNumeroMesa(), siguientePosicion);

        return mapToResponseDTO(listaEsperaRepository.save(entrada));
    }

    /**
     * Llamado desde ReservaService cuando se cancela una reserva.
     * Busca el primero de la lista de espera y lo notifica.
     */
    @Transactional
    public void notificarSiguienteEnEspera(Long mesaId, LocalDateTime fechaReserva) {
        listaEsperaRepository
                .findFirstByMesaIdAndFechaSolicitadaAndEstadoOrderByPosicionAsc(
                        mesaId, fechaReserva, EstadoEspera.WAITING)
                .ifPresent(entrada -> {
                    entrada.setEstado(EstadoEspera.NOTIFIED);
                    entrada.setFechaNotificacion(LocalDateTime.now());
                    listaEsperaRepository.save(entrada);
                    log.info("[Lista Espera] Usuario {} notificado para mesa {} en {}",
                            entrada.getUsuario().getEmail(),
                            entrada.getMesa().getNumeroMesa(),
                            fechaReserva);
                });
    }

    @Transactional(readOnly = true)
    public List<WaitlistResponseDTO> getMiListaEspera(String username) {
        Usuario usuario = usuarioRepository.findByEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        return listaEsperaRepository.findByUsuarioIdOrderByFechaSolicitadaAsc(usuario.getId())
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    private WaitlistResponseDTO mapToResponseDTO(ListaEspera l) {
        return new WaitlistResponseDTO(
                l.getId(),
                l.getPosicion(),
                "Mesa " + l.getMesa().getNumeroMesa(),
                l.getFechaSolicitada(),
                l.getEstado().name(),
                l.getFechaNotificacion()
        );
    }
}
```

---

### `service/ReservaService.java` — notificar lista de espera al cancelar

```java
// Añadir dependencia:
private final ListaEsperaService listaEsperaService;

// En cancelReservation(), justo antes de reservaRepository.save(reserva):
listaEsperaService.notificarSiguienteEnEspera(
        reserva.getMesa().getId(),
        reserva.getFechaReserva()
);
```

---

### `controller/ListaEsperaController.java` — archivo nuevo completo

```java
package com.example.GastroTech.controller;

import com.example.GastroTech.dto.request.WaitlistRequestDTO;
import com.example.GastroTech.dto.response.WaitlistResponseDTO;
import com.example.GastroTech.service.ListaEsperaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reservations/waitlist")
@RequiredArgsConstructor
@Tag(name = "Lista de espera")
@SecurityRequirement(name = "BearerAuth")
public class ListaEsperaController {

    private final ListaEsperaService listaEsperaService;

    @PostMapping
    @Operation(summary = "Unirse a la lista de espera para una mesa y fecha")
    public ResponseEntity<WaitlistResponseDTO> unirse(@Valid @RequestBody WaitlistRequestDTO dto) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return new ResponseEntity<>(
                listaEsperaService.unirseAListaEspera(dto, username), HttpStatus.CREATED);
    }

    @GetMapping("/me")
    @Operation(summary = "Ver mis entradas en lista de espera")
    public ResponseEntity<List<WaitlistResponseDTO>> getMiLista() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(listaEsperaService.getMiListaEspera(username));
    }
}
```

---

### Commits sugeridos

```bash
git commit -m "feat(enum): add EstadoEspera enum"
git commit -m "feat(model): add ListaEspera entity with Mesa and Usuario relations"
git commit -m "feat(repository): add ListaEsperaRepository with position and notification queries"
git commit -m "feat(dto): add WaitlistRequestDTO and WaitlistResponseDTO"
git commit -m "feat(exception): add AlreadyOnWaitlistException mapped to 409"
git commit -m "feat(service): add ListaEsperaService with join, notify and list methods"
git commit -m "feat(service): trigger waitlist notification on reservation cancellation"
git commit -m "feat(controller): add ListaEsperaController with join and list endpoints"
```

---

---

## Resumen final

| # | Feature | Rama | Entidad nueva | Endpoints | Scheduler | Dificultad |
|---|---------|------|--------------|-----------|-----------|-----------|
| 1 | Límite de reservas activas | `feature/reservation-limit` | No | 0 | No | ⭐⭐ |
| 2 | Sugerencia automática de mesa | `feature/table-suggestion` | No | 1 | No | ⭐⭐ |
| 3 | Notas internas del ADMIN | `feature/internal-notes` | Sí | 2 | No | ⭐⭐⭐ |
| 4 | Aforo máximo simultáneo | `feature/max-capacity` | No | 1 | No | ⭐⭐⭐ |
| 5 | Caducidad de penalización | `feature/penalty-expiry` | No | 0 | Sí | ⭐⭐⭐ |
| 6 | Bloqueo de mesa por mantenimiento | `feature/table-block` | Sí | 2 | No | ⭐⭐⭐ |
| 7 | Valoración post-reserva | `feature/ratings` | Sí | 2 | No | ⭐⭐⭐ |
| 8 | Transferencia de reserva | `feature/reservation-transfer` | No | 1 | No | ⭐⭐⭐⭐ |
| 9 | Historial de cambios de estado | `feature/reservation-audit` | Sí | 1 | No | ⭐⭐⭐⭐ |
| 10 | Reserva recurrente semanal | `feature/recurring-reservations` | Sí | 1 | No | ⭐⭐⭐⭐ |
| 11 | Sistema de fidelización | `feature/loyalty-system` | No | 1 | Sí | ⭐⭐⭐⭐ |
| 12 | Lista de espera | `feature/waitlist` | Sí | 2 | No | ⭐⭐⭐⭐⭐ |

---

---

## 13. Check-in de reserva

**Dificultad:** ⭐⭐⭐  
**Rama Git:** `feature/checkin`  
**Resumen:** El usuario confirma su llegada al local mediante un endpoint. Si no hace check-in en los primeros 15 minutos tras la hora de la reserva, un scheduler cancela la reserva automáticamente y libera la mesa (no-show).

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Modificar | `model/Entity/Reserva.java` |
| Modificar | `model/Enum/EstadoReserva.java` |
| Modificar | `service/ReservaService.java` |
| Modificar | `GastroTechApplication.java` |
| Crear | `config/CheckInScheduler.java` |
| Modificar | `controller/ReservaController.java` |
| Modificar | `repository/ReservaRepository.java` |

---

### `model/Enum/EstadoReserva.java` — añadir estado NO_SHOW

```java
public enum EstadoReserva {
    PENDIENTE,
    CONFIRMADA,
    COMPLETADA,
    CANCELADA,
    NO_SHOW    // <- nuevo: no se presentó sin avisar
}
```

---

### `model/Entity/Reserva.java` — añadir campo de check-in

```java
// Añadir después de observaciones:
private LocalDateTime fechaCheckIn;   // null hasta que el usuario confirma llegada
```

---

### `repository/ReservaRepository.java` — añadir query para el scheduler

```java
// Reservas CONFIRMADAS cuya hora ya pasó el margen sin check-in
@Query("""
    SELECT r FROM Reserva r
    WHERE r.estado = 'CONFIRMADA'
    AND r.fechaCheckIn IS NULL
    AND r.fechaReserva <= :limiteNoShow
    """)
List<Reserva> findReservasNoShow(@Param("limiteNoShow") LocalDateTime limiteNoShow);
```

---

### `service/ReservaService.java` — añadir método checkIn

```java
// Importar:
import com.example.GastroTech.model.Enum.EstadoReserva;

@Transactional
public ReservationResponseDTO checkIn(Long reservaId, String username) {
    Reserva reserva = reservaRepository.findById(reservaId)
            .orElseThrow(() -> new ResourceNotFoundException(
                    "Reserva no encontrada con id: " + reservaId));

    Usuario usuario = usuarioRepository.findByEmail(username)
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

    if (!reserva.getUsuario().getId().equals(usuario.getId())) {
        throw new BusinessException("Solo puedes hacer check-in en tus propias reservas");
    }

    if (reserva.getEstado() != EstadoReserva.CONFIRMADA) {
        throw new BusinessException("Solo se puede hacer check-in en reservas confirmadas");
    }

    if (reserva.getFechaCheckIn() != null) {
        throw new BusinessException("Ya has hecho check-in en esta reserva");
    }

    // Solo se puede hacer check-in desde 15 min antes hasta 15 min despues
    LocalDateTime ventanaInicio = reserva.getFechaReserva().minusMinutes(15);
    LocalDateTime ventanaFin    = reserva.getFechaReserva().plusMinutes(15);
    LocalDateTime ahora         = LocalDateTime.now();

    if (ahora.isBefore(ventanaInicio) || ahora.isAfter(ventanaFin)) {
        throw new BusinessException(
                "El check-in solo esta disponible entre 15 minutos antes y 15 minutos despues"
                + " de la hora de la reserva");
    }

    reserva.setFechaCheckIn(LocalDateTime.now());
    return mapToResponseDTO(reservaRepository.save(reserva));
}
```

---

### `GastroTechApplication.java` — añadir @EnableScheduling si no está

```java
@SpringBootApplication
@EnableScheduling
public class GastroTechApplication { ... }
```

---

### `config/CheckInScheduler.java` — archivo nuevo completo

```java
package com.example.GastroTech.config;

import com.example.GastroTech.model.Entity.Reserva;
import com.example.GastroTech.model.Enum.EstadoReserva;
import com.example.GastroTech.repository.ReservaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CheckInScheduler {

    private final ReservaRepository reservaRepository;

    /**
     * Cada 5 minutos comprueba si hay reservas CONFIRMADAS cuya hora pasó
     * hace más de 15 minutos sin que el usuario haya hecho check-in.
     * Las marca como NO_SHOW.
     */
    @Scheduled(fixedRate = 300_000)
    @Transactional
    public void cancelarNoShows() {
        // "No show" = reserva confirmada + sin check-in + más de 15 min tarde
        LocalDateTime limiteNoShow = LocalDateTime.now().minusMinutes(15);

        List<Reserva> noShows = reservaRepository.findReservasNoShow(limiteNoShow);

        for (Reserva reserva : noShows) {
            reserva.setEstado(EstadoReserva.NO_SHOW);
            reservaRepository.save(reserva);
            log.warn("[CheckIn Scheduler] Reserva {} marcada como NO_SHOW (usuario: {})",
                    reserva.getId(), reserva.getUsuario().getEmail());
        }

        if (!noShows.isEmpty()) {
            log.info("[CheckIn Scheduler] {} reservas marcadas como NO_SHOW", noShows.size());
        }
    }
}
```

---

### `controller/ReservaController.java` — añadir endpoint

```java
@PatchMapping("/{id}/checkin")
@Operation(summary = "Confirmar llegada al local (check-in)")
public ResponseEntity<ReservationResponseDTO> checkIn(@PathVariable Long id) {
    String username = getCurrentUsername();
    return ResponseEntity.ok(reservaService.checkIn(id, username));
}
```

---

### Commits sugeridos

```bash
git commit -m "feat(enum): add NO_SHOW value to EstadoReserva"
git commit -m "feat(model): add fechaCheckIn field to Reserva"
git commit -m "feat(repository): add findReservasNoShow query for scheduler"
git commit -m "feat(service): add checkIn method with 15-minute window validation"
git commit -m "feat(scheduler): add CheckInScheduler to auto-cancel no-show reservations"
git commit -m "feat(controller): add PATCH /reservations/{id}/checkin endpoint"
```

---

---

## 14. Preferencias del usuario

**Dificultad:** ⭐⭐  
**Rama Git:** `feature/user-preferences`  
**Resumen:** El usuario puede guardar en su perfil sus preferencias habituales (alergias, dieta, ubicación favorita). Al crear una reserva, estas preferencias se copian automáticamente como observaciones si el usuario no escribe ninguna.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Crear | `model/Entity/PreferenciasUsuario.java` |
| Crear | `repository/PreferenciasRepository.java` |
| Crear | `dto/request/PreferenciasRequestDTO.java` |
| Crear | `dto/response/PreferenciasResponseDTO.java` |
| Crear | `service/PreferenciasService.java` |
| Modificar | `service/ReservaService.java` |
| Modificar | `controller/UsuarioController.java` |

---

### `model/Entity/PreferenciasUsuario.java` — archivo nuevo completo

```java
package com.example.GastroTech.model.Entity;

import com.example.GastroTech.model.Enum.UbicacionMesa;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "preferencias_usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreferenciasUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String alergias;

    private String dietaEspecial;         // vegetariano, vegano, sin gluten...

    @Enumerated(EnumType.STRING)
    private UbicacionMesa ubicacionFavorita;   // null = sin preferencia

    private String notasGenerales;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;
}
```

---

### `repository/PreferenciasRepository.java` — archivo nuevo completo

```java
package com.example.GastroTech.repository;

import com.example.GastroTech.model.Entity.PreferenciasUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PreferenciasRepository extends JpaRepository<PreferenciasUsuario, Long> {
    Optional<PreferenciasUsuario> findByUsuarioId(Long usuarioId);
}
```

---

### `dto/request/PreferenciasRequestDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.request;

import com.example.GastroTech.model.Enum.UbicacionMesa;
import jakarta.validation.constraints.Size;

public record PreferenciasRequestDTO(
        @Size(max = 200, message = "Maximo 200 caracteres")
        String alergias,

        @Size(max = 100, message = "Maximo 100 caracteres")
        String dietaEspecial,

        UbicacionMesa ubicacionFavorita,   // opcional

        @Size(max = 300, message = "Maximo 300 caracteres")
        String notasGenerales
) {}
```

---

### `dto/response/PreferenciasResponseDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.response;

public record PreferenciasResponseDTO(
        Long id,
        String alergias,
        String dietaEspecial,
        String ubicacionFavorita,
        String notasGenerales
) {}
```

---

### `service/PreferenciasService.java` — archivo nuevo completo

```java
package com.example.GastroTech.service;

import com.example.GastroTech.dto.request.PreferenciasRequestDTO;
import com.example.GastroTech.dto.response.PreferenciasResponseDTO;
import com.example.GastroTech.exception.ResourceNotFoundException;
import com.example.GastroTech.model.Entity.PreferenciasUsuario;
import com.example.GastroTech.model.Entity.Usuario;
import com.example.GastroTech.repository.PreferenciasRepository;
import com.example.GastroTech.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PreferenciasService {

    private final PreferenciasRepository preferenciasRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional
    public PreferenciasResponseDTO guardarPreferencias(String email,
                                                        PreferenciasRequestDTO dto) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // Upsert: actualizar si ya existe, crear si no
        PreferenciasUsuario preferencias = preferenciasRepository
                .findByUsuarioId(usuario.getId())
                .orElse(PreferenciasUsuario.builder().usuario(usuario).build());

        preferencias.setAlergias(dto.alergias());
        preferencias.setDietaEspecial(dto.dietaEspecial());
        preferencias.setUbicacionFavorita(dto.ubicacionFavorita());
        preferencias.setNotasGenerales(dto.notasGenerales());

        return mapToResponseDTO(preferenciasRepository.save(preferencias));
    }

    @Transactional(readOnly = true)
    public PreferenciasResponseDTO getPreferencias(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        return preferenciasRepository.findByUsuarioId(usuario.getId())
                .map(this::mapToResponseDTO)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No tienes preferencias guardadas todavia"));
    }

    private PreferenciasResponseDTO mapToResponseDTO(PreferenciasUsuario p) {
        return new PreferenciasResponseDTO(
                p.getId(),
                p.getAlergias(),
                p.getDietaEspecial(),
                p.getUbicacionFavorita() != null ? p.getUbicacionFavorita().name() : null,
                p.getNotasGenerales()
        );
    }
}
```

---

### `service/ReservaService.java` — usar preferencias como observaciones por defecto

```java
// Añadir dependencia:
private final PreferenciasRepository preferenciasRepository;

// En saveReservation(), justo antes de construir la Reserva:
// Si el DTO no trae observaciones, usar las preferencias del usuario como fallback
String observaciones = dto.observaciones();
if (observaciones == null || observaciones.isBlank()) {
    observaciones = preferenciasRepository.findByUsuarioId(usuario.getId())
            .map(p -> {
                StringBuilder sb = new StringBuilder();
                if (p.getAlergias() != null) sb.append("Alergias: ").append(p.getAlergias()).append(". ");
                if (p.getDietaEspecial() != null) sb.append("Dieta: ").append(p.getDietaEspecial()).append(". ");
                if (p.getNotasGenerales() != null) sb.append(p.getNotasGenerales());
                return sb.toString().trim();
            })
            .orElse(null);
}

// Y en el builder de Reserva, añadir:
.observaciones(observaciones)
```

---

### `controller/UsuarioController.java` — añadir endpoints de preferencias

```java
// Añadir dependencia:
private final PreferenciasService preferenciasService;

@PutMapping("/me/preferences")
@Operation(summary = "Guardar o actualizar mis preferencias")
public ResponseEntity<PreferenciasResponseDTO> savePreferences(
        @Valid @RequestBody PreferenciasRequestDTO dto) {
    String email = SecurityContextHolder.getContext().getAuthentication().getName();
    return ResponseEntity.ok(preferenciasService.guardarPreferencias(email, dto));
}

@GetMapping("/me/preferences")
@Operation(summary = "Ver mis preferencias guardadas")
public ResponseEntity<PreferenciasResponseDTO> getPreferences() {
    String email = SecurityContextHolder.getContext().getAuthentication().getName();
    return ResponseEntity.ok(preferenciasService.getPreferencias(email));
}
```

---

### Commits sugeridos

```bash
git commit -m "feat(model): add PreferenciasUsuario entity with OneToOne to Usuario"
git commit -m "feat(repository): add PreferenciasRepository with findByUsuarioId"
git commit -m "feat(dto): add PreferenciasRequestDTO and PreferenciasResponseDTO"
git commit -m "feat(service): add PreferenciasService with upsert logic"
git commit -m "feat(service): use user preferences as default reservation observations"
git commit -m "feat(controller): add PUT and GET /users/me/preferences endpoints"
```

---

---

## 15. Dashboard de estadísticas para ADMIN

**Dificultad:** ⭐⭐⭐  
**Rama Git:** `feature/admin-dashboard`  
**Resumen:** Un único endpoint devuelve un resumen estadístico del local: total de reservas por estado, mesa más reservada, hora punta, media de comensales y usuarios baneados activos.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Crear | `dto/response/DashboardResponseDTO.java` |
| Crear | `dto/response/MesaEstadisticaDTO.java` |
| Modificar | `repository/ReservaRepository.java` |
| Modificar | `repository/UsuarioRepository.java` |
| Modificar | `repository/MesaRepository.java` |
| Crear | `service/DashboardService.java` |
| Crear | `controller/DashboardController.java` |

---

### `dto/response/MesaEstadisticaDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.response;

public record MesaEstadisticaDTO(
        Long mesaId,
        int numeroMesa,
        long totalReservas
) {}
```

---

### `dto/response/DashboardResponseDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.response;

import java.util.Map;

public record DashboardResponseDTO(
        long totalReservas,
        Map<String, Long> reservasPorEstado,   // "PENDIENTE" -> 12, "CANCELADA" -> 3...
        MesaEstadisticaDTO mesaMasReservada,
        int horaPunta,                          // hora del día con más reservas (0-23)
        double mediaComensalesPorReserva,
        long usuariosBaneados,
        long totalUsuarios
) {}
```

---

### `repository/ReservaRepository.java` — añadir queries de estadísticas

```java
// Contar reservas agrupadas por estado
@Query("SELECT r.estado, COUNT(r) FROM Reserva r GROUP BY r.estado")
List<Object[]> countByEstado();

// Mesa con más reservas
@Query("""
    SELECT r.mesa.id, r.mesa.numeroMesa, COUNT(r)
    FROM Reserva r
    WHERE r.estado != 'CANCELADA'
    GROUP BY r.mesa.id, r.mesa.numeroMesa
    ORDER BY COUNT(r) DESC
    LIMIT 1
    """)
Optional<Object[]> findMesaMasReservada();

// Hora punta (hora del día con más reservas)
@Query("SELECT FUNCTION('HOUR', r.fechaReserva), COUNT(r) FROM Reserva r " +
       "WHERE r.estado != 'CANCELADA' GROUP BY FUNCTION('HOUR', r.fechaReserva) " +
       "ORDER BY COUNT(r) DESC LIMIT 1")
Optional<Object[]> findHoraPunta();

// Media de comensales
@Query("SELECT AVG(r.numeroPersonas) FROM Reserva r WHERE r.estado != 'CANCELADA'")
Optional<Double> findMediaComensales();
```

---

### `repository/UsuarioRepository.java` — añadir conteo de baneados

```java
// Importar:
import com.example.GastroTech.model.Enum.EstadoUsuario;

long countByStatus(EstadoUsuario status);
```

---

### `service/DashboardService.java` — archivo nuevo completo

```java
package com.example.GastroTech.service;

import com.example.GastroTech.dto.response.DashboardResponseDTO;
import com.example.GastroTech.dto.response.MesaEstadisticaDTO;
import com.example.GastroTech.model.Enum.EstadoUsuario;
import com.example.GastroTech.repository.ReservaRepository;
import com.example.GastroTech.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ReservaRepository reservaRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public DashboardResponseDTO getDashboard() {

        // Reservas por estado
        Map<String, Long> porEstado = new HashMap<>();
        List<Object[]> estadoRows = reservaRepository.countByEstado();
        for (Object[] row : estadoRows) {
            porEstado.put(row[0].toString(), (Long) row[1]);
        }

        long totalReservas = porEstado.values().stream().mapToLong(Long::longValue).sum();

        // Mesa mas reservada
        MesaEstadisticaDTO mesaMasReservada = reservaRepository.findMesaMasReservada()
                .map(row -> new MesaEstadisticaDTO(
                        (Long) row[0], (Integer) row[1], (Long) row[2]))
                .orElse(null);

        // Hora punta
        int horaPunta = reservaRepository.findHoraPunta()
                .map(row -> ((Number) row[0]).intValue())
                .orElse(-1);

        // Media de comensales
        double mediaComensales = reservaRepository.findMediaComensales()
                .orElse(0.0);

        // Usuarios
        long baneados  = usuarioRepository.countByStatus(EstadoUsuario.BANNED);
        long totalUsuarios = usuarioRepository.count();

        return new DashboardResponseDTO(
                totalReservas,
                porEstado,
                mesaMasReservada,
                horaPunta,
                Math.round(mediaComensales * 100.0) / 100.0,
                baneados,
                totalUsuarios
        );
    }
}
```

---

### `controller/DashboardController.java` — archivo nuevo completo

```java
package com.example.GastroTech.controller;

import com.example.GastroTech.dto.response.DashboardResponseDTO;
import com.example.GastroTech.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Dashboard", description = "Estadisticas del local (solo ADMIN)")
@SecurityRequirement(name = "BearerAuth")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @Operation(summary = "Obtener estadisticas generales del local")
    public ResponseEntity<DashboardResponseDTO> getDashboard() {
        return ResponseEntity.ok(dashboardService.getDashboard());
    }
}
```

---

### Commits sugeridos

```bash
git commit -m "feat(dto): add DashboardResponseDTO and MesaEstadisticaDTO"
git commit -m "feat(repository): add JPQL aggregate queries for dashboard stats"
git commit -m "feat(repository): add countByStatus to UsuarioRepository"
git commit -m "feat(service): add DashboardService with full stats aggregation"
git commit -m "feat(controller): add GET /api/v1/admin/dashboard endpoint"
```

---

---

## 16. Descuento automático por nivel de fidelización

**Dificultad:** ⭐⭐⭐  
**Rama Git:** `feature/loyalty-discount`  
**Resumen:** Al crear una reserva, el sistema aplica automáticamente un descuento sobre el número mínimo de comensales requerido según el nivel de fidelización del usuario. El porcentaje de descuento queda reflejado en la respuesta.

> **Nota:** Requiere haber implementado la feature 11 (Sistema de fidelización) ya que depende del campo `loyaltyTier` en `Usuario`.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Crear | `model/Enum/DescuentoTier.java` |
| Modificar | `dto/response/ReservationResponseDTO.java` |
| Modificar | `service/ReservaService.java` |

---

### `model/Enum/DescuentoTier.java` — archivo nuevo completo

```java
package com.example.GastroTech.model.Enum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Porcentaje de descuento asociado a cada nivel de fidelizacion.
 * Uso: DescuentoTier.fromTier(LoyaltyTier.GOLD).getPorcentaje() -> 15
 */
@Getter
@RequiredArgsConstructor
public enum DescuentoTier {
    BRONZE(0),
    SILVER(5),
    GOLD(15);

    private final int porcentaje;

    public static DescuentoTier fromTier(LoyaltyTier tier) {
        return switch (tier) {
            case GOLD   -> GOLD;
            case SILVER -> SILVER;
            default     -> BRONZE;
        };
    }
}
```

---

### `dto/response/ReservationResponseDTO.java` — añadir campo de descuento

```java
// Reemplazar el record actual con este (añade discountApplied):
public record ReservationResponseDTO(
        Long id,
        String tableName,
        String customerName,
        LocalDateTime reservationDate,
        String status,
        int discountApplied   // porcentaje de descuento aplicado (0, 5 o 15)
) {}
```

---

### `service/ReservaService.java` — calcular y registrar descuento

```java
// Importar:
import com.example.GastroTech.model.Enum.DescuentoTier;

// En mapToResponseDTO(), añadir el calculo del descuento:
private ReservationResponseDTO mapToResponseDTO(Reserva reserva) {
    int descuento = 0;

    // Calcular descuento segun tier (solo si el usuario tiene el sistema de fidelizacion)
    if (reserva.getUsuario().getLoyaltyTier() != null) {
        descuento = DescuentoTier
                .fromTier(reserva.getUsuario().getLoyaltyTier())
                .getPorcentaje();
    }

    return new ReservationResponseDTO(
            reserva.getId(),
            "Mesa " + reserva.getMesa().getNumeroMesa(),
            reserva.getUsuario().getNombre(),
            reserva.getFechaReserva(),
            reserva.getEstado().name(),
            descuento
    );
}
```

---

### Commits sugeridos

```bash
git commit -m "feat(enum): add DescuentoTier enum with percentage per loyalty level"
git commit -m "feat(dto): add discountApplied field to ReservationResponseDTO"
git commit -m "feat(service): calculate and include loyalty discount in reservation response"
```

---

---

## 17. Exportación de reservas a CSV

**Dificultad:** ⭐⭐⭐  
**Rama Git:** `feature/csv-export`  
**Resumen:** El ADMIN puede descargar un fichero CSV con todas las reservas (o filtradas por fecha). No se usa ninguna librería externa: se construye el CSV manualmente con `StringBuilder` y se devuelve como `ResponseEntity<byte[]>`.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Modificar | `repository/ReservaRepository.java` |
| Crear | `service/ExportService.java` |
| Crear | `controller/ExportController.java` |

---

### `repository/ReservaRepository.java` — añadir query por rango de fechas

```java
// Reservas en un rango de fechas (para el filtro del export)
List<Reserva> findByFechaReservaBetweenOrderByFechaReservaAsc(
        LocalDateTime inicio,
        LocalDateTime fin
);
```

---

### `service/ExportService.java` — archivo nuevo completo

```java
package com.example.GastroTech.service;

import com.example.GastroTech.model.Entity.Reserva;
import com.example.GastroTech.repository.ReservaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExportService {

    private final ReservaRepository reservaRepository;

    @Transactional(readOnly = true)
    public byte[] exportReservasToCsv(LocalDateTime desde, LocalDateTime hasta) {
        List<Reserva> reservas = (desde != null && hasta != null)
                ? reservaRepository.findByFechaReservaBetweenOrderByFechaReservaAsc(
                        desde, hasta)
                : reservaRepository.findAll();

        StringBuilder csv = new StringBuilder();

        // Cabecera
        csv.append("ID,Mesa,Usuario,Email,Fecha Reserva,Comensales,Estado,Fecha Creacion\n");

        // Filas — los campos con coma se encierran entre comillas
        for (Reserva r : reservas) {
            csv.append(r.getId()).append(",")
               .append(r.getMesa().getNumeroMesa()).append(",")
               .append(escapeCsv(r.getUsuario().getNombre())).append(",")
               .append(r.getUsuario().getEmail()).append(",")
               .append(r.getFechaReserva()).append(",")
               .append(r.getNumeroPersonas()).append(",")
               .append(r.getEstado().name()).append(",")
               .append(r.getFechaCreacion()).append("\n");
        }

        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Escapa un campo que pueda contener comas o comillas. */
    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
```

---

### `controller/ExportController.java` — archivo nuevo completo

```java
package com.example.GastroTech.controller;

import com.example.GastroTech.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/admin/export")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Exportacion", description = "Exportacion de datos (solo ADMIN)")
@SecurityRequirement(name = "BearerAuth")
public class ExportController {

    private final ExportService exportService;

    @GetMapping(value = "/reservations", produces = "text/csv")
    @Operation(summary = "Exportar reservas a CSV. Parametros opcionales: desde, hasta (ISO date-time)")
    public ResponseEntity<byte[]> exportReservations(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta) {

        byte[] csvBytes = exportService.exportReservasToCsv(desde, hasta);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"reservas.csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csvBytes);
    }
}
```

> **Cómo probarlo en Postman:** enviar `GET /api/v1/admin/export/reservations` con JWT. En la respuesta pulsar *Save to file* para descargar el CSV.

---

### Commits sugeridos

```bash
git commit -m "feat(repository): add findByFechaReservaBetween query for date range filter"
git commit -m "feat(service): add ExportService with manual CSV generation"
git commit -m "feat(controller): add GET /admin/export/reservations endpoint returning CSV file"
```

---

---

## 18. Sistema de referidos

**Dificultad:** ⭐⭐⭐⭐  
**Rama Git:** `feature/referral-system`  
**Resumen:** Cada usuario tiene un código de referido único. Al registrarse, el nuevo usuario puede indicar ese código y ambos (referidor y referido) reciben puntos de fidelización como recompensa.

> **Nota:** Requiere la feature 11 (Sistema de fidelización) por el campo `loyaltyPoints`.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Modificar | `model/Entity/Usuario.java` |
| Modificar | `dto/request/RegisterRequestDTO.java` |
| Modificar | `repository/UsuarioRepository.java` |
| Modificar | `service/impl/AuthServiceImpl.java` |

---

### `model/Entity/Usuario.java` — añadir campo codigoReferido

```java
// Añadir después de los campos existentes:

/** Codigo unico que este usuario puede compartir con otros. */
@Column(unique = true)
private String codigoReferido;
```

---

### `repository/UsuarioRepository.java` — añadir búsqueda por código

```java
Optional<Usuario> findByCodigoReferido(String codigoReferido);
```

---

### `dto/request/RegisterRequestDTO.java` — añadir campo opcional

```java
public record RegisterRequestDTO(
        @NotBlank(message = "El nombre es obligatorio")
        String nombre,

        @Email(message = "El email no tiene un formato valido")
        @NotBlank(message = "El email es obligatorio")
        String email,

        @NotBlank(message = "La contrasena es obligatoria")
        @Size(min = 6, message = "La contrasena debe tener al menos 6 caracteres")
        String password,

        // Opcional: codigo del usuario que te refirio
        String codigoReferidoPor
) {}
```

---

### `service/impl/AuthServiceImpl.java` — generar código y aplicar recompensa al registrar

```java
// Importar:
import java.util.UUID;

// En el método register(), ANTES de usuarioRepository.save(usuario):

// Generar codigo de referido unico para el nuevo usuario
String codigoPropio = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
usuario.setCodigoReferido(codigoPropio);

// Si viene un codigo de referido, recompensar a ambos
if (request.codigoReferidoPor() != null && !request.codigoReferidoPor().isBlank()) {
    usuarioRepository.findByCodigoReferido(request.codigoReferidoPor())
            .ifPresent(referidor -> {
                // El referidor recibe 20 puntos
                referidor.setLoyaltyPoints(referidor.getLoyaltyPoints() + 20);
                actualizarTier(referidor);
                usuarioRepository.save(referidor);

                // El nuevo usuario empieza con 10 puntos de bienvenida
                usuario.setLoyaltyPoints(10);
            });
}

// ─── Método privado de apoyo (añadir al final de la clase) ───────────────────
private void actualizarTier(Usuario usuario) {
    int puntos = usuario.getLoyaltyPoints();
    if (puntos >= 100)     usuario.setLoyaltyTier(LoyaltyTier.GOLD);
    else if (puntos >= 50) usuario.setLoyaltyTier(LoyaltyTier.SILVER);
    else                   usuario.setLoyaltyTier(LoyaltyTier.BRONZE);
}
```

---

### `controller/UsuarioController.java` — añadir endpoint para ver el propio código

```java
@GetMapping("/me/referral-code")
@Operation(summary = "Ver mi codigo de referido para compartir")
public ResponseEntity<Map<String, String>> getReferralCode() {
    String email = SecurityContextHolder.getContext().getAuthentication().getName();
    Usuario usuario = usuarioService.buscarPorEmailOExcepcion(email);
    return ResponseEntity.ok(Map.of("codigoReferido", usuario.getCodigoReferido()));
}
```

---

### Commits sugeridos

```bash
git commit -m "feat(model): add codigoReferido unique field to Usuario"
git commit -m "feat(repository): add findByCodigoReferido query"
git commit -m "feat(dto): add optional codigoReferidoPor to RegisterRequestDTO"
git commit -m "feat(service): generate referral code on register and reward both users"
git commit -m "feat(controller): add GET /users/me/referral-code endpoint"
```

---

---

## 19. Límite de cancelaciones mensuales

**Dificultad:** ⭐⭐⭐  
**Rama Git:** `feature/monthly-cancellation-limit`  
**Resumen:** Un usuario no puede cancelar más de N reservas en el mismo mes natural. Si lo intenta, recibe un error 422. El contador se reinicia automáticamente el primer día de cada mes.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Modificar | `model/Entity/Usuario.java` |
| Modificar | `application.properties` |
| Crear | `exception/CancellationLimitException.java` |
| Modificar | `exception/GlobalExceptionHandler.java` |
| Modificar | `service/ReservaService.java` |
| Modificar | `GastroTechApplication.java` |
| Crear | `config/CancelacionScheduler.java` |

---

### `application.properties` — añadir propiedad

```properties
gastrotech.cancelaciones.max-mensuales=3
```

---

### `model/Entity/Usuario.java` — añadir campos de control mensual

```java
// Añadir después de los campos existentes:

@Column(nullable = false)
@Builder.Default
private int cancelacionesMesActual = 0;

// Mes en que se registraron esas cancelaciones (1-12) para saber cuándo resetear
@Builder.Default
private int mesCancelaciones = 0;
```

---

### `exception/CancellationLimitException.java` — archivo nuevo completo

```java
package com.example.GastroTech.exception;

public class CancellationLimitException extends RuntimeException {
    public CancellationLimitException(int limite) {
        super("Has alcanzado el limite de " + limite
                + " cancelaciones este mes. Podras cancelar de nuevo el proximo mes");
    }
}
```

---

### `exception/GlobalExceptionHandler.java` — añadir handler

```java
@ExceptionHandler(CancellationLimitException.class)
public ResponseEntity<ErrorResponse> handleCancellationLimit(CancellationLimitException ex) {
    return new ResponseEntity<>(
            new ErrorResponse("CANCELLATION_LIMIT", ex.getMessage(), LocalDateTime.now()),
            HttpStatus.UNPROCESSABLE_ENTITY);   // 422
}
```

---

### `service/ReservaService.java` — comprobar límite antes de cancelar

```java
// Importar:
import com.example.GastroTech.exception.CancellationLimitException;
import org.springframework.beans.factory.annotation.Value;

// Propiedad inyectada:
@Value("${gastrotech.cancelaciones.max-mensuales:3}")
private int maxCancelacionesMes;

// En cancelReservation(), ANTES de cambiar el estado, añadir para el propietario:
if (esPropietario) {
    int mesActual = LocalDateTime.now().getMonthValue();

    // Resetear contador si el mes cambio
    if (usuario.getMesCancelaciones() != mesActual) {
        usuario.setCancelacionesMesActual(0);
        usuario.setMesCancelaciones(mesActual);
    }

    if (usuario.getCancelacionesMesActual() >= maxCancelacionesMes) {
        throw new CancellationLimitException(maxCancelacionesMes);
    }

    usuario.setCancelacionesMesActual(usuario.getCancelacionesMesActual() + 1);
    usuarioRepository.save(usuario);
}
```

---

### `config/CancelacionScheduler.java` — archivo nuevo (reset automático el día 1)

```java
package com.example.GastroTech.config;

import com.example.GastroTech.model.Entity.Usuario;
import com.example.GastroTech.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CancelacionScheduler {

    private final UsuarioRepository usuarioRepository;

    /**
     * El dia 1 de cada mes a las 00:01 resetea el contador de cancelaciones
     * de todos los usuarios, como capa extra de seguridad.
     */
    @Scheduled(cron = "0 1 0 1 * *")
    @Transactional
    public void resetearContadoresMensuales() {
        List<Usuario> usuarios = usuarioRepository.findAll();
        for (Usuario u : usuarios) {
            u.setCancelacionesMesActual(0);
            usuarioRepository.save(u);
        }
        log.info("[Scheduler] Contadores de cancelaciones mensuales reseteados ({} usuarios)",
                usuarios.size());
    }
}
```

---

### Commits sugeridos

```bash
git commit -m "feat(config): add gastrotech.cancelaciones.max-mensuales property"
git commit -m "feat(model): add cancelacionesMesActual and mesCancelaciones to Usuario"
git commit -m "feat(exception): add CancellationLimitException mapped to 422"
git commit -m "feat(service): enforce monthly cancellation limit before soft delete"
git commit -m "feat(scheduler): add monthly reset job for cancellation counters"
```

---

---

## 20. Franja horaria flexible al reservar

**Dificultad:** ⭐⭐⭐  
**Rama Git:** `feature/flexible-timeslot`  
**Resumen:** El usuario en lugar de indicar una hora exacta puede escoger una franja predefinida (LUNCH, DINNER, BRUNCH). El sistema asigna automáticamente la hora de inicio estándar de esa franja y verifica disponibilidad en toda ella.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Crear | `model/Enum/FranjaHoraria.java` |
| Crear | `dto/request/FlexibleReservationRequestDTO.java` |
| Modificar | `service/ReservaService.java` |
| Modificar | `controller/ReservaController.java` |

---

### `model/Enum/FranjaHoraria.java` — archivo nuevo completo

```java
package com.example.GastroTech.model.Enum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import java.time.LocalTime;

/**
 * Franjas horarias predefinidas con su hora de inicio y duracion.
 */
@Getter
@RequiredArgsConstructor
public enum FranjaHoraria {
    BRUNCH (LocalTime.of(10, 0), 2),
    LUNCH  (LocalTime.of(13, 0), 2),
    DINNER (LocalTime.of(20, 0), 3);

    private final LocalTime horaInicio;
    private final int duracionHoras;
}
```

---

### `dto/request/FlexibleReservationRequestDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.request;

import com.example.GastroTech.model.Enum.FranjaHoraria;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record FlexibleReservationRequestDTO(
        @NotNull(message = "La mesa es obligatoria")
        Long tableId,

        @NotNull(message = "La fecha es obligatoria")
        @Future(message = "La fecha debe ser futura")
        LocalDate fecha,

        @NotNull(message = "La franja horaria es obligatoria")
        FranjaHoraria franja,

        @Min(value = 1, message = "Minimo 1 comensal")
        @Max(value = 12, message = "Maximo 12 comensales")
        int numberOfGuests
) {}
```

---

### `service/ReservaService.java` — añadir método para reserva con franja

```java
// Importar:
import com.example.GastroTech.dto.request.FlexibleReservationRequestDTO;
import com.example.GastroTech.model.Enum.FranjaHoraria;

@Transactional
public ReservationResponseDTO saveFlexibleReservation(
        FlexibleReservationRequestDTO dto, String username) {

    FranjaHoraria franja = dto.franja();

    // Construir el LocalDateTime con la hora estandar de la franja
    LocalDateTime fechaHora = dto.fecha()
            .atTime(franja.getHoraInicio());

    // Delegar al metodo principal reutilizando toda la logica de validacion
    // Para ello construimos un ReservationRequestDTO equivalente
    ReservationRequestDTO requestEquivalente = new ReservationRequestDTO(
            dto.tableId(),
            fechaHora,
            dto.numberOfGuests(),
            false,   // no es recurrente
            1        // semanas (ignorado si no es recurrente)
    );

    return saveReservation(requestEquivalente, username);
}
```

---

### `controller/ReservaController.java` — añadir endpoint

```java
// Importar:
import com.example.GastroTech.dto.request.FlexibleReservationRequestDTO;

@PostMapping("/flexible")
@Operation(summary = "Crear reserva eligiendo franja horaria (BRUNCH, LUNCH, DINNER)")
public ResponseEntity<ReservationResponseDTO> createFlexibleReservation(
        @Valid @RequestBody FlexibleReservationRequestDTO dto) {
    String username = getCurrentUsername();
    return new ResponseEntity<>(
            reservaService.saveFlexibleReservation(dto, username), HttpStatus.CREATED);
}
```

---

### Commits sugeridos

```bash
git commit -m "feat(enum): add FranjaHoraria enum with standard start times and durations"
git commit -m "feat(dto): add FlexibleReservationRequestDTO with date and timeslot"
git commit -m "feat(service): add saveFlexibleReservation delegating to existing validation"
git commit -m "feat(controller): add POST /reservations/flexible endpoint"
```

---

---

## 21. Eventos especiales en el local

**Dificultad:** ⭐⭐⭐⭐  
**Rama Git:** `feature/special-events`  
**Resumen:** El ADMIN puede crear eventos especiales (cenas de degustación, noches de música en vivo) que bloquean un conjunto de mesas durante una franja. Los usuarios pueden consultar los eventos activos antes de reservar.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Crear | `model/Entity/Evento.java` |
| Crear | `repository/EventoRepository.java` |
| Crear | `dto/request/EventoRequestDTO.java` |
| Crear | `dto/response/EventoResponseDTO.java` |
| Crear | `service/EventoService.java` |
| Modificar | `service/ReservaService.java` |
| Crear | `controller/EventoController.java` |

---

### `model/Entity/Evento.java` — archivo nuevo completo

```java
package com.example.GastroTech.model.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "evento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Evento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    @Column(nullable = false)
    private LocalDateTime inicio;

    @Column(nullable = false)
    private LocalDateTime fin;

    @Column(nullable = false)
    private int aforoMaximoEvento;

    /** Mesas reservadas para el evento. */
    @ManyToMany
    @JoinTable(
        name = "evento_mesa",
        joinColumns = @JoinColumn(name = "evento_id"),
        inverseJoinColumns = @JoinColumn(name = "mesa_id")
    )
    private List<Mesa> mesas;
}
```

---

### `repository/EventoRepository.java` — archivo nuevo completo

```java
package com.example.GastroTech.repository;

import com.example.GastroTech.model.Entity.Evento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventoRepository extends JpaRepository<Evento, Long> {

    // Eventos activos en una franja (para el calendario publico)
    List<Evento> findByFinAfterOrderByInicioAsc(LocalDateTime ahora);

    // Comprueba si una mesa esta ocupada por un evento en esa franja
    @Query("""
        SELECT COUNT(e) > 0 FROM Evento e JOIN e.mesas m
        WHERE m.id = :mesaId
        AND e.inicio < :fin
        AND e.fin > :inicio
        """)
    boolean isMesaOcupadaPorEvento(
            @Param("mesaId") Long mesaId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );
}
```

---

### `dto/request/EventoRequestDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public record EventoRequestDTO(
        @NotBlank(message = "El nombre del evento es obligatorio")
        String nombre,

        @Size(max = 500)
        String descripcion,

        @NotNull(message = "La hora de inicio es obligatoria")
        LocalDateTime inicio,

        @NotNull(message = "La hora de fin es obligatoria")
        LocalDateTime fin,

        @Min(value = 1, message = "El aforo debe ser positivo")
        int aforoMaximoEvento,

        @NotNull(message = "Debes indicar al menos una mesa")
        List<Long> mesaIds
) {}
```

---

### `dto/response/EventoResponseDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record EventoResponseDTO(
        Long id,
        String nombre,
        String descripcion,
        LocalDateTime inicio,
        LocalDateTime fin,
        int aforoMaximoEvento,
        List<Integer> numerosDeMesa
) {}
```

---

### `service/EventoService.java` — archivo nuevo completo

```java
package com.example.GastroTech.service;

import com.example.GastroTech.dto.request.EventoRequestDTO;
import com.example.GastroTech.dto.response.EventoResponseDTO;
import com.example.GastroTech.exception.BusinessException;
import com.example.GastroTech.exception.ResourceNotFoundException;
import com.example.GastroTech.model.Entity.Evento;
import com.example.GastroTech.model.Entity.Mesa;
import com.example.GastroTech.repository.EventoRepository;
import com.example.GastroTech.repository.MesaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventoService {

    private final EventoRepository eventoRepository;
    private final MesaRepository mesaRepository;

    @Transactional
    public EventoResponseDTO crearEvento(EventoRequestDTO dto) {
        if (!dto.fin().isAfter(dto.inicio())) {
            throw new BusinessException("La hora de fin debe ser posterior a la de inicio");
        }

        List<Mesa> mesas = dto.mesaIds().stream()
                .map(id -> mesaRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Mesa no encontrada con id: " + id)))
                .collect(Collectors.toList());

        Evento evento = Evento.builder()
                .nombre(dto.nombre())
                .descripcion(dto.descripcion())
                .inicio(dto.inicio())
                .fin(dto.fin())
                .aforoMaximoEvento(dto.aforoMaximoEvento())
                .mesas(mesas)
                .build();

        return mapToResponseDTO(eventoRepository.save(evento));
    }

    @Transactional(readOnly = true)
    public List<EventoResponseDTO> getEventosActivos() {
        return eventoRepository.findByFinAfterOrderByInicioAsc(LocalDateTime.now())
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void eliminarEvento(Long id) {
        if (!eventoRepository.existsById(id)) {
            throw new ResourceNotFoundException("Evento no encontrado con id: " + id);
        }
        eventoRepository.deleteById(id);
    }

    private EventoResponseDTO mapToResponseDTO(Evento e) {
        List<Integer> numeros = e.getMesas().stream()
                .map(Mesa::getNumeroMesa)
                .collect(Collectors.toList());
        return new EventoResponseDTO(
                e.getId(), e.getNombre(), e.getDescripcion(),
                e.getInicio(), e.getFin(), e.getAforoMaximoEvento(), numeros);
    }
}
```

---

### `service/ReservaService.java` — bloquear reservas si hay un evento en esa mesa

```java
// Añadir dependencia:
private final EventoRepository eventoRepository;

// En saveReservation(), ANTES de comprobar conflictos de reservas normales:
boolean mesaEnEvento = eventoRepository.isMesaOcupadaPorEvento(
        dto.tableId(),
        dto.reservationDate().minusHours(2),
        dto.reservationDate().plusHours(2)
);

if (mesaEnEvento) {
    throw new BusinessException(
            "La mesa esta reservada para un evento especial en esa franja horaria");
}
```

---

### `controller/EventoController.java` — archivo nuevo completo

```java
package com.example.GastroTech.controller;

import com.example.GastroTech.dto.request.EventoRequestDTO;
import com.example.GastroTech.dto.response.EventoResponseDTO;
import com.example.GastroTech.service.EventoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Tag(name = "Eventos especiales")
public class EventoController {

    private final EventoService eventoService;

    @GetMapping
    @Operation(summary = "Listar eventos activos (publico)")
    public ResponseEntity<List<EventoResponseDTO>> getEventosActivos() {
        return ResponseEntity.ok(eventoService.getEventosActivos());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Crear un evento especial (solo ADMIN)")
    public ResponseEntity<EventoResponseDTO> crearEvento(
            @Valid @RequestBody EventoRequestDTO dto) {
        return new ResponseEntity<>(eventoService.crearEvento(dto), HttpStatus.CREATED);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Eliminar un evento (solo ADMIN)")
    public ResponseEntity<Void> eliminarEvento(@PathVariable Long id) {
        eventoService.eliminarEvento(id);
        return ResponseEntity.noContent().build();
    }
}
```

---

### Commits sugeridos

```bash
git commit -m "feat(model): add Evento entity with ManyToMany relation to Mesa"
git commit -m "feat(repository): add EventoRepository with active events and overlap check"
git commit -m "feat(dto): add EventoRequestDTO and EventoResponseDTO"
git commit -m "feat(service): add EventoService with create, list and delete"
git commit -m "feat(service): block reservation if table is occupied by a special event"
git commit -m "feat(controller): add EventoController with public list and ADMIN management"
```

---

---

## 22. Mensajes internos entre usuario y ADMIN

**Dificultad:** ⭐⭐⭐⭐  
**Rama Git:** `feature/messaging`  
**Resumen:** Sistema de mensajería interna donde el usuario puede enviar consultas y el ADMIN puede responder. Los mensajes están vinculados a una reserva concreta o son generales. Cada usuario solo ve sus propios hilos.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Crear | `model/Enum/TipoMensaje.java` |
| Crear | `model/Entity/Mensaje.java` |
| Crear | `repository/MensajeRepository.java` |
| Crear | `dto/request/MensajeRequestDTO.java` |
| Crear | `dto/response/MensajeResponseDTO.java` |
| Crear | `service/MensajeService.java` |
| Crear | `controller/MensajeController.java` |

---

### `model/Enum/TipoMensaje.java` — archivo nuevo completo

```java
package com.example.GastroTech.model.Enum;

public enum TipoMensaje {
    CONSULTA,    // enviado por el usuario
    RESPUESTA    // enviado por el ADMIN
}
```

---

### `model/Entity/Mensaje.java` — archivo nuevo completo

```java
package com.example.GastroTech.model.Entity;

import com.example.GastroTech.model.Enum.TipoMensaje;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mensaje")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Mensaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1000)
    private String contenido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoMensaje tipo;

    @Column(nullable = false)
    private LocalDateTime fechaEnvio;

    private boolean leido;

    /** Usuario que origino el hilo (siempre el cliente, nunca el admin). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** Reserva a la que hace referencia (puede ser null si es consulta general). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserva_id")
    private Reserva reserva;
}
```

---

### `repository/MensajeRepository.java` — archivo nuevo completo

```java
package com.example.GastroTech.repository;

import com.example.GastroTech.model.Entity.Mensaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MensajeRepository extends JpaRepository<Mensaje, Long> {

    // Todos los mensajes de un usuario (su hilo completo)
    List<Mensaje> findByUsuarioIdOrderByFechaEnvioAsc(Long usuarioId);

    // Mensajes vinculados a una reserva concreta
    List<Mensaje> findByReservaIdOrderByFechaEnvioAsc(Long reservaId);

    // Mensajes no leidos de un usuario (para notificar al admin)
    long countByLeidoFalseAndTipo(com.example.GastroTech.model.Enum.TipoMensaje tipo);
}
```

---

### `dto/request/MensajeRequestDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MensajeRequestDTO(
        @NotBlank(message = "El contenido del mensaje no puede estar vacio")
        @Size(max = 1000, message = "Maximo 1000 caracteres")
        String contenido,

        // Opcional: si el mensaje va ligado a una reserva concreta
        Long reservaId
) {}
```

---

### `dto/response/MensajeResponseDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.response;

import java.time.LocalDateTime;

public record MensajeResponseDTO(
        Long id,
        String contenido,
        String tipo,
        LocalDateTime fechaEnvio,
        boolean leido,
        String emailUsuario,
        Long reservaId
) {}
```

---

### `service/MensajeService.java` — archivo nuevo completo

```java
package com.example.GastroTech.service;

import com.example.GastroTech.dto.request.MensajeRequestDTO;
import com.example.GastroTech.dto.response.MensajeResponseDTO;
import com.example.GastroTech.exception.BusinessException;
import com.example.GastroTech.exception.ResourceNotFoundException;
import com.example.GastroTech.model.Entity.Mensaje;
import com.example.GastroTech.model.Entity.Reserva;
import com.example.GastroTech.model.Entity.Usuario;
import com.example.GastroTech.model.Enum.RolUsuario;
import com.example.GastroTech.model.Enum.TipoMensaje;
import com.example.GastroTech.repository.MensajeRepository;
import com.example.GastroTech.repository.ReservaRepository;
import com.example.GastroTech.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MensajeService {

    private final MensajeRepository mensajeRepository;
    private final UsuarioRepository usuarioRepository;
    private final ReservaRepository reservaRepository;

    /** El usuario envia una consulta. */
    @Transactional
    public MensajeResponseDTO enviarConsulta(MensajeRequestDTO dto, String emailRemitente) {
        Usuario usuario = usuarioRepository.findByEmail(emailRemitente)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        Reserva reserva = null;
        if (dto.reservaId() != null) {
            reserva = reservaRepository.findById(dto.reservaId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Reserva no encontrada con id: " + dto.reservaId()));

            if (!reserva.getUsuario().getId().equals(usuario.getId())) {
                throw new BusinessException("Solo puedes consultar sobre tus propias reservas");
            }
        }

        Mensaje mensaje = Mensaje.builder()
                .contenido(dto.contenido())
                .tipo(TipoMensaje.CONSULTA)
                .fechaEnvio(LocalDateTime.now())
                .leido(false)
                .usuario(usuario)
                .reserva(reserva)
                .build();

        return mapToResponseDTO(mensajeRepository.save(mensaje));
    }

    /** El ADMIN responde a un hilo de usuario. */
    @Transactional
    public MensajeResponseDTO responder(Long usuarioDestinatarioId, MensajeRequestDTO dto) {
        Usuario destinatario = usuarioRepository.findById(usuarioDestinatarioId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario no encontrado con id: " + usuarioDestinatarioId));

        Reserva reserva = null;
        if (dto.reservaId() != null) {
            reserva = reservaRepository.findById(dto.reservaId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Reserva no encontrada con id: " + dto.reservaId()));
        }

        Mensaje respuesta = Mensaje.builder()
                .contenido(dto.contenido())
                .tipo(TipoMensaje.RESPUESTA)
                .fechaEnvio(LocalDateTime.now())
                .leido(false)
                .usuario(destinatario)
                .reserva(reserva)
                .build();

        return mapToResponseDTO(mensajeRepository.save(respuesta));
    }

    /** El usuario ve su hilo completo. */
    @Transactional(readOnly = true)
    public List<MensajeResponseDTO> getMiHilo(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // Marcar como leidos los mensajes de respuesta no leidos
        List<Mensaje> mensajes = mensajeRepository
                .findByUsuarioIdOrderByFechaEnvioAsc(usuario.getId());

        mensajes.stream()
                .filter(m -> m.getTipo() == TipoMensaje.RESPUESTA && !m.isLeido())
                .forEach(m -> {
                    m.setLeido(true);
                    mensajeRepository.save(m);
                });

        return mensajes.stream().map(this::mapToResponseDTO).collect(Collectors.toList());
    }

    /** El ADMIN ve el hilo completo de cualquier usuario. */
    @Transactional(readOnly = true)
    public List<MensajeResponseDTO> getHiloDeUsuario(Long usuarioId) {
        return mensajeRepository.findByUsuarioIdOrderByFechaEnvioAsc(usuarioId)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    private MensajeResponseDTO mapToResponseDTO(Mensaje m) {
        return new MensajeResponseDTO(
                m.getId(),
                m.getContenido(),
                m.getTipo().name(),
                m.getFechaEnvio(),
                m.isLeido(),
                m.getUsuario().getEmail(),
                m.getReserva() != null ? m.getReserva().getId() : null
        );
    }
}
```

---

### `controller/MensajeController.java` — archivo nuevo completo

```java
package com.example.GastroTech.controller;

import com.example.GastroTech.dto.request.MensajeRequestDTO;
import com.example.GastroTech.dto.response.MensajeResponseDTO;
import com.example.GastroTech.service.MensajeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
@Tag(name = "Mensajes internos")
@SecurityRequirement(name = "BearerAuth")
public class MensajeController {

    private final MensajeService mensajeService;

    @PostMapping
    @Operation(summary = "Enviar una consulta al local")
    public ResponseEntity<MensajeResponseDTO> enviarConsulta(
            @Valid @RequestBody MensajeRequestDTO dto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return new ResponseEntity<>(mensajeService.enviarConsulta(dto, email), HttpStatus.CREATED);
    }

    @GetMapping("/me")
    @Operation(summary = "Ver mi hilo de mensajes con el local")
    public ResponseEntity<List<MensajeResponseDTO>> getMiHilo() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(mensajeService.getMiHilo(email));
    }

    @PostMapping("/admin/reply/{usuarioId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "ADMIN responde al hilo de un usuario")
    public ResponseEntity<MensajeResponseDTO> responder(
            @PathVariable Long usuarioId,
            @Valid @RequestBody MensajeRequestDTO dto) {
        return new ResponseEntity<>(mensajeService.responder(usuarioId, dto), HttpStatus.CREATED);
    }

    @GetMapping("/admin/thread/{usuarioId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "ADMIN ve el hilo completo de un usuario")
    public ResponseEntity<List<MensajeResponseDTO>> getHiloDeUsuario(
            @PathVariable Long usuarioId) {
        return ResponseEntity.ok(mensajeService.getHiloDeUsuario(usuarioId));
    }
}
```

---

### Commits sugeridos

```bash
git commit -m "feat(enum): add TipoMensaje enum (CONSULTA, RESPUESTA)"
git commit -m "feat(model): add Mensaje entity linked to Usuario and optional Reserva"
git commit -m "feat(repository): add MensajeRepository with thread and unread queries"
git commit -m "feat(dto): add MensajeRequestDTO and MensajeResponseDTO"
git commit -m "feat(service): add MensajeService with send, reply, read-mark and list"
git commit -m "feat(controller): add MensajeController with user and admin endpoints"
```

---

---

## Resumen completo (features 13-22)

| # | Feature | Rama | Entidad nueva | Endpoints | Scheduler | Dificultad |
|---|---------|------|--------------|-----------|-----------|-----------|
| 13 | Check-in de reserva | `feature/checkin` | No | 1 | Sí | ⭐⭐⭐ |
| 14 | Preferencias del usuario | `feature/user-preferences` | Sí | 2 | No | ⭐⭐ |
| 15 | Dashboard de estadísticas | `feature/admin-dashboard` | No | 1 | No | ⭐⭐⭐ |
| 16 | Descuento por fidelización | `feature/loyalty-discount` | No | 0 | No | ⭐⭐⭐ |
| 17 | Exportación a CSV | `feature/csv-export` | No | 1 | No | ⭐⭐⭐ |
| 18 | Sistema de referidos | `feature/referral-system` | No | 1 | No | ⭐⭐⭐⭐ |
| 19 | Límite cancelaciones mensual | `feature/monthly-cancellation-limit` | No | 0 | Sí | ⭐⭐⭐ |
| 20 | Franja horaria flexible | `feature/flexible-timeslot` | No | 1 | No | ⭐⭐⭐ |
| 21 | Eventos especiales | `feature/special-events` | Sí | 3 | No | ⭐⭐⭐⭐ |
| 22 | Mensajes internos | `feature/messaging` | Sí | 4 | No | ⭐⭐⭐⭐ |

---

---

## 13. Confirmación manual de reservas por el ADMIN

**Dificultad:** ⭐⭐  
**Rama Git:** `feature/reservation-confirm`  
**Resumen:** Las reservas se crean como `PENDIENTE`. El ADMIN debe confirmarlas explícitamente antes de que pasen a `CONFIRMADA`. Añade un ciclo de vida real a la reserva y desbloquea la lógica del scheduler de completar reservas.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Modificar | `service/ReservaService.java` |
| Modificar | `controller/ReservaController.java` |

> **Nota:** `EstadoReserva` ya tiene el valor `CONFIRMADA`. No hace falta tocar el enum.

---

### `service/ReservaService.java` — añadir método `confirmarReserva()`

```java
@Transactional
public ReservationResponseDTO confirmarReserva(Long id) {
    Reserva reserva = reservaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                    "Reserva no encontrada con id: " + id));

    if (reserva.getEstado() != EstadoReserva.PENDIENTE) {
        throw new BusinessException(
                "Solo se pueden confirmar reservas en estado PENDIENTE. " +
                "Estado actual: " + reserva.getEstado());
    }

    reserva.setEstado(EstadoReserva.CONFIRMADA);
    return mapToResponseDTO(reservaRepository.save(reserva));
}
```

---

### `controller/ReservaController.java` — añadir endpoint

```java
// Importar al inicio:
import org.springframework.security.access.prepost.PreAuthorize;

@PatchMapping("/{id}/confirm")
@PreAuthorize("hasRole('ADMIN')")
@Operation(summary = "Confirmar una reserva pendiente (solo ADMIN)")
public ResponseEntity<ReservationResponseDTO> confirmarReserva(@PathVariable Long id) {
    return ResponseEntity.ok(reservaService.confirmarReserva(id));
}
```

---

### Ciclo de vida completo tras este cambio

```
PENDIENTE
   │
   │ PATCH /reservations/{id}/confirm  (ADMIN)
   ▼
CONFIRMADA
   │
   │ Scheduler (pasadas 2h desde la hora de reserva)
   ▼
COMPLETADA

PENDIENTE / CONFIRMADA
   │
   │ DELETE /reservations/{id}  (usuario o ADMIN)
   ▼
CANCELADA
```

---

### Commits sugeridos

```bash
git commit -m "feat(service): add confirmarReserva method with PENDIENTE state validation"
git commit -m "feat(controller): add PATCH /reservations/{id}/confirm endpoint for ADMIN"
```

---

---

## 14. Check-in digital con código de reserva

**Dificultad:** ⭐⭐⭐  
**Rama Git:** `feature/checkin`  
**Resumen:** Al crear una reserva se genera un código único (UUID). Cuando el cliente llega al restaurante, el ADMIN escanea o introduce ese código para hacer el check-in, cambiando el estado a `CHECKED_IN` y registrando la hora de llegada.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Modificar | `model/Entity/Reserva.java` |
| Modificar | `model/Enum/EstadoReserva.java` |
| Modificar | `dto/response/ReservationResponseDTO.java` |
| Modificar | `service/ReservaService.java` |
| Modificar | `controller/ReservaController.java` |

---

### `model/Enum/EstadoReserva.java` — añadir valor

```java
public enum EstadoReserva {
    PENDIENTE,
    CONFIRMADA,
    CHECKED_IN,   // <- nuevo
    COMPLETADA,
    CANCELADA
}
```

---

### `model/Entity/Reserva.java` — añadir campos después de `observaciones`

```java
// Importar al inicio:
import java.util.UUID;

// Código único generado al crear la reserva
@Column(nullable = false, unique = true, updatable = false)
private String codigoReserva;

// Fecha y hora real de llegada del cliente
private LocalDateTime fechaCheckIn;
```

---

### `dto/response/ReservationResponseDTO.java` — añadir campo `codigoReserva`

```java
// Reemplazar el record completo:
public record ReservationResponseDTO(
        Long id,
        String tableName,
        String customerName,
        LocalDateTime reservationDate,
        String status,
        String codigoReserva    // <- nuevo, visible solo para el propietario
) {}
```

---

### `service/ReservaService.java` — generar código al crear y añadir método de check-in

```java
// Importar al inicio:
import java.util.UUID;

// En saveReservation(), dentro del builder de Reserva, añadir:
.codigoReserva(UUID.randomUUID().toString())

// Nuevo método de check-in:
@Transactional
public ReservationResponseDTO checkIn(String codigoReserva) {
    Reserva reserva = reservaRepository.findByCodigoReserva(codigoReserva)
            .orElseThrow(() -> new ResourceNotFoundException(
                    "No se encontro ninguna reserva con el codigo: " + codigoReserva));

    if (reserva.getEstado() != EstadoReserva.CONFIRMADA) {
        throw new BusinessException(
                "El check-in solo es posible en reservas CONFIRMADAS. " +
                "Estado actual: " + reserva.getEstado());
    }

    reserva.setEstado(EstadoReserva.CHECKED_IN);
    reserva.setFechaCheckIn(LocalDateTime.now());
    return mapToResponseDTO(reservaRepository.save(reserva));
}

// Actualizar mapToResponseDTO para incluir el codigo:
private ReservationResponseDTO mapToResponseDTO(Reserva reserva) {
    return new ReservationResponseDTO(
            reserva.getId(),
            "Mesa " + reserva.getMesa().getNumeroMesa(),
            reserva.getUsuario().getNombre(),
            reserva.getFechaReserva(),
            reserva.getEstado().name(),
            reserva.getCodigoReserva()
    );
}
```

---

### `repository/ReservaRepository.java` — añadir query por código

```java
import java.util.Optional;

Optional<Reserva> findByCodigoReserva(String codigoReserva);
```

---

### `controller/ReservaController.java` — añadir endpoint de check-in

```java
@PatchMapping("/checkin/{codigo}")
@PreAuthorize("hasRole('ADMIN')")
@Operation(summary = "Registrar la llegada del cliente mediante su codigo de reserva")
public ResponseEntity<ReservationResponseDTO> checkIn(@PathVariable String codigo) {
    return ResponseEntity.ok(reservaService.checkIn(codigo));
}
```

---

### Commits sugeridos

```bash
git commit -m "feat(enum): add CHECKED_IN value to EstadoReserva"
git commit -m "feat(model): add codigoReserva and fechaCheckIn fields to Reserva"
git commit -m "feat(repository): add findByCodigoReserva query"
git commit -m "feat(dto): add codigoReserva field to ReservationResponseDTO"
git commit -m "feat(service): generate UUID on reservation creation and add checkIn method"
git commit -m "feat(controller): add PATCH /reservations/checkin/{codigo} endpoint"
```

---

---

## 15. Dashboard de estadísticas para el ADMIN

**Dificultad:** ⭐⭐⭐  
**Rama Git:** `feature/statistics`  
**Resumen:** Un único endpoint devuelve métricas globales del restaurante: reservas por estado, mesa más solicitada, hora punta, usuarios baneados, tasa de cancelación. Solo accesible por ADMIN.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Modificar | `repository/ReservaRepository.java` |
| Modificar | `repository/UsuarioRepository.java` |
| Crear | `dto/response/EstadisticasResponseDTO.java` |
| Crear | `service/EstadisticasService.java` |
| Crear | `controller/EstadisticasController.java` |

---

### `repository/ReservaRepository.java` — añadir queries de agregación

```java
// Importar al inicio:
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Contar reservas por estado
long countByEstado(EstadoReserva estado);

// Mesa con más reservas (devuelve el ID de la mesa)
@Query("SELECT r.mesa.id FROM Reserva r WHERE r.estado != 'CANCELADA' " +
       "GROUP BY r.mesa.id ORDER BY COUNT(r.id) DESC LIMIT 1")
Optional<Long> findMesaMasSolicitadaId();

// Hora con más reservas (0-23)
@Query("SELECT HOUR(r.fechaReserva) FROM Reserva r " +
       "WHERE r.estado != 'CANCELADA' " +
       "GROUP BY HOUR(r.fechaReserva) ORDER BY COUNT(r.id) DESC LIMIT 1")
Optional<Integer> findHoraPunta();

// Total de reservas en el último mes
@Query("SELECT COUNT(r) FROM Reserva r WHERE r.fechaCreacion >= :inicio")
long countReservasDesde(@Param("inicio") LocalDateTime inicio);
```

---

### `repository/UsuarioRepository.java` — añadir query

```java
// Importar al inicio:
import com.example.GastroTech.model.Enum.EstadoUsuario;

long countByStatus(EstadoUsuario status);
```

---

### `dto/response/EstadisticasResponseDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.response;

public record EstadisticasResponseDTO(
        long totalReservas,
        long reservasPendientes,
        long reservasConfirmadas,
        long reservasCompletadas,
        long reservasCanceladas,
        long reservasUltimoMes,
        Long mesaMasSolicitadaId,
        Integer horaPunta,
        long usuariosBaneados,
        double tasaCancelacion   // porcentaje sobre el total
) {}
```

---

### `service/EstadisticasService.java` — archivo nuevo completo

```java
package com.example.GastroTech.service;

import com.example.GastroTech.dto.response.EstadisticasResponseDTO;
import com.example.GastroTech.model.Enum.EstadoReserva;
import com.example.GastroTech.model.Enum.EstadoUsuario;
import com.example.GastroTech.repository.ReservaRepository;
import com.example.GastroTech.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EstadisticasService {

    private final ReservaRepository reservaRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public EstadisticasResponseDTO getEstadisticas() {
        long total       = reservaRepository.count();
        long pendientes  = reservaRepository.countByEstado(EstadoReserva.PENDIENTE);
        long confirmadas = reservaRepository.countByEstado(EstadoReserva.CONFIRMADA);
        long completadas = reservaRepository.countByEstado(EstadoReserva.COMPLETADA);
        long canceladas  = reservaRepository.countByEstado(EstadoReserva.CANCELADA);
        long ultimoMes   = reservaRepository.countReservasDesde(
                LocalDateTime.now().minusDays(30));
        long baneados    = usuarioRepository.countByStatus(EstadoUsuario.BANNED);

        Long mesaTop = reservaRepository.findMesaMasSolicitadaId().orElse(null);
        Integer horaPunta = reservaRepository.findHoraPunta().orElse(null);

        double tasa = total > 0
                ? Math.round((canceladas * 100.0 / total) * 10.0) / 10.0
                : 0.0;

        return new EstadisticasResponseDTO(
                total, pendientes, confirmadas, completadas,
                canceladas, ultimoMes, mesaTop, horaPunta, baneados, tasa);
    }
}
```

---

### `controller/EstadisticasController.java` — archivo nuevo completo

```java
package com.example.GastroTech.controller;

import com.example.GastroTech.dto.response.EstadisticasResponseDTO;
import com.example.GastroTech.service.EstadisticasService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Estadisticas", description = "Dashboard de metricas para ADMIN")
@SecurityRequirement(name = "BearerAuth")
public class EstadisticasController {

    private final EstadisticasService estadisticasService;

    @GetMapping
    @Operation(summary = "Obtener estadisticas globales del restaurante")
    public ResponseEntity<EstadisticasResponseDTO> getEstadisticas() {
        return ResponseEntity.ok(estadisticasService.getEstadisticas());
    }
}
```

---

### Commits sugeridos

```bash
git commit -m "feat(repository): add aggregation queries for statistics in ReservaRepository and UsuarioRepository"
git commit -m "feat(dto): add EstadisticasResponseDTO"
git commit -m "feat(service): add EstadisticasService with cancellation rate and peak hour"
git commit -m "feat(controller): add GET /api/v1/admin/stats endpoint"
```

---

---

## 16. Política de no-show

**Dificultad:** ⭐⭐⭐  
**Rama Git:** `feature/no-show`  
**Resumen:** Un scheduler detecta reservas `CONFIRMADA` cuya hora ya pasó sin que el cliente hiciera check-in. Las marca como `NO_SHOW` y añade 3 puntos de penalización al usuario (más grave que una cancelación tardía).

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Modificar | `model/Enum/EstadoReserva.java` |
| Modificar | `GastroTechApplication.java` |
| Modificar | `repository/ReservaRepository.java` |
| Crear o modificar | `config/ReservaScheduler.java` |

> **Nota:** Requiere que el sistema de penalización base (del enunciado) y el check-in (feature 14) estén implementados, ya que `NO_SHOW` implica que no hubo `CHECKED_IN`.

---

### `model/Enum/EstadoReserva.java` — añadir valor

```java
public enum EstadoReserva {
    PENDIENTE,
    CONFIRMADA,
    CHECKED_IN,
    COMPLETADA,
    CANCELADA,
    NO_SHOW    // <- nuevo
}
```

---

### `GastroTechApplication.java` — asegurar @EnableScheduling

```java
@SpringBootApplication
@EnableScheduling
public class GastroTechApplication { ... }
```

---

### `repository/ReservaRepository.java` — reutilizar query existente

```java
// La query findReservasActivasCaducadas ya sirve.
// Llamarla con EstadoReserva.CONFIRMADA y limite = ahora (sin restar horas,
// porque si ya pasó la hora y sigue CONFIRMADA = no se presentó)
```

---

### `config/ReservaScheduler.java` — añadir método (o crear el archivo si no existe)

```java
// Importar al inicio del fichero:
import com.example.GastroTech.model.Enum.EstadoUsuario;

/**
 * Detecta reservas CONFIRMADAS cuya hora ya pasó sin check-in.
 * Las marca como NO_SHOW y penaliza al usuario con 3 puntos.
 * Se ejecuta cada 10 minutos.
 */
@Scheduled(fixedRate = 600_000)
@Transactional
public void marcarNoShows() {
    // Sin restar horas: si la hora ya pasó y sigue CONFIRMADA = no-show
    List<Reserva> noShows = reservaRepository
            .findReservasActivasCaducadas(EstadoReserva.CONFIRMADA, LocalDateTime.now());

    if (noShows.isEmpty()) return;

    for (Reserva reserva : noShows) {
        reserva.setEstado(EstadoReserva.NO_SHOW);
        reservaRepository.save(reserva);

        Usuario usuario = reserva.getUsuario();
        int nuevosPuntos = usuario.getPenalizationPoints() + 3;
        usuario.setPenalizationPoints(nuevosPuntos);

        if (nuevosPuntos > 6) {
            usuario.setStatus(EstadoUsuario.BANNED);
            log.warn("[No-show] Usuario {} baneado por acumular {} puntos",
                    usuario.getEmail(), nuevosPuntos);
        }

        usuarioRepository.save(usuario);
        log.info("[No-show] Reserva {} marcada como NO_SHOW. Puntos de {}: {}",
                reserva.getId(), usuario.getEmail(), nuevosPuntos);
    }
}
```

---

### Commits sugeridos

```bash
git commit -m "feat(enum): add NO_SHOW value to EstadoReserva"
git commit -m "feat(scheduler): add no-show detection with 3 penalty points every 10 minutes"
```

---

---

## 17. Búsqueda y filtrado avanzado de reservas

**Dificultad:** ⭐⭐⭐  
**Rama Git:** `feature/reservation-search`  
**Resumen:** El ADMIN puede buscar reservas filtrando por cualquier combinación de: estado, mesa, usuario, y rango de fechas. Usa `JpaSpecificationExecutor` para construir la query dinámicamente sin escribir múltiples métodos en el repositorio.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Modificar | `repository/ReservaRepository.java` |
| Crear | `dto/request/ReservaFiltroDTO.java` |
| Crear | `service/ReservaSpecification.java` |
| Modificar | `service/ReservaService.java` |
| Modificar | `controller/ReservaController.java` |

---

### `repository/ReservaRepository.java` — extender de JpaSpecificationExecutor

```java
// Importar al inicio:
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

// Cambiar la firma de la interfaz:
public interface ReservaRepository extends JpaRepository<Reserva, Long>,
        JpaSpecificationExecutor<Reserva> {
    // ... métodos existentes sin cambios
}
```

---

### `dto/request/ReservaFiltroDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.request;

import com.example.GastroTech.model.Enum.EstadoReserva;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;

// No es un @RequestBody sino parámetros de query string, por eso no usa @NotNull
public record ReservaFiltroDTO(
        EstadoReserva estado,          // ?estado=PENDIENTE
        Long mesaId,                   // ?mesaId=3
        Long usuarioId,                // ?usuarioId=7

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime fechaDesde,      // ?fechaDesde=2025-06-01T00:00

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime fechaHasta       // ?fechaHasta=2025-06-30T23:59
) {}
```

---

### `service/ReservaSpecification.java` — archivo nuevo completo

```java
package com.example.GastroTech.service;

import com.example.GastroTech.dto.request.ReservaFiltroDTO;
import com.example.GastroTech.model.Entity.Reserva;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class ReservaSpecification {

    private ReservaSpecification() {}   // clase de utilidad, no instanciar

    public static Specification<Reserva> conFiltros(ReservaFiltroDTO filtro) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filtro.estado() != null) {
                predicates.add(cb.equal(root.get("estado"), filtro.estado()));
            }
            if (filtro.mesaId() != null) {
                predicates.add(cb.equal(root.get("mesa").get("id"), filtro.mesaId()));
            }
            if (filtro.usuarioId() != null) {
                predicates.add(cb.equal(root.get("usuario").get("id"), filtro.usuarioId()));
            }
            if (filtro.fechaDesde() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("fechaReserva"), filtro.fechaDesde()));
            }
            if (filtro.fechaHasta() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("fechaReserva"), filtro.fechaHasta()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
```

---

### `service/ReservaService.java` — añadir método de búsqueda

```java
// Importar al inicio:
import com.example.GastroTech.dto.request.ReservaFiltroDTO;

@Transactional(readOnly = true)
public List<ReservationResponseDTO> buscarConFiltros(ReservaFiltroDTO filtro) {
    return reservaRepository
            .findAll(ReservaSpecification.conFiltros(filtro))
            .stream()
            .map(this::mapToResponseDTO)
            .collect(Collectors.toList());
}
```

---

### `controller/ReservaController.java` — añadir endpoint con @ModelAttribute

```java
// Importar al inicio:
import com.example.GastroTech.dto.request.ReservaFiltroDTO;
import org.springframework.web.bind.annotation.ModelAttribute;

@GetMapping("/search")
@PreAuthorize("hasRole('ADMIN')")
@Operation(summary = "Buscar reservas con filtros opcionales (solo ADMIN)")
public ResponseEntity<List<ReservationResponseDTO>> buscar(
        @ModelAttribute ReservaFiltroDTO filtro) {
    return ResponseEntity.ok(reservaService.buscarConFiltros(filtro));
}
```

> Se usa `@ModelAttribute` en lugar de `@RequestBody` porque los filtros llegan como parámetros de query string (`?estado=PENDIENTE&mesaId=3`), no como JSON en el cuerpo.

---

### Commits sugeridos

```bash
git commit -m "feat(repository): extend ReservaRepository with JpaSpecificationExecutor"
git commit -m "feat(dto): add ReservaFiltroDTO for optional search parameters"
git commit -m "feat(service): add ReservaSpecification with dynamic predicate builder"
git commit -m "feat(service): add buscarConFiltros method to ReservaService"
git commit -m "feat(controller): add GET /reservations/search with query string filters"
```

---

---

## 18. Exportación de reservas en CSV

**Dificultad:** ⭐⭐⭐  
**Rama Git:** `feature/csv-export`  
**Resumen:** El ADMIN puede descargar todas las reservas (o las filtradas por mes) como un fichero `.csv`. No requiere librerías externas: se construye manualmente con `StringBuilder` y se devuelve como `text/csv`.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Modificar | `service/ReservaService.java` |
| Modificar | `controller/ReservaController.java` |

---

### `service/ReservaService.java` — añadir método de exportación

```java
// Importar al inicio:
import java.time.format.DateTimeFormatter;

@Transactional(readOnly = true)
public String exportarReservasComoCSV(int anio, int mes) {
    LocalDateTime inicio = LocalDateTime.of(anio, mes, 1, 0, 0);
    LocalDateTime fin    = inicio.plusMonths(1).minusSeconds(1);

    List<Reserva> reservas = reservaRepository
            .findReservasEnRango(inicio, fin);   // ver query más abajo

    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    StringBuilder sb = new StringBuilder();

    // Cabecera
    sb.append("ID,Mesa,Cliente,Email,Fecha,Comensales,Estado\n");

    // Filas
    for (Reserva r : reservas) {
        sb.append(r.getId()).append(",")
          .append("Mesa ").append(r.getMesa().getNumeroMesa()).append(",")
          .append(r.getUsuario().getNombre()).append(",")
          .append(r.getUsuario().getEmail()).append(",")
          .append(r.getFechaReserva().format(fmt)).append(",")
          .append(r.getNumeroPersonas()).append(",")
          .append(r.getEstado().name()).append("\n");
    }

    return sb.toString();
}
```

---

### `repository/ReservaRepository.java` — añadir query de rango

```java
@Query("SELECT r FROM Reserva r WHERE r.fechaReserva BETWEEN :inicio AND :fin " +
       "ORDER BY r.fechaReserva ASC")
List<Reserva> findReservasEnRango(@Param("inicio") LocalDateTime inicio,
                                   @Param("fin") LocalDateTime fin);
```

---

### `controller/ReservaController.java` — añadir endpoint de exportación

```java
// Importar al inicio:
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@GetMapping("/export")
@PreAuthorize("hasRole('ADMIN')")
@Operation(summary = "Exportar reservas de un mes en formato CSV (solo ADMIN)")
public ResponseEntity<byte[]> exportarCSV(
        @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") int anio,
        @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") int mes) {

    String csv = reservaService.exportarReservasComoCSV(anio, mes);
    String nombreFichero = "reservas-" + anio + "-" + String.format("%02d", mes) + ".csv";

    return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + nombreFichero + "\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
}
```

---

### Commits sugeridos

```bash
git commit -m "feat(repository): add findReservasEnRango query for date range filtering"
git commit -m "feat(service): add exportarReservasComoCSV with manual CSV builder"
git commit -m "feat(controller): add GET /reservations/export?anio=2025&mes=6 endpoint"
```

---

---

## 19. Cierre temporal del restaurante (días festivos)

**Dificultad:** ⭐⭐⭐  
**Rama Git:** `feature/restaurant-closure`  
**Resumen:** El ADMIN puede marcar días enteros como cerrados (festivos, vacaciones). Al intentar crear una reserva en una fecha marcada como cerrada, la API devuelve un error descriptivo.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Crear | `model/Entity/CierreTemporal.java` |
| Crear | `repository/CierreTemporalRepository.java` |
| Crear | `dto/request/CierreTemporalRequestDTO.java` |
| Crear | `dto/response/CierreTemporalResponseDTO.java` |
| Crear | `service/CierreTemporalService.java` |
| Crear | `controller/CierreTemporalController.java` |
| Modificar | `service/ReservaService.java` |

---

### `model/Entity/CierreTemporal.java` — archivo nuevo completo

```java
package com.example.GastroTech.model.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "cierre_temporal")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CierreTemporal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate fecha;

    @Column(nullable = false, length = 200)
    private String motivo;

    @Column(nullable = false)
    private String creadoPor;
}
```

---

### `repository/CierreTemporalRepository.java` — archivo nuevo completo

```java
package com.example.GastroTech.repository;

import com.example.GastroTech.model.Entity.CierreTemporal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface CierreTemporalRepository extends JpaRepository<CierreTemporal, Long> {
    boolean existsByFecha(LocalDate fecha);
    List<CierreTemporal> findByFechaGreaterThanEqualOrderByFechaAsc(LocalDate desde);
}
```

---

### `dto/request/CierreTemporalRequestDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CierreTemporalRequestDTO(
        @NotNull(message = "La fecha de cierre es obligatoria")
        @Future(message = "Solo se pueden programar cierres futuros")
        LocalDate fecha,

        @NotBlank(message = "El motivo es obligatorio")
        @Size(max = 200, message = "Maximo 200 caracteres")
        String motivo
) {}
```

---

### `dto/response/CierreTemporalResponseDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.response;

import java.time.LocalDate;

public record CierreTemporalResponseDTO(
        Long id,
        LocalDate fecha,
        String motivo,
        String creadoPor
) {}
```

---

### `service/CierreTemporalService.java` — archivo nuevo completo

```java
package com.example.GastroTech.service;

import com.example.GastroTech.dto.request.CierreTemporalRequestDTO;
import com.example.GastroTech.dto.response.CierreTemporalResponseDTO;
import com.example.GastroTech.exception.BusinessException;
import com.example.GastroTech.exception.ResourceNotFoundException;
import com.example.GastroTech.model.Entity.CierreTemporal;
import com.example.GastroTech.repository.CierreTemporalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CierreTemporalService {

    private final CierreTemporalRepository cierreRepository;

    @Transactional
    public CierreTemporalResponseDTO crearCierre(CierreTemporalRequestDTO dto, String admin) {
        if (cierreRepository.existsByFecha(dto.fecha())) {
            throw new BusinessException(
                    "Ya existe un cierre registrado para el dia: " + dto.fecha());
        }

        CierreTemporal cierre = CierreTemporal.builder()
                .fecha(dto.fecha())
                .motivo(dto.motivo())
                .creadoPor(admin)
                .build();

        return mapToResponseDTO(cierreRepository.save(cierre));
    }

    @Transactional
    public void eliminarCierre(Long id) {
        if (!cierreRepository.existsById(id)) {
            throw new ResourceNotFoundException("Cierre no encontrado con id: " + id);
        }
        cierreRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<CierreTemporalResponseDTO> getCierresFuturos() {
        return cierreRepository
                .findByFechaGreaterThanEqualOrderByFechaAsc(LocalDate.now())
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    public boolean estaFechaCerrada(LocalDate fecha) {
        return cierreRepository.existsByFecha(fecha);
    }

    private CierreTemporalResponseDTO mapToResponseDTO(CierreTemporal c) {
        return new CierreTemporalResponseDTO(c.getId(), c.getFecha(),
                c.getMotivo(), c.getCreadoPor());
    }
}
```

---

### `service/ReservaService.java` — añadir comprobación en `saveReservation()`

```java
// Añadir dependencia (Lombok la inyecta):
private final CierreTemporalService cierreTemporalService;

// En saveReservation(), después de la validación de fecha futura:
if (cierreTemporalService.estaFechaCerrada(dto.reservationDate().toLocalDate())) {
    throw new BusinessException(
            "El restaurante esta cerrado el dia " + dto.reservationDate().toLocalDate()
            + ". Consulta los dias de apertura disponibles.");
}
```

---

### `controller/CierreTemporalController.java` — archivo nuevo completo

```java
package com.example.GastroTech.controller;

import com.example.GastroTech.dto.request.CierreTemporalRequestDTO;
import com.example.GastroTech.dto.response.CierreTemporalResponseDTO;
import com.example.GastroTech.service.CierreTemporalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/closures")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Cierres temporales", description = "Gestion de dias de cierre (solo ADMIN)")
@SecurityRequirement(name = "BearerAuth")
public class CierreTemporalController {

    private final CierreTemporalService cierreService;

    @PostMapping
    @Operation(summary = "Registrar un dia de cierre del restaurante")
    public ResponseEntity<CierreTemporalResponseDTO> crearCierre(
            @Valid @RequestBody CierreTemporalRequestDTO dto) {
        String admin = SecurityContextHolder.getContext().getAuthentication().getName();
        return new ResponseEntity<>(cierreService.crearCierre(dto, admin), HttpStatus.CREATED);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar un cierre programado")
    public ResponseEntity<Void> eliminarCierre(@PathVariable Long id) {
        cierreService.eliminarCierre(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "Listar todos los cierres futuros programados")
    public ResponseEntity<List<CierreTemporalResponseDTO>> getCierres() {
        return ResponseEntity.ok(cierreService.getCierresFuturos());
    }
}
```

---

### Commits sugeridos

```bash
git commit -m "feat(model): add CierreTemporal entity"
git commit -m "feat(repository): add CierreTemporalRepository with date queries"
git commit -m "feat(dto): add CierreTemporalRequestDTO and CierreTemporalResponseDTO"
git commit -m "feat(service): add CierreTemporalService and inject closure check in ReservaService"
git commit -m "feat(controller): add CierreTemporalController under /admin/closures"
```

---

---

## 20. Límite mensual de cancelaciones tardías

**Dificultad:** ⭐⭐⭐⭐  
**Rama Git:** `feature/monthly-cancellation-limit`  
**Resumen:** Además de los puntos acumulados, un usuario no puede hacer más de 2 cancelaciones tardías en el mismo mes natural. Al tercer intento en el mismo mes, se le bloquea directamente aunque no supere los 6 puntos totales.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Modificar | `model/Entity/Usuario.java` |
| Modificar | `service/ReservaService.java` |
| Modificar | `GastroTechApplication.java` |
| Crear o modificar | `config/PenalizacionScheduler.java` |

---

### `model/Entity/Usuario.java` — añadir campos después de `ultimaPenalizacion`

```java
// Importar al inicio:
import java.time.YearMonth;

// Número de cancelaciones tardías en el mes actual
@Column(nullable = false)
@Builder.Default
private int cancelacionesTardiasMes = 0;

// Mes al que corresponde el contador (formato "2025-06")
private String mesCancelaciones;
```

---

### `service/ReservaService.java` — actualizar `aplicarPenalizacionSiEsTardia()`

```java
// Importar al inicio:
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

private void aplicarPenalizacionSiEsTardia(Reserva reserva, Usuario usuario) {
    LocalDateTime limite = reserva.getFechaReserva().minusHours(2);

    if (LocalDateTime.now().isAfter(limite)) {
        // Gestionar contador mensual
        String mesActual = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));

        if (!mesActual.equals(usuario.getMesCancelaciones())) {
            // Nuevo mes: resetear el contador
            usuario.setCancelacionesTardiasMes(0);
            usuario.setMesCancelaciones(mesActual);
        }

        int cancelacionesMes = usuario.getCancelacionesTardiasMes() + 1;
        usuario.setCancelacionesTardiasMes(cancelacionesMes);

        // Ban directo si supera el limite mensual (independientemente de puntos totales)
        if (cancelacionesMes > 2) {
            usuario.setStatus(EstadoUsuario.BANNED);
            throw new BusinessException(
                    "Cancelacion rechazada: has superado el limite de 2 cancelaciones " +
                    "tardias en el mes de " + mesActual + ". Tu cuenta ha sido suspendida.");
        }

        // Lógica de puntos existente
        int nuevosPuntos = usuario.getPenalizationPoints() + 2;
        usuario.setPenalizationPoints(nuevosPuntos);
        usuario.setUltimaPenalizacion(LocalDateTime.now());

        if (nuevosPuntos > 6) {
            usuario.setStatus(EstadoUsuario.BANNED);
        }

        usuarioRepository.save(usuario);
    }
}
```

---

### `config/PenalizacionScheduler.java` — añadir reset mensual del contador

```java
/**
 * El primer dia de cada mes a las 00:05 resetea el contador mensual
 * de todos los usuarios. Asi el limite de 2/mes es por mes natural.
 */
@Scheduled(cron = "0 5 0 1 * *")
@Transactional
public void resetearContadorMensual() {
    String mesAnterior = YearMonth.now().minusMonths(1)
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));

    // Buscar usuarios cuyo contador pertenece al mes anterior
    List<Usuario> aResetear = usuarioRepository
            .findByMesCancelaciones(mesAnterior);

    for (Usuario u : aResetear) {
        u.setCancelacionesTardiasMes(0);
        u.setMesCancelaciones(null);
        usuarioRepository.save(u);
    }

    log.info("[Scheduler] Contador mensual reseteado en {} usuarios", aResetear.size());
}
```

---

### `repository/UsuarioRepository.java` — añadir query para el reset

```java
List<Usuario> findByMesCancelaciones(String mes);
```

---

### Commits sugeridos

```bash
git commit -m "feat(model): add cancelacionesTardiasMes and mesCancelaciones to Usuario"
git commit -m "feat(service): add monthly cancellation limit with direct ban on third attempt"
git commit -m "feat(repository): add findByMesCancelaciones for monthly reset"
git commit -m "feat(scheduler): add monthly counter reset on first day of each month"
```

---

---

## 21. Sistema de notificaciones internas

**Dificultad:** ⭐⭐⭐⭐  
**Rama Git:** `feature/notifications`  
**Resumen:** Un scheduler crea notificaciones automáticas 24 horas antes de cada reserva confirmada. El usuario puede leerlas y marcarlas como leídas. Las notificaciones también se generan cuando alguien transfiere una reserva o cuando se le sube de la lista de espera.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Crear | `model/Enum/TipoNotificacion.java` |
| Crear | `model/Entity/Notificacion.java` |
| Crear | `repository/NotificacionRepository.java` |
| Crear | `dto/response/NotificacionResponseDTO.java` |
| Crear | `service/NotificacionService.java` |
| Crear | `controller/NotificacionController.java` |
| Modificar | `GastroTechApplication.java` |
| Crear o modificar | `config/ReservaScheduler.java` |

---

### `model/Enum/TipoNotificacion.java` — archivo nuevo completo

```java
package com.example.GastroTech.model.Enum;

public enum TipoNotificacion {
    RECORDATORIO_RESERVA,
    RESERVA_TRANSFERIDA,
    LISTA_ESPERA_DISPONIBLE,
    RESERVA_CANCELADA_POR_ADMIN,
    CUENTA_BANEADA
}
```

---

### `model/Entity/Notificacion.java` — archivo nuevo completo

```java
package com.example.GastroTech.model.Entity;

import com.example.GastroTech.model.Enum.TipoNotificacion;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notificacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoNotificacion tipo;

    @Column(nullable = false, length = 300)
    private String mensaje;

    @Column(nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(nullable = false)
    @Builder.Default
    private boolean leida = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
}
```

---

### `repository/NotificacionRepository.java` — archivo nuevo completo

```java
package com.example.GastroTech.repository;

import com.example.GastroTech.model.Entity.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    List<Notificacion> findByUsuarioIdOrderByFechaCreacionDesc(Long usuarioId);

    long countByUsuarioIdAndLeidaFalse(Long usuarioId);

    // Reservas confirmadas cuya fecha es en 24-25 horas (ventana de 1h para no duplicar)
    @Query("""
        SELECT r FROM Reserva r
        WHERE r.estado = 'CONFIRMADA'
        AND r.fechaReserva BETWEEN :desde AND :hasta
        AND NOT EXISTS (
            SELECT n FROM Notificacion n
            WHERE n.usuario.id = r.usuario.id
            AND n.tipo = 'RECORDATORIO_RESERVA'
            AND n.fechaCreacion >= :haceUnaHora
        )
        """)
    List<com.example.GastroTech.model.Entity.Reserva> findReservasParaRecordatorio(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            @Param("haceUnaHora") LocalDateTime haceUnaHora);
}
```

---

### `dto/response/NotificacionResponseDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.response;

import java.time.LocalDateTime;

public record NotificacionResponseDTO(
        Long id,
        String tipo,
        String mensaje,
        LocalDateTime fechaCreacion,
        boolean leida
) {}
```

---

### `service/NotificacionService.java` — archivo nuevo completo

```java
package com.example.GastroTech.service;

import com.example.GastroTech.dto.response.NotificacionResponseDTO;
import com.example.GastroTech.exception.ResourceNotFoundException;
import com.example.GastroTech.model.Entity.Notificacion;
import com.example.GastroTech.model.Entity.Usuario;
import com.example.GastroTech.model.Enum.TipoNotificacion;
import com.example.GastroTech.repository.NotificacionRepository;
import com.example.GastroTech.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificacionService {

    private final NotificacionRepository notificacionRepository;
    private final UsuarioRepository usuarioRepository;

    /** Crea una notificacion para un usuario. Llamado desde otros servicios. */
    @Transactional
    public void crearNotificacion(Usuario usuario, TipoNotificacion tipo, String mensaje) {
        Notificacion notificacion = Notificacion.builder()
                .usuario(usuario)
                .tipo(tipo)
                .mensaje(mensaje)
                .fechaCreacion(LocalDateTime.now())
                .leida(false)
                .build();
        notificacionRepository.save(notificacion);
    }

    @Transactional(readOnly = true)
    public List<NotificacionResponseDTO> getMisNotificaciones(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        return notificacionRepository
                .findByUsuarioIdOrderByFechaCreacionDesc(usuario.getId())
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void marcarComoLeida(Long notificacionId, String email) {
        Notificacion notif = notificacionRepository.findById(notificacionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notificacion no encontrada con id: " + notificacionId));

        if (!notif.getUsuario().getEmail().equals(email)) {
            throw new com.example.GastroTech.exception.BusinessException(
                    "No puedes marcar como leida una notificacion que no es tuya");
        }

        notif.setLeida(true);
        notificacionRepository.save(notif);
    }

    @Transactional(readOnly = true)
    public long contarNoLeidas(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        return notificacionRepository.countByUsuarioIdAndLeidaFalse(usuario.getId());
    }

    private NotificacionResponseDTO mapToResponseDTO(Notificacion n) {
        return new NotificacionResponseDTO(
                n.getId(), n.getTipo().name(), n.getMensaje(),
                n.getFechaCreacion(), n.isLeida());
    }
}
```

---

### `config/ReservaScheduler.java` — añadir método de recordatorios

```java
// Añadir dependencia:
private final NotificacionService notificacionService;
private final NotificacionRepository notificacionRepository;

/**
 * Cada hora revisa si hay reservas confirmadas dentro de 24-25 horas
 * y crea un recordatorio si todavia no se ha enviado.
 */
@Scheduled(fixedRate = 3_600_000)   // cada hora
@Transactional
public void enviarRecordatorios() {
    LocalDateTime desde      = LocalDateTime.now().plusHours(24);
    LocalDateTime hasta      = LocalDateTime.now().plusHours(25);
    LocalDateTime haceUnaHora = LocalDateTime.now().minusHours(1);

    List<Reserva> reservas = notificacionRepository
            .findReservasParaRecordatorio(desde, hasta, haceUnaHora);

    for (Reserva reserva : reservas) {
        String mensaje = String.format(
                "Recordatorio: tienes una reserva manana a las %s en Mesa %d para %d personas.",
                reserva.getFechaReserva().toLocalTime(),
                reserva.getMesa().getNumeroMesa(),
                reserva.getNumeroPersonas()
        );
        notificacionService.crearNotificacion(
                reserva.getUsuario(),
                TipoNotificacion.RECORDATORIO_RESERVA,
                mensaje
        );
        log.info("[Scheduler] Recordatorio enviado a {}", reserva.getUsuario().getEmail());
    }
}
```

---

### `controller/NotificacionController.java` — archivo nuevo completo

```java
package com.example.GastroTech.controller;

import com.example.GastroTech.dto.response.NotificacionResponseDTO;
import com.example.GastroTech.service.NotificacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notificaciones")
@SecurityRequirement(name = "BearerAuth")
public class NotificacionController {

    private final NotificacionService notificacionService;

    @GetMapping("/me")
    @Operation(summary = "Ver todas mis notificaciones")
    public ResponseEntity<List<NotificacionResponseDTO>> getMisNotificaciones() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(notificacionService.getMisNotificaciones(email));
    }

    @GetMapping("/me/unread-count")
    @Operation(summary = "Contar notificaciones no leidas")
    public ResponseEntity<Map<String, Long>> contarNoLeidas() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(Map.of("noLeidas", notificacionService.contarNoLeidas(email)));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Marcar una notificacion como leida")
    public ResponseEntity<Void> marcarLeida(@PathVariable Long id) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        notificacionService.marcarComoLeida(id, email);
        return ResponseEntity.noContent().build();
    }
}
```

---

### Commits sugeridos

```bash
git commit -m "feat(enum): add TipoNotificacion enum"
git commit -m "feat(model): add Notificacion entity"
git commit -m "feat(repository): add NotificacionRepository with unread count and reminder query"
git commit -m "feat(dto): add NotificacionResponseDTO"
git commit -m "feat(service): add NotificacionService with create, list and mark-as-read"
git commit -m "feat(scheduler): add hourly reminder job for confirmed reservations"
git commit -m "feat(controller): add NotificacionController with read and unread-count endpoints"
```

---

---

## 22. Gestión de carta del restaurante

**Dificultad:** ⭐⭐⭐⭐⭐  
**Rama Git:** `feature/menu-management`  
**Resumen:** El ADMIN puede gestionar la carta del restaurante con categorías y platos. Los platos tienen nombre, descripción, precio, categoría y pueden marcarse como no disponibles. Es un CRUD completo con relación entre entidades y es público para los clientes (sin JWT).

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Crear | `model/Entity/CategoriaMenu.java` |
| Crear | `model/Entity/Plato.java` |
| Crear | `repository/CategoriaMenuRepository.java` |
| Crear | `repository/PlatoRepository.java` |
| Crear | `dto/request/CategoriaMenuRequestDTO.java` |
| Crear | `dto/request/PlatoRequestDTO.java` |
| Crear | `dto/response/CategoriaMenuResponseDTO.java` |
| Crear | `dto/response/PlatoResponseDTO.java` |
| Crear | `service/MenuService.java` |
| Crear | `controller/MenuController.java` |
| Modificar | `security/SecurityConfig.java` |

---

### `model/Entity/CategoriaMenu.java` — archivo nuevo completo

```java
package com.example.GastroTech.model.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name = "categoria_menu")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoriaMenu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nombre;

    private String descripcion;

    @Column(nullable = false)
    private int orden;    // para ordenar la carta (entrantes=1, principales=2, postres=3...)

    @OneToMany(mappedBy = "categoria", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Plato> platos;
}
```

---

### `model/Entity/Plato.java` — archivo nuevo completo

```java
package com.example.GastroTech.model.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "plato")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal precio;

    @Column(nullable = false)
    @Builder.Default
    private boolean disponible = true;

    private String alergenos;   // "gluten,lactosa,huevo"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    private CategoriaMenu categoria;
}
```

---

### `repository/CategoriaMenuRepository.java` — archivo nuevo completo

```java
package com.example.GastroTech.repository;

import com.example.GastroTech.model.Entity.CategoriaMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CategoriaMenuRepository extends JpaRepository<CategoriaMenu, Long> {
    List<CategoriaMenu> findAllByOrderByOrdenAsc();
    boolean existsByNombre(String nombre);
}
```

---

### `repository/PlatoRepository.java` — archivo nuevo completo

```java
package com.example.GastroTech.repository;

import com.example.GastroTech.model.Entity.Plato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PlatoRepository extends JpaRepository<Plato, Long> {
    List<Plato> findByCategoriaIdAndDisponibleTrue(Long categoriaId);
    List<Plato> findByCategoriaId(Long categoriaId);
}
```

---

### `dto/request/CategoriaMenuRequestDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CategoriaMenuRequestDTO(
        @NotBlank(message = "El nombre de la categoria es obligatorio")
        String nombre,

        String descripcion,

        @Min(value = 1, message = "El orden debe ser positivo")
        int orden
) {}
```

---

### `dto/request/PlatoRequestDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PlatoRequestDTO(
        @NotBlank(message = "El nombre del plato es obligatorio")
        String nombre,

        String descripcion,

        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0.01", message = "El precio debe ser mayor que 0")
        BigDecimal precio,

        boolean disponible,

        String alergenos,

        @NotNull(message = "La categoria es obligatoria")
        Long categoriaId
) {}
```

---

### `dto/response/PlatoResponseDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.response;

import java.math.BigDecimal;

public record PlatoResponseDTO(
        Long id,
        String nombre,
        String descripcion,
        BigDecimal precio,
        boolean disponible,
        String alergenos,
        Long categoriaId,
        String categoriaNombre
) {}
```

---

### `dto/response/CategoriaMenuResponseDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.response;

import java.util.List;

public record CategoriaMenuResponseDTO(
        Long id,
        String nombre,
        String descripcion,
        int orden,
        List<PlatoResponseDTO> platos
) {}
```

---

### `service/MenuService.java` — archivo nuevo completo

```java
package com.example.GastroTech.service;

import com.example.GastroTech.dto.request.CategoriaMenuRequestDTO;
import com.example.GastroTech.dto.request.PlatoRequestDTO;
import com.example.GastroTech.dto.response.CategoriaMenuResponseDTO;
import com.example.GastroTech.dto.response.PlatoResponseDTO;
import com.example.GastroTech.exception.BusinessException;
import com.example.GastroTech.exception.ResourceNotFoundException;
import com.example.GastroTech.model.Entity.CategoriaMenu;
import com.example.GastroTech.model.Entity.Plato;
import com.example.GastroTech.repository.CategoriaMenuRepository;
import com.example.GastroTech.repository.PlatoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final CategoriaMenuRepository categoriaRepository;
    private final PlatoRepository platoRepository;

    // ─── Categorías ──────────────────────────────────────────────────────────

    @Transactional
    public CategoriaMenuResponseDTO crearCategoria(CategoriaMenuRequestDTO dto) {
        if (categoriaRepository.existsByNombre(dto.nombre())) {
            throw new BusinessException("Ya existe una categoria con el nombre: " + dto.nombre());
        }
        CategoriaMenu categoria = CategoriaMenu.builder()
                .nombre(dto.nombre())
                .descripcion(dto.descripcion())
                .orden(dto.orden())
                .build();
        return mapCategoriaToDTO(categoriaRepository.save(categoria));
    }

    @Transactional(readOnly = true)
    public List<CategoriaMenuResponseDTO> getCarta() {
        return categoriaRepository.findAllByOrderByOrdenAsc()
                .stream()
                .map(this::mapCategoriaToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void eliminarCategoria(Long id) {
        if (!categoriaRepository.existsById(id)) {
            throw new ResourceNotFoundException("Categoria no encontrada con id: " + id);
        }
        categoriaRepository.deleteById(id);
    }

    // ─── Platos ──────────────────────────────────────────────────────────────

    @Transactional
    public PlatoResponseDTO crearPlato(PlatoRequestDTO dto) {
        CategoriaMenu categoria = categoriaRepository.findById(dto.categoriaId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Categoria no encontrada con id: " + dto.categoriaId()));

        Plato plato = Plato.builder()
                .nombre(dto.nombre())
                .descripcion(dto.descripcion())
                .precio(dto.precio())
                .disponible(dto.disponible())
                .alergenos(dto.alergenos())
                .categoria(categoria)
                .build();

        return mapPlatoToDTO(platoRepository.save(plato));
    }

    @Transactional
    public PlatoResponseDTO toggleDisponibilidad(Long platoId) {
        Plato plato = platoRepository.findById(platoId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Plato no encontrado con id: " + platoId));
        plato.setDisponible(!plato.isDisponible());
        return mapPlatoToDTO(platoRepository.save(plato));
    }

    @Transactional
    public void eliminarPlato(Long id) {
        if (!platoRepository.existsById(id)) {
            throw new ResourceNotFoundException("Plato no encontrado con id: " + id);
        }
        platoRepository.deleteById(id);
    }

    // ─── Mapeos ───────────────────────────────────────────────────────────────

    private CategoriaMenuResponseDTO mapCategoriaToDTO(CategoriaMenu c) {
        List<PlatoResponseDTO> platos = platoRepository
                .findByCategoriaIdAndDisponibleTrue(c.getId())
                .stream()
                .map(this::mapPlatoToDTO)
                .collect(Collectors.toList());

        return new CategoriaMenuResponseDTO(
                c.getId(), c.getNombre(), c.getDescripcion(), c.getOrden(), platos);
    }

    private PlatoResponseDTO mapPlatoToDTO(Plato p) {
        return new PlatoResponseDTO(
                p.getId(), p.getNombre(), p.getDescripcion(), p.getPrecio(),
                p.isDisponible(), p.getAlergenos(),
                p.getCategoria().getId(), p.getCategoria().getNombre());
    }
}
```

---

### `security/SecurityConfig.java` — permitir GET de la carta sin JWT

```java
// Dentro de authorizeHttpRequests(), añadir antes de anyRequest().authenticated():
.requestMatchers(HttpMethod.GET, "/api/v1/menu/**").permitAll()
```

---

### `controller/MenuController.java` — archivo nuevo completo

```java
package com.example.GastroTech.controller;

import com.example.GastroTech.dto.request.CategoriaMenuRequestDTO;
import com.example.GastroTech.dto.request.PlatoRequestDTO;
import com.example.GastroTech.dto.response.CategoriaMenuResponseDTO;
import com.example.GastroTech.dto.response.PlatoResponseDTO;
import com.example.GastroTech.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/menu")
@RequiredArgsConstructor
@Tag(name = "Carta del restaurante")
public class MenuController {

    private final MenuService menuService;

    // ─── Endpoints públicos (sin JWT) ────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Ver la carta completa con categorias y platos disponibles")
    public ResponseEntity<List<CategoriaMenuResponseDTO>> getCarta() {
        return ResponseEntity.ok(menuService.getCarta());
    }

    // ─── Endpoints de gestión (solo ADMIN) ──────────────────────────────────

    @PostMapping("/categories")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Crear una nueva categoria en la carta")
    public ResponseEntity<CategoriaMenuResponseDTO> crearCategoria(
            @Valid @RequestBody CategoriaMenuRequestDTO dto) {
        return new ResponseEntity<>(menuService.crearCategoria(dto), HttpStatus.CREATED);
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Eliminar una categoria y sus platos")
    public ResponseEntity<Void> eliminarCategoria(@PathVariable Long id) {
        menuService.eliminarCategoria(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/dishes")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Añadir un plato a la carta")
    public ResponseEntity<PlatoResponseDTO> crearPlato(
            @Valid @RequestBody PlatoRequestDTO dto) {
        return new ResponseEntity<>(menuService.crearPlato(dto), HttpStatus.CREATED);
    }

    @PatchMapping("/dishes/{id}/toggle-availability")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Activar o desactivar la disponibilidad de un plato")
    public ResponseEntity<PlatoResponseDTO> toggleDisponibilidad(@PathVariable Long id) {
        return ResponseEntity.ok(menuService.toggleDisponibilidad(id));
    }

    @DeleteMapping("/dishes/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Eliminar un plato de la carta")
    public ResponseEntity<Void> eliminarPlato(@PathVariable Long id) {
        menuService.eliminarPlato(id);
        return ResponseEntity.noContent().build();
    }
}
```

---

### Commits sugeridos

```bash
git commit -m "feat(model): add CategoriaMenu and Plato entities with OneToMany relation"
git commit -m "feat(repository): add CategoriaMenuRepository and PlatoRepository"
git commit -m "feat(dto): add request and response DTOs for menu management"
git commit -m "feat(service): add MenuService with full CRUD for categories and dishes"
git commit -m "fix(security): permit GET /api/v1/menu/** without JWT"
git commit -m "feat(controller): add MenuController with public read and ADMIN write endpoints"
```

---

---

## Resumen completo (features 1-22)

| # | Feature | Rama | Entidad nueva | Endpoints | Scheduler | Dificultad |
|---|---------|------|--------------|-----------|-----------|-----------|
| 1 | Límite de reservas activas | `feature/reservation-limit` | No | 0 | No | ⭐⭐ |
| 2 | Sugerencia automática de mesa | `feature/table-suggestion` | No | 1 | No | ⭐⭐ |
| 3 | Notas internas del ADMIN | `feature/internal-notes` | Sí | 2 | No | ⭐⭐⭐ |
| 4 | Aforo máximo simultáneo | `feature/max-capacity` | No | 1 | No | ⭐⭐⭐ |
| 5 | Caducidad de penalización | `feature/penalty-expiry` | No | 0 | Sí | ⭐⭐⭐ |
| 6 | Bloqueo de mesa por mantenimiento | `feature/table-block` | Sí | 2 | No | ⭐⭐⭐ |
| 7 | Valoración post-reserva | `feature/ratings` | Sí | 2 | No | ⭐⭐⭐ |
| 8 | Transferencia de reserva | `feature/reservation-transfer` | No | 1 | No | ⭐⭐⭐⭐ |
| 9 | Historial de cambios de estado | `feature/reservation-audit` | Sí | 1 | No | ⭐⭐⭐⭐ |
| 10 | Reserva recurrente semanal | `feature/recurring-reservations` | Sí | 1 | No | ⭐⭐⭐⭐ |
| 11 | Sistema de fidelización | `feature/loyalty-system` | No | 1 | Sí | ⭐⭐⭐⭐ |
| 12 | Lista de espera | `feature/waitlist` | Sí | 2 | No | ⭐⭐⭐⭐⭐ |
| 13 | Confirmación manual de reservas | `feature/reservation-confirm` | No | 1 | No | ⭐⭐ |
| 14 | Check-in digital con código | `feature/checkin` | No | 1 | No | ⭐⭐⭐ |
| 15 | Dashboard de estadísticas | `feature/statistics` | No | 1 | No | ⭐⭐⭐ |
| 16 | Política de no-show | `feature/no-show` | No | 0 | Sí | ⭐⭐⭐ |
| 17 | Búsqueda y filtrado avanzado | `feature/reservation-search` | No | 1 | No | ⭐⭐⭐ |
| 18 | Exportación en CSV | `feature/csv-export` | No | 1 | No | ⭐⭐⭐ |
| 19 | Cierre temporal del restaurante | `feature/restaurant-closure` | Sí | 3 | No | ⭐⭐⭐ |
| 20 | Límite mensual de cancelaciones | `feature/monthly-cancellation-limit` | No | 0 | Sí | ⭐⭐⭐⭐ |
| 21 | Sistema de notificaciones internas | `feature/notifications` | Sí | 3 | Sí | ⭐⭐⭐⭐ |
| 22 | Gestión de carta del restaurante | `feature/menu-management` | Sí x2 | 6 | No | ⭐⭐⭐⭐⭐ |

---

---

## 23. Consulta pública de disponibilidad de una mesa concreta

**Dificultad:** ⭐⭐  
**Rama Git:** `feature/table-availability-check`  
**Resumen:** Cualquier visitante (sin JWT) puede consultar si una mesa concreta está libre en una fecha y hora determinadas. Requiere tocar `SecurityConfig` porque el endpoint `/api/v1/tables/**` ya tiene una regla que lo restringe a ADMIN; la nueva ruta pública debe declararse antes de esa regla para que no quede bloqueada.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Crear | `dto/response/DisponibilidadResponseDTO.java` |
| Modificar | `repository/ReservaRepository.java` |
| Modificar | `service/MesaService.java` |
| Modificar | `controller/MesaController.java` |
| Modificar | `security/SecurityConfig.java` |

---

### `dto/response/DisponibilidadResponseDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.response;

import java.time.LocalDateTime;

public record DisponibilidadResponseDTO(
        Long mesaId,
        int numeroMesa,
        int capacidad,
        String ubicacion,
        LocalDateTime fechaConsultada,
        boolean disponible,
        String mensaje
) {}
```

---

### `repository/ReservaRepository.java` — añadir método

El repositorio ya tiene `existsByMesaIdAndFechaReservaBetweenAndEstadoNot`. Lo reutilizamos directamente en el service: no hace falta añadir nada nuevo. Sin embargo, añadimos un método de nombre más expresivo que deja clara su intención de cara a la disponibilidad pública:

```java
// Alias semánticamente más claro para consultas de disponibilidad externa
default boolean isMesaDisponibleEnFranja(Long mesaId,
                                          java.time.LocalDateTime inicio,
                                          java.time.LocalDateTime fin) {
    return !existsByMesaIdAndFechaReservaBetweenAndEstadoNot(
            mesaId, inicio, fin, com.example.GastroTech.model.Enum.EstadoReserva.CANCELADA);
}
```

> Los métodos `default` en interfaces de repositorio permiten añadir lógica sin escribir una implementación separada.

---

### `service/MesaService.java` — añadir método

```java
// Nuevos imports:
import com.example.GastroTech.dto.response.DisponibilidadResponseDTO;
import java.time.LocalDateTime;

@Transactional(readOnly = true)
public DisponibilidadResponseDTO checkDisponibilidad(Long mesaId, LocalDateTime fechaHora) {
    Mesa mesa = mesaRepository.findById(mesaId)
            .orElseThrow(() -> new ResourceNotFoundException(
                    "Mesa no encontrada con id: " + mesaId));

    LocalDateTime inicio = fechaHora.minusHours(2);
    LocalDateTime fin    = fechaHora.plusHours(2);

    boolean disponible = reservaRepository.isMesaDisponibleEnFranja(mesaId, inicio, fin);

    String mensaje = disponible
            ? "La mesa esta disponible en esa franja horaria"
            : "La mesa ya tiene una reserva activa en esa franja (margen de 2 horas)";

    return new DisponibilidadResponseDTO(
            mesa.getId(),
            mesa.getNumeroMesa(),
            mesa.getCapacidad(),
            mesa.getUbicacion().name(),
            fechaHora,
            disponible,
            mensaje
    );
}
```

---

### `controller/MesaController.java` — añadir endpoint

```java
// Nuevos imports:
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
import com.example.GastroTech.dto.response.DisponibilidadResponseDTO;

@GetMapping("/{id}/check-availability")
@Operation(summary = "Comprobar si una mesa esta libre en una franja horaria (publico, sin JWT)")
public ResponseEntity<DisponibilidadResponseDTO> checkDisponibilidad(
        @PathVariable Long id,
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime dateTime) {
    return ResponseEntity.ok(mesaService.checkDisponibilidad(id, dateTime));
}
```

---

### `security/SecurityConfig.java` — añadir regla ANTES de la restricción de ADMIN

Este es el cambio más importante de esta feature. Sin él, la nueva ruta queda bloqueada por la regla existente `hasRole("ADMIN")` que ya cubre todo `/api/v1/tables/**`.

Spring Security evalúa las reglas **en el orden en que están declaradas**: la primera que coincide gana. La ruta específica debe declararse antes que la genérica:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/v1/auth/**").permitAll()
    .requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                     "/v3/api-docs/**", "/h2-console/**").permitAll()

    // ── NUEVO: debe ir ANTES de la regla ADMIN de tables ──────────────
    .requestMatchers(HttpMethod.GET, "/api/v1/tables/*/check-availability").permitAll()
    // ──────────────────────────────────────────────────────────────────

    // Esta regla ya existía — ahora no bloquea la ruta de disponibilidad
    .requestMatchers(HttpMethod.GET,  "/api/v1/tables/**").hasRole("ADMIN")
    .requestMatchers(HttpMethod.POST, "/api/v1/tables/**").hasRole("ADMIN")

    .anyRequest().authenticated()
)
```

> **Por qué el orden importa:** si `GET /api/v1/tables/**` aparece primero, Spring Security lo aplica también a `/api/v1/tables/3/check-availability` y devuelve 403 aunque luego haya una regla `permitAll`. Declarar el caso específico antes del genérico es el patrón estándar en Spring Security 6.

---

### Commits sugeridos

```bash
git commit -m "feat(dto): add DisponibilidadResponseDTO"
git commit -m "feat(repository): add isMesaDisponibleEnFranja default method to ReservaRepository"
git commit -m "feat(service): add checkDisponibilidad method to MesaService"
git commit -m "feat(controller): add public GET /tables/{id}/check-availability endpoint"
git commit -m "fix(security): permit GET /tables/*/check-availability before ADMIN table rule"
```

---

---

## 24. Cambio de contraseña del usuario autenticado

**Dificultad:** ⭐⭐  
**Rama Git:** `feature/change-password`  
**Resumen:** El usuario autenticado puede cambiar su propia contraseña enviando la contraseña actual (para verificación), la nueva y su confirmación. Requiere tocar `SecurityConfig` para declarar explícitamente la ruta como autenticada (en lugar de depender del `anyRequest().authenticated()` genérico), lo que sirve de documentación de intención y permite añadir restricciones futuras como rate limiting.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Crear | `dto/request/ChangePasswordRequestDTO.java` |
| Modificar | `repository/UsuarioRepository.java` |
| Modificar | `service/UsuarioService.java` |
| Modificar | `controller/UsuarioController.java` |
| Modificar | `security/SecurityConfig.java` |

---

### `dto/request/ChangePasswordRequestDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequestDTO(
        @NotBlank(message = "La contrasena actual es obligatoria")
        String currentPassword,

        @NotBlank(message = "La nueva contrasena es obligatoria")
        @Size(min = 6, message = "La nueva contrasena debe tener al menos 6 caracteres")
        String newPassword,

        @NotBlank(message = "La confirmacion de la nueva contrasena es obligatoria")
        String confirmNewPassword
) {}
```

---

### `repository/UsuarioRepository.java` — añadir método

```java
// Útil para verificar que el usuario sigue activo antes de permitir el cambio
boolean existsByIdAndActivoTrue(Long id);
```

---

### `service/UsuarioService.java` — añadir método e inyectar PasswordEncoder

```java
// Nuevos imports:
import com.example.GastroTech.dto.request.ChangePasswordRequestDTO;
import org.springframework.security.crypto.password.PasswordEncoder;

// Añadir dependencia (Lombok la inyecta automáticamente con @RequiredArgsConstructor):
private final PasswordEncoder passwordEncoder;

// Nuevo método:
@Transactional
public void changePassword(String email, ChangePasswordRequestDTO dto) {
    Usuario usuario = usuarioRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

    // Verificar que la cuenta sigue activa
    if (!usuarioRepository.existsByIdAndActivoTrue(usuario.getId())) {
        throw new BusinessException("La cuenta no esta activa");
    }

    // Verificar que la contraseña actual es correcta
    if (!passwordEncoder.matches(dto.currentPassword(), usuario.getPassword())) {
        throw new BusinessException("La contrasena actual no es correcta");
    }

    // Verificar que la nueva contraseña y su confirmacion coinciden
    if (!dto.newPassword().equals(dto.confirmNewPassword())) {
        throw new BusinessException(
                "La nueva contrasena y su confirmacion no coinciden");
    }

    // Verificar que la nueva contraseña es distinta a la actual
    if (passwordEncoder.matches(dto.newPassword(), usuario.getPassword())) {
        throw new BusinessException(
                "La nueva contrasena debe ser distinta a la actual");
    }

    usuario.setPassword(passwordEncoder.encode(dto.newPassword()));
    usuarioRepository.save(usuario);
}
```

---

### `controller/UsuarioController.java` — añadir endpoint

```java
// Nuevos imports:
import com.example.GastroTech.dto.request.ChangePasswordRequestDTO;
import jakarta.validation.Valid;

@PatchMapping("/me/password")
@Operation(summary = "Cambiar la contrasena del usuario autenticado")
public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequestDTO dto) {
    String email = SecurityContextHolder.getContext().getAuthentication().getName();
    usuarioService.changePassword(email, dto);
    return ResponseEntity.noContent().build();
}
```

---

### `security/SecurityConfig.java` — declarar la ruta explícitamente

Aunque `anyRequest().authenticated()` ya cubriría este endpoint, declararlo de forma explícita comunica la intención al equipo y facilita añadir restricciones futuras (rate limiting, MFA) sin tocar el bloque genérico:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/v1/auth/**").permitAll()
    .requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                     "/v3/api-docs/**", "/h2-console/**").permitAll()
    .requestMatchers(HttpMethod.GET,  "/api/v1/tables/*/check-availability").permitAll()
    .requestMatchers(HttpMethod.GET,  "/api/v1/tables/**").hasRole("ADMIN")
    .requestMatchers(HttpMethod.POST, "/api/v1/tables/**").hasRole("ADMIN")

    // ── NUEVO: declaración explícita del endpoint de cambio de contraseña ─
    .requestMatchers(HttpMethod.PATCH, "/api/v1/users/me/password").authenticated()
    // ─────────────────────────────────────────────────────────────────────

    .anyRequest().authenticated()
)
```

> **Buena práctica:** declarar explícitamente los endpoints sensibles (cambio de contraseña, datos personales) en `SecurityConfig` en lugar de dejarlos caer en `anyRequest()`. Así quedan documentados como decisiones de seguridad conscientes y son más fáciles de auditar.

---

### Commits sugeridos

```bash
git commit -m "feat(dto): add ChangePasswordRequestDTO with current, new and confirm fields"
git commit -m "feat(repository): add existsByIdAndActivoTrue to UsuarioRepository"
git commit -m "feat(service): add changePassword with BCrypt verification and mismatch checks"
git commit -m "feat(controller): add PATCH /api/v1/users/me/password endpoint"
git commit -m "fix(security): explicitly declare /users/me/password as authenticated route"
```

---

---

## 25. Listado y filtrado de usuarios para el ADMIN

**Dificultad:** ⭐⭐⭐  
**Rama Git:** `feature/admin-user-management`  
**Resumen:** El ADMIN puede listar todos los usuarios con filtros opcionales por rol, estado y puntos de penalización mínimos. También puede ver el detalle de un usuario concreto. Requiere tocar `SecurityConfig` para añadir una regla que proteja todo el prefijo `/api/v1/admin/**` de forma centralizada en lugar de confiar únicamente en `@PreAuthorize`.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Crear | `dto/request/UsuarioFiltroDTO.java` |
| Crear | `dto/response/UsuarioAdminResponseDTO.java` |
| Modificar | `repository/UsuarioRepository.java` |
| Modificar | `service/UsuarioService.java` |
| Crear | `controller/AdminUsuarioController.java` |
| Modificar | `security/SecurityConfig.java` |

---

### `dto/request/UsuarioFiltroDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.request;

import com.example.GastroTech.model.Enum.EstadoUsuario;
import com.example.GastroTech.model.Enum.RolUsuario;

// Parámetros de query string: ?rol=USER&status=BANNED&minPenalty=4
public record UsuarioFiltroDTO(
        RolUsuario rol,             // ?rol=USER  (opcional)
        EstadoUsuario status,       // ?status=BANNED  (opcional)
        Integer minPenaltyPoints    // ?minPenalty=4  (opcional)
) {}
```

---

### `dto/response/UsuarioAdminResponseDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.response;

import java.time.LocalDateTime;

public record UsuarioAdminResponseDTO(
        Long id,
        String nombre,
        String email,
        String rol,
        String status,
        int penalizationPoints,
        boolean activo,
        LocalDateTime fechaCreacion
) {}
```

---

### `repository/UsuarioRepository.java` — añadir métodos de filtrado

```java
// Importar al inicio:
import com.example.GastroTech.model.Enum.EstadoUsuario;
import com.example.GastroTech.model.Enum.RolUsuario;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

// Filtro combinado: todos los parámetros son opcionales (null = ignorar ese filtro)
@Query("""
    SELECT u FROM Usuario u
    WHERE (:rol IS NULL OR u.rol = :rol)
    AND (:status IS NULL OR u.status = :status)
    AND (:minPenalty IS NULL OR u.penalizationPoints >= :minPenalty)
    ORDER BY u.fechaCreacion DESC
    """)
List<Usuario> findWithFilters(
        @Param("rol")        RolUsuario rol,
        @Param("status")     EstadoUsuario status,
        @Param("minPenalty") Integer minPenalty
);
```

---

### `service/UsuarioService.java` — añadir métodos de búsqueda

```java
// Nuevos imports:
import com.example.GastroTech.dto.request.UsuarioFiltroDTO;
import com.example.GastroTech.dto.response.UsuarioAdminResponseDTO;
import java.util.List;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
public List<UsuarioAdminResponseDTO> findUsersWithFilters(UsuarioFiltroDTO filtro) {
    return usuarioRepository
            .findWithFilters(filtro.rol(), filtro.status(), filtro.minPenaltyPoints())
            .stream()
            .map(this::mapToAdminResponseDTO)
            .collect(Collectors.toList());
}

@Transactional(readOnly = true)
public UsuarioAdminResponseDTO findUsuarioById(Long id) {
    return usuarioRepository.findById(id)
            .map(this::mapToAdminResponseDTO)
            .orElseThrow(() -> new ResourceNotFoundException(
                    "Usuario no encontrado con id: " + id));
}

// Mapper privado — las entidades no salen del service
private UsuarioAdminResponseDTO mapToAdminResponseDTO(Usuario usuario) {
    return new UsuarioAdminResponseDTO(
            usuario.getId(),
            usuario.getNombre(),
            usuario.getEmail(),
            usuario.getRol().name(),
            usuario.getStatus().name(),
            usuario.getPenalizationPoints(),
            usuario.isActivo(),
            usuario.getFechaCreacion()
    );
}
```

---

### `controller/AdminUsuarioController.java` — archivo nuevo completo

```java
package com.example.GastroTech.controller;

import com.example.GastroTech.dto.request.UsuarioFiltroDTO;
import com.example.GastroTech.dto.response.UsuarioAdminResponseDTO;
import com.example.GastroTech.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "Gestion de usuarios (ADMIN)",
     description = "Listado y detalle de usuarios. Requiere rol ADMIN.")
@SecurityRequirement(name = "BearerAuth")
public class AdminUsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    @Operation(summary = "Listar usuarios con filtros opcionales: rol, status, minPenalty")
    public ResponseEntity<List<UsuarioAdminResponseDTO>> listUsers(
            @ModelAttribute UsuarioFiltroDTO filtro) {
        return ResponseEntity.ok(usuarioService.findUsersWithFilters(filtro));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ver detalle de un usuario concreto")
    public ResponseEntity<UsuarioAdminResponseDTO> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.findUsuarioById(id));
    }
}
```

> Se usa `@ModelAttribute` en lugar de `@RequestBody` porque los filtros llegan como parámetros de query string (`?rol=USER&status=BANNED`), igual que en la feature 17.

---

### `security/SecurityConfig.java` — proteger todo `/api/v1/admin/**` de forma centralizada

Actualmente cada controlador bajo `/admin` depende de `@PreAuthorize` o del `anyRequest().authenticated()` genérico. El problema es que si en el futuro alguien añade un endpoint bajo `/api/v1/admin/` y olvida poner `@PreAuthorize`, queda desprotegido.

La solución es añadir una regla de red en `SecurityConfig` que actúe como segunda línea de defensa:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/v1/auth/**").permitAll()
    .requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                     "/v3/api-docs/**", "/h2-console/**").permitAll()
    .requestMatchers(HttpMethod.GET,  "/api/v1/tables/*/check-availability").permitAll()

    // ── NUEVO: protección centralizada para todo el prefijo /admin ────
    // Actúa como segunda línea de defensa independiente de @PreAuthorize
    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
    // ──────────────────────────────────────────────────────────────────

    .requestMatchers(HttpMethod.GET,  "/api/v1/tables/**").hasRole("ADMIN")
    .requestMatchers(HttpMethod.POST, "/api/v1/tables/**").hasRole("ADMIN")
    .requestMatchers(HttpMethod.PATCH, "/api/v1/users/me/password").authenticated()
    .anyRequest().authenticated()
)
```

> **Defense in depth:** con esta regla, aunque `@PreAuthorize` falle o se olvide en un controlador bajo `/admin`, Spring Security rechaza la petición a nivel de filtro HTTP antes de que llegue al método. Es el mismo principio que `hasRole("ADMIN")` en las tablas, aplicado a todo el espacio de administración.

---

### Commits sugeridos

```bash
git commit -m "feat(dto): add UsuarioFiltroDTO and UsuarioAdminResponseDTO"
git commit -m "feat(repository): add findWithFilters JPQL query with optional params to UsuarioRepository"
git commit -m "feat(service): add findUsersWithFilters and findUsuarioById to UsuarioService"
git commit -m "feat(controller): add AdminUsuarioController with list and detail endpoints"
git commit -m "fix(security): centralize ADMIN protection for /api/v1/admin/** prefix"
```

---

---

## Resumen completo (features 1-25)

| # | Feature | Rama | Entidad nueva | Endpoints | Scheduler | Dificultad |
|---|---------|------|--------------|-----------|-----------|-----------|
| 1 | Límite de reservas activas | `feature/reservation-limit` | No | 0 | No | ⭐⭐ |
| 2 | Sugerencia automática de mesa | `feature/table-suggestion` | No | 1 | No | ⭐⭐ |
| 13 | Confirmación manual de reservas | `feature/reservation-confirm` | No | 1 | No | ⭐⭐ |
| 23 | Disponibilidad de mesa concreta | `feature/table-availability-check` | No | 1 | No | ⭐⭐ |
| 24 | Cambio de contraseña | `feature/change-password` | No | 1 | No | ⭐⭐ |
| 3 | Notas internas del ADMIN | `feature/internal-notes` | Sí | 2 | No | ⭐⭐⭐ |
| 4 | Aforo máximo simultáneo | `feature/max-capacity` | No | 1 | No | ⭐⭐⭐ |
| 5 | Caducidad de penalización | `feature/penalty-expiry` | No | 0 | Sí | ⭐⭐⭐ |
| 6 | Bloqueo de mesa por mantenimiento | `feature/table-block` | Sí | 2 | No | ⭐⭐⭐ |
| 7 | Valoración post-reserva | `feature/ratings` | Sí | 2 | No | ⭐⭐⭐ |
| 14 | Check-in digital con código | `feature/checkin` | No | 1 | No | ⭐⭐⭐ |
| 15 | Dashboard de estadísticas | `feature/statistics` | No | 1 | No | ⭐⭐⭐ |
| 16 | Política de no-show | `feature/no-show` | No | 0 | Sí | ⭐⭐⭐ |
| 17 | Búsqueda y filtrado avanzado | `feature/reservation-search` | No | 1 | No | ⭐⭐⭐ |
| 18 | Exportación en CSV | `feature/csv-export` | No | 1 | No | ⭐⭐⭐ |
| 19 | Cierre temporal del restaurante | `feature/restaurant-closure` | Sí | 3 | No | ⭐⭐⭐ |
| 25 | Listado y filtrado de usuarios | `feature/admin-user-management` | No | 2 | No | ⭐⭐⭐ |
| 8 | Transferencia de reserva | `feature/reservation-transfer` | No | 1 | No | ⭐⭐⭐⭐ |
| 9 | Historial de cambios de estado | `feature/reservation-audit` | Sí | 1 | No | ⭐⭐⭐⭐ |
| 10 | Reserva recurrente semanal | `feature/recurring-reservations` | Sí | 1 | No | ⭐⭐⭐⭐ |
| 11 | Sistema de fidelización | `feature/loyalty-system` | No | 1 | Sí | ⭐⭐⭐⭐ |
| 20 | Límite mensual de cancelaciones | `feature/monthly-cancellation-limit` | No | 0 | Sí | ⭐⭐⭐⭐ |
| 21 | Sistema de notificaciones internas | `feature/notifications` | Sí | 3 | Sí | ⭐⭐⭐⭐ |
| 12 | Lista de espera | `feature/waitlist` | Sí | 2 | No | ⭐⭐⭐⭐⭐ |
| 22 | Gestión de carta del restaurante | `feature/menu-management` | Sí x2 | 6 | No | ⭐⭐⭐⭐⭐ |

---

---

## 23. Gestión de usuarios para ADMIN

**Dificultad:** ⭐⭐⭐  
**Rama Git:** `feature/admin-user-management`  
**Resumen:** El ADMIN puede listar todos los usuarios, filtrarlos por rol o estado, y consultar el detalle de cualquier usuario concreto. Se añade una ruta `/api/v1/admin/**` protegida globalmente en `SecurityConfig` a nivel de configuración (no solo con `@PreAuthorize`), dejando el filtro de seguridad explícito e independiente de las anotaciones de los controllers.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Modificar | `dto/response/UsuarioResponseDTO.java` |
| Modificar | `repository/UsuarioRepository.java` |
| Modificar | `service/UsuarioService.java` |
| Modificar | `controller/UsuarioController.java` |
| Modificar | `security/SecurityConfig.java` |

---

### `dto/response/UsuarioResponseDTO.java` — ampliar el record existente

```java
// Reemplazar el record actual por este (añade campos de estado y penalizacion):
package com.example.GastroTech.dto.response;

import java.time.LocalDateTime;

public record UsuarioResponseDTO(
        Long id,
        String nombre,
        String email,
        String rol,
        int penalizationPoints,
        String status,
        LocalDateTime fechaCreacion,
        boolean activo
) {}
```

---

### `repository/UsuarioRepository.java` — añadir queries de filtrado

```java
// Importar al inicio:
import com.example.GastroTech.model.Enum.EstadoUsuario;
import com.example.GastroTech.model.Enum.RolUsuario;
import java.util.List;

// Filtrar por estado
List<Usuario> findByStatus(EstadoUsuario status);

// Filtrar por rol
List<Usuario> findByRol(RolUsuario rol);

// Filtrar por rol y estado a la vez
List<Usuario> findByRolAndStatus(RolUsuario rol, EstadoUsuario status);
```

---

### `service/UsuarioService.java` — añadir métodos de gestión

```java
// Importar al inicio:
import com.example.GastroTech.model.Enum.EstadoUsuario;
import com.example.GastroTech.model.Enum.RolUsuario;
import java.util.List;
import java.util.stream.Collectors;

// Listar todos los usuarios con filtros opcionales
@Transactional(readOnly = true)
public List<UsuarioResponseDTO> findAll(RolUsuario rol, EstadoUsuario status) {
    List<Usuario> usuarios;

    if (rol != null && status != null) {
        usuarios = usuarioRepository.findByRolAndStatus(rol, status);
    } else if (rol != null) {
        usuarios = usuarioRepository.findByRol(rol);
    } else if (status != null) {
        usuarios = usuarioRepository.findByStatus(status);
    } else {
        usuarios = usuarioRepository.findAll();
    }

    return usuarios.stream()
            .map(this::mapToResponseDTO)
            .collect(Collectors.toList());
}

// Consultar un usuario concreto por ID
@Transactional(readOnly = true)
public UsuarioResponseDTO findById(Long id) {
    Usuario usuario = usuarioRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                    "Usuario no encontrado con id: " + id));
    return mapToResponseDTO(usuario);
}

// Mapeo privado de entidad a DTO
private UsuarioResponseDTO mapToResponseDTO(Usuario usuario) {
    return new UsuarioResponseDTO(
            usuario.getId(),
            usuario.getNombre(),
            usuario.getEmail(),
            usuario.getRol().name(),
            usuario.getPenalizationPoints(),
            usuario.getStatus().name(),
            usuario.getFechaCreacion(),
            usuario.isActivo()
    );
}
```

---

### `controller/UsuarioController.java` — añadir endpoints de listado y detalle

```java
// Importar al inicio:
import com.example.GastroTech.model.Enum.EstadoUsuario;
import com.example.GastroTech.model.Enum.RolUsuario;
import java.util.List;

// Listar usuarios con filtros opcionales por query string
@GetMapping
@Operation(summary = "Listar todos los usuarios con filtros opcionales (solo ADMIN)")
public ResponseEntity<List<UsuarioResponseDTO>> getAllUsuarios(
        @RequestParam(required = false) RolUsuario rol,
        @RequestParam(required = false) EstadoUsuario status) {
    return ResponseEntity.ok(usuarioService.findAll(rol, status));
}

// Consultar un usuario concreto
@GetMapping("/{id}")
@Operation(summary = "Ver el detalle de un usuario por ID (solo ADMIN)")
public ResponseEntity<UsuarioResponseDTO> getUsuarioById(@PathVariable Long id) {
    return ResponseEntity.ok(usuarioService.findById(id));
}
```

---

### `security/SecurityConfig.java` — proteger `/api/v1/admin/**` a nivel de configuración

```java
// Dentro de authorizeHttpRequests(), añadir ANTES de anyRequest().authenticated():

// Protección global de rutas /admin a nivel de config (complementa @PreAuthorize)
.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

// Y en la sección de mesas, sustituir las dos líneas de /tables por la ruta /admin
// si se mueven los endpoints al prefijo /admin. Si se mantiene /api/v1/users/**, añadir:
.requestMatchers("/api/v1/users/**").hasRole("ADMIN")
```

> **Por qué tocar SecurityConfig aquí:** hasta ahora los endpoints de `/api/v1/users/**` estaban protegidos solo por `@PreAuthorize("hasRole('ADMIN')")` en cada método. Añadirlo también en `SecurityConfig` aplica la restricción a nivel de filtro HTTP, **antes de que la petición llegue al dispatcher**. Es una defensa en profundidad: aunque alguien elimine o ignore la anotación del controller, la capa de seguridad lo bloquea igualmente.

---

### Commits sugeridos

```bash
git commit -m "feat(dto): extend UsuarioResponseDTO with fechaCreacion and activo fields"
git commit -m "feat(repository): add findByStatus, findByRol and findByRolAndStatus queries"
git commit -m "feat(service): add findAll with optional filters and findById to UsuarioService"
git commit -m "feat(controller): add GET /api/v1/users and GET /api/v1/users/{id} endpoints"
git commit -m "fix(security): add explicit /api/v1/users/** hasRole(ADMIN) rule to SecurityConfig"
```

---

---

## 24. Consulta pública de disponibilidad de mesas

**Dificultad:** ⭐⭐  
**Rama Git:** `feature/public-availability`  
**Resumen:** Cualquier visitante (sin JWT) puede consultar qué mesas están libres para una fecha, hora y número de comensales determinados. El endpoint es público y devuelve la lista de mesas disponibles con su capacidad y ubicación. Requiere añadir la ruta `/api/v1/public/**` a `SecurityConfig` como `permitAll`.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Crear | `dto/request/DisponibilidadRequestDTO.java` |
| Crear | `dto/response/DisponibilidadResponseDTO.java` |
| Modificar | `repository/ReservaRepository.java` |
| Modificar | `service/MesaService.java` |
| Crear | `controller/PublicController.java` |
| Modificar | `security/SecurityConfig.java` |

---

### `dto/request/DisponibilidadRequestDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

// Los campos llegan como parámetros de query string (?fecha=...&comensales=2)
// Se usa @ModelAttribute en el controller, no @RequestBody
public record DisponibilidadRequestDTO(
        @NotNull(message = "La fecha y hora son obligatorias")
        @Future(message = "La fecha debe ser futura")
        LocalDateTime fecha,

        @Min(value = 1, message = "Minimo 1 comensal")
        @Max(value = 12, message = "Maximo 12 comensales")
        int comensales
) {}
```

---

### `dto/response/DisponibilidadResponseDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record DisponibilidadResponseDTO(
        LocalDateTime fechaConsultada,
        int comensalesSolicitados,
        int mesasDisponibles,
        List<MesaResponseDTO> mesas
) {}
```

---

### `repository/ReservaRepository.java` — añadir query de IDs ocupados

```java
// Devuelve los IDs de las mesas que tienen reserva activa en esa franja
@Query("""
    SELECT r.mesa.id FROM Reserva r
    WHERE r.estado != 'CANCELADA'
    AND r.fechaReserva BETWEEN :inicio AND :fin
    """)
List<Long> findMesasOcupadasEnFranja(
        @Param("inicio") LocalDateTime inicio,
        @Param("fin") LocalDateTime fin
);
```

---

### `service/MesaService.java` — añadir método de disponibilidad

```java
// Importar al inicio:
import com.example.GastroTech.dto.request.DisponibilidadRequestDTO;
import com.example.GastroTech.dto.response.DisponibilidadResponseDTO;
import java.util.List;

@Transactional(readOnly = true)
public DisponibilidadResponseDTO consultarDisponibilidad(DisponibilidadRequestDTO dto) {
    LocalDateTime inicio = dto.fecha().minusHours(2);
    LocalDateTime fin    = dto.fecha().plusHours(2);

    // IDs de mesas ya reservadas en esa franja
    List<Long> ocupadas = reservaRepository.findMesasOcupadasEnFranja(inicio, fin);

    // Mesas libres con capacidad suficiente
    List<MesaResponseDTO> disponibles = mesaRepository.findAll().stream()
            .filter(m -> !ocupadas.contains(m.getId()))
            .filter(m -> m.getCapacidad() >= dto.comensales())
            .map(this::mapToResponseDTO)
            .collect(Collectors.toList());

    return new DisponibilidadResponseDTO(
            dto.fecha(),
            dto.comensales(),
            disponibles.size(),
            disponibles
    );
}
```

---

### `controller/PublicController.java` — archivo nuevo completo

```java
package com.example.GastroTech.controller;

import com.example.GastroTech.dto.request.DisponibilidadRequestDTO;
import com.example.GastroTech.dto.response.DisponibilidadResponseDTO;
import com.example.GastroTech.service.MesaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
@Tag(name = "Consultas publicas", description = "Endpoints accesibles sin autenticacion")
public class PublicController {

    private final MesaService mesaService;

    @GetMapping("/availability")
    @Operation(summary = "Consultar mesas disponibles para una fecha y numero de comensales")
    public ResponseEntity<DisponibilidadResponseDTO> checkAvailability(
            @Valid @ModelAttribute DisponibilidadRequestDTO dto) {
        return ResponseEntity.ok(mesaService.consultarDisponibilidad(dto));
    }
}
```

> Se usa `@ModelAttribute` porque los parámetros llegan en la query string:  
> `GET /api/v1/public/availability?fecha=2025-10-15T20:00&comensales=4`

---

### `security/SecurityConfig.java` — añadir ruta pública

```java
// Dentro de authorizeHttpRequests(), añadir junto al bloque de rutas públicas:
.requestMatchers("/api/v1/public/**").permitAll()
```

> **Por qué es necesario:** sin esta línea, cualquier petición a `/api/v1/public/**` caería en `anyRequest().authenticated()` y devolvería un 401. La ruta es genuinamente pública (consulta de disponibilidad para visitantes que aún no tienen cuenta), por lo que debe declararse explícitamente en el filtro de seguridad, no solo omitir `@PreAuthorize` en el controller.

---

### Commits sugeridos

```bash
git commit -m "feat(dto): add DisponibilidadRequestDTO and DisponibilidadResponseDTO"
git commit -m "feat(repository): add findMesasOcupadasEnFranja query to ReservaRepository"
git commit -m "feat(service): add consultarDisponibilidad method to MesaService"
git commit -m "feat(controller): add PublicController with GET /api/v1/public/availability"
git commit -m "fix(security): permit all requests to /api/v1/public/** without JWT"
```

---

---

## 25. Cambio de contraseña del usuario autenticado

**Dificultad:** ⭐⭐  
**Rama Git:** `feature/change-password`  
**Resumen:** Un usuario autenticado puede cambiar su propia contraseña enviando la contraseña actual (para verificar que es él) y la nueva con confirmación. El endpoint requiere JWT pero no rol ADMIN. Se añade una regla explícita en `SecurityConfig` para que la ruta `/api/v1/users/me/**` sea accesible por cualquier usuario autenticado, separándola de la restricción de ADMIN del resto de `/api/v1/users/**`.

### Archivos a modificar o crear

| Acción | Archivo |
|--------|---------|
| Crear | `dto/request/ChangePasswordRequestDTO.java` |
| Modificar | `repository/UsuarioRepository.java` |
| Modificar | `service/UsuarioService.java` |
| Modificar | `controller/UsuarioController.java` |
| Modificar | `security/SecurityConfig.java` |

---

### `dto/request/ChangePasswordRequestDTO.java` — archivo nuevo completo

```java
package com.example.GastroTech.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequestDTO(
        @NotBlank(message = "La contrasena actual es obligatoria")
        String currentPassword,

        @NotBlank(message = "La nueva contrasena es obligatoria")
        @Size(min = 6, message = "La nueva contrasena debe tener al menos 6 caracteres")
        String newPassword,

        @NotBlank(message = "La confirmacion de contrasena es obligatoria")
        String confirmPassword
) {}
```

---

### `repository/UsuarioRepository.java` — sin cambios nuevos

```java
// findByEmail(String email) ya existe. No hace falta añadir nada.
// Se documenta aquí para recordar que el service lo reutiliza.
Optional<Usuario> findByEmail(String email);   // ya existente
```

---

### `service/UsuarioService.java` — añadir método de cambio de contraseña

```java
// Importar al inicio:
import com.example.GastroTech.dto.request.ChangePasswordRequestDTO;
import org.springframework.security.crypto.password.PasswordEncoder;

// Añadir dependencia (Lombok la inyecta con @RequiredArgsConstructor):
private final PasswordEncoder passwordEncoder;

@Transactional
public void changePassword(String email, ChangePasswordRequestDTO dto) {
    // Verificar que nueva contraseña y confirmación coinciden
    if (!dto.newPassword().equals(dto.confirmPassword())) {
        throw new BusinessException("La nueva contrasena y su confirmacion no coinciden");
    }

    // Verificar que la nueva es distinta a la actual
    if (dto.currentPassword().equals(dto.newPassword())) {
        throw new BusinessException(
                "La nueva contrasena debe ser diferente a la actual");
    }

    Usuario usuario = usuarioRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

    // Verificar que la contraseña actual introducida es correcta
    if (!passwordEncoder.matches(dto.currentPassword(), usuario.getPassword())) {
        throw new BusinessException("La contrasena actual introducida no es correcta");
    }

    usuario.setPassword(passwordEncoder.encode(dto.newPassword()));
    usuarioRepository.save(usuario);
}
```

---

### `controller/UsuarioController.java` — añadir endpoint de cambio de contraseña

```java
// Importar al inicio:
import com.example.GastroTech.dto.request.ChangePasswordRequestDTO;

// El endpoint usa /me para referirse al usuario autenticado, no necesita @PreAuthorize ADMIN
@PatchMapping("/me/password")
@Operation(summary = "Cambiar la contrasena del usuario autenticado")
public ResponseEntity<Void> changePassword(
        @Valid @RequestBody ChangePasswordRequestDTO dto) {
    String email = SecurityContextHolder.getContext().getAuthentication().getName();
    usuarioService.changePassword(email, dto);
    return ResponseEntity.noContent().build();
}
```

---

### `security/SecurityConfig.java` — separar `/me` del resto de `/users`

```java
// Dentro de authorizeHttpRequests(), el orden de las reglas importa:
// Spring evalúa de arriba a abajo y aplica la primera que coincide.
// Por eso /me debe declararse ANTES que /users/**.

.requestMatchers("/api/v1/users/me/**").authenticated()    // cualquier usuario con JWT
.requestMatchers("/api/v1/users/**").hasRole("ADMIN")      // el resto solo ADMIN
```

> **Por qué el orden importa aquí:** si pusieramos `/api/v1/users/**` primero, la regla `hasRole('ADMIN')` capturaría también `/me/password` y un usuario normal nunca podría cambiar su contraseña aunque tuviera JWT. Al poner `/me/**` antes, Spring lo resuelve correctamente: `/me/password` → `authenticated()`, el resto de `/users/**` → `ADMIN`.

---

### Commits sugeridos

```bash
git commit -m "feat(dto): add ChangePasswordRequestDTO with current, new and confirm fields"
git commit -m "feat(service): add changePassword method with BCrypt verification to UsuarioService"
git commit -m "feat(controller): add PATCH /api/v1/users/me/password endpoint"
git commit -m "fix(security): split /users/me/** (authenticated) from /users/** (ADMIN) in SecurityConfig"
```

---

---

## Resumen completo (features 1-25)

| # | Feature | Rama | Model/DTO | Repository | Service | Controller | SecurityConfig | Dificultad |
|---|---------|------|-----------|------------|---------|------------|----------------|-----------|
| 1 | Límite de reservas activas | `feature/reservation-limit` | ✅ | ✅ | ✅ | ✅ | ❌ | ⭐⭐ |
| 2 | Sugerencia automática de mesa | `feature/table-suggestion` | ✅ | ✅ | ✅ | ✅ | ✅ | ⭐⭐ |
| 3 | Notas internas del ADMIN | `feature/internal-notes` | ✅ | ✅ | ✅ | ✅ | ❌ | ⭐⭐⭐ |
| 4 | Aforo máximo simultáneo | `feature/max-capacity` | ✅ | ✅ | ✅ | ✅ | ❌ | ⭐⭐⭐ |
| 5 | Caducidad de penalización | `feature/penalty-expiry` | ✅ | ✅ | ✅ | ❌ | ❌ | ⭐⭐⭐ |
| 6 | Bloqueo de mesa por mantenimiento | `feature/table-block` | ✅ | ✅ | ✅ | ✅ | ❌ | ⭐⭐⭐ |
| 7 | Valoración post-reserva | `feature/ratings` | ✅ | ✅ | ✅ | ✅ | ❌ | ⭐⭐⭐ |
| 8 | Transferencia de reserva | `feature/reservation-transfer` | ✅ | ❌ | ✅ | ✅ | ❌ | ⭐⭐⭐⭐ |
| 9 | Historial de cambios de estado | `feature/reservation-audit` | ✅ | ✅ | ✅ | ✅ | ❌ | ⭐⭐⭐⭐ |
| 10 | Reserva recurrente semanal | `feature/recurring-reservations` | ✅ | ✅ | ✅ | ✅ | ❌ | ⭐⭐⭐⭐ |
| 11 | Sistema de fidelización | `feature/loyalty-system` | ✅ | ✅ | ✅ | ✅ | ❌ | ⭐⭐⭐⭐ |
| 12 | Lista de espera | `feature/waitlist` | ✅ | ✅ | ✅ | ✅ | ❌ | ⭐⭐⭐⭐⭐ |
| 13 | Confirmación manual de reservas | `feature/reservation-confirm` | ❌ | ❌ | ✅ | ✅ | ❌ | ⭐⭐ |
| 14 | Check-in digital con código | `feature/checkin` | ✅ | ✅ | ✅ | ✅ | ❌ | ⭐⭐⭐ |
| 15 | Dashboard de estadísticas | `feature/statistics` | ✅ | ✅ | ✅ | ✅ | ❌ | ⭐⭐⭐ |
| 16 | Política de no-show | `feature/no-show` | ✅ | ✅ | ✅ | ❌ | ❌ | ⭐⭐⭐ |
| 17 | Búsqueda y filtrado avanzado | `feature/reservation-search` | ✅ | ✅ | ✅ | ✅ | ❌ | ⭐⭐⭐ |
| 18 | Exportación en CSV | `feature/csv-export` | ❌ | ✅ | ✅ | ✅ | ❌ | ⭐⭐⭐ |
| 19 | Cierre temporal del restaurante | `feature/restaurant-closure` | ✅ | ✅ | ✅ | ✅ | ❌ | ⭐⭐⭐ |
| 20 | Límite mensual de cancelaciones | `feature/monthly-cancellation-limit` | ✅ | ✅ | ✅ | ❌ | ❌ | ⭐⭐⭐⭐ |
| 21 | Sistema de notificaciones internas | `feature/notifications` | ✅ | ✅ | ✅ | ✅ | ❌ | ⭐⭐⭐⭐ |
| 22 | Gestión de carta del restaurante | `feature/menu-management` | ✅ | ✅ | ✅ | ✅ | ✅ | ⭐⭐⭐⭐⭐ |
| **23** | **Gestión de usuarios para ADMIN** | `feature/admin-user-management` | **✅** | **✅** | **✅** | **✅** | **✅** | **⭐⭐⭐** |
| **24** | **Consulta pública de disponibilidad** | `feature/public-availability` | **✅** | **✅** | **✅** | **✅** | **✅** | **⭐⭐** |
| **25** | **Cambio de contraseña** | `feature/change-password` | **✅** | **✅** | **✅** | **✅** | **✅** | **⭐⭐** |
