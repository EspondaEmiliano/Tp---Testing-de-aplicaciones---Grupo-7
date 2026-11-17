package lotes;

import base.TestBase;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import helpers.AuthHelper;
import helpers.ClockMockHelper;
import helpers.ExamFormHelper;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ============================================================================
 * Lote: Gestión de Temas y Puntajes (Casos ID-036 al ID-043)
 *
 * Comando para ejecutar en terminal:
 *   mvn test -Dgroups="lote-temas-examen"
 *
 * Con Maven Wrapper:
 *   .\mvnw.cmd test "-Dgroups=lote-temas-examen"
 * ============================================================================
 */
@Epic("Gestión de Evaluaciones")
@Feature("Gestión de Temas y Puntajes")
@Tag("lote-temas-examen")
public class GestionTemasExamenTest extends TestBase {

    @BeforeEach
    void setUpGestionTemas() {
        ClockMockHelper.instalarRelojSimulado(page);
        AuthHelper.loginProfesor(page, baseUrl);
        ClockMockHelper.clearSimTime(page);
        ExamFormHelper.navegarANuevoExamen(page, baseUrl);
    }

    @AfterEach
    void tearDownGestionTemas() {
        ClockMockHelper.clearSimTime(page);
        ExamFormHelper.limpiarExamenesCreados(baseUrl);
    }

    // ========================================================================
    // 1. GESTIÓN DE TEMAS (ID-036 a ID-038)
    // ========================================================================

    @Test
    @Tag("lote-temas-examen")
    @Story("ID-036 - Creación exitosa de múltiples temas para un parcial")
    @DisplayName("ID-036 - Creación exitosa de múltiples temas para un parcial")
    @Description("Verificar que un profesor pueda crear al menos 2 temas distintos para un mismo parcial con sus respectivas consignas y puntajes válidos (Tema A y Tema B con 10 puntos cada uno) y guardar en < 1s.")
    @Severity(SeverityLevel.CRITICAL)
    void testID036_CreacionExitosaDeMultiplesTemasParaUnParcial() {
        String titulo = "Parcial Multi-Tema ID-036 " + System.currentTimeMillis();

        // 1. Completar campos base del examen
        ExamFormHelper.completarCamposBase(page, titulo, "Tema 1", "60", "Consigna 1 de Tema A (5 puntos)");

        Locator puntajeInputs = page.locator("input.puntaje-input, label:has-text('Puntaje') input");
        if (puntajeInputs.count() > 0 && puntajeInputs.first().isVisible()) {
            puntajeInputs.first().fill("5");
        }

        // Agregar segunda consigna de 5 puntos (Total: 10)
        Locator selectAgregarApartado = page.locator("select.block-add, select[aria-label='Agregar apartado']");
        if (selectAgregarApartado.count() > 0 && selectAgregarApartado.first().isVisible()) {
            selectAgregarApartado.first().selectOption("texto");
            page.locator("textarea[placeholder*='consigna']").nth(1).fill("Consigna 2 de Tema A (5 puntos)");
            puntajeInputs.nth(1).fill("5");
        }

        // 2. Intentar configurar un segundo tema ("Tema B" / "Tema 2") dentro del mismo parcial
        Locator btnAgregarTema = page.locator("button:has-text('Agregar tema'), button:has-text('+ Tema'), .add-theme");
        boolean permiteMultiplesTemas = btnAgregarTema.count() > 0 && btnAgregarTema.first().isVisible();

        if (permiteMultiplesTemas) {
            btnAgregarTema.first().click();
            Locator inputTema2 = page.locator("input[placeholder*='tema'], select[name*='tema']").last();
            inputTema2.fill("Tema B");

            long inicio = System.currentTimeMillis();
            Response resp = page.waitForResponse(
                    r -> r.url().contains("/api/documentos") && r.request().method().equalsIgnoreCase("POST"),
                    () -> page.locator("button[type='submit']:has-text('Crear')").click()
            );
            long duracionMs = System.currentTimeMillis() - inicio;

            page.waitForURL(url -> !url.endsWith("/nuevo") && url.contains("/documentos/"));
            ExamFormHelper.registrarExamenParaLimpieza(page.url());

            assertTrue(resp.ok(), "Error HTTP al guardar el examen multi-tema: " + resp.status());
            assertTrue(duracionMs <= perfToleranceMs,
                    "El guardado del parcial con múltiples temas excedió el tiempo esperado (" + duracionMs + " ms).");

            boolean temaAPresente = page.locator("text='Tema A'").isVisible() || page.locator("text='Tema 1'").isVisible();
            boolean temaBPresente = page.locator("text='Tema B'").isVisible() || page.locator("text='Tema 2'").isVisible();
            assertTrue(temaAPresente && temaBPresente,
                    "El examen no se creó correctamente con ambos temas ('Tema A' y 'Tema B').");
        } else {
            assertTrue(permiteMultiplesTemas,
                    "[Bug ID-036] La interfaz no permite agregar múltiples temas simultáneos ('Tema A' y 'Tema B') para un mismo parcial.");
        }
    }

    @Test
    @Tag("lote-temas-examen")
    @Story("ID-037 - Validación de unicidad de nombre de tema por parcial")
    @DisplayName("ID-037 - Validación de unicidad de nombre de tema por parcial")
    @Description("Impedir que se creen dos temas con exactamente el mismo nombre dentro de un mismo parcial.")
    @Severity(SeverityLevel.NORMAL)
    void testID037_ValidacionDeUnicidadDeNombreDeTemaPorParcial() {
        ExamFormHelper.completarCamposBase(page, "Parcial Temas Repetidos ID-037", "Tema 1", "60", "Consigna para test de temas repetidos.");

        Locator btnAgregarTema = page.locator("button:has-text('Agregar tema'), button:has-text('+ Tema'), .add-theme");
        boolean permiteMultiplesTemas = btnAgregarTema.count() > 0 && btnAgregarTema.first().isVisible();

        if (permiteMultiplesTemas) {
            btnAgregarTema.first().click();
            Locator inputTema2 = page.locator("input[placeholder*='tema'], select[name*='tema']").last();
            inputTema2.fill("Tema 1");

            page.locator("button[type='submit']:has-text('Crear')").click();
            page.locator("p.error, .field-error").first().waitFor();

            String textoError = String.join(" ", page.locator("p.error, .field-error, .toast-error").allInnerTexts()).toLowerCase();
            boolean indicaNombreRepetido = textoError.contains("repetido") ||
                    textoError.contains("duplicado") ||
                    textoError.contains("mismo nombre") ||
                    textoError.contains("ya existe");
            boolean impidioCreacion = page.url().contains("/documentos/nuevo");

            assertTrue(impidioCreacion && indicaNombreRepetido,
                    "El sistema no impidió la creación ni alertó sobre nombres de tema repetidos ('Tema 1'). Texto de error: " + textoError);
        } else {
            assertTrue(permiteMultiplesTemas,
                    "[Bug ID-037] La interfaz no permite agregar temas múltiples para validar la restricción por nombres repetidos ('Tema 1').");
        }
    }

    @Test
    @Tag("lote-temas-examen")
    @Story("ID-038 - Creación de temas mediante pestañas expansibles (hasta 3)")
    @DisplayName("ID-038 - Creación de temas mediante pestañas expansibles (hasta 3)")
    @Description("Verificar que el sistema permita crear hasta 3 temas para un parcial y editar cada uno seleccionándolo desde su pestaña expansible.")
    @Severity(SeverityLevel.CRITICAL)
    void testID038_CreacionDeTemasMediantePestanasExpansibles() {
        String titulo = "Parcial Pestañas ID-038 " + System.currentTimeMillis();
        ExamFormHelper.completarCamposBase(page, titulo, "Tema 1", "60", "Consigna 1 del Tema 1 (5 puntos)");

        Locator puntajeInputs = page.locator("input.puntaje-input, label:has-text('Puntaje') input");
        if (puntajeInputs.count() > 0 && puntajeInputs.first().isVisible()) {
            puntajeInputs.first().fill("5");
        }

        // Agregar Consigna 2 a Tema 1 con 5 puntos
        Locator selectAgregarApartado = page.locator("select.block-add, select[aria-label='Agregar apartado']");
        if (selectAgregarApartado.count() > 0 && selectAgregarApartado.first().isVisible()) {
            selectAgregarApartado.first().selectOption("texto");
            page.locator("textarea[placeholder*='consigna']").nth(1).fill("Consigna 2 del Tema 1 (5 puntos)");
            puntajeInputs.nth(1).fill("5");
        }

        // Verificar existencia de pestañas expansibles para agregar y editar Tema 2 y Tema 3 (hasta 3 temas)
        Locator btnAgregarTema = page.locator("button:has-text('Agregar tema'), button:has-text('+ Tema'), .add-theme");
        Locator pestanasTema = page.locator(".theme-tab, [role='tab']:has-text('Tema'), details.theme-panel summary");
        boolean soportaPestanasExpansibles = (btnAgregarTema.count() > 0 && btnAgregarTema.first().isVisible())
                || pestanasTema.count() >= 2;

        if (soportaPestanasExpansibles) {
            // Crear Tema 2 y Tema 3 (hasta 3 temas) y desplegar cada pestaña expansible
            btnAgregarTema.first().click();
            btnAgregarTema.first().click();

            assertTrue(pestanasTema.count() >= 3, "No se generaron las 3 pestañas expansibles de temas.");
            for (int i = 0; i < 3; i++) {
                pestanasTema.nth(i).click();
            }

            ExamFormHelper.guardarFormularioNuevoExamen(page);
            assertTrue(page.locator("text='Tema 1'").isVisible()
                            && page.locator("text='Tema 2'").isVisible()
                            && page.locator("text='Tema 3'").isVisible(),
                    "El examen no conservó los 3 temas configurados en sus pestañas expansibles.");
        } else {
            assertTrue(soportaPestanasExpansibles,
                    "[Bug ID-038] La interfaz no dispone de pestañas expansibles para crear y editar hasta 3 temas en un mismo parcial.");
        }
    }

    @Test
    @Tag("auxiliar")
    @Story("Auxiliar - Validación de campo de tema vacío")
    @DisplayName("Validación auxiliar - Examen con campo de tema vacío")
    @Description("Intenta crear el parcial dejando el nombre del tema vacío y aserta el mensaje de campo obligatorio.")
    @Severity(SeverityLevel.NORMAL)
    void testValidacionCampoDeTemaVacio() {
        ExamFormHelper.completarCamposBase(page, "Parcial Validación Tema Vacío", null, "60", "Consigna para examen con tema vacío.");

        // Intentar guardar el examen sin haber seleccionado un tema
        page.locator("button[type='submit']:has-text('Crear')").click();

        Locator errorMsg = page.locator("p.error, .field-error").first();
        errorMsg.waitFor();

        String textoError = String.join(" ", page.locator("p.error, .field-error").allInnerTexts()).toLowerCase();
        boolean impidioGuardado = page.url().contains("/documentos/nuevo");
        boolean mensajeCampoObligatorio = textoError.contains("tema") ||
                textoError.contains("elegí") ||
                textoError.contains("obligatorio");

        assertTrue(impidioGuardado && mensajeCampoObligatorio,
                "El sistema no impidió el guardado ni alertó sobre el campo obligatorio de tema vacío. Mensaje: " + textoError);
    }

    // ========================================================================
    // 2. LÍMITES Y SIMETRÍA DE PUNTAJES (ID-039 a ID-041)
    // ========================================================================

    @Test
    @Tag("lote-temas-examen")
    @Story("ID-039 - Examen con consignas con puntaje sumado =-1")
    @DisplayName("ID-039 - Examen con consignas con puntaje sumado =-1")
    @Description("Verificar que el sistema no permite crear o guardar un tema con consignas cuya suma de puntajes sea menor a 0 (ej. -1).")
    @Severity(SeverityLevel.NORMAL)
    void testID039_ExamenConConsignasConPuntajeSumadoNegativo() {
        ExamFormHelper.completarCamposBase(page, "Parcial Puntaje Negativo ID-039", "Tema 1", "60", "Consigna con puntaje negativo.");

        Locator puntajeInput = page.locator("input.puntaje-input, label:has-text('Puntaje') input").first();
        assertTrue(puntajeInput.isVisible(),
                "[Bug ID-039] El sistema no dispone del motor de puntaje de consignas para validar puntaje menor a 0.");

        puntajeInput.fill("-1");
        page.locator("button[type='submit']:has-text('Crear')").click();

        Locator errorMsg = page.locator("p.error, .field-error").first();
        errorMsg.waitFor();

        String textoError = String.join(" ", page.locator("p.error, .field-error").allInnerTexts()).toLowerCase();
        boolean errorMenorACero = textoError.contains("puntaje") || textoError.contains("0,01");
        boolean bloqueoGuardado = page.url().contains("/documentos/nuevo");

        assertTrue(bloqueoGuardado && errorMenorACero,
                "El sistema no arrojó error ni bloqueó el guardado al ingresar puntaje -1. Texto obtenido: " + textoError);
    }

    @Test
    @Tag("lote-temas-examen")
    @Story("ID-040 - Examen con consignas con puntaje sumado =11")
    @DisplayName("ID-040 - Examen con consignas con puntaje sumado =11")
    @Description("Verificar que el sistema no permite crear o guardar un tema con consignas cuya suma de puntajes sea mayor a 10 (ej. 11).")
    @Severity(SeverityLevel.NORMAL)
    void testID040_ExamenConConsignasConPuntajeSumadoMayorADiez() {
        String titulo = "Parcial Puntaje Excedido ID-040 " + System.currentTimeMillis();
        ExamFormHelper.completarCamposBase(page, titulo, "Tema 1", "60", "Consigna 1 con 6 puntos.");

        Locator puntajeInputs = page.locator("input.puntaje-input, label:has-text('Puntaje') input");
        assertTrue(puntajeInputs.first().isVisible(),
                "[Bug ID-040] El sistema no dispone del motor de puntaje de consignas para bloquear excesos de puntaje (> 10).");

        // Configurar 2 consignas cuyos puntajes suman 11 (6 + 5 = 11 > 10)
        puntajeInputs.first().fill("6");
        page.locator("select.block-add, select[aria-label='Agregar apartado']").first().selectOption("texto");
        page.locator("textarea[placeholder*='consigna']").nth(1).fill("Consigna 2 con 5 puntos (suma 11).");
        puntajeInputs.nth(1).fill("5");

        page.locator("button[type='submit']:has-text('Crear')").click();
        page.waitForCondition(() -> !page.url().endsWith("/nuevo") || page.locator("p.error, .field-error").count() > 0,
                new Page.WaitForConditionOptions().setTimeout(5000));

        if (!page.url().endsWith("/nuevo") && page.url().contains("/documentos/")) {
            ExamFormHelper.registrarExamenParaLimpieza(page.url());
        }

        String textoError = String.join(" ", page.locator("p.error, .field-error").allInnerTexts()).toLowerCase();
        boolean errorExceso = textoError.contains("10") ||
                textoError.contains("máximo") ||
                textoError.contains("maximo") ||
                textoError.contains("supera");
        boolean bloqueoGuardado = page.url().contains("/documentos/nuevo");

        assertTrue(bloqueoGuardado && errorExceso,
                "[Bug ID-040] El sistema permitió guardar un examen cuyas consignas suman 11 puntos sin validar que el puntaje total no supere 10.");
    }

    @Test
    @Tag("lote-temas-examen")
    @Story("ID-041 - Examen con temas con puntaje distinto")
    @DisplayName("ID-041 - Examen con temas con puntaje distinto")
    @Description("Verificar que el sistema no permite crear un parcial con temas con puntaje distinto entre sí (Tema 1: 7 pts y Tema 2: 8 pts).")
    @Severity(SeverityLevel.CRITICAL)
    void testID041_ExamenConTemasConPuntajeDistinto() {
        ExamFormHelper.completarCamposBase(page, "Parcial Disparidad Temas ID-041", "Tema 1", "60", "Consigna de Tema 1 (7 puntos)");

        Locator puntajeInputs = page.locator("input.puntaje-input, label:has-text('Puntaje') input");
        if (puntajeInputs.count() > 0 && puntajeInputs.first().isVisible()) {
            puntajeInputs.first().fill("7");
        }

        Locator btnAgregarTema = page.locator("button:has-text('Agregar tema'), button:has-text('+ Tema'), .add-theme");
        boolean permiteMultiplesTemas = btnAgregarTema.count() > 0 && btnAgregarTema.first().isVisible();

        if (permiteMultiplesTemas) {
            btnAgregarTema.first().click();
            page.locator("textarea[placeholder*='consigna']").last().fill("Consigna de Tema 2 (8 puntos)");
            puntajeInputs.last().fill("8");

            page.locator("button[type='submit']:has-text('Crear')").click();
            page.locator("p.error, .field-error").first().waitFor();

            String textoError = String.join(" ", page.locator("p.error, .field-error, .theme-score-warning, .score-mismatch").allInnerTexts()).toLowerCase();
            boolean bloqueoCreacion = page.url().contains("/documentos/nuevo");
            boolean alertaDisparidad = textoError.contains("disparidad") ||
                    textoError.contains("puntaje") ||
                    textoError.contains("no coinciden") ||
                    textoError.contains("igual");

            assertTrue(bloqueoCreacion && alertaDisparidad,
                    "El sistema no bloqueó la creación ni alertó sobre la disparidad de puntajes entre Tema 1 (7 pts) y Tema 2 (8 pts). Texto obtenido: " + textoError);
        } else {
            assertTrue(permiteMultiplesTemas,
                    "[Bug ID-041] El sistema no dispone de control multi-tema para configurar Tema 1 (7 pts) y Tema 2 (8 pts) y validar el bloqueo por disparidad.");
        }
    }

    // ========================================================================
    // 3. EDICIÓN Y ELIMINACIÓN SEGÚN ESTADO DEL EXAMEN (ID-042 e ID-043)
    // ========================================================================

    @Test
    @Tag("lote-temas-examen")
    @Story("ID-042 - Edición y eliminación de un tema antes de ser iniciado")
    @DisplayName("ID-042 - Edición y eliminación de un tema antes de ser iniciado")
    @Description("Verificar que el sistema permite editar las consignas de un tema dentro de su pestaña expansible y eliminar un tema antes de que un alumno lo haya iniciado.")
    @Severity(SeverityLevel.CRITICAL)
    void testID042_EdicionYEliminacionDeTemaAntesDeSerIniciado() {
        String tituloOriginal = "Parcial Edicion ID-042 " + System.currentTimeMillis();
        String consignaEditada = "Consigna actualizada de Tema 1 antes del inicio.";

        // 1. Crear un examen que queda en estado no iniciado
        ExamFormHelper.completarCamposBase(page, tituloOriginal, "Tema 1", "60", "Consigna inicial para prueba de edición.");
        ExamFormHelper.guardarFormularioNuevoExamen(page);

        // 2. Acceder a la vista de edición del examen no iniciado
        Locator btnEditar = page.locator("a:has-text('Editar'), button:has-text('Editar')").first();
        btnEditar.waitFor();
        assertTrue(btnEditar.isVisible(), "No se encontró el botón 'Editar' en el examen no iniciado.");
        ExamFormHelper.abrirEdicionExamen(page);

        // 3. Editar la consigna del tema (y verificar si existen pestañas expansibles de temas para eliminar un tema secundario)
        Locator consignaInput = page.locator("textarea[placeholder*='consigna']").first();
        assertTrue(consignaInput.isVisible() && !consignaInput.isDisabled(),
                "La consigna del tema no está habilitada para edición antes de iniciar el examen.");
        consignaInput.fill(consignaEditada);

        // Guardar la edición de la consigna
        ExamFormHelper.guardarCambiosEdicionExamen(page);

        Locator promptActualizado = page.locator(".block-prompt:has-text('" + consignaEditada + "')").first();
        promptActualizado.waitFor(new Locator.WaitForOptions().setTimeout(5000));
        assertTrue(promptActualizado.isVisible(),
                "El sistema no reflejó la edición de la consigna del tema antes de ser iniciado.");

        // 4. Eliminar el examen/tema no iniciado y comprobar su eliminación exitosa
        Locator btnEliminar = page.locator("button.danger:has-text('Eliminar')").first();
        assertTrue(btnEliminar.isVisible() && !btnEliminar.isDisabled(),
                "El botón 'Eliminar' no está disponible antes de que el examen sea iniciado.");

        page.onceDialog(dialog -> dialog.accept());
        Response deleteResp = page.waitForResponse(
                r -> r.url().contains("/api/documentos/") && r.request().method().equalsIgnoreCase("DELETE"),
                btnEliminar::click
        );
        assertTrue(deleteResp.ok(), "El sistema no permitió eliminar el tema/examen antes de ser iniciado. HTTP " + deleteResp.status());
    }

    @Test
    @Tag("lote-temas-examen")
    @Story("ID-043 - Edición y eliminación de un tema después de ser iniciado")
    @DisplayName("ID-043 - Edición y eliminación de un tema después de ser iniciado")
    @Description("Verificar que el sistema no permite editar o eliminar el tema ni sus consignas una vez que algún alumno ha iniciado el examen.")
    @Severity(SeverityLevel.BLOCKER)
    void testID043_EdicionYEliminacionDeTemaDespuesDeSerIniciado() {
        String tituloExamen = "Parcial Iniciado Protegido ID-043 " + System.currentTimeMillis();

        // 1. Crear examen como Profesor
        ExamFormHelper.completarCamposBase(page, tituloExamen, "Tema 1", "60", "Consigna protegida para examen iniciado.");
        String docUrl = ExamFormHelper.guardarFormularioNuevoExamen(page);

        // 2. Activar y publicar el examen ("Tomar ahora") y adelantar hora simulada
        ExamFormHelper.tomarExamenAhora(page);
        ClockMockHelper.setSimTime(page, "2028-03-15T13:35:00");
        ClockMockHelper.clearSimTime(page);

        // 3. Contexto secundario del Alumno: abrir e iniciar efectivamente el examen en Modo Restringido
        BrowserContext contextAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = contextAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);

        pageAlumno.navigate(baseUrl + "/alumno/parciales");
        pageAlumno.locator("h1:has-text('Parciales')").waitFor();

        Locator cardExamen = pageAlumno.locator("li.student-exam:has-text('" + tituloExamen + "')").first();
        cardExamen.waitFor();
        cardExamen.locator("a.button").first().click();

        // Aceptar reglas del modo restringido e ingresar al examen
        Locator checkReglas = pageAlumno.locator("label.restricted-accept input[type='checkbox']");
        checkReglas.waitFor();
        checkReglas.check();

        Response respComenzar = pageAlumno.waitForResponse(
                r -> r.url().contains("/comenzar") && r.request().method().equalsIgnoreCase("POST"),
                () -> pageAlumno.locator("button:has-text('Ingresar al examen')").click()
        );

        // Aserción obligatoria: confirmar que el alumno efectivamente inició el examen
        assertTrue(respComenzar.ok(),
                "El alumno no pudo iniciar el examen (falló POST /comenzar con HTTP " + respComenzar.status() + ").");
        pageAlumno.locator(".exam-content").first().waitFor();
        assertTrue(pageAlumno.locator(".exam-content").isVisible(),
                "El alumno no ingresó a la vista activa de resolución del examen (.exam-content).");

        // 4. Como Profesor, recargar datos y acceder a la vista de edición (/documentos/:id/editar)
        page.navigate(docUrl + "/editar");
        page.locator("form").first().waitFor();

        // 5. Validar que el selector de Tema esté deshabilitado, las consignas no sean editables ni eliminables y se muestre el aviso
        Locator selectorTema = page.locator("label:has-text('Tema') select").first();
        Locator avisoBloqueo = page.locator("p.notice:has-text('un alumno ya empezó el parcial')");
        Locator consignasEditables = page.locator(".blocks textarea");
        Locator botonesEliminarConsigna = page.locator(".blocks button:has-text('Eliminar')");

        assertTrue(selectorTema.isDisabled(),
                "El selector de Tema sigue habilitado después de que un alumno inició el examen.");
        assertTrue(avisoBloqueo.isVisible(),
                "No se mostró el aviso de bloqueo de edición indicando que un alumno ya empezó el parcial.");
        assertEquals(0, consignasEditables.count(),
                "Las consignas siguen siendo editables después de que un alumno inició el examen.");
        assertEquals(0, botonesEliminarConsigna.count(),
                "Los botones para eliminar consignas siguen presentes después de que un alumno inició el examen.");
    }
}
