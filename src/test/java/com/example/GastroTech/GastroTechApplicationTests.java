package com.example.GastroTech;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test de integración: verifica que el contexto de Spring arranca correctamente.
 * Usa el perfil "test" (application-test.properties) para aislar la BD de tests.
 */
@SpringBootTest
@ActiveProfiles("test")
class GastroTechApplicationTests {

    @Test
    void contextLoads() {
        // Si este test pasa, todos los beans se han podido instanciar correctamente
    }
}
