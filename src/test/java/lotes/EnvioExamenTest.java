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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ============================================================================
 * Lote: Envío de Examen, Confirmación, Modo Lectura y Resiliencia (Casos ID-026 al ID-029, ID-056, ID-058 e ID-059)
 *
 * Comando para ejecutar en terminal:
 *   mvn test -Dgroups="lote-envio-examen"
 *
 * Con Maven Wrapper:
 *   .\mvnw.cmd test "-Dgroups=lote-envio-examen"
 * ============================================================================
 */
@Epic("Ejecución y Resolución de Examen")
@Feature("Envío de Examen y Resiliencia de Entrega")
@Tag("lote-envio-examen")
public class EnvioExamenTest extends TestBase {

    @BeforeEach
    void setUpEnvioExamen() {
        ClockMockHelper.instalarRelojSimulado(page);
        AuthHelper.loginProfesor(page, baseUrl);
    }

    @AfterEach
    void tearDownEnvioExamen() {
        ClockMockHelper.clearSimTime(page);
        ExamFormHelper.limpiarExamenesCreados(baseUrl);
    }

    @Test
    @Tag("lote-envio-examen")
    @Story("ID-026 - Modal de confirmación antes de enviar")
    @DisplayName("ID-026 - Modal de confirmación antes de enviar")
    @Description("Al hacer clic en 'Enviar', validar que se despliegue un modal de confirmación advirtiendo que una vez enviado no se podrá editar/modificar, y que al cancelar permita continuar en el examen.")
    @Severity(SeverityLevel.CRITICAL)
    void testID026_ModalDeConfirmacionAntesDeEnviar() {
        String titulo = "Parcial Modal Envio ID026 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of("Consigna única para validar modal de confirmación.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);

        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);
        StudentExamHelper.responderConsignaTexto(pageAlumno, 0, "Respuesta lista para confirmar envío.");

        // Clic en "Enviar parcial" para abrir el modal
        Locator modal = StudentExamHelper.abrirModalEnviarParcial(pageAlumno);
        assertTrue(modal.isVisible(), "No se desplegó el modal de confirmación al hacer clic en 'Enviar parcial'.");

        String textoModal = modal.innerText();
        boolean advierteNoEditable = textoModal.contains("¿Enviar el parcial?") &&
                (textoModal.contains("Una vez enviado no se puede modificar") ||
                 textoModal.contains("¿Seguro? No vas a poder editar"));
        assertTrue(advierteNoEditable,
                "El modal de confirmación no contiene la advertencia esperada de irreversibilidad. Texto: " + textoModal);

        // Cancelar en el modal y comprobar que continúa en el examen sin haber enviado
        modal.locator("button:has-text('Cancelar')").click();
        assertFalse(modal.isVisible(), "El modal de confirmación no se cerró al presionar 'Cancelar'.");
        assertTrue(pageAlumno.locator(".exam-submit button:has-text('Enviar parcial')").isVisible(),
                "El alumno no permaneció en la vista de edición del examen tras cancelar el modal.");
    }

    @Test
    @Tag("lote-envio-examen")
    @Story("ID-058 - Proceso de envío con confirmación de ítems incompletos")
    @DisplayName("ID-058 - Proceso de envío con confirmación de ítems incompletos")
    @Description("Dejar al menos 1 consigna sin responder, clicar 'Enviar', verificar que el modal indique la cantidad exacta de consignas faltantes, confirmar envío y validar procesamiento <= 3s, mensaje de éxito, registro de timestamp y bloqueo posterior.")
    @Severity(SeverityLevel.CRITICAL)
    void testID058_ProcesoDeEnvioConConfirmacionDeItemsIncompletos() {
        String titulo = "Parcial Incompleto ID058 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of("Consigna 1 (será respondida).", "Consigna 2 (quedará sin responder).", "Consigna 3 (quedará sin responder).")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);

        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);

        // Responder solo la Consigna 1 y dejar 2 consignas vacías
        StudentExamHelper.responderConsignaTexto(pageAlumno, 0, "Respuesta completa de la consigna 1.");

        Locator modal = StudentExamHelper.abrirModalEnviarParcial(pageAlumno);
        String textoModal = modal.innerText();
        assertTrue(textoModal.contains("Tenés 2 apartados sin responder."),
                "El modal de envío no indicó la cantidad exacta de consignas sin responder (esperado: 2). Texto: " + textoModal);

        // Confirmar envío y medir tiempo de procesamiento <= 3s
        long inicioEnvio = System.currentTimeMillis();
        Response respEntregar = StudentExamHelper.confirmarEnvioEnModal(pageAlumno);
        long duracionEnvioMs = System.currentTimeMillis() - inicioEnvio;

        assertTrue(respEntregar.ok(), "El envío del examen con ítems incompletos falló: HTTP " + respEntregar.status());
        assertTrue(duracionEnvioMs <= 3000,
                "El procesamiento del envío superó los 3 segundos: " + duracionEnvioMs + " ms");

        // Validar aviso de entrega exitosa con timestamp y bloqueo posterior
        Locator noticeEntregado = pageAlumno.locator(".card.notice:has(span.chip:has-text('Entregado'))").first();
        noticeEntregado.waitFor();
        assertTrue(noticeEntregado.isVisible(), "No se mostró el banner de estado 'Entregado' tras confirmar el envío.");
        assertTrue(noticeEntregado.innerText().contains("Enviaste este parcial el"),
                "El aviso de entrega no incluye el registro de fecha y hora de envío.");

        Locator primerTextarea = pageAlumno.locator("textarea[aria-label='Respuesta']").first();
        assertTrue(Boolean.TRUE.equals(primerTextarea.evaluate("el => el.readOnly")),
                "Los campos de respuesta no quedaron bloqueados en modo solo lectura tras el envío.");
    }

    @Test
    @Tag("lote-envio-examen")
    @Story("ID-027 - Procesamiento de envío exitoso y registro de metadatos")
    @DisplayName("ID-027 - Procesamiento de envío exitoso y registro de metadatos")
    @Description("Confirmar el envío con respuestas completas, validar procesamiento en < 3s, mensaje de éxito y registro de fecha/hora tanto en la respuesta HTTP como en la vista docente.")
    @Severity(SeverityLevel.BLOCKER)
    void testID027_ProcesamientoDeEnvioExitosoYRegistroDeMetadatos() {
        String titulo = "Parcial Envio Exitoso ID027 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                true,
                false,
                List.of("Consigna 1: Explique polimorfismo.", "Consigna 2: Explique encapsulamiento.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);

        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);
        StudentExamHelper.responderConsignaTexto(pageAlumno, 0, "Polimorfismo permite tratar objetos derivados de forma uniforme.");
        StudentExamHelper.responderConsignaTexto(pageAlumno, 1, "Encapsulamiento oculta el estado interno exponiendo una interfaz segura.");

        Locator modal = StudentExamHelper.abrirModalEnviarParcial(pageAlumno);
        assertFalse(modal.innerText().contains("sin responder"),
                "El modal reportó apartados sin responder cuando todas las consignas fueron completadas.");

        long inicioEnvio = System.currentTimeMillis();
        Response respEntregar = StudentExamHelper.confirmarEnvioEnModal(pageAlumno);
        long duracionEnvioMs = System.currentTimeMillis() - inicioEnvio;

        assertTrue(respEntregar.ok(), "Falló POST /entregar: HTTP " + respEntregar.status());
        assertTrue(duracionEnvioMs <= 3000,
                "El procesamiento del envío superó los 3 segundos: " + duracionEnvioMs + " ms");

        String jsonEntrega = respEntregar.text();
        assertTrue(jsonEntrega.contains("\"fechaEntrega\"") && jsonEntrega.contains("\"alumno\":\"" + AuthHelper.getAlumnoUser() + "\""),
                "La respuesta HTTP de entrega no contiene los metadatos esperados (fechaEntrega y alumno).");

        // Verificar en la vista del Profesor que la entrega aparezca registrada con fecha/hora
        page.waitForResponse(
                r -> r.url().contains("/api/datos") && r.request().method().equalsIgnoreCase("GET"),
                () -> page.navigate(baseUrl + "/documentos/" + docId)
        );
        Locator cardEntregaProfesor = page.locator("section.submissions li.card:has-text('Entregado:')").first();
        cardEntregaProfesor.waitFor();
        assertTrue(cardEntregaProfesor.isVisible(),
                "La entrega del alumno con su fecha y hora no aparece registrada en la vista del profesor.");
    }

    @Test
    @Tag("lote-envio-examen")
    @Story("ID-028 / ID-056 - Bloqueo de modificación o reenvío posentrega y modo solo lectura")
    @DisplayName("ID-028 / ID-056 - Bloqueo de modificación o reenvío posentrega y apertura en modo lectura")
    @Description("Para un examen ya enviado, verificar que no se pueda editar ni reenviar (ID-028) y que al reabrirlo desde el panel de entregados del alumno se muestre estrictamente en modo solo lectura (ID-056).")
    @Severity(SeverityLevel.CRITICAL)
    void testID028_ID056_BloqueoDeModificacionOReenvioPosentregaYModoLectura() {
        String titulo = "Parcial Posentrega ID028_056 " + System.currentTimeMillis();
        String respuestaDefinitiva = "Respuesta definitiva sellada en la entrega.";
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of("Consigna para validar inmutabilidad posentrega.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);

        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);
        StudentExamHelper.responderConsignaTexto(pageAlumno, 0, respuestaDefinitiva);
        StudentExamHelper.abrirModalEnviarParcial(pageAlumno);
        StudentExamHelper.confirmarEnvioEnModal(pageAlumno);

        // --- Validación ID-028: Bloqueo de modificación o reenvío en vista posentrega y en backend ---
        pageAlumno.locator(".card.notice:has(span.chip:has-text('Entregado'))").first().waitFor();
        assertEquals(0, pageAlumno.locator("button:has-text('Enviar parcial')").count(),
                "ID-028: El botón 'Enviar parcial' sigue presente después de haber enviado el examen.");

        int statusModificacionApi = (int) pageAlumno.evaluate(
                "async (id) => {" +
                "  const token = sessionStorage.getItem('rendix_token');" +
                "  const r = await fetch('/api/examen/' + id + '/respuestas', {" +
                "    method: 'PUT'," +
                "    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token }," +
                "    body: JSON.stringify({ respuestas: { hack: 'intento posentrega' }, seq: 99 })" +
                "  });" +
                "  return r.status;" +
                "}",
                docId
        );
        assertTrue(statusModificacionApi >= 400,
                "ID-028: El backend permitió modificar respuestas sobre un examen ya entregado. HTTP: " + statusModificacionApi);

        // Incluso si se invoca nuevamente /entregar con otro payload, el contenido sellado original debe ser inmutable
        pageAlumno.evaluate(
                "async (id) => {" +
                "  const token = sessionStorage.getItem('rendix_token');" +
                "  await fetch('/api/examen/' + id + '/entregar', {" +
                "    method: 'POST'," +
                "    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token }," +
                "    body: JSON.stringify({ respuestas: {}, seq: 100, automatico: false })" +
                "  });" +
                "}",
                docId
        );

        // --- Validación ID-056: Reapertura desde el panel del alumno en modo estrictamente solo lectura ---
        pageAlumno.navigate(baseUrl + "/alumno/parciales?tab=entregados");
        Locator cardEntregado = pageAlumno.locator("li.student-exam:has-text('" + titulo + "')").first();
        cardEntregado.waitFor();
        assertTrue(cardEntregado.innerText().contains("Entregado"),
                "ID-056: El examen no figura con estado 'Entregado' en la pestaña de entregados del alumno.");

        cardEntregado.locator("a:has-text('Ver entrega'), a:has-text('Ver nota')").first().click();
        pageAlumno.waitForURL(url -> url.contains("/alumno/parciales/" + docId));

        Locator textareaLectura = pageAlumno.locator("textarea[aria-label='Respuesta']").first();
        textareaLectura.waitFor();
        assertTrue(Boolean.TRUE.equals(textareaLectura.evaluate("el => el.readOnly")),
                "ID-056: El campo de respuesta no está en modo solo lectura (readOnly) al reabrir la entrega.");
        assertEquals(respuestaDefinitiva, textareaLectura.inputValue(),
                "ID-056: El contenido entregado no se visualiza correctamente en el modo lectura.");
        assertEquals(0, pageAlumno.locator("button:has-text('Enviar parcial'), button:has-text('Salir')").count(),
                "ID-056: Se muestran botones de acción de examen en curso dentro de la vista de solo lectura.");
    }

    @Test
    @Tag("lote-envio-examen")
    @Story("ID-029 / ID-059 - Resiliencia ante fallos de red durante el envío")
    @DisplayName("ID-029 / ID-059 - Resiliencia ante fallos de red durante el envío")
    @Description("Simular pérdida de red (context.setOffline(true)) al confirmar el envío, validar que las respuestas no se pierdan, se muestre el aviso de error de conexión y se habilite el reintento exitoso al reconectar.")
    @Severity(SeverityLevel.CRITICAL)
    void testID029_ID059_ResilienciaAnteFallosDeRedDuranteElEnvio() {
        String titulo = "Parcial Resiliencia Envio ID029_059 " + System.currentTimeMillis();
        String textoRespuesta = "Respuesta crítica protegida ante corte de conexión durante el envío.";
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of("Consigna para prueba de resiliencia de red al enviar.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);

        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);
        StudentExamHelper.responderConsignaTexto(pageAlumno, 0, textoRespuesta);

        // 1. Abrir modal de confirmación de envío
        Locator modal = StudentExamHelper.abrirModalEnviarParcial(pageAlumno);

        // 2. Cortar la conexión de red del alumno justo antes de confirmar el envío
        ctxAlumno.setOffline(true);
        modal.locator(".confirm-dialog-actions button:has-text('Enviar parcial')").click();

        // 3. Validar mensaje de error de red, preservación de la respuesta y habilitación de reintento
        Locator errorRed = pageAlumno.locator("p.error").first();
        errorRed.waitFor();
        String textoError = errorRed.innerText().toLowerCase();
        assertTrue(textoError.contains("sin conexión") && textoError.contains("guardadas"),
                "No se mostró el mensaje explicativo de fallo de conexión y preservación local. Texto: " + textoError);

        Locator textarea = pageAlumno.locator("textarea[aria-label='Respuesta']").first();
        assertEquals(textoRespuesta, textarea.inputValue(),
                "Se perdió el texto de la respuesta del alumno tras el fallo de red durante el envío.");

        Locator btnReintentarEnvio = pageAlumno.locator(".exam-submit button:has-text('Enviar parcial')").first();
        assertTrue(btnReintentarEnvio.isVisible() && !btnReintentarEnvio.isDisabled(),
                "El botón 'Enviar parcial' no quedó habilitado para reintentar tras el fallo de red.");

        // 4. Restablecer la conexión de red y reintentar el envío con éxito
        ctxAlumno.setOffline(false);
        StudentExamHelper.abrirModalEnviarParcial(pageAlumno);
        Response respReintento = StudentExamHelper.confirmarEnvioEnModal(pageAlumno);

        assertTrue(respReintento.ok(), "El reintento de envío tras restablecer la red falló: HTTP " + respReintento.status());
        Locator noticeEntregado = pageAlumno.locator(".card.notice:has(span.chip:has-text('Entregado'))").first();
        noticeEntregado.waitFor();
        assertTrue(noticeEntregado.isVisible(),
                "El examen no pasó a estado 'Entregado' luego de reintentar el envío con conexión restablecida.");
    }
}
