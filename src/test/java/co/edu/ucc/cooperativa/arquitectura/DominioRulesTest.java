package co.edu.ucc.cooperativa.arquitectura;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Reglas de Arquitectura para el Dominio (Pure Domain).
 *
 * Enfoque DDD / Arquitectura Hexagonal:
 * - El Dominio no debe estar contaminado por ningún framework (Spring, Jakarta, Lombok).
 * - Debe ser Java 17+ puro, garantizando total portabilidad e independencia de infraestructura.
 */
@AnalyzeClasses(packages = "co.edu.ucc.cooperativa.reservas.dominio", importOptions = {ImportOption.DoNotIncludeTests.class})
public class DominioRulesTest {

    private static final String PAQUETE_DOMINIO = "co.edu.ucc.cooperativa.reservas.dominio..";

    @ArchTest
    public static final ArchRule dominio_no_debe_depender_de_spring = noClasses()
            .that().resideInAPackage(PAQUETE_DOMINIO)
            .should().dependOnClassesThat().resideInAPackage("org.springframework..")
            .as("El Dominio debe ser agnóstico del framework y no debe depender de Spring");

    @ArchTest
    public static final ArchRule dominio_no_debe_depender_de_tecnologias_de_persistencia = noClasses()
            .that().resideInAPackage(PAQUETE_DOMINIO)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "jakarta.persistence..",
                    "javax.persistence..",
                    "org.springframework.data..",
                    "com.mongodb..",
                    "org.springframework.data.redis.."
            )
            .as("El Dominio no debe tener acoplamiento con tecnologías de persistencia (JPA, Mongo, Redis)");

    @ArchTest
    public static final ArchRule dominio_no_debe_depender_de_lombok = noClasses()
            .that().resideInAPackage(PAQUETE_DOMINIO)
            .should().dependOnClassesThat().resideInAPackage("lombok..")
            .as("El Dominio debe ser Java puro sin dependencia de librerías como Lombok");

    @ArchTest
    public static final ArchRule clases_del_dominio_no_deben_tener_estereotipos_spring = noClasses()
            .that().resideInAPackage(PAQUETE_DOMINIO)
            .should().beAnnotatedWith("org.springframework.stereotype.Component")
            .orShould().beAnnotatedWith("org.springframework.stereotype.Service")
            .orShould().beAnnotatedWith("org.springframework.stereotype.Repository")
            .orShould().beAnnotatedWith("org.springframework.stereotype.Controller")
            .orShould().beAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .as("Las clases y entidades de dominio no deben usar estereotipos de Spring");

    @ArchTest
    public static final ArchRule excepciones_de_dominio_deben_residir_en_dominio_y_heredar_de_runtime_exception = classes()
            .that().resideInAPackage(PAQUETE_DOMINIO)
            .and().haveSimpleNameEndingWith("Exception")
            .should().beAssignableTo(RuntimeException.class)
            .as("Las excepciones del dominio deben residir en el dominio y ser subclases de RuntimeException");
}
