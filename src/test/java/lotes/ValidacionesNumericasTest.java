package lotes;

import base.TestBase;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ============================================================================
 * Lote: Validaciones Numéricas y Tiempos (Casos ID-016 al ID-020)
 *
 * Comando para ejecutar en terminal:
 *   mvn test -Dgroups="lote-validaciones-numericas"
 *
 * Con Maven Wrapper:
 *   .\mvnw.cmd test "-Dgroups=lote-validaciones-numericas"
 * ============================================================================
 */
@Epic("Gestión de Evaluaciones")
@Feature("Validaciones Numéricas y Tiempos")
@Tag("lote-validaciones-numericas")
public class ValidacionesNumericasTest extends TestBase {

    @BeforeEach
    void setUpNuevoParcial() {
        ClockMockHelper.instalarRelojSimulado(page);
        AuthHelper.loginProfesor(page, baseUrl);
        ClockMockHelper.clearSimTime(page);
        ExamFormHelper.navegarANuevoExamen(page, baseUrl);
    }

    @AfterEach
    void tearDownValidacionesNumericas() {
        ClockMockHelper.clearSimTime(page);
        ExamFormHelper.limpiarExamenesCreados(baseUrl);
    }

    // =========================================================================
    // CASOS DE PRUEBA: PUNTAJES DE CONSIGNAS (ID-016 e ID-017)
    // =========================================================================

    @Test
    @Tag("lote-validaciones-numericas")
    @Story("ID-016 - Asignación de puntaje válido en consigna")
    @DisplayName("ID-016 - Asignación de puntaje válido en consigna")
    @Description("Asegurar que se puedan agregar consignas con puntajes dentro del rango permitido (0 a 10) y guardar dentro de la tolerancia.")
    @Severity(SeverityLevel.NORMAL)
    void testID016_AsignacionPuntajeValidoEnConsigna() {
        String tituloExamen = "Parcial ID-016 Puntaje 7 " + System.currentTimeMillis();
        ExamFormHelper.completarCamposBase(page, tituloExamen, "Tema 1", "60", "Consigna para validación de puntaje 7.");

        // Localizar el campo real de puntaje en la consigna (.puntaje-input / label Puntaje)
        Locator puntajeInput = page.locator("input.puntaje-input, label:has-text('Puntaje') input").first();
        assertTrue(puntajeInput.isVisible(),
                "[Bug ID-016] El sistema no dispone del campo para asignar puntaje numérico a la consigna.");

        puntajeInput.fill("7");
        assertEquals("7", puntajeInput.inputValue(), "El sistema no aceptó el puntaje 7 asignado.");

        // Guardar examen y validar que la petición finalice exitosamente dentro de perfToleranceMs
        long inicio = System.currentTimeMillis();
        Response resp = page.waitForResponse(
                r -> r.url().contains("/api/documentos") && r.request().method().equalsIgnoreCase("POST"),
                () -> page.locator("button[type='submit']:has-text('Crear')").click()
        );
        long tiempoRespuesta = System.currentTimeMillis() - inicio;

        page.waitForURL(url -> !url.endsWith("/nuevo") && url.contains("/documentos/"),
                new Page.WaitForURLOptions().setTimeout(5000));
        ExamFormHelper.registrarExamenParaLimpieza(page.url());

        assertTrue(resp.ok(), "La petición de guardado no fue exitosa. Código: " + resp.status());
        assertTrue(tiempoRespuesta <= perfToleranceMs,
                "El guardado demoró " + tiempoRespuesta + " ms (límite configurado: " + perfToleranceMs + " ms).");
        assertFalse(page.locator("p.error, .field-error").isVisible(),
                "Apareció un mensaje de error inesperado al guardar con puntaje válido 7.");
    }

    @Test
    @Tag("lote-validaciones-numericas")
    @Story("ID-017 - Validación de puntaje fuera de rango o inválido")
    @DisplayName("ID-017 - Validación de puntaje fuera de rango o inválido")
    @Description("Verificar que el sistema rechace puntajes de consignas menores a 0 (ej. -5) o mayores a 10 (ej. 12) bloqueando el guardado y mostrando mensaje de error.")
    @Severity(SeverityLevel.NORMAL)
    void testID017_ValidacionPuntajeFueraDeRangoOInvalido() {
        String tituloExamen = "Parcial ID-017 Puntaje Inválido " + System.currentTimeMillis();
        ExamFormHelper.completarCamposBase(page, tituloExamen, "Tema 1", "60", "Consigna para prueba de puntaje fuera de rango.");

        Locator puntajeInput = page.locator("input.puntaje-input, label:has-text('Puntaje') input").first();
        assertTrue(puntajeInput.isVisible(),
                "[Bug ID-017] El sistema no dispone del campo de puntaje para validar restricciones de formato y rango.");

        // 1. Evaluar puntaje negativo: "-5"
        puntajeInput.fill("-5");
        page.locator("button[type='submit']:has-text('Crear')").click();

        Locator errorNegativo = page.locator("p.error, .field-error").first();
        errorNegativo.waitFor();
        boolean bloqueoNegativo = page.url().contains("/documentos/nuevo");
        String textoErrorNegativo = errorNegativo.innerText().toLowerCase();
        assertTrue(bloqueoNegativo && textoErrorNegativo.contains("puntaje"),
                "El sistema no bloqueó el guardado o no mostró error ante un puntaje negativo (-5). Texto: " + textoErrorNegativo);

        // 2. Evaluar puntaje superior al máximo de la matriz (12 > 10)
        puntajeInput.fill("12");
        page.locator("button[type='submit']:has-text('Crear')").click();

        page.waitForCondition(() -> !page.url().endsWith("/nuevo") || page.locator("p.error, .field-error").count() > 0,
                new Page.WaitForConditionOptions().setTimeout(5000));

        if (!page.url().endsWith("/nuevo") && page.url().contains("/documentos/")) {
            ExamFormHelper.registrarExamenParaLimpieza(page.url());
        }

        boolean bloqueoMayorADiez = page.url().contains("/documentos/nuevo");
        String textoError12 = String.join(" ", page.locator("p.error, .field-error").allInnerTexts()).toLowerCase();
        boolean muestraErrorRango10 = textoError12.contains("puntaje") && textoError12.contains("10");

        assertTrue(bloqueoMayorADiez && muestraErrorRango10,
                "[Bug ID-017] El sistema bloqueó el puntaje negativo (-5), pero permitió guardar la consigna con puntaje 12 (el tope en la app es 100 en lugar de 10 según la matriz).");
    }

    // =========================================================================
    // CASOS DE PRUEBA: CONFIGURACIÓN DE TIEMPOS (ID-018 a ID-020)
    // =========================================================================

    @Test
    @Tag("lote-validaciones-numericas")
    @Story("ID-018 - Definición válida del tiempo límite")
    @DisplayName("ID-018 - Definición válida del tiempo límite")
    @Description("Comprobar que el profesor pueda designar un tiempo límite correcto (ej. 120 minutos) y guardar dentro de la tolerancia.")
    @Severity(SeverityLevel.NORMAL)
    void testID018_DefinicionValidaDelTiempoLimite() {
        String tituloExamen = "Parcial ID-018 Duración 120 " + System.currentTimeMillis();
        ExamFormHelper.completarCamposBase(page, tituloExamen, "Tema 1", "120", "Consigna para verificación de guardado con duración 120 min.");

        Locator duracionInput = page.locator("label:has-text('Duración') input[type='number']");
        assertEquals("120", duracionInput.inputValue(), "El campo de duración no retuvo el valor 120 ingresado.");

        // Guardar examen midiendo el tiempo de respuesta de red
        long inicio = System.currentTimeMillis();
        Response resp = page.waitForResponse(
                r -> r.url().contains("/api/documentos") && r.request().method().equalsIgnoreCase("POST"),
                () -> page.locator("button[type='submit']:has-text('Crear')").click()
        );
        long tiempoRespuesta = System.currentTimeMillis() - inicio;

        page.waitForURL(url -> !url.endsWith("/nuevo") && url.contains("/documentos/"),
                new Page.WaitForURLOptions().setTimeout(5000));
        ExamFormHelper.registrarExamenParaLimpieza(page.url());

        assertTrue(resp.ok(), "La petición de guardado no fue exitosa. Código: " + resp.status());
        assertTrue(tiempoRespuesta <= perfToleranceMs,
                "El guardado tardó " + tiempoRespuesta + " ms (límite configurado: " + perfToleranceMs + " ms).");

        page.locator("h1").first().waitFor();
        boolean duracionVisible = page.locator("text='120 min'").isVisible() || page.locator("text='120'").isVisible();
        assertTrue(duracionVisible, "No se visualiza la duración de 120 minutos en el detalle del examen.");
    }

    @Test
    @Tag("lote-validaciones-numericas")
    @Story("ID-019 - Validación del campo tiempo límite (Rango y Formato)")
    @DisplayName("ID-019 - Validación del campo tiempo límite (Rango y Formato)")
    @Description("Asegurar que el campo acepte enteros entre 1 y 480 y rechace valores inválidos del CSV ('abc', '-10', '0', '481').")
    @Severity(SeverityLevel.NORMAL)
    void testID019_ValidacionCampoTiempoLimiteRangoYFormato() {
        ExamFormHelper.completarCamposBase(page, "Parcial ID-019 Tiempo Inválido " + System.currentTimeMillis(), "Tema 1", "60", "Consigna para testing de tiempo límite.");
        Locator duracionInput = page.locator("label:has-text('Duración') input[type='number']");
        assertTrue(duracionInput.isVisible(), "No se encontró el campo de duración en el formulario.");

        // 1. Probar formato alfanumérico / texto: "abc"
        duracionInput.fill("");
        boolean rechazoTexto = false;
        try {
            duracionInput.fill("abc");
        } catch (PlaywrightException e) {
            rechazoTexto = true;
        }
        if (!rechazoTexto) {
            duracionInput.pressSequentially("abc");
            String valor = duracionInput.inputValue();
            rechazoTexto = valor.isEmpty() || !valor.equals("abc") || Boolean.TRUE.equals(duracionInput.evaluate("el => !el.checkValidity()"));
        }
        assertTrue(rechazoTexto, "El campo de duración aceptó caracteres alfanuméricos ('abc') indebidamente.");

        // 2. Probar valor negativo: "-10"
        duracionInput.fill("-10");
        page.locator("button[type='submit']:has-text('Crear')").click();
        boolean html5InvalidoNeg = Boolean.TRUE.equals(duracionInput.evaluate("el => !el.checkValidity()"));
        boolean impidioNegativo = page.url().contains("/documentos/nuevo") && (html5InvalidoNeg || page.locator("p.error, .field-error").count() > 0);
        assertTrue(impidioNegativo, "El sistema permitió guardar o no marcó error con valor negativo '-10'.");

        // 3. Probar valor "0" (inferior al mínimo de 1 minuto)
        duracionInput.fill("0");
        page.locator("button[type='submit']:has-text('Crear')").click();
        boolean html5InvalidoCero = Boolean.TRUE.equals(duracionInput.evaluate("el => !el.checkValidity()"));
        boolean impidioCero = page.url().contains("/documentos/nuevo") && (html5InvalidoCero || page.locator("p.error, .field-error").count() > 0);
        assertTrue(impidioCero, "El sistema permitió guardar o no marcó error con duración '0'.");

        // 4. Probar valor superior al límite del CSV: "481"
        duracionInput.fill("481");
        page.locator("button[type='submit']:has-text('Crear')").click();
        boolean html5Invalido481 = Boolean.TRUE.equals(duracionInput.evaluate("el => !el.checkValidity()"));
        boolean alerta481 = page.locator("p.error, .field-error").count() > 0;
        boolean impidio481 = page.url().contains("/documentos/nuevo") && (html5Invalido481 || alerta481);
        assertTrue(impidio481, "El sistema no restringió ni alertó al ingresar el valor fuera de rango '481'.");

        // 5. Verificar aceptación del borde inferior válido: "1"
        duracionInput.fill("1");
        boolean borde1Valido = "1".equals(duracionInput.inputValue()) &&
                Boolean.TRUE.equals(duracionInput.evaluate("el => el.checkValidity()"));
        assertTrue(borde1Valido, "El campo de duración no aceptó el borde inferior válido de 1 minuto.");

        // 6. Verificar aceptación del borde superior válido según matriz: "480"
        duracionInput.fill("480");
        boolean borde480Valido = "480".equals(duracionInput.inputValue()) &&
                Boolean.TRUE.equals(duracionInput.evaluate("el => el.checkValidity()"));
        assertTrue(borde480Valido,
                "[Bug ID-019] El campo de tiempo límite rechazó el borde superior válido de 480 minutos especificado en la matriz (la aplicación restringe max=120).");
    }

    @Test
    @Tag("lote-validaciones-numericas")
    @Story("ID-020 - Modificación del tiempo límite según estado del examen")
    @DisplayName("ID-020 - Modificación del tiempo límite según estado del examen")
    @Description("Permitir modificar el tiempo límite solo si ningún alumno ha iniciado el examen; en exámenes iniciados o en curso, bloquear la modificación.")
    @Severity(SeverityLevel.CRITICAL)
    void testID020_ModificacionDelTiempoLimiteSegunEstadoDelExamen() {
        String tituloExamen = "Parcial ID-020 Estado Modif " + System.currentTimeMillis();

        // 1. Crear un examen nuevo con duración 60 (Escenario A: No iniciado)
        ExamFormHelper.completarCamposBase(page, tituloExamen, "Tema 1", "60", "Consigna para prueba de modificación de tiempo límite según estado.");
        String docUrl = ExamFormHelper.guardarFormularioNuevoExamen(page);

        // --- ESCENARIO A: Examen No Iniciado ---
        Locator btnEditar = page.locator("a:has-text('Editar'), button:has-text('Editar')").first();
        btnEditar.waitFor();
        assertTrue(btnEditar.isVisible(), "No se encontró el botón 'Editar' en el examen recién creado.");
        ExamFormHelper.abrirEdicionExamen(page);

        Locator duracionInput = page.locator("label:has-text('Duración') input[type='number'], input.duration-input").first();
        assertTrue(duracionInput.isVisible(), "No se visualiza el input de duración en la vista de edición.");
        assertFalse(duracionInput.isDisabled(), "En Escenario A (No iniciado), el campo de duración debería estar habilitado.");

        // Modificar el tiempo límite a 90 minutos y guardar cambios
        duracionInput.fill("90");
        ExamFormHelper.guardarCambiosEdicionExamen(page);

        Locator duracionActualizada = page.locator(".exam-tools:has-text('90 min'), span:has-text('90 min')").first();
        duracionActualizada.waitFor(new Locator.WaitForOptions().setTimeout(5000));
        assertTrue(duracionActualizada.isVisible(), "En Escenario A, no se reflejó la modificación de duración a 90 min en el examen.");

        // --- ESCENARIO B: Examen Iniciado / En curso ---
        page.navigate(docUrl);
        ExamFormHelper.publicarExamenActual(page);

        // Adelantar la hora simulada al horario en curso del turno programado
        ClockMockHelper.setSimTime(page, "2028-03-15T13:35:00");

        // Como Profesor, navegar a la edición del examen en curso
        page.navigate(docUrl + "/editar");
        page.locator("form").first().waitFor();

        Locator duracionInputIniciado = page.locator("label:has-text('Duración') input[type='number'], input.duration-input").first();
        Locator hintBloqueo = page.locator("text='Uno de los turnos ya empezó: la duración no se puede cambiar.'");

        boolean duracionBloqueada = duracionInputIniciado.isDisabled() && hintBloqueo.isVisible();
        assertTrue(duracionBloqueada,
                "En Escenario B (Iniciado / En curso), el sistema no bloqueó la modificación del tiempo límite.");
    }
}
