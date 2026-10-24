package co.edu.ucc.cooperativa.arquitectura;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Reglas de Arquitectura para la Capa de Infraestructura (Adaptadores de Entrada, Salida y Configuración).
 *
 * Enfoque Puertos y Adaptadores:
 * - Los controladores REST (entrada) consumen únicamente puertos de entrada (casos de uso).
 * - Los adaptadores de persistencia y externos (salida) implementan puertos de salida.
 * - Las entidades de persistencia (JPA, Mongo) solo residen en adaptadores de salida.
 */
@AnalyzeClasses(packages = "co.edu.ucc.cooperativa.reservas.infraestructura", importOptions = {ImportOption.DoNotIncludeTests.class})
public class InfraestructuraRulesTest {

    private static final String PAQUETE_ENTRADA = "co.edu.ucc.cooperativa.reservas.infraestructura.entrada..";
    private static final String PAQUETE_SALIDA = "co.edu.ucc.cooperativa.reservas.infraestructura.salida..";
    private static final String PAQUETE_CONFIG = "co.edu.ucc.cooperativa.reservas.infraestructura.config..";
    private static final String PAQUETE_PUERTOS_SALIDA = "co.edu.ucc.cooperativa.reservas.aplicacion.puertos.salida..";

    @ArchTest
    public static final ArchRule controladores_deben_residir_en_entrada_y_terminar_en_controller = classes()
            .that().areAnnotatedWith(RestController.class)
            .or().areAnnotatedWith(Controller.class)
            .should().resideInAPackage(PAQUETE_ENTRADA)
            .andShould().haveSimpleNameEndingWith("Controller")
            .as("Los controladores web deben residir en infraestructura.entrada y tener sufijo Controller");

    @ArchTest
    public static final ArchRule clases_controller_deben_estar_anotadas_con_rest_controller = classes()
            .that().resideInAPackage(PAQUETE_ENTRADA)
            .and().haveSimpleNameEndingWith("Controller")
            .should().beAnnotatedWith(RestController.class)
            .orShould().beAnnotatedWith(Controller.class)
            .as("Las clases Controller de infraestructura.entrada deben estar anotadas con @RestController o @Controller");

    @ArchTest
    public static final ArchRule controladores_no_deben_depender_directamente_de_puertos_de_salida = noClasses()
            .that().resideInAPackage(PAQUETE_ENTRADA)
            .and().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().resideInAPackage(PAQUETE_PUERTOS_SALIDA)
            .as("Los controladores no deben comunicarse directamente con puertos de salida; deben usar puertos de entrada");

    @ArchTest
    public static final ArchRule adaptadores_de_entrada_no_deben_depender_de_adaptadores_de_salida = noClasses()
            .that().resideInAPackage(PAQUETE_ENTRADA)
            .should().dependOnClassesThat().resideInAPackage(PAQUETE_SALIDA)
            .as("Los adaptadores de entrada no deben acoplarse con adaptadores de salida");

    @ArchTest
    public static final ArchRule adaptadores_de_repositorio_deben_implementar_puertos_de_salida = classes()
            .that().resideInAPackage(PAQUETE_SALIDA)
            .and().haveSimpleNameEndingWith("Adapter")
            .should().dependOnClassesThat().resideInAPackage(PAQUETE_PUERTOS_SALIDA)
            .as("Los adaptadores de salida con sufijo Adapter deben implementar o depender de puertos de salida");

    @ArchTest
    public static final ArchRule adaptadores_de_salida_no_deben_depender_de_adaptadores_de_entrada = noClasses()
            .that().resideInAPackage(PAQUETE_SALIDA)
            .should().dependOnClassesThat().resideInAPackage(PAQUETE_ENTRADA)
            .as("Los adaptadores de salida no deben depender de los adaptadores de entrada");

    @ArchTest
    public static final ArchRule entidades_jpa_y_documentos_mongo_deben_residir_en_salida = classes()
            .that().areAnnotatedWith("jakarta.persistence.Entity")
            .or().areAnnotatedWith("org.springframework.data.mongodb.core.mapping.Document")
            .should().resideInAPackage(PAQUETE_SALIDA)
            .as("Las entidades de base de datos JPA y Mongo deben residir exclusivamente en infraestructura.salida");

    @ArchTest
    public static final ArchRule clases_de_configuracion_deben_estar_anotadas_con_configuration = classes()
            .that().resideInAPackage(PAQUETE_CONFIG)
            .should().beAnnotatedWith(Configuration.class)
            .as("Las clases en infraestructura.config deben estar anotadas con @Configuration");
}
