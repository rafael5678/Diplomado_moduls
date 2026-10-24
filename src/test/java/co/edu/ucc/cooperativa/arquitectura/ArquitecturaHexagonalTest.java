package co.edu.ucc.cooperativa.arquitectura;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Pruebas de Arquitectura Hexagonal con ArchUnit.
 *
 * Valida los límites y dependencias entre las capas del módulo de Reservas:
 * - Dominio: Núcleo puro de negocio, sin dependencias externas.
 * - Aplicación: Casos de uso y puertos, solo depende del Dominio.
 * - Infraestructura: Adaptadores (REST, MySQL, MongoDB, etc.), depende de Aplicación y Dominio.
 */
@AnalyzeClasses(packages = "co.edu.ucc.cooperativa", importOptions = {ImportOption.DoNotIncludeTests.class})
public class ArquitecturaHexagonalTest {

    private static final String PAQUETE_DOMINIO = "co.edu.ucc.cooperativa.reservas.dominio..";
    private static final String PAQUETE_APLICACION = "co.edu.ucc.cooperativa.reservas.aplicacion..";
    private static final String PAQUETE_INFRAESTRUCTURA = "co.edu.ucc.cooperativa.reservas.infraestructura..";

    @ArchTest
    public static final ArchRule capas_deben_respetar_jerarquia_hexagonal = layeredArchitecture()
            .consideringAllDependencies()
            .layer("Dominio").definedBy(PAQUETE_DOMINIO)
            .layer("Aplicacion").definedBy(PAQUETE_APLICACION)
            .layer("Infraestructura").definedBy(PAQUETE_INFRAESTRUCTURA)
            .whereLayer("Dominio").mayOnlyBeAccessedByLayers("Aplicacion", "Infraestructura")
            .whereLayer("Aplicacion").mayOnlyBeAccessedByLayers("Infraestructura")
            .whereLayer("Infraestructura").mayNotBeAccessedByAnyLayer();

    @ArchTest
    public static final ArchRule dominio_no_debe_depender_de_aplicacion_ni_de_infraestructura = noClasses()
            .that().resideInAPackage(PAQUETE_DOMINIO)
            .should().dependOnClassesThat().resideInAnyPackage(PAQUETE_APLICACION, PAQUETE_INFRAESTRUCTURA)
            .as("El dominio debe ser independiente y no depender de la aplicación ni de la infraestructura");

    @ArchTest
    public static final ArchRule aplicacion_no_debe_depender_de_infraestructura = noClasses()
            .that().resideInAPackage(PAQUETE_APLICACION)
            .should().dependOnClassesThat().resideInAPackage(PAQUETE_INFRAESTRUCTURA)
            .as("La capa de aplicación no debe depender de la infraestructura");
}
