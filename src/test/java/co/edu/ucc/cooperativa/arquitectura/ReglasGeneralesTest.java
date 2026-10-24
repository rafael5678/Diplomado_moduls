package co.edu.ucc.cooperativa.arquitectura;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Reglas Generales de Calidad de Código y Diseño Arquitectónico.
 *
 * - Ausencia de dependencias cíclicas entre módulos y capas.
 * - Inyección de dependencias exclusivamente por constructor (sin @Autowired en atributos).
 * - Prohibición de flujos estándar y java.util.logging en código productivo.
 */
@AnalyzeClasses(packages = "co.edu.ucc.cooperativa", importOptions = {ImportOption.DoNotIncludeTests.class})
public class ReglasGeneralesTest {

    @ArchTest
    public static final ArchRule paquetes_deben_estar_libres_de_ciclos = slices()
            .matching("co.edu.ucc.cooperativa.reservas.(*)..")
            .should().beFreeOfCycles()
            .as("Los paquetes principales de reservas no deben tener dependencias cíclicas");

    @ArchTest
    public static final ArchRule no_debe_haber_inyeccion_por_campo = GeneralCodingRules
            .NO_CLASSES_SHOULD_USE_FIELD_INJECTION
            .as("No se permite inyección de dependencias por campo con @Autowired; debe usarse inyección por constructor");

    @ArchTest
    public static final ArchRule no_debe_usarse_java_util_logging = GeneralCodingRules
            .NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING
            .as("No se permite usar java.util.logging directamente en clases productivas");

    @ArchTest
    public static final ArchRule no_debe_accederse_a_flujos_estandar = GeneralCodingRules
            .NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
            .as("No se permite usar System.out ni System.err en código de producción");
}
