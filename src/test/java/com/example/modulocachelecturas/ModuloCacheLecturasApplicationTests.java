package com.example.modulocachelecturas;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Prueba de integración del contexto completo. Requiere PostgreSQL y Redis activos (utilizar entorno Docker Compose).")
@SpringBootTest
class ModuloCacheLecturasApplicationTests {

	@Test
	void contextLoads() {
	}

}
