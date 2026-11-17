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

import static org.junit.jupiter.api.Assertions.*;

@Epic("Módulo de Estudiante - Resiliencia y Temporizador")
@Feature("Autoguardado, Persistencia Offline y Temporizador")
@Tag("lote-resiliencia-temporizador")
public class ResilienciaTemporizadorTest extends TestBase {

    @BeforeEach
    void iniciarSesionProfesor() {
        AuthHelper.loginProfesor(page, baseUrl);
    }

    @AfterEach
    void limpiarExamenes() {
        ExamFormHelper.limpiarExamenesCreados(baseUrl);
    }

    // =========================================================================
    // SUB-BLOQUE A: Autoguardado y Resiliencia de Red / Sesión
    // =========================================================================

    @Test
    @Tag("lote-resiliencia-temporizador")
    @Story("ID-066 - Autoguardado continuo en tiempo real con conexión activa")
    @DisplayName("ID-066 - Autoguardado continuo (< 1s) y transición de estado Guardando -> Guardado")
    @Description("Escribir y editar una respuesta en un examen activo. Validar la transición de estado a 'Guardado' y la petición PUT /api/examen/:id/respuestas exitosa en < 1s.")
    @Severity(SeverityLevel.CRITICAL)
    void testID066_AutoguardadoContinuoEnTiempoRealConConexionActiva() {
        String titulo = "Parcial Autoguardado ID066 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of("Consigna para validar autoguardado continuo online.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);

        Locator textarea = pageAlumno.locator("textarea[aria-label='Respuesta']").first();
        textarea.waitFor();

        // Escribir respuesta y medir la sincronización de autoguardado
        Response respPut = pageAlumno.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/respuestas") && r.request().method().equalsIgnoreCase("PUT"),
                () -> textarea.fill("Respuesta inicial sincronizada en tiempo real.")
        );
        assertTrue(respPut.ok(), "La petición PUT /respuestas falló con HTTP: " + respPut.status());

        Locator chipGuardado = pageAlumno.locator(".save-status.guardado");
        chipGuardado.waitFor();
        assertEquals("Guardado", chipGuardado.innerText().trim(),
                "El indicador de estado no mostró 'Guardado' tras sincronizar con el servidor.");

        // Editar nuevamente y medir latencia de guardado desde el disparo de la petición
        long inicioEdicion = System.currentTimeMillis();
        Response respEdicion = pageAlumno.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/respuestas") && r.request().method().equalsIgnoreCase("PUT"),
                () -> textarea.fill("Respuesta editada y resincronizada automáticamente.")
        );
        chipGuardado.waitFor();
        long duracionSyncMs = System.currentTimeMillis() - inicioEdicion;

        assertTrue(respEdicion.ok(), "La segunda petición PUT /respuestas falló: HTTP " + respEdicion.status());
        // Incluye los 300ms de debounce del cliente + viaje de red
        assertTrue(duracionSyncMs <= Math.max(1300, perfToleranceMs),
                "El autoguardado continuo superó el umbral esperado: " + duracionSyncMs + " ms");
    }

    @Test
    @Tag("lote-resiliencia-temporizador")
    @Story("ID-057 - Guardado automático de respuestas parciales")
    @DisplayName("ID-057 - Autoguardado de respuestas parciales (3 de 5) y recuperación en nueva sesión")
    @Description("En un examen con 5 preguntas, responder solo 3 y dejar 2 en blanco. Verificar contador 'Respondiste 3 de 5', autoguardado y persistencia al reabrir en otro BrowserContext.")
    @Severity(SeverityLevel.CRITICAL)
    void testID057_GuardadoAutomaticoDeRespuestasParciales() {
        String titulo = "Parcial Respuestas Parciales ID057 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of(
                        "Consigna 1 de 5",
                        "Consigna 2 de 5",
                        "Consigna 3 de 5",
                        "Consigna 4 de 5 (quedará vacía)",
                        "Consigna 5 de 5 (quedará vacía)"
                )
        );

        BrowserContext ctxAlumno1 = crearContextoSecundario("alumno_sesion1");
        Page pageAlumno1 = ctxAlumno1.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno1);
        AuthHelper.loginAlumno(pageAlumno1, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno1, baseUrl, docId);

        StudentExamHelper.responderConsignaTexto(pageAlumno1, 0, "Respuesta parcial 1");
        StudentExamHelper.responderConsignaTexto(pageAlumno1, 1, "Respuesta parcial 2");
        StudentExamHelper.responderConsignaTexto(pageAlumno1, 2, "Respuesta parcial 3");

        Locator progreso = pageAlumno1.locator(".exam-progress");
        progreso.waitFor();
        assertTrue(progreso.innerText().contains("Respondiste 3 de 5"),
                "El contador de progreso no refleja 3 de 5 respondidas: " + progreso.innerText());

        // Cerrar el primer contexto y recuperar la sesión en un segundo BrowserContext limpio
        ctxAlumno1.close();

        BrowserContext ctxAlumno2 = crearContextoSecundario("alumno_sesion2");
        Page pageAlumno2 = ctxAlumno2.newPage();
        ClockMockHelper.activarPantallaCompletaPrevia(pageAlumno2);
        ClockMockHelper.instalarRelojSimulado(pageAlumno2);
        AuthHelper.loginAlumno(pageAlumno2, baseUrl);

        pageAlumno2.navigate(baseUrl + "/alumno/parciales/" + docId);
        Locator textareasRecuperados = pageAlumno2.locator("textarea[aria-label='Respuesta']");
        textareasRecuperados.first().waitFor();

        assertEquals(5, textareasRecuperados.count(), "Deben mostrarse las 5 consignas al recuperar la sesión.");
        assertEquals("Respuesta parcial 1", textareasRecuperados.nth(0).inputValue());
        assertEquals("Respuesta parcial 2", textareasRecuperados.nth(1).inputValue());
        assertEquals("Respuesta parcial 3", textareasRecuperados.nth(2).inputValue());
        assertEquals("", textareasRecuperados.nth(3).inputValue());
        assertEquals("", textareasRecuperados.nth(4).inputValue());
        assertTrue(pageAlumno2.locator(".exam-progress").innerText().contains("Respondiste 3 de 5"),
                "El contador de progreso recuperado no conserva 'Respondiste 3 de 5'.");
    }

    @Test
    @Tag("lote-resiliencia-temporizador")
    @Story("ID-067 - Persistencia local en caché ante caída de conexión (Modo Offline)")
    @DisplayName("ID-067 - Persistencia en localStorage y estado 'Sin conexión (guardado en este equipo)'")
    @Description("Activar context.setOffline(true), escribir nuevas respuestas y verificar que el estado cambie a 'Sin conexión (guardado en este equipo)' y que la respuesta se almacene en localStorage.")
    @Severity(SeverityLevel.CRITICAL)
    void testID067_PersistenciaLocalEnCacheAnteCaidaDeConexion() {
        String titulo = "Parcial Offline Cache ID067 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of("Consigna para probar persistencia en caché local sin conexión.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);

        StudentExamHelper.responderConsignaTexto(pageAlumno, 0, "Texto inicial online.");

        // Cortar conexión de red del navegador del alumno
        ctxAlumno.setOffline(true);

        String textoOffline = "Texto escrito durante corte de red almacenado localmente.";
        Locator textarea = pageAlumno.locator("textarea[aria-label='Respuesta']").first();
        textarea.fill(textoOffline);

        // Esperar a que el intento de autoguardado detecte la falta de red y muestre el estado sin-conexion
        Locator chipSinConexion = pageAlumno.locator(".save-status.sin-conexion");
        chipSinConexion.waitFor();
        assertTrue(chipSinConexion.innerText().contains("Sin conexión (guardado en este equipo)"),
                "El indicador no mostró 'Sin conexión (guardado en este equipo)': " + chipSinConexion.innerText());

        // Verificar que localStorage contenga la clave rendix_cache_alumno1_<docId> con el texto offline
        String cacheKey = "rendix_cache_" + AuthHelper.ALUMNO_USER + "_" + docId;
        String cacheJson = (String) pageAlumno.evaluate("(key) => window.localStorage.getItem(key)", cacheKey);
        assertNotNull(cacheJson, "No se encontró el respaldo local en localStorage bajo la clave: " + cacheKey);
        assertTrue(cacheJson.contains(textoOffline),
                "El respaldo en localStorage no contiene el texto editado sin conexión: " + cacheJson);

        ctxAlumno.setOffline(false);
    }

    @Test
    @Tag("lote-resiliencia-temporizador")
    @Story("ID-068 - Resincronización automática tras recuperar conexión (Reconexión)")
    @DisplayName("ID-068 - Resincronización automática (< 1s) con 0 pérdida de datos al volver online")
    @Description("Partiendo de cambios locales en modo offline, restaurar la red con context.setOffline(false) y validar que las respuestas se sincronicen automáticamente (< 1s) pasando a 'Guardado'.")
    @Severity(SeverityLevel.CRITICAL)
    void testID068_ResincronizacionAutomaticaTrasRecuperarConexion() {
        String titulo = "Parcial Reconexion ID068 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of("Consigna para probar resincronización tras corte de red.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);

        ctxAlumno.setOffline(true);
        String respuestaOffline = "Respuesta redactada íntegramente offline y sincronizada al reconectar.";
        Locator textarea = pageAlumno.locator("textarea[aria-label='Respuesta']").first();
        textarea.fill(respuestaOffline);
        pageAlumno.locator(".save-status.sin-conexion").waitFor();

        // Restaurar conexión y medir el tiempo de resincronización automática
        long inicioReconexion = System.currentTimeMillis();
        Response respResync = pageAlumno.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/respuestas") && r.request().method().equalsIgnoreCase("PUT") && r.ok(),
                () -> {
                    ctxAlumno.setOffline(false);
                    pageAlumno.evaluate("() => window.dispatchEvent(new Event('online'))");
                }
        );
        Locator chipGuardado = pageAlumno.locator(".save-status.guardado");
        chipGuardado.waitFor();
        long duracionResyncMs = System.currentTimeMillis() - inicioReconexion;

        assertTrue(respResync.ok(), "El PUT de resincronización falló al reconectar.");
        assertTrue(duracionResyncMs <= Math.max(1500, perfToleranceMs),
                "La resincronización tras reconectar superó el tiempo esperado: " + duracionResyncMs + " ms");
        assertEquals(respuestaOffline, textarea.inputValue(),
                "Se produjo pérdida o alteración de datos tras la resincronización.");
    }

    @Test
    @Tag("lote-resiliencia-temporizador")
    @Story("ID-069 - Recuperación de sesión y respuestas tras cierre abrupto del navegador")
    @DisplayName("ID-069 - Recuperación de respuestas y descuento de tiempo tras cierre abrupto de contexto")
    @Description("Con respuestas en estado 'Guardado', cerrar abruptamente el BrowserContext, esperar unos segundos y reabrir en un nuevo contexto. Validar respuestas intactas y tiempo descontado.")
    @Severity(SeverityLevel.CRITICAL)
    void testID069_RecuperacionDeSesionYRespuestasTrasCierreAbruptoDelNavegador() {
        String titulo = "Parcial Cierre Abrupto ID069 " + System.currentTimeMillis();
        String textoPersistido = "Desarrollo algebraico guardado antes del cierre abrupto del navegador.";
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of("Consigna para validar recuperación tras cierre abrupto.")
        );

        BrowserContext ctxAlumno1 = crearContextoSecundario("alumno_antes_cierre");
        Page pageAlumno1 = ctxAlumno1.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno1);
        AuthHelper.loginAlumno(pageAlumno1, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno1, baseUrl, docId);

        StudentExamHelper.responderConsignaTexto(pageAlumno1, 0, textoPersistido);
        int segundosAntesDeCerrar = StudentExamHelper.obtenerSegundosRestantesUI(pageAlumno1);

        // Cierre abrupto del navegador/contexto
        ctxAlumno1.close();

        // Pequeña pausa real (2s) para comprobar que el reloj del examen siguió corriendo en el servidor
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        BrowserContext ctxAlumno2 = crearContextoSecundario("alumno_despues_cierre");
        Page pageAlumno2 = ctxAlumno2.newPage();
        ClockMockHelper.activarPantallaCompletaPrevia(pageAlumno2);
        ClockMockHelper.instalarRelojSimulado(pageAlumno2);
        AuthHelper.loginAlumno(pageAlumno2, baseUrl);

        pageAlumno2.navigate(baseUrl + "/alumno/parciales/" + docId);
        Locator textareaRecuperado = pageAlumno2.locator("textarea[aria-label='Respuesta']").first();
        textareaRecuperado.waitFor();

        assertEquals(textoPersistido, textareaRecuperado.inputValue(),
                "La respuesta guardada antes del cierre abrupto no se recuperó intacta.");

        int segundosAlReabrir = StudentExamHelper.obtenerSegundosRestantesUI(pageAlumno2);
        assertTrue(segundosAlReabrir < segundosAntesDeCerrar,
                "El temporizador no descontó el tiempo transcurrido durante el cierre: antes="
                        + segundosAntesDeCerrar + "s, después=" + segundosAlReabrir + "s");
    }

    // =========================================================================
    // SUB-BLOQUE B: Temporizador, Alertas y Vencimiento
    // =========================================================================

    @Test
    @Tag("lote-resiliencia-temporizador")
    @Story("ID-063 - Visualización continua del temporizador en formato HH:MM:SS")
    @DisplayName("ID-063 - Temporizador siempre visible (sticky), formato HH:MM:SS y descuento continuo durante 10s")
    @Description("Durante un examen activo, verificar que el temporizador esté siempre visible al hacer scroll, tenga formato HH:MM:SS y descuente segundo a segundo durante al menos 10 segundos sin congelarse.")
    @Severity(SeverityLevel.CRITICAL)
    void testID063_VisualizacionContinuaDelTemporizadorEnFormatoHHMMSS() {
        String titulo = "Parcial Temporizador Continuo ID063 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of(
                        "Consigna 1 para generar scroll vertical.",
                        "Consigna 2 para generar scroll vertical.",
                        "Consigna 3 para generar scroll vertical.",
                        "Consigna 4 para generar scroll vertical."
                )
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);

        Locator headerSticky = pageAlumno.locator(".exam-header.sticky");
        Locator timerStrong = pageAlumno.locator("[role='timer'] strong");
        headerSticky.waitFor();
        timerStrong.waitFor();

        String textoInicial = timerStrong.innerText().trim();
        assertTrue(textoInicial.matches("^\\d{2}:\\d{2}:\\d{2}$"),
                "El temporizador no respeta el formato HH:MM:SS: " + textoInicial);
        int segInicial = StudentExamHelper.obtenerSegundosRestantesUI(pageAlumno);

        // Hacer scroll hasta el final de la página y verificar que el header sticky y el timer sigan visibles
        pageAlumno.evaluate("() => window.scrollTo(0, document.body.scrollHeight)");
        assertTrue(timerStrong.isVisible(), "El temporizador dejó de ser visible tras hacer scroll vertical.");

        // Observar el descuento segundo a segundo durante 10 segundos (espera justificada por regla de negocio de ID-063)
        pageAlumno.waitForTimeout(5000);
        int segIntermedio = StudentExamHelper.obtenerSegundosRestantesUI(pageAlumno);
        assertTrue(segIntermedio < segInicial && (segInicial - segIntermedio) >= 3 && (segInicial - segIntermedio) <= 7,
                "El temporizador no descontó de forma continua a los 5s: inicial=" + segInicial + ", intermedio=" + segIntermedio);

        pageAlumno.waitForTimeout(5000);
        int segFinal = StudentExamHelper.obtenerSegundosRestantesUI(pageAlumno);
        int descuentoTotal = segInicial - segFinal;
        assertTrue(descuentoTotal >= 8 && descuentoTotal <= 13,
                "El temporizador no descontó ~10 segundos tras 10s de observación: inicial=" + segInicial + ", final=" + segFinal);
        assertTrue(timerStrong.innerText().trim().matches("^\\d{2}:\\d{2}:\\d{2}$"),
                "El temporizador perdió el formato HH:MM:SS tras 10s.");
    }

    @Test
    @Tag("lote-resiliencia-temporizador")
    @Story("ID-064 - Sincronización del temporizador con el reloj del servidor (Anti-tampering)")
    @DisplayName("ID-064 - Inmunidad del temporizador frente a manipulación de Date.now en el cliente")
    @Description("Inyectar un desfase de +1 hora / -1 hora en el reloj local del navegador (Date.now) mientras el servidor mantiene su hora real. Validar que el temporizador se mantenga anclado al servidor (precisión <= 1s).")
    @Severity(SeverityLevel.CRITICAL)
    void testID064_SincronizacionDelTemporizadorConRelojDelServidorAntiTampering() {
        String titulo = "Parcial AntiTampering ID064 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of("Consigna para validar protección anti-tampering del reloj.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);

        int segundosAntesTampering = StudentExamHelper.obtenerSegundosRestantesUI(pageAlumno);

        // El alumno intenta manipular su reloj local Date.now atrasándolo 1 hora (-3600000 ms) para ganar tiempo
        pageAlumno.evaluate("() => { window.__clientClockOffsetMs = -3600000; }");

        // Esperar al siguiente latido del servidor (/api/examen/:id/latido cada 2s)
        long inicioLatido = System.currentTimeMillis();
        Response respLatido = pageAlumno.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/latido") && r.request().method().equalsIgnoreCase("POST"),
                () -> {}
        );
        assertTrue(respLatido.ok(), "Falló el latido de sincronización con el servidor.");
        long transcurridoSeg = Math.max(1, Math.round((System.currentTimeMillis() - inicioLatido) / 1000.0));

        int segundosDespuesTampering = StudentExamHelper.obtenerSegundosRestantesUI(pageAlumno);
        int esperadoServidor = (int) (segundosAntesTampering - transcurridoSeg);

        // El temporizador NO debe haber sumado 3600s; debe mantenerse dentro de ~2s del valor real del servidor
        assertTrue(Math.abs(segundosDespuesTampering - esperadoServidor) <= 3,
                "El temporizador fue alterado por la manipulación del reloj local: antes="
                        + segundosAntesTampering + "s, después=" + segundosDespuesTampering + "s");
    }

    @Test
    @Tag("lote-resiliencia-temporizador")
    @Story("ID-065 - Alertas visuales progresivas de tiempo restante (10 min, 5 min y 1 min)")
    @DisplayName("ID-065 - Avisos visuales progresivos a los 10 min, 5 min y 1 min sin interrumpir la escritura")
    @Description("Simular umbrales de 10 min, 5 min y 1 min restantes en el temporizador. Validar la aparición de los avisos visuales y clases de advertencia/urgencia sin interrumpir el trabajo del alumno.")
    @Severity(SeverityLevel.NORMAL)
    void testID065_AlertasVisualesProgresivasDeTiempoRestante() {
        String titulo = "Parcial Alertas Tiempo ID065 " + System.currentTimeMillis();
        // Duración de 15 minutos (900.000 ms)
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "15",
                false,
                false,
                List.of("Consigna para verificar alertas de 10, 5 y 1 minuto.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);

        Locator textarea = pageAlumno.locator("textarea[aria-label='Respuesta']").first();
        textarea.fill("Escribiendo sin interrupciones mientras saltan alertas.");

        // 1) Umbral 10 minutos restantes: avanzar 5m 05s (305.000 ms) sobre los 15m -> quedan ~9m 55s
        pageAlumno.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/latido"),
                () -> ClockMockHelper.setServerOffsetMs(pageAlumno, 305_000L)
        );
        Locator alerta10Min = pageAlumno.locator(".exam-alert:has-text('Quedan 10 minutos.')");
        alerta10Min.waitFor();
        assertTrue(pageAlumno.locator("[role='timer']").getAttribute("class").contains("warning"),
                "El temporizador no activó la clase 'warning' al quedar <= 10 minutos.");
        assertFalse(Boolean.TRUE.equals(textarea.evaluate("el => el.readOnly")),
                "La alerta de 10 minutos bloqueó o interrumpió el campo de texto del alumno.");

        // 2) Umbral 5 minutos restantes: avanzar 10m 05s (605.000 ms) sobre los 15m -> quedan ~4m 55s
        pageAlumno.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/latido"),
                () -> ClockMockHelper.setServerOffsetMs(pageAlumno, 605_000L)
        );
        Locator alerta5Min = pageAlumno.locator(".exam-alert:has-text('Quedan 5 minutos.')");
        alerta5Min.waitFor();
        assertTrue(pageAlumno.locator("[role='timer']").getAttribute("class").contains("warning"),
                "El temporizador no mantuvo la clase 'warning' al quedar <= 5 minutos.");

        // 3) Umbral 1 minuto restante: avanzar 14m 05s (845.000 ms) sobre los 15m -> quedan ~55s
        pageAlumno.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/latido"),
                () -> ClockMockHelper.setServerOffsetMs(pageAlumno, 845_000L)
        );
        Locator alerta1Min = pageAlumno.locator(".exam-alert.urgent:has-text('Queda 1 minuto. Al llegar a cero el parcial se envía solo.')");
        alerta1Min.waitFor();
        assertTrue(pageAlumno.locator("[role='timer']").getAttribute("class").contains("urgent"),
                "El temporizador no activó la clase 'urgent' al quedar <= 1 minuto.");
        assertFalse(Boolean.TRUE.equals(textarea.evaluate("el => el.readOnly")),
                "La alerta de 1 minuto bloqueó prematuramente el campo de respuesta antes de llegar a 00:00:00.");
    }

    @Test
    @Tag("lote-resiliencia-temporizador")
    @Story("ID-070 - Autocierre y envío automático al agotarse el tiempo (Time Out Online)")
    @DisplayName("ID-070 - Bloqueo inmediato al llegar a 00:00:00 y envío automático (<= 3s) con estado 'Enviado por vencimiento'")
    @Description("Con un examen próximo a vencer, dejar que el contador llegue a 00:00:00 con respuestas cargadas. Validar bloqueo inmediato de edición y envío automático en <= 3s con estado 'Enviado por vencimiento'.")
    @Severity(SeverityLevel.BLOCKER)
    void testID070_AutocierreYEnvioAutomaticoAlAgotarseElTiempoOnline() {
        String titulo = "Parcial TimeOut Online ID070 " + System.currentTimeMillis();
        String respuestaGuardada = "Respuesta escrita antes de que el reloj llegue a cero.";
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "10",
                false,
                false,
                List.of("Consigna para validar autocierre y envío automático al expirar el tiempo.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);

        StudentExamHelper.responderConsignaTexto(pageAlumno, 0, respuestaGuardada);

        // Avanzar el reloj del servidor más allá de los 10 minutos (605.000 ms) para alcanzar 00:00:00 en el próximo latido
        Response respAutoEntrega = pageAlumno.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/entregar") && r.request().method().equalsIgnoreCase("POST"),
                () -> ClockMockHelper.setServerOffsetMs(pageAlumno, 605_000L)
        );
        long momentoPeticion = System.currentTimeMillis();
        assertTrue(respAutoEntrega.ok(), "Falló el POST automático a /entregar tras llegar a 00:00:00.");
        assertTrue(respAutoEntrega.request().postData().contains("\"automatico\":true"),
                "La entrega por vencimiento no envió el flag 'automatico: true'.");

        Locator chipVencimiento = pageAlumno.locator(".card.notice span.chip:has-text('Enviado por vencimiento')");
        chipVencimiento.waitFor();
        long duracionCierreMs = System.currentTimeMillis() - momentoPeticion;

        assertTrue(duracionCierreMs <= Math.max(3000, perfToleranceMs),
                "La transición posentrega por vencimiento superó los 3 segundos: " + duracionCierreMs + " ms");

        Locator textareaFinal = pageAlumno.locator("textarea[aria-label='Respuesta']").first();
        assertTrue(Boolean.TRUE.equals(textareaFinal.evaluate("el => el.readOnly")),
                "El campo de respuesta no quedó bloqueado en modo solo lectura tras el vencimiento.");
        assertEquals(respuestaGuardada, textareaFinal.inputValue(),
                "La respuesta autoguardada no se incluyó en el envío automático por vencimiento.");
    }

    @Test
    @Tag("lote-resiliencia-temporizador")
    @Story("ID-071 - Vencimiento del tiempo límite mientras el alumno está Offline")
    @DisplayName("ID-071 - Bloqueo en 00:00:00 estando Offline y envío diferido al reconectar con 'Enviado por vencimiento'")
    @Description("Guardar respuesta, desconectar la red del alumno (setOffline(true)), dejar que expire el temporizador, verificar bloqueo inmediato de edición y reconectar para validar el envío diferido como 'Enviado por vencimiento'.")
    @Severity(SeverityLevel.CRITICAL)
    void testID071_VencimientoDelTiempoLimiteMientrasElAlumnoEstaOffline() {
        String titulo = "Parcial TimeOut Offline ID071 " + System.currentTimeMillis();
        String respuestaOfflineAntesDeCero = "Respuesta editada sin conexión justo antes de llegar a 00:00:00.";
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "10",
                false,
                false,
                List.of("Consigna para validar vencimiento del temporizador sin conexión.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);

        // Configurar que al iniciar el examen queden 4 segundos para el vencimiento
        pageAlumno.navigate(baseUrl + "/alumno/parciales/" + docId);
        pageAlumno.locator("section.restricted-rules").waitFor();
        ClockMockHelper.setCustomRemainingMs(pageAlumno, 4000L);
        pageAlumno.locator("label.restricted-accept input[type='checkbox']").check();
        pageAlumno.locator("button:has-text('Ingresar al examen')").click();
        pageAlumno.locator(".exam-content").first().waitFor();

        // Pasar a modo offline y escribir una respuesta antes de que se agoten los 4 segundos
        ctxAlumno.setOffline(true);
        Locator textarea = pageAlumno.locator("textarea[aria-label='Respuesta']").first();
        textarea.fill(respuestaOfflineAntesDeCero);

        // Esperar a que el contador llegue a 00:00:00 estando offline y bloquee los bloques (.blocks.exam-locked)
        pageAlumno.locator(".blocks.exam-locked").waitFor();
        assertTrue(Boolean.TRUE.equals(textarea.evaluate("el => el.readOnly")),
                "El examen no bloqueó la edición al llegar a 00:00:00 estando offline.");
        assertTrue(pageAlumno.locator(".exam-alert.urgent").first().isVisible(),
                "No se mostró la alerta urgente de tiempo terminado estando offline.");

        // Reconectar la red y validar que envíe automáticamente lo último guardado sin permitir editar más
        Response respEntregaDiferida = pageAlumno.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/entregar") && r.request().method().equalsIgnoreCase("POST") && r.ok(),
                () -> {
                    ctxAlumno.setOffline(false);
                    pageAlumno.evaluate("() => window.dispatchEvent(new Event('online'))");
                }
        );
        assertTrue(respEntregaDiferida.ok(), "Falló la entrega diferida tras recuperar la conexión.");

        Locator chipVencimiento = pageAlumno.locator(".card.notice span.chip:has-text('Enviado por vencimiento')");
        chipVencimiento.waitFor();
        assertEquals(respuestaOfflineAntesDeCero, pageAlumno.locator("textarea[aria-label='Respuesta']").first().inputValue(),
                "El envío diferido al reconectar no respetó la última respuesta guardada en caché local antes de 00:00:00.");
    }
}
