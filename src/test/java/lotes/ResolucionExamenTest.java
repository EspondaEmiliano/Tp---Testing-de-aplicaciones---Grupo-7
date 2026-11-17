package lotes;

import base.TestBase;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import helpers.AuthHelper;
import helpers.ClockMockHelper;
import helpers.ExamFormHelper;
import helpers.StudentExamHelper;
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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ============================================================================
 * Lote: Resolución de Examen y Herramientas de Trabajo (Casos ID-054, ID-021, ID-022 e ID-023)
 *
 * Comando para ejecutar en terminal:
 *   mvn test -Dgroups="lote-resolucion-examen"
 *
 * Con Maven Wrapper:
 *   .\mvnw.cmd test "-Dgroups=lote-resolucion-examen"
 * ============================================================================
 */
@Epic("Ejecución y Resolución de Examen")
@Feature("Apertura en Turno y Herramientas del Alumno")
@Tag("lote-resolucion-examen")
public class ResolucionExamenTest extends TestBase {

    @BeforeEach
    void setUpResolucionExamen() {
        ClockMockHelper.instalarRelojSimulado(page);
        AuthHelper.loginProfesor(page, baseUrl);
    }

    @AfterEach
    void tearDownResolucionExamen() {
        ClockMockHelper.clearSimTime(page);
        ExamFormHelper.limpiarExamenesCreados(baseUrl);
    }

    @Test
    @Tag("lote-resolucion-examen")
    @Story("ID-054 - Apertura exitosa de examen dentro del horario de turno")
    @DisplayName("ID-054 - Apertura exitosa de examen dentro del horario de turno")
    @Description("Abrir un examen publicado dentro de su horario de turno como alumno1. Validar carga < 3s, visibilidad de enunciado, consignas, herramientas habilitadas y registro único de fecha/hora de primera apertura.")
    @Severity(SeverityLevel.BLOCKER)
    void testID054_AperturaExitosaDeExamenDentroDelHorarioDeTurno() {
        String titulo = "Parcial Apertura ID054 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                true,
                true,
                List.of("Primera consigna de apertura.", "Segunda consigna de apertura.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);

        // Medir el tiempo de carga al abrir la vista del examen en horario de turno desde el panel del alumno
        pageAlumno.navigate(baseUrl + "/alumno/parciales");
        Locator card = pageAlumno.locator("li.student-exam:has-text('" + titulo + "')").first();
        card.waitFor();

        long inicioCarga = System.currentTimeMillis();
        Response respGetExamen = pageAlumno.waitForResponse(
                r -> r.url().endsWith("/api/examen/" + docId) && r.request().method().equalsIgnoreCase("GET"),
                () -> card.locator("a:has-text('Abrir'), a.button").first().click()
        );
        pageAlumno.locator("section.restricted-rules").waitFor();
        long duracionCargaMs = System.currentTimeMillis() - inicioCarga;

        assertTrue(respGetExamen.ok(), "La carga del examen devolvió estado HTTP de error: " + respGetExamen.status());
        assertTrue(duracionCargaMs <= Math.max(3000, perfToleranceMs),
                "La carga inicial del examen dentro del turno superó los 3 segundos: " + duracionCargaMs + " ms");

        // Aceptar reglas e ingresar al examen
        pageAlumno.locator("label.restricted-accept input[type='checkbox']").check();
        Response respComenzar = pageAlumno.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/comenzar") && r.request().method().equalsIgnoreCase("POST"),
                () -> pageAlumno.locator("button:has-text('Ingresar al examen')").click()
        );
        assertTrue(respComenzar.ok(), "Falló POST /comenzar al iniciar el examen: HTTP " + respComenzar.status());
        pageAlumno.locator(".exam-content").first().waitFor();

        // Extraer timestamp de primera apertura de la respuesta del servidor
        String bodyComenzar = respComenzar.text();
        Matcher mApertura = Pattern.compile("\"apertura\"\\s*:\\s*\"([^\"]+)\"").matcher(bodyComenzar);
        assertTrue(mApertura.find(), "La respuesta de POST /comenzar no registró el timestamp 'apertura' de primera apertura.");
        String primeraAperturaIso = mApertura.group(1);
        assertNotNull(primeraAperturaIso);

        // Validar visibilidad de título/enunciado, consignas y herramientas habilitadas
        assertTrue(pageAlumno.locator("h1:has-text('" + titulo + "')").isVisible(),
                "No se visualiza el título del examen.");
        assertEquals(2, pageAlumno.locator(".block-prompt").count(),
                "No se visualizan las 2 consignas del examen.");
        assertEquals(2, pageAlumno.locator("textarea[aria-label='Respuesta']").count(),
                "No están habilitados los campos de respuesta de las consignas.");
        assertTrue(pageAlumno.locator("aside.tools-dock button.tools-tab:has-text('Calculadora')").isVisible(),
                "No se muestra la herramienta Calculadora habilitada.");
        assertTrue(pageAlumno.locator("aside.tools-dock button.tools-tab:has-text('GeoGebra')").isVisible(),
                "No se muestra la herramienta GeoGebra habilitada.");

        // Verificar que la fecha/hora de primera apertura sea un registro único e inmutable ante consultas posteriores
        String segundaConsultaApertura = (String) pageAlumno.evaluate(
                "async (id) => {" +
                "  const token = sessionStorage.getItem('rendix_token');" +
                "  const r = await fetch('/api/examen/' + id, { headers: { 'Authorization': 'Bearer ' + token } });" +
                "  const j = await r.json();" +
                "  return j.sesion?.apertura ?? null;" +
                "}",
                docId
        );
        assertEquals(primeraAperturaIso, segundaConsultaApertura,
                "El registro de fecha/hora de primera apertura cambió tras una nueva consulta al servidor.");
    }

    @Test
    @Tag("lote-resolucion-examen")
    @Story("ID-021 - Apertura del examen y disponibilidad de herramientas")
    @DisplayName("ID-021 - Apertura del examen y disponibilidad de herramientas")
    @Description("Crear y publicar un examen solo con Escritura y Calculadora habilitadas (GeoGebra deshabilitado). Al abrirlo como alumno, comprobar que la barra de herramientas muestre exclusivamente Escritura y Calculadora.")
    @Severity(SeverityLevel.CRITICAL)
    void testID021_AperturaDelExamenYDisponibilidadDeHerramientas() {
        String titulo = "Parcial Herramientas ID021 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                true,   // Calculadora habilitada
                false,  // GeoGebra deshabilitado
                List.of("Consigna con escritura y calculadora habilitadas.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);

        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);

        // Validar área de Escritura habilitada
        Locator areaEscritura = pageAlumno.locator("textarea[aria-label='Respuesta']").first();
        assertTrue(areaEscritura.isVisible() && !areaEscritura.isDisabled(),
                "El área de escritura de respuesta no se encuentra disponible ni habilitada.");

        // Validar que en la barra de herramientas esté presente Calculadora y ausente GeoGebra
        Locator tabCalculadora = pageAlumno.locator("aside.tools-dock button.tools-tab:has-text('Calculadora')");
        Locator tabGeoGebra = pageAlumno.locator("aside.tools-dock button.tools-tab:has-text('GeoGebra')");
        Locator chipsHerramientasHeader = pageAlumno.locator(".exam-tools .chip");

        assertTrue(tabCalculadora.isVisible(),
                "El botón de la herramienta 'Calculadora' no está visible en el dock del alumno.");
        assertEquals(0, tabGeoGebra.count(),
                "La herramienta 'GeoGebra' no debería mostrarse cuando fue deshabilitada en la configuración.");
        assertTrue(chipsHerramientasHeader.first().innerText().contains("Calculadora"),
                "El encabezado de herramientas no lista 'Calculadora'.");
        assertFalse(pageAlumno.locator(".exam-tools").innerText().contains("GeoGebra"),
                "El encabezado de herramientas muestra 'GeoGebra' pese a estar deshabilitado.");
    }

    @Test
    @Tag("lote-resolucion-examen")
    @Story("ID-022 - Conservación del texto en la herramienta de Escritura")
    @DisplayName("ID-022 - Conservación del texto en la herramienta de Escritura")
    @Description("En un examen con al menos 2 consignas y Escritura habilitada, escribir texto en la Consigna 1, navegar a la Consigna 2, regresar a la Consigna 1 y comprobar que el texto se conserve al 100%.")
    @Severity(SeverityLevel.CRITICAL)
    void testID022_ConservacionDelTextoEnLaHerramientaDeEscritura() {
        String titulo = "Parcial Escritura ID022 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                true,
                false,
                List.of("Consigna 1: Desarrolle el teorema fundamental.", "Consigna 2: Ejemplifique con un caso práctico.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);

        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);

        String respuestaConsigna1 = "El teorema fundamental del cálculo conecta la derivación con la integración definida: F'(x) = f(x).";
        String respuestaConsigna2 = "Ejemplo práctico: integral de 2x entre 0 y 3 es igual a 3^2 - 0^2 = 9.";

        // 1. Escribir texto en la Consigna 1
        Locator inputConsigna1 = pageAlumno.locator("textarea[aria-label='Respuesta']").nth(0);
        Locator inputConsigna2 = pageAlumno.locator("textarea[aria-label='Respuesta']").nth(1);

        inputConsigna1.click();
        inputConsigna1.fill(respuestaConsigna1);

        // 2. Navegar a la Consigna 2, escribir respuesta y abrir/cerrar panel de herramienta lateral
        inputConsigna2.scrollIntoViewIfNeeded();
        inputConsigna2.click();
        inputConsigna2.fill(respuestaConsigna2);

        pageAlumno.locator("aside.tools-dock button.tools-tab:has-text('Calculadora')").click();
        pageAlumno.locator(".tools-window-calculadora button[aria-label='Cerrar herramienta']").click();

        // 3. Regresar a la Consigna 1 y comprobar que el texto se conserva íntegro al 100%
        inputConsigna1.scrollIntoViewIfNeeded();
        inputConsigna1.click();

        assertEquals(respuestaConsigna1, inputConsigna1.inputValue(),
                "El texto escrito en la Consigna 1 no se conservó al 100% tras navegar a otra consigna y usar herramientas.");
        assertEquals(respuestaConsigna2, inputConsigna2.inputValue(),
                "El texto escrito en la Consigna 2 no se conservó íntegro.");
    }

    @Test
    @Tag("lote-resolucion-examen")
    @Story("ID-023 - Operaciones de Calculadora y manejo de división por cero")
    @DisplayName("ID-023 - Operaciones de Calculadora y manejo de división por cero")
    @Description("Con la herramienta Calculadora abierta durante el examen, ejecutar 8 * 5 (validar resultado 40) y 15 / 0 (validar mensaje de error matemático en la calculadora).")
    @Severity(SeverityLevel.NORMAL)
    void testID023_OperacionesDeCalculadoraYManejoDeDivisionPorCero() {
        String titulo = "Parcial Calculadora ID023 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                true,
                false,
                List.of("Consigna de cálculo asistido.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);

        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);

        // Abrir la Calculadora desde el dock de herramientas
        pageAlumno.locator("aside.tools-dock button.tools-tab:has-text('Calculadora')").click();
        Locator calcContainer = pageAlumno.locator(".calculator");
        calcContainer.waitFor();

        Locator displayInput = calcContainer.locator("input[aria-label='Expresión']");

        // 1. Ejecutar operación válida: 8 * 5 -> debe dar 40
        calcContainer.locator("button.calc-key:has-text('8')").click();
        calcContainer.locator("button.calc-key:has-text('×')").click();
        calcContainer.locator("button.calc-key:has-text('5')").click();
        calcContainer.locator("button.calc-key.equals").click();

        assertEquals("40", displayInput.inputValue(),
                "La calculadora no arrojó el resultado exacto '40' para la operación 8 * 5.");

        // Limpiar con botón 'C'
        calcContainer.locator("button.calc-key:text-is('C')").click();
        assertEquals("", displayInput.inputValue(), "El botón 'C' no limpió el visor de la calculadora.");

        // 2. Ejecutar división por cero: 15 / 0 -> debe mostrar mensaje de error matemático
        calcContainer.locator("button.calc-key:has-text('1')").click();
        calcContainer.locator("button.calc-key:has-text('5')").click();
        calcContainer.locator("button.calc-key:has-text('÷')").click();
        calcContainer.locator("button.calc-key:has-text('0')").click();
        calcContainer.locator("button.calc-key.equals").click();

        Locator errorCalc = calcContainer.locator(".calculator-status span.error");
        errorCalc.waitFor();
        assertTrue(errorCalc.isVisible(),
                "La calculadora no mostró el indicador de error al intentar dividir 15 / 0.");
        assertTrue(errorCalc.innerText().toLowerCase().contains("inválida") || errorCalc.innerText().toLowerCase().contains("error"),
                "El mensaje de error matemático de la calculadora no es el esperado: " + errorCalc.innerText());
    }
}
