package com.example.modulocachelecturas;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class DocumentacionArquitecturaTest {

    @Test
    void generarDiagramaArquitectura() {

        ApplicationModules modulos =
                ApplicationModules.of(ModuloCacheLecturasApplication.class);

        new Documenter(modulos)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();
    }
}
