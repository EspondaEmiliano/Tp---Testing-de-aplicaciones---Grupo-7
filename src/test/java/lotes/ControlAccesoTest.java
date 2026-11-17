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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ============================================================================
 * Lote: Control de Acceso y Seguridad de Rutas (Casos ID-015 e ID-055)
 *
 * Comando para ejecutar en terminal:
 *   mvn test -Dgroups="lote-control-acceso"
 *
 * Con Maven Wrapper:
 *   .\mvnw.cmd test "-Dgroups=lote-control-acceso"
 * ============================================================================
 */
@Epic("Seguridad y Control de Acceso")
@Feature("Protección de Rutas y Horarios de Examen")
@Tag("lote-control-acceso")
public class ControlAccesoTest extends TestBase {

    @BeforeEach
    void setUpControlAcceso() {
        ClockMockHelper.instalarRelojSimulado(page);
    }

    @AfterEach
    void tearDownControlAcceso() {
        ClockMockHelper.clearSimTime(page);
        ExamFormHelper.limpiarExamenesCreados(baseUrl);
    }

    @Test
    @Tag("lote-control-acceso")
    @Story("ID-015 - Control de acceso por URL")
    @DisplayName("ID-015 - Control de acceso por URL")
    @Description("Con sesión activa de alumno1, intentar navegar directamente a /documentos/nuevo y validar bloqueo/redirección al panel de alumno sin exponer formulario ni datos de profesor.")
    @Severity(SeverityLevel.BLOCKER)
    void testID015_ControlDeAccesoPorURL() {
        AuthHelper.loginAlumno(page, baseUrl);

        // Intentar navegar directamente a la ruta de creación de exámenes exclusiva para profesores
        page.navigate(baseUrl + "/documentos/nuevo");
        page.waitForURL(url -> url.contains("/alumno") && !url.contains("/documentos/nuevo"),
                new Page.WaitForURLOptions().setTimeout(5000));

        // Aserción 1: El sistema redirige al panel del alumno y NO expone el formulario docente
        assertTrue(page.url().contains("/alumno"),
                "El alumno no fue redirigido a /alumno al intentar ingresar a /documentos/nuevo. URL: " + page.url());
        assertEquals(0, page.locator("h1:has-text('Nuevo parcial'), form:has(label:has-text('Umbral de confianza'))").count(),
                "Se expuso el formulario de creación de parciales a un usuario con rol Alumno.");

        // Aserción 2: Verificar que la API backend rechaza con HTTP 403 cualquier intento de creación desde el token de alumno
        int statusApi = (int) page.evaluate(
                "async () => {" +
                "  const token = sessionStorage.getItem('rendix_token');" +
                "  const res = await fetch('/api/documentos', {" +
                "    method: 'POST'," +
                "    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token }," +
                "    body: JSON.stringify({ titulo: 'Intento No Autorizado' })" +
                "  });" +
                "  return res.status;" +
                "}"
        );
        assertEquals(403, statusApi,
                "El endpoint POST /api/documentos no devolvió HTTP 403 ante un token de rol Alumno.");
    }

    @Test
    @Tag("lote-control-acceso")
    @Story("ID-055 - Bloqueo de apertura fuera del horario de turno o por URL no autorizada")
    @DisplayName("ID-055 - Bloqueo de apertura fuera del horario de turno o por URL no autorizada")
    @Description("Con sesión de alumno1, validar bloqueo y motivo al intentar abrir: (a) un examen fuera de su ventana horaria, (b) un examen en borrador por URL directa, y (c) un examen de otro turno/sin asignación por URL directa.")
    @Severity(SeverityLevel.CRITICAL)
    void testID055_BloqueoDeAperturaFueraDelHorarioDeTurnoOPorURLNoAutorizada() {
        AuthHelper.loginProfesor(page, baseUrl);

        // Preparar Escenario (a): Examen publicado pero con turno en el futuro (fuera de ventana horaria actual)
        String tituloFueraHorario = "Parcial Fuera Horario ID055A " + System.currentTimeMillis();
        String urlFueraHorario = ExamFormHelper.crearExamenEnBorrador(page, baseUrl, tituloFueraHorario, "Consigna fuera de horario.");
        String idFueraHorario = ExamFormHelper.extraerDocId(urlFueraHorario);
        ExamFormHelper.publicarExamenActual(page);

        // Preparar Escenario (b): Examen en estado Borrador (no publicado)
        String tituloBorrador = "Parcial Borrador ID055B " + System.currentTimeMillis();
        String urlBorrador = ExamFormHelper.crearExamenEnBorrador(page, baseUrl, tituloBorrador, "Consigna de borrador.");
        String idBorrador = ExamFormHelper.extraerDocId(urlBorrador);

        // Preparar Escenario (c): Examen publicado donde alumno1 es quitado del turno asignado
        String tituloOtroTurno = "Parcial No Asignado ID055C " + System.currentTimeMillis();
        String urlOtroTurno = ExamFormHelper.crearExamenEnBorrador(page, baseUrl, tituloOtroTurno, "Consigna de otro turno.");
        String idOtroTurno = ExamFormHelper.extraerDocId(urlOtroTurno);
        ExamFormHelper.publicarExamenActual(page);

        // Quitar a alumno1 del turno del tercer examen
        Locator linkSeguimiento = page.locator("a:has-text('Seguimiento y notas')").first();
        linkSeguimiento.waitFor();
        linkSeguimiento.click();
        page.waitForURL(url -> url.contains("/turnos/"));
        page.locator("table.tracking-table").waitFor();

        Locator filaAlumno1 = page.locator("tr:has-text('" + AuthHelper.getAlumnoUser() + "')").first();
        if (filaAlumno1.isVisible()) {
            page.onceDialog(dialog -> dialog.accept());
            Response respQuitar = page.waitForResponse(
                    r -> r.url().contains("/alumnos/") && r.request().method().equalsIgnoreCase("DELETE"),
                    () -> filaAlumno1.locator("button:has-text('Quitar')").click()
            );
            assertTrue(respQuitar.ok(), "No se pudo desasignar a alumno1 del turno de prueba.");
        }

        // Abrir sesión de alumno1 en contexto secundario
        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);

        // --- Validación (a): Examen fuera de su ventana horaria ---
        pageAlumno.navigate(baseUrl + "/alumno/parciales/" + idFueraHorario);
        Locator cardWait = pageAlumno.locator(".exam-wait");
        cardWait.waitFor();
        assertTrue(cardWait.isVisible(),
                "Escenario (a): No se mostró la tarjeta de espera (.exam-wait) para un examen fuera de horario.");
        String textoWait = cardWait.innerText().toLowerCase();
        assertTrue(textoWait.contains("todavía no empezó") || textoWait.contains("empieza el"),
                "Escenario (a): El aviso no explica el motivo de bloqueo por horario. Texto: " + textoWait);
        assertFalse(pageAlumno.locator("button:has-text('Ingresar al examen'), .exam-content").isVisible(),
                "Escenario (a): Se permitió ingresar al examen fuera de su ventana horaria.");

        // --- Validación (b): Examen en borrador por URL directa ---
        Response respGetBorrador = pageAlumno.waitForResponse(
                r -> r.url().endsWith("/api/examen/" + idBorrador),
                () -> pageAlumno.navigate(baseUrl + "/alumno/parciales/" + idBorrador)
        );
        assertTrue(respGetBorrador.status() == 403 || respGetBorrador.status() == 404,
                "Escenario (b): El backend no denegó con 403/404 el acceso a un examen en borrador. Status: " + respGetBorrador.status());
        pageAlumno.locator("h1:has-text('Parcial no encontrado')").waitFor();
        assertFalse(pageAlumno.locator(".exam-content, section.restricted-rules").isVisible(),
                "Escenario (b): Se expuso contenido de un examen en borrador al alumno.");

        // --- Validación (c): Examen de otro turno / no asignado al alumno por URL directa ---
        Response respGetNoAsignado = pageAlumno.waitForResponse(
                r -> r.url().endsWith("/api/examen/" + idOtroTurno),
                () -> pageAlumno.navigate(baseUrl + "/alumno/parciales/" + idOtroTurno)
        );
        assertTrue(respGetNoAsignado.status() == 403 || respGetNoAsignado.status() == 404,
                "Escenario (c): El backend no denegó con 403/404 el acceso a un examen no asignado al alumno. Status: " + respGetNoAsignado.status());
        pageAlumno.locator("h1:has-text('Parcial no encontrado')").waitFor();
        assertFalse(pageAlumno.locator(".exam-content, section.restricted-rules").isVisible(),
                "Escenario (c): Se expuso contenido de un examen de otro turno no asignado al alumno.");
    }
}
