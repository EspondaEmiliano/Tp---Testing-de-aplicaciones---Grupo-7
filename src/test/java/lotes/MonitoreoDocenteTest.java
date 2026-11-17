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
import helpers.TeacherMonitorHelper;
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

import static org.junit.jupiter.api.Assertions.*;

@Epic("Módulo de Profesor - Monitoreo y Supervisión")
@Feature("Seguimiento de Turnos, Monitoreo en Vivo, Gestión de Incidentes y Bitácora")
@Tag("lote-monitoreo-docente")
public class MonitoreoDocenteTest extends TestBase {

    @BeforeEach
    void iniciarSesionProfesor() {
        AuthHelper.loginProfesor(page, baseUrl);
    }

    @AfterEach
    void limpiarExamenes() {
        ExamFormHelper.limpiarExamenesCreados(baseUrl);
    }

    @Test
    @Tag("lote-monitoreo-docente")
    @Story("ID-030 - Recepción de entrega y acceso al detalle desde la vista del profesor")
    @DisplayName("ID-030 - Recepción de examen enviado y visualización de respuestas en modo lectura por el profesor")
    @Description("El alumno1 completa y envía un examen. Desde la sesión del profesor, abrir el detalle de la entrega y validar nombre del alumno, fechas de inicio/entrega, consigna y respuesta exacta en modo solo lectura.")
    @Severity(SeverityLevel.CRITICAL)
    void testID030_RecepcionDeEntregaYAccesoAlDetalleDesdeVistaDelProfesor() {
        String titulo = "Parcial Recepcion Docente ID030 " + System.currentTimeMillis();
        String consigna = "Explicar el teorema fundamental del cálculo.";
        String respuestaAlumno = "La derivada de la integral definida respecto a su límite superior es el integrando.";
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of(consigna)
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno1");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);
        StudentExamHelper.responderConsignaTexto(pageAlumno, 0, respuestaAlumno);
        StudentExamHelper.abrirModalEnviarParcial(pageAlumno);
        StudentExamHelper.confirmarEnvioEnModal(pageAlumno);

        // El profesor recarga/navega al detalle del parcial y verifica la sección Entregas (1)
        page.navigate(baseUrl + "/documentos/" + docId);
        Locator itemEntrega = page.locator("section.submissions li.card:has-text('Alumno 1')").first();
        itemEntrega.waitFor();
        assertTrue(itemEntrega.innerText().contains("Entregado:"),
                "La tarjeta de entrega no muestra la marca temporal de entrega.");

        itemEntrega.locator("a:has-text('Ver entrega')").click();
        page.waitForURL(url -> url.contains("/documentos/" + docId + "/entregas/"));

        Locator encabezado = page.locator("h1").first();
        encabezado.waitFor();
        assertTrue(encabezado.textContent().contains(titulo) && encabezado.textContent().contains("Alumno 1"),
                "El encabezado de la entrega no incluye el título del parcial y el nombre del alumno: " + encabezado.textContent());

        String metaEntrega = page.locator("p.chips-line").first().textContent();
        assertTrue(metaEntrega.contains("Comenzó:") && metaEntrega.contains("Entregó:"),
                "No se muestran las marcas de tiempo 'Comenzó:' y 'Entregó:' en la vista de revisión: " + metaEntrega);

        Locator textareaRevision = page.locator("textarea[aria-label='Respuesta']").first();
        textareaRevision.waitFor();
        assertTrue(Boolean.TRUE.equals(textareaRevision.evaluate("el => el.readOnly")),
                "La respuesta del alumno en la vista del profesor debe ser estrictamente de solo lectura.");
        assertEquals(respuestaAlumno, textareaRevision.inputValue(),
                "La respuesta visualizada por el profesor no coincide con la enviada por el alumno.");
    }

    @Test
    @Tag("lote-monitoreo-docente")
    @Story("ID-060 - Visualización de estados por alumno en la tabla de seguimiento del turno")
    @DisplayName("ID-060 - Transición de estados en seguimiento del turno: No abrió -> En curso -> Entregado")
    @Description("En la vista de seguimiento del turno (/documentos/:id/turnos/:turnoId), verificar los estados por alumno ('No abrió', 'En curso' con hora de apertura y 'Entregado' con hora de envío) usando alumno1 y alumno2.")
    @Severity(SeverityLevel.CRITICAL)
    void testID060_VisualizacionDeEstadosPorAlumnoEnTablaDeSeguimientoDelTurno() {
        String titulo = "Parcial Estados Seguimiento ID060 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of("Consigna para seguimiento de estados por alumno.")
        );

        TeacherMonitorHelper.abrirSeguimientoDelTurno(page, baseUrl, docId);

        Locator filaAlumno1 = TeacherMonitorHelper.obtenerFilaAlumnoEnSeguimiento(page, AuthHelper.ALUMNO_USER);
        Locator filaAlumno2 = TeacherMonitorHelper.obtenerFilaAlumnoEnSeguimiento(page, AuthHelper.ALUMNO2_USER);
        filaAlumno1.waitFor();
        filaAlumno2.waitFor();

        // 1) Estado inicial: ambos alumnos en 'No abrió'
        assertTrue(filaAlumno1.innerText().contains("No abrió"),
                "alumno1 debería iniciar en estado 'No abrió': " + filaAlumno1.innerText());
        assertTrue(filaAlumno2.innerText().contains("No abrió"),
                "alumno2 debería iniciar en estado 'No abrió': " + filaAlumno2.innerText());

        // 2) alumno1 abre e inicia el examen -> pasa a 'En curso' mientras alumno2 sigue en 'No abrió'
        BrowserContext ctxAlumno1 = crearContextoSecundario("alumno1");
        Page pageAlumno1 = ctxAlumno1.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno1);
        AuthHelper.loginAlumno(pageAlumno1, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno1, baseUrl, docId);

        filaAlumno1.locator("span.chip:has-text('En curso')").waitFor();
        assertFalse(filaAlumno1.locator("td").nth(2).innerText().trim().equals("—"),
                "La columna 'Apertura' de alumno1 debe mostrar la fecha/hora de inicio.");
        assertTrue(filaAlumno2.innerText().contains("No abrió"),
                "alumno2 debe permanecer en estado 'No abrió'.");

        // 3) alumno1 entrega el examen -> pasa a 'Entregado' con fecha de envío y enlace 'Ver entrega'
        StudentExamHelper.responderConsignaTexto(pageAlumno1, 0, "Respuesta completada por alumno1.");
        StudentExamHelper.abrirModalEnviarParcial(pageAlumno1);
        StudentExamHelper.confirmarEnvioEnModal(pageAlumno1);

        filaAlumno1.locator("span.chip:has-text('Entregado')").waitFor();
        assertFalse(filaAlumno1.locator("td").nth(3).innerText().trim().equals("—"),
                "La columna 'Envío' de alumno1 debe mostrar la fecha/hora de entrega.");
        assertTrue(filaAlumno1.locator("a:has-text('Ver entrega')").isVisible(),
                "Debe habilitarse la acción 'Ver entrega' para alumno1 tras entregar.");
    }

    @Test
    @Tag("lote-monitoreo-docente")
    @Story("ID-061 - Contadores agregados del turno: asignados, abrieron y enviaron")
    @DisplayName("ID-061 - Actualización dinámica de contadores: Alumnos asignados, Abrieron X de N y Enviaron Y de N")
    @Description("Validar que los contadores del encabezado de seguimiento del turno ('Alumnos asignados', 'Abrieron X de N', 'Enviaron Y de N') se actualicen en vivo cuando alumno1 y alumno2 abren y entregan el parcial.")
    @Severity(SeverityLevel.CRITICAL)
    void testID061_ContadoresAgregadosDelTurnoAsignadosAbrieronYEnviaron() {
        String titulo = "Parcial Contadores Turno ID061 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of("Consigna para validar contadores agregados del turno.")
        );

        TeacherMonitorHelper.abrirSeguimientoDelTurno(page, baseUrl, docId);
        Locator headerTracking = page.locator("section.submissions .tracking-header").first();
        headerTracking.waitFor();

        // Inicialmente: Abrieron 0 de 10 · Enviaron 0 de 10
        assertTrue(headerTracking.innerText().contains("Alumnos asignados (10)"),
                "El contador de alumnos asignados no muestra 10: " + headerTracking.innerText());
        assertTrue(headerTracking.innerText().contains("Abrieron 0 de 10")
                        && headerTracking.innerText().contains("Enviaron 0 de 10"),
                "Los contadores iniciales deben estar en 0: " + headerTracking.innerText());

        // alumno1 inicia el examen -> Abrieron 1 de 10 · Enviaron 0 de 10
        BrowserContext ctxAlumno1 = crearContextoSecundario("alumno1");
        Page pageAlumno1 = ctxAlumno1.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno1);
        AuthHelper.loginAlumno(pageAlumno1, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno1, baseUrl, docId);

        page.locator(".tracking-header:has-text('Abrieron 1 de 10')").first().waitFor();

        // alumno2 inicia el examen -> Abrieron 2 de 10 · Enviaron 0 de 10
        BrowserContext ctxAlumno2 = crearContextoSecundario("alumno2");
        Page pageAlumno2 = ctxAlumno2.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno2);
        AuthHelper.login(pageAlumno2, baseUrl, AuthHelper.getAlumno2User(), AuthHelper.getAlumno2Pass());
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno2, baseUrl, docId);

        page.locator(".tracking-header:has-text('Abrieron 2 de 10')").first().waitFor();

        // alumno1 entrega -> Abrieron 2 de 10 · Enviaron 1 de 10
        StudentExamHelper.responderConsignaTexto(pageAlumno1, 0, "Entrega de alumno1 para contador.");
        StudentExamHelper.abrirModalEnviarParcial(pageAlumno1);
        StudentExamHelper.confirmarEnvioEnModal(pageAlumno1);

        page.locator(".tracking-header:has-text('Enviaron 1 de 10')").first().waitFor();
        assertTrue(headerTracking.innerText().contains("Abrieron 2 de 10")
                        && headerTracking.innerText().contains("Enviaron 1 de 10"),
                "Los contadores finales no reflejan 2 abiertos y 1 enviado: " + headerTracking.innerText());
    }

    @Test
    @Tag("lote-monitoreo-docente")
    @Story("ID-072 - Supervisión en vivo en /monitoreo: tiempo restante, última actividad y cambios pendientes")
    @DisplayName("ID-072 - Panel /monitoreo en vivo: tiempo restante del turno, última actividad y cambios pendientes")
    @Description("En el panel /monitoreo del profesor, validar el contador de tiempo restante del turno, la marca de última actividad ('hace X s') y el indicador de cambios pendientes de sincronizar del alumno.")
    @Severity(SeverityLevel.CRITICAL)
    void testID072_SupervisionEnVivoEnMonitoreoTiempoRestanteUltimaActividadYCambiosPendientes() {
        String titulo = "Parcial Monitoreo Vivo ID072 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of("Consigna para probar supervisión en vivo en /monitoreo.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno1");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);
        StudentExamHelper.responderConsignaTexto(pageAlumno, 0, "Respuesta sincronizada inicialmente.");

        Locator cardMonitor = TeacherMonitorHelper.abrirMonitoreoEnVivo(page, baseUrl, titulo);

        // Validar tarjeta de Tiempo restante del turno en formato HH:MM:SS
        Locator valorTiempoTurno = cardMonitor.locator(".monitor-stat:has-text('Tiempo restante del turno') .monitor-stat-value");
        valorTiempoTurno.waitFor();
        assertTrue(valorTiempoTurno.innerText().trim().matches("^\\d{2}:\\d{2}:\\d{2}$"),
                "El tiempo restante del turno en monitoreo no tiene formato HH:MM:SS: " + valorTiempoTurno.innerText());

        // Validar fila de alumno1 en curso con barra de progreso y última actividad
        Locator filaAlumno1 = TeacherMonitorHelper.obtenerFilaAlumnoEnMonitoreo(cardMonitor, AuthHelper.ALUMNO_USER);
        filaAlumno1.waitFor();
        assertTrue(filaAlumno1.innerText().contains("En curso"), "alumno1 no figura 'En curso' en monitoreo.");
        assertTrue(filaAlumno1.locator("[role='progressbar']").isVisible(), "No se visualiza la barra de progreso de tiempo del alumno.");
        assertTrue(filaAlumno1.locator("td.monitor-num").innerText().contains("hace "),
                "La columna 'Última actividad' no muestra el tiempo relativo ('hace X s'): " + filaAlumno1.locator("td.monitor-num").innerText());

        // Simular que el alumno tiene cambios pendientes de sincronizar en su próximo latido
        pageAlumno.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/latido") && r.request().method().equalsIgnoreCase("POST"),
                () -> pageAlumno.evaluate(
                        "(id) => {" +
                        "  const token = sessionStorage.getItem('rendix_token');" +
                        "  fetch('/api/examen/' + id + '/latido', {" +
                        "    method: 'POST'," +
                        "    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token }," +
                        "    body: JSON.stringify({ pendientes: true })" +
                        "  });" +
                        "}",
                        docId
                )
        );

        // En el panel de monitoreo debe encenderse el indicador de cambios pendientes
        filaAlumno1.locator("span.monitor-pending[aria-label='Cambios pendientes de sincronizar']").waitFor();
        assertEquals("1", cardMonitor.locator(".monitor-stat.pending .monitor-stat-value").innerText().trim(),
                "El contador de 'Cambios pendientes' en monitoreo no subió a 1.");
    }

    @Test
    @Tag("lote-monitoreo-docente")
    @Story("ID-073 - Detección de pérdida de conexión / falta de latido en el panel del profesor")
    @DisplayName("ID-073 - Alerta automática 'Sin señal hace X s' en /monitoreo cuando el alumno pierde conexión")
    @Description("Con alumno1 rindiendo activamente y el profesor en /monitoreo, cortar la red del alumno (setOffline(true)). Validar que tras > 4s sin latido aparezca el aviso 'Sin señal hace X s' y desaparezca al reconectar.")
    @Severity(SeverityLevel.CRITICAL)
    void testID073_DeteccionDePerdidaDeConexionFaltaDeLatidoEnPanelDelProfesor() {
        String titulo = "Parcial Sin Senal ID073 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of("Consigna para probar detección de falta de latido en monitoreo.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno1");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);

        Locator cardMonitor = TeacherMonitorHelper.abrirMonitoreoEnVivo(page, baseUrl, titulo);
        Locator filaAlumno1 = TeacherMonitorHelper.obtenerFilaAlumnoEnMonitoreo(cardMonitor, AuthHelper.ALUMNO_USER);
        filaAlumno1.waitFor();

        // Desconectar a alumno1 para interrumpir el envío de latidos (/latido cada 2s; umbral qs = 4s)
        ctxAlumno.setOffline(true);

        Locator avisoSinSenal = filaAlumno1.locator("span.table-sub:has-text('Sin señal hace')");
        avisoSinSenal.waitFor(new Locator.WaitForOptions().setTimeout(10000));
        assertTrue(avisoSinSenal.innerText().contains("Sin señal hace"),
                "El panel del profesor no alertó la falta de latido del alumno: " + avisoSinSenal.innerText());

        // Reconectar a alumno1 y verificar que al retomarse el latido desaparezca el aviso 'Sin señal'
        ctxAlumno.setOffline(false);
        page.waitForFunction(
                "(username) => {" +
                "  const row = [...document.querySelectorAll('tr.monitor-row')].find(r => r.innerText.includes(username));" +
                "  return row && !row.innerText.includes('Sin señal hace');" +
                "}",
                AuthHelper.ALUMNO_USER
        );
    }

    @Test
    @Tag("lote-monitoreo-docente")
    @Story("ID-074 - Actualización en tiempo real en el panel del profesor cuando un alumno entrega por vencimiento")
    @DisplayName("ID-074 - Reflejo automático (< 4s) de entrega por vencimiento de tiempo en /monitoreo")
    @Description("Mientras el profesor observa /monitoreo, el temporizador de alumno1 llega a 00:00:00 y dispara la entrega automática. Validar que en < 4s sin recargar la página el estado cambie a 'Entregado' ('Por tiempo').")
    @Severity(SeverityLevel.CRITICAL)
    void testID074_ActualizacionEnTiempoRealEnPanelDelProfesorCuandoAlumnoEntregaPorVencimiento() {
        String titulo = "Parcial Vencimiento Monitoreo ID074 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "10",
                false,
                false,
                List.of("Consigna para probar reflejo en vivo de entrega por vencimiento.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno1");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);
        StudentExamHelper.responderConsignaTexto(pageAlumno, 0, "Respuesta antes del vencimiento monitoreado.");

        Locator cardMonitor = TeacherMonitorHelper.abrirMonitoreoEnVivo(page, baseUrl, titulo);
        Locator filaAlumno1 = TeacherMonitorHelper.obtenerFilaAlumnoEnMonitoreo(cardMonitor, AuthHelper.ALUMNO_USER);
        filaAlumno1.locator("span.chip:has-text('En curso')").waitFor();

        // Provocar vencimiento del tiempo en la sesión del alumno
        pageAlumno.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/entregar") && r.request().method().equalsIgnoreCase("POST"),
                () -> ClockMockHelper.setServerOffsetMs(pageAlumno, 605_000L)
        );
        long momentoEntrega = System.currentTimeMillis();

        // Sin recargar la página del profesor, esperar a que el polling de /monitoreo refleje el nuevo estado
        filaAlumno1.locator("span.chip:has-text('Entregado')").waitFor();
        long demoraReflejoMs = System.currentTimeMillis() - momentoEntrega;

        assertTrue(demoraReflejoMs <= Math.max(4000, perfToleranceMs),
                "El panel del profesor tardó más de 4s en reflejar la entrega por vencimiento: " + demoraReflejoMs + " ms");
        assertTrue(filaAlumno1.innerText().contains("Por tiempo") || filaAlumno1.innerText().contains("Entregado"),
                "La fila del alumno no indica que la entrega fue por vencimiento de tiempo: " + filaAlumno1.innerText());
    }

    @Test
    @Tag("lote-monitoreo-docente")
    @Story("ID-088 - Alerta destacada en vivo cuando un alumno entra en estado Incidente con causa reportada")
    @DisplayName("ID-088 - Alerta inmediata de Incidente en /monitoreo con contador destacado y motivo exacto")
    @Description("Con el profesor en /monitoreo, provocar en alumno1 una salida de pantalla completa. Validar que sin recargar se incremente el contador de Incidentes ('Esperan tu decisión'), la fila pase a estado '⚠ Incidente' y muestre la causa 'salida de pantalla completa'.")
    @Severity(SeverityLevel.BLOCKER)
    void testID088_AlertaDestacadaEnVivoCuandoUnAlumnoEntraEnEstadoIncidenteConCausaReportada() {
        String titulo = "Parcial Alerta Incidente ID088 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of("Consigna para probar alerta de incidente en vivo.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno1");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);

        Locator cardMonitor = TeacherMonitorHelper.abrirMonitoreoEnVivo(page, baseUrl, titulo);

        // alumno1 sale de pantalla completa
        pageAlumno.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/incidente"),
                () -> pageAlumno.evaluate("() => document.exitFullscreen()")
        );

        // Verificar tarjeta resumen de Incidentes en /monitoreo
        Locator statIncidentes = cardMonitor.locator(".monitor-stat.incident");
        statIncidentes.waitFor();
        assertEquals("1", statIncidentes.locator(".monitor-stat-value").innerText().trim(),
                "El contador de incidentes en vivo no subió a 1.");
        assertTrue(statIncidentes.innerText().contains("Esperan tu decisión"),
                "La tarjeta de incidentes no muestra la nota 'Esperan tu decisión'.");

        // Verificar fila destacada de alumno1 con causa reportada
        Locator filaAlumno1 = TeacherMonitorHelper.obtenerFilaAlumnoEnMonitoreo(cardMonitor, AuthHelper.ALUMNO_USER);
        assertTrue(filaAlumno1.getAttribute("class").contains("incidente"),
                "La fila del alumno no tiene la clase visual 'incidente'.");
        assertTrue(filaAlumno1.innerText().contains("Incidente"),
                "La fila del alumno no muestra el chip 'Incidente'.");
        assertTrue(filaAlumno1.innerText().contains("salida de pantalla completa"),
                "La fila del alumno no informa la causa específica 'salida de pantalla completa': " + filaAlumno1.innerText());
    }

    @Test
    @Tag("lote-monitoreo-docente")
    @Story("ID-089 - Priorización visual y ordenamiento superior de alumnos en estado Incidente")
    @DisplayName("ID-089 - Los alumnos en estado 'Incidente' se posicionan automáticamente al tope de la tabla")
    @Description("Tener a alumno1 ('En curso') y alumno2 ('En curso') junto al resto de alumnos ('No abrió'). Provocar un incidente en alumno2 y verificar que su fila suba automáticamente a la primera posición (índice 0) de la tabla de monitoreo.")
    @Severity(SeverityLevel.CRITICAL)
    void testID089_PriorizacionVisualYOrdenamientoSuperiorDeAlumnosEnEstadoIncidente() {
        String titulo = "Parcial Orden Incidente ID089 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of("Consigna para verificar ordenamiento prioritario de incidentes.")
        );

        // alumno1 y alumno2 inician el examen (alfabéticamente Alumno 1 aparece antes que Alumno 2)
        BrowserContext ctxAlumno1 = crearContextoSecundario("alumno1");
        Page pageAlumno1 = ctxAlumno1.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno1);
        AuthHelper.loginAlumno(pageAlumno1, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno1, baseUrl, docId);

        BrowserContext ctxAlumno2 = crearContextoSecundario("alumno2");
        Page pageAlumno2 = ctxAlumno2.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno2);
        AuthHelper.login(pageAlumno2, baseUrl, AuthHelper.getAlumno2User(), AuthHelper.getAlumno2Pass());
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno2, baseUrl, docId);

        Locator cardMonitor = TeacherMonitorHelper.abrirMonitoreoEnVivo(page, baseUrl, titulo);
        Locator filas = cardMonitor.locator("table.monitor-table tbody tr.monitor-row");
        filas.first().waitFor();

        // Antes del incidente, alumno1 ('En curso') está en la primera fila (índice 0)
        assertTrue(filas.nth(0).innerText().contains(AuthHelper.ALUMNO_USER),
                "Antes del incidente, alumno1 debería ocupar la primera fila.");

        // alumno2 sufre un incidente -> debe subir automáticamente al índice 0 por encima de alumno1
        pageAlumno2.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/incidente"),
                () -> pageAlumno2.evaluate("() => document.exitFullscreen()")
        );

        cardMonitor.locator(".monitor-stat.incident").waitFor();
        assertTrue(filas.nth(0).innerText().contains(AuthHelper.ALUMNO2_USER)
                        && filas.nth(0).innerText().contains("Incidente"),
                "El alumno en estado 'Incidente' (alumno2) no fue priorizado en la primera fila de la tabla: "
                        + filas.nth(0).innerText());
        assertTrue(filas.nth(1).innerText().contains(AuthHelper.ALUMNO_USER),
                "alumno1 ('En curso') debería ubicarse inmediatamente después del alumno con incidente.");
    }

    @Test
    @Tag("lote-monitoreo-docente")
    @Story("ID-090 - Distinción visual clara en monitoreo entre 'Salió y se entregó' e 'Incidente'")
    @DisplayName("ID-090 - Diferenciación entre abandono no autorizado ('⚠ Incidente') y retiro con botón SALIR ('Salió y se entregó')")
    @Description("Hacer que alumno1 sufra un Incidente (salida de fullscreen) y que alumno2 se retire voluntariamente usando el botón SALIR. Validar que en /monitoreo se distingan claramente sus chips, motivos y que solo alumno1 tenga botón 'Reanudar'.")
    @Severity(SeverityLevel.CRITICAL)
    void testID090_DistincionVisualClaraEnMonitoreoEntreSalioYSeEntregoEIncidente() {
        String titulo = "Parcial Distincion Salida ID090 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of("Consigna para distinguir salida voluntaria SALIR vs Incidente.")
        );

        // alumno1 entra y provoca Incidente
        BrowserContext ctxAlumno1 = crearContextoSecundario("alumno1");
        Page pageAlumno1 = ctxAlumno1.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno1);
        AuthHelper.loginAlumno(pageAlumno1, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno1, baseUrl, docId);
        pageAlumno1.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/incidente"),
                () -> pageAlumno1.evaluate("() => document.exitFullscreen()")
        );

        // alumno2 entra y se retira voluntariamente confirmando el botón rojo SALIR
        BrowserContext ctxAlumno2 = crearContextoSecundario("alumno2");
        Page pageAlumno2 = ctxAlumno2.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno2);
        AuthHelper.login(pageAlumno2, baseUrl, AuthHelper.getAlumno2User(), AuthHelper.getAlumno2Pass());
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno2, baseUrl, docId);
        pageAlumno2.locator("button.exam-exit:has-text('Salir')").click();
        Locator modalSalir = pageAlumno2.locator("dialog.confirm-dialog.danger[open]");
        modalSalir.waitFor();
        pageAlumno2.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/salir"),
                () -> modalSalir.locator("button:has-text('Salir y entregar')").click()
        );

        // Inspeccionar /monitoreo desde el profesor
        Locator cardMonitor = TeacherMonitorHelper.abrirMonitoreoEnVivo(page, baseUrl, titulo);
        Locator filaAlumno1 = TeacherMonitorHelper.obtenerFilaAlumnoEnMonitoreo(cardMonitor, AuthHelper.ALUMNO_USER);
        Locator filaAlumno2 = TeacherMonitorHelper.obtenerFilaAlumnoEnMonitoreo(cardMonitor, AuthHelper.ALUMNO2_USER);
        filaAlumno1.waitFor();
        filaAlumno2.waitFor();

        // Validaciones sobre alumno1 (Incidente)
        assertTrue(filaAlumno1.locator("span.chip.incident").isVisible(),
                "alumno1 debe tener chip con clase 'chip incident'.");
        assertEquals(1, filaAlumno1.locator("button.resume-button:has-text('Reanudar')").count(),
                "alumno1 en estado Incidente debe mostrar el botón 'Reanudar'.");

        // Validaciones sobre alumno2 (Salida voluntaria con SALIR)
        assertTrue(filaAlumno2.locator("span.chip.exited:has-text('Salió y se entregó')").isVisible(),
                "alumno2 debe mostrar el chip diferenciado 'Salió y se entregó' (clase 'chip exited').");
        assertTrue(filaAlumno2.innerText().contains("Usó el botón SALIR"),
                "La fila de alumno2 debe detallar 'Usó el botón SALIR': " + filaAlumno2.innerText());
        assertEquals(0, filaAlumno2.locator("button.resume-button:has-text('Reanudar')").count(),
                "Un alumno que salió voluntariamente con SALIR NO debe tener botón 'Reanudar'.");
    }

    @Test
    @Tag("lote-monitoreo-docente")
    @Story("ID-091 - Flujo docente de 'Reanudar examen': confirmación y actualización de estados en el panel")
    @DisplayName("ID-091 - Modal de confirmación docente al Reanudar examen y transición a 'Reanudado 1 vez'")
    @Description("Desde /monitoreo, hacer clic en 'Reanudar' sobre un alumno con Incidente, verificar el diálogo de confirmación, autorizar y comprobar el cambio de estado antes y después de que el alumno vuelva a pantalla completa.")
    @Severity(SeverityLevel.CRITICAL)
    void testID091_FlujoDocenteDeReanudarExamenConfirmacionYActualizacionDeEstados() {
        String titulo = "Parcial Flujo Reanudar ID091 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of("Consigna para validar flujo docente de Reanudar examen.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno1");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);

        pageAlumno.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/incidente"),
                () -> pageAlumno.evaluate("() => document.exitFullscreen()")
        );

        Locator cardMonitor = TeacherMonitorHelper.abrirMonitoreoEnVivo(page, baseUrl, titulo);
        Locator filaAlumno1 = TeacherMonitorHelper.obtenerFilaAlumnoEnMonitoreo(cardMonitor, AuthHelper.ALUMNO_USER);
        filaAlumno1.locator("button.resume-button:has-text('Reanudar')").click();

        // Validar diálogo de confirmación docente
        Locator modalReanudar = page.locator("dialog.confirm-dialog[open]");
        modalReanudar.waitFor();
        String textoModal = modalReanudar.textContent();
        assertTrue(textoModal.contains("¿Reanudar el examen de Alumno 1?"),
                "El modal no muestra el título con el nombre del alumno: " + textoModal);
        assertTrue(textoModal.contains("volver a entrar en pantalla completa")
                        && textoModal.contains("El incidente queda registrado"),
                "El modal no explica las condiciones de la reanudación: " + textoModal);

        Response respReanudar = page.waitForResponse(
                r -> r.url().contains("/alumnos/" + AuthHelper.ALUMNO_USER + "/reanudar") && r.request().method().equalsIgnoreCase("POST"),
                () -> modalReanudar.locator(".confirm-dialog-actions button:has-text('Reanudar')").click()
        );
        assertTrue(respReanudar.ok(), "Falló POST /reanudar.");

        // Mientras el alumno aún no pulsó 'Volver al examen', el panel muestra 'Reanudado: esperando que vuelva a pantalla completa'
        filaAlumno1.locator("span.table-sub:has-text('Reanudado: esperando que vuelva a pantalla completa')").waitFor();

        // El alumno presiona 'Volver al examen' -> el panel actualiza a 'Reanudado 1 vez'
        Locator btnVolver = pageAlumno.locator("section.restricted-block.resumed button:has-text('Volver al examen')");
        btnVolver.waitFor();
        btnVolver.click();
        pageAlumno.locator(".exam-content").first().waitFor();

        filaAlumno1.locator("span.table-sub:has-text('Reanudado 1 vez')").waitFor();
        assertEquals("0", cardMonitor.locator(".monitor-stat:has-text('Incidentes') .monitor-stat-value").innerText().trim(),
                "El contador de incidentes activos no volvió a 0 tras reanudar.");
    }

    @Test
    @Tag("lote-monitoreo-docente")
    @Story("ID-092 - Bitácora cronológica / historial de eventos de seguridad por alumno")
    @DisplayName("ID-092 - Drawer de Actividad con bitácora cronológica completa (inicio, señal, incidente, reanudación, reingreso y entrega)")
    @Description("Abrir el detalle de actividad ('Ver actividad') de un alumno que inició el examen, sufrió un incidente, fue reanudado, reingresó y entregó. Validar que la bitácora registre todos los hitos ordenados con hora del servidor.")
    @Severity(SeverityLevel.CRITICAL)
    void testID092_BitacoraCronologicaHistorialDeEventosDeSeguridadPorAlumno() {
        String titulo = "Parcial Bitacora Eventos ID092 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of("Consigna para auditar bitácora cronológica de eventos.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno1");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);

        // 1) Inicio del examen
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);
        StudentExamHelper.responderConsignaTexto(pageAlumno, 0, "Respuesta auditada en bitácora.");

        // 2) Incidente por salida de pantalla completa
        pageAlumno.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/incidente"),
                () -> pageAlumno.evaluate("() => document.exitFullscreen()")
        );

        // 3) Reanudación por el profesor desde /monitoreo
        Locator cardMonitor = TeacherMonitorHelper.abrirMonitoreoEnVivo(page, baseUrl, titulo);
        Locator filaAlumno1 = TeacherMonitorHelper.obtenerFilaAlumnoEnMonitoreo(cardMonitor, AuthHelper.ALUMNO_USER);
        TeacherMonitorHelper.reanudarExamenDeAlumno(page, filaAlumno1, AuthHelper.ALUMNO_USER);

        // 4) Reingreso del alumno a pantalla completa y entrega final
        Locator btnVolver = pageAlumno.locator("section.restricted-block.resumed button:has-text('Volver al examen')");
        btnVolver.waitFor();
        btnVolver.click();
        pageAlumno.locator(".exam-content").first().waitFor();
        StudentExamHelper.abrirModalEnviarParcial(pageAlumno);
        StudentExamHelper.confirmarEnvioEnModal(pageAlumno);

        // 5) Abrir el drawer 'Ver actividad' desde el profesor y verificar la bitácora cronológica
        filaAlumno1.locator("span.chip:has-text('Entregado')").waitFor();
        Locator drawer = TeacherMonitorHelper.abrirDrawerActividad(page, filaAlumno1);

        Locator itemsTimeline = drawer.locator("ol.timeline li.timeline-item");
        itemsTimeline.first().waitFor();
        assertTrue(itemsTimeline.count() >= 5,
                "La bitácora debería registrar al menos 5 eventos del ciclo de vida, pero tiene: " + itemsTimeline.count());

        String textoTimeline = drawer.locator("ol.timeline").innerText();
        assertTrue(textoTimeline.contains("Inicio del examen") || textoTimeline.contains("Aceptó las reglas"),
                "La bitácora no registra el inicio del examen: " + textoTimeline);
        assertTrue(textoTimeline.contains("Señal: salida de pantalla completa"),
                "La bitácora no registra la señal de salida de pantalla completa: " + textoTimeline);
        assertTrue(textoTimeline.contains("Incidente: salida de pantalla completa · examen bloqueado"),
                "La bitácora no registra el bloqueo por Incidente con su causa: " + textoTimeline);
        assertTrue(textoTimeline.contains("Reanudado por"),
                "La bitácora no registra la reanudación autorizada por el profesor: " + textoTimeline);
        assertTrue(textoTimeline.contains("Volvió a pantalla completa (reingreso)"),
                "La bitácora no registra el reingreso a pantalla completa: " + textoTimeline);
        assertTrue(textoTimeline.contains("Entregado"),
                "La bitácora no registra la entrega final del examen: " + textoTimeline);

        // Verificar que cada ítem tenga su hora del servidor (time.timeline-time en formato HH:MM:SS)
        int totalItems = itemsTimeline.count();
        for (int i = 0; i < totalItems; i++) {
            String horaEvento = itemsTimeline.nth(i).locator("time.timeline-time").innerText().trim();
            assertTrue(horaEvento.matches("^\\d{2}:\\d{2}:\\d{2}$"),
                    "El evento #" + (i + 1) + " no posee marca de hora del servidor válida: " + horaEvento);
        }
    }
}
