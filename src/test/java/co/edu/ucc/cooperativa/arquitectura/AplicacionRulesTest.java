package co.edu.ucc.cooperativa.arquitectura;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Reglas de Arquitectura para la Capa de Aplicación.
 *
 * Enfoque Arquitectura Hexagonal (Puertos y Adaptadores):
 * - Los casos de uso (puertos de entrada) y puertos de salida deben ser interfaces.
 * - Los servicios de aplicación implementan los casos de uso sin depender de anotaciones de Spring (@Service)
 *   ni de frameworks de persistencia.
 */
@AnalyzeClasses(packages = "co.edu.ucc.cooperativa.reservas.aplicacion", importOptions = {ImportOption.DoNotIncludeTests.class})
public class AplicacionRulesTest {

    private static final String PAQUETE_APLICACION = "co.edu.ucc.cooperativa.reservas.aplicacion..";
    private static final String PAQUETE_PUERTOS_ENTRADA = "co.edu.ucc.cooperativa.reservas.aplicacion.puertos.entrada..";
    private static final String PAQUETE_PUERTOS_SALIDA = "co.edu.ucc.cooperativa.reservas.aplicacion.puertos.salida..";
    private static final String PAQUETE_SERVICIOS = "co.edu.ucc.cooperativa.reservas.aplicacion.servicios..";

    @ArchTest
    public static final ArchRule aplicacion_no_debe_depender_de_spring = noClasses()
            .that().resideInAPackage(PAQUETE_APLICACION)
            .should().dependOnClassesThat().resideInAPackage("org.springframework..")
            .as("La capa de aplicación no debe tener acoplamiento con Spring");

    @ArchTest
    public static final ArchRule aplicacion_no_debe_depender_de_persistencia = noClasses()
            .that().resideInAPackage(PAQUETE_APLICACION)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "jakarta.persistence..",
                    "javax.persistence..",
                    "org.springframework.data..",
                    "com.mongodb.."
            )
            .as("La capa de aplicación no debe depender de tecnologías de persistencia");

    @ArchTest
    public static final ArchRule puertos_de_entrada_deben_ser_interfaces = classes()
            .that().resideInAPackage(PAQUETE_PUERTOS_ENTRADA)
            .should().beInterfaces()
            .as("Los puertos de entrada deben ser interfaces");

    @ArchTest
    public static final ArchRule puertos_de_entrada_deben_terminar_en_use_case_o_port = classes()
            .that().resideInAPackage(PAQUETE_PUERTOS_ENTRADA)
            .should().haveSimpleNameEndingWith("UseCase")
            .orShould().haveSimpleNameEndingWith("Port")
            .as("Los puertos de entrada deben tener sufijo UseCase o Port");

    @ArchTest
    public static final ArchRule puertos_de_salida_deben_ser_interfaces = classes()
            .that().resideInAPackage(PAQUETE_PUERTOS_SALIDA)
            .should().beInterfaces()
            .as("Los puertos de salida deben ser interfaces");

    @ArchTest
    public static final ArchRule puertos_de_salida_deben_terminar_en_port = classes()
            .that().resideInAPackage(PAQUETE_PUERTOS_SALIDA)
            .should().haveSimpleNameEndingWith("Port")
            .as("Los puertos de salida deben tener sufijo Port");

    @ArchTest
    public static final ArchRule servicios_deben_terminar_en_service = classes()
            .that().resideInAPackage(PAQUETE_SERVICIOS)
            .should().haveSimpleNameEndingWith("Service")
            .as("Las clases del paquete de servicios deben tener sufijo Service");

    @ArchTest
    public static final ArchRule servicios_deben_implementar_puertos_de_entrada = classes()
            .that().resideInAPackage(PAQUETE_SERVICIOS)
            .should().dependOnClassesThat().resideInAPackage(PAQUETE_PUERTOS_ENTRADA)
            .as("Los servicios de aplicación deben implementar o depender de puertos de entrada");

    @ArchTest
    public static final ArchRule servicios_no_deben_usar_anotacion_service_de_spring = noClasses()
            .that().resideInAPackage(PAQUETE_SERVICIOS)
            .should().beAnnotatedWith("org.springframework.stereotype.Service")
            .as("Los servicios de aplicación deben ser puros y registrarse mediante clases de configuración @Bean");
}
