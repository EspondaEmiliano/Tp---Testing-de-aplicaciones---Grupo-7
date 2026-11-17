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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Epic("Módulo de Estudiante - Seguridad y Modo Restringido")
@Feature("Modo Restringido, Detección de Incidentes, Reingreso y Salida Voluntaria")
@Tag("lote-modo-restringido")
public class ModoRestringidoTest extends TestBase {

    @BeforeEach
    void iniciarSesionProfesor() {
        AuthHelper.loginProfesor(page, baseUrl);
    }

    @AfterEach
    void limpiarExamenes() {
        ExamFormHelper.limpiarExamenesCreados(baseUrl);
    }

    @Test
    @Tag("lote-modo-restringido")
    @Story("ID-076 - Pantalla informativa previa y aceptación obligatoria del modo restringido")
    @DisplayName("ID-076 - Reglas del modo restringido, aceptación obligatoria e inicio en pantalla completa (< 2s)")
    @Description("Al abrir un examen en horario válido, validar que se listen todas las reglas del modo restringido, que el botón 'Ingresar al examen' esté deshabilitado hasta marcar el checkbox de aceptación y que al confirmar se active pantalla completa y cargue en < 2s.")
    @Severity(SeverityLevel.BLOCKER)
    void testID076_PantallaInformativaPreviaYAceptacionObligatoriaDelModoRestringido() {
        String titulo = "Parcial Reglas Modo Restringido ID076 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of("Consigna protegida bajo modo restringido.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);

        pageAlumno.navigate(baseUrl + "/alumno/parciales/" + docId);
        Locator seccionReglas = pageAlumno.locator("section.restricted-rules");
        seccionReglas.waitFor();

        // Verificar que la lista de reglas incluya todos los puntos clave requeridos (usando textContent por si CSS aplica uppercase)
        String textoReglas = seccionReglas.textContent();
        assertTrue(textoReglas.contains("Modo restringido"), "Falta el título 'Modo restringido'.");
        assertTrue(textoReglas.contains("pantalla completa"), "Falta la regla sobre pantalla completa obligatoria.");
        assertTrue(textoReglas.contains("cambiar de pestaña o de ventana"), "Falta la regla sobre prohibición de cambiar pestaña/ventana.");
        assertTrue(textoReglas.contains("SALIR"), "Falta la mención al botón rojo SALIR.");
        assertTrue(textoReglas.contains("Incidente"), "Falta la advertencia de bloqueo por Incidente.");
        assertTrue(textoReglas.contains("Solo tu profesor puede reanudar"), "Falta la regla sobre reanudación exclusiva del profesor.");

        // Verificar que el botón esté deshabilitado antes de aceptar las reglas
        Locator checkboxAceptar = pageAlumno.locator("label.restricted-accept input[type='checkbox']");
        Locator btnIngresar = pageAlumno.locator("button:has-text('Ingresar al examen')");
        assertFalse(checkboxAceptar.isChecked(), "El checkbox de aceptación no debe iniciar marcado.");
        assertTrue(btnIngresar.isDisabled(), "El botón 'Ingresar al examen' debe estar deshabilitado antes de aceptar las reglas.");

        // Marcar el checkbox y verificar habilitación del botón
        checkboxAceptar.check();
        assertTrue(btnIngresar.isEnabled(), "El botón 'Ingresar al examen' no se habilitó tras marcar el checkbox.");

        long inicioIngreso = System.currentTimeMillis();
        Response respComenzar = pageAlumno.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/comenzar") && r.request().method().equalsIgnoreCase("POST"),
                btnIngresar::click
        );
        pageAlumno.locator(".exam-content").first().waitFor();
        long duracionIngresoMs = System.currentTimeMillis() - inicioIngreso;

        assertTrue(respComenzar.ok(), "Falló POST /comenzar al ingresar al examen: HTTP " + respComenzar.status());
        assertTrue(respComenzar.request().postData().contains("\"aceptoReglas\":true"),
                "La petición POST /comenzar no registró 'aceptoReglas: true'.");
        assertTrue(duracionIngresoMs <= Math.max(2000, perfToleranceMs),
                "La transición a pantalla completa e inicio del examen superó los 2s: " + duracionIngresoMs + " ms");

        Boolean enPantallaCompleta = (Boolean) pageAlumno.evaluate("() => document.fullscreenElement !== null");
        assertTrue(Boolean.TRUE.equals(enPantallaCompleta), "El documento no quedó en estado de pantalla completa activa.");
    }

    @Test
    @Tag("lote-modo-restringido")
    @Story("ID-077 - Bloqueo de inicio si el navegador no soporta o rechaza pantalla completa")
    @DisplayName("ID-077 - Bloqueo de inicio y mensaje claro cuando Fullscreen API no está disponible o es rechazada")
    @Description("Simular tanto la falta de soporte de Fullscreen API (fullscreenEnabled=false) como el rechazo de requestFullscreen(). Validar que no se inicie el examen, no se revelen las consignas y se informe el motivo.")
    @Severity(SeverityLevel.CRITICAL)
    void testID077_BloqueoDeInicioSiElNavegadorNoSoportaORechazaPantallaCompleta() {
        String titulo = "Parcial Rechazo Fullscreen ID077 " + System.currentTimeMillis();
        String textoConsignaSecreta = "Consigna confidencial que NO debe revelarse sin pantalla completa.";
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of(textoConsignaSecreta)
        );

        // --- Escenario A: Navegador sin soporte de pantalla completa (fullscreenEnabled = false) ---
        BrowserContext ctxSinSoporte = crearContextoSecundario("alumno_sin_fullscreen");
        Page pageSinSoporte = ctxSinSoporte.newPage();
        pageSinSoporte.addInitScript("window.__fsDisabled = true;");
        ClockMockHelper.instalarRelojSimulado(pageSinSoporte);
        AuthHelper.loginAlumno(pageSinSoporte, baseUrl);

        pageSinSoporte.navigate(baseUrl + "/alumno/parciales/" + docId);
        pageSinSoporte.locator("section.restricted-rules").waitFor();

        Locator alertaSinSoporte = pageSinSoporte.locator("section.restricted-rules p.error[role='alert']").first();
        alertaSinSoporte.waitFor();
        assertTrue(alertaSinSoporte.innerText().contains("no permiten la pantalla completa"),
                "No se mostró el mensaje de falta de soporte de pantalla completa: " + alertaSinSoporte.innerText());
        assertTrue(pageSinSoporte.locator("label.restricted-accept input[type='checkbox']").isDisabled(),
                "El checkbox debería estar deshabilitado cuando fullscreenEnabled es false.");
        assertTrue(pageSinSoporte.locator("button:has-text('Ingresar al examen')").isDisabled(),
                "El botón 'Ingresar al examen' debería estar deshabilitado cuando fullscreenEnabled es false.");
        assertFalse(pageSinSoporte.locator("body").textContent().contains(textoConsignaSecreta),
                "Se reveló el contenido del examen sin soporte de pantalla completa.");

        // --- Escenario B: El navegador rechaza la solicitud requestFullscreen() ---
        BrowserContext ctxRechazo = crearContextoSecundario("alumno_rechaza_fullscreen");
        Page pageRechazo = ctxRechazo.newPage();
        ClockMockHelper.instalarRelojSimulado(pageRechazo);
        AuthHelper.loginAlumno(pageRechazo, baseUrl);

        AtomicInteger llamadasComenzar = new AtomicInteger(0);
        pageRechazo.onRequest(req -> {
            if (req.url().contains("/api/examen/" + docId + "/comenzar") && req.method().equalsIgnoreCase("POST")) {
                llamadasComenzar.incrementAndGet();
            }
        });

        pageRechazo.navigate(baseUrl + "/alumno/parciales/" + docId);
        pageRechazo.locator("section.restricted-rules").waitFor();
        pageRechazo.evaluate("() => { window.__fsReject = true; }");

        pageRechazo.locator("label.restricted-accept input[type='checkbox']").check();
        pageRechazo.locator("button:has-text('Ingresar al examen')").click();

        Locator alertaRechazo = pageRechazo.locator("section.restricted-rules p.error[role='alert']").first();
        alertaRechazo.waitFor();
        assertTrue(alertaRechazo.innerText().contains("El navegador rechazó la pantalla completa"),
                "No se mostró el error de rechazo de pantalla completa: " + alertaRechazo.innerText());
        assertEquals(0, llamadasComenzar.get(),
                "No debe enviarse POST /comenzar cuando requestFullscreen() es rechazado.");
        assertEquals(0, pageRechazo.locator(".exam-content").count(),
                "No debe renderizarse .exam-content si falla la entrada a pantalla completa.");
        assertFalse(pageRechazo.locator("body").innerText().contains(textoConsignaSecreta),
                "Se filtró el enunciado de la consigna pese al rechazo de pantalla completa.");
    }

    @Test
    @Tag("lote-modo-restringido")
    @Story("ID-078 - Ocultamiento de navegación general y presencia exclusiva del botón SALIR")
    @DisplayName("ID-078 - Barra de navegación general oculta y presencia visible del botón rojo SALIR en el examen")
    @Description("Durante la resolución activa en modo restringido, verificar que la navegación general (topbar) esté oculta y que el encabezado del examen muestre únicamente los indicadores del parcial y el botón 'Salir'.")
    @Severity(SeverityLevel.CRITICAL)
    void testID078_OcultamientoDeNavegacionGeneralYPresenciaExclusivaDelBotonSalir() {
        String titulo = "Parcial UI Modo Restringido ID078 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of("Consigna para verificar ocultamiento de topbar y presencia de botón SALIR.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);

        // Validar clase body.modo-restringido y ocultamiento de la barra superior general (.topbar)
        Boolean tieneClaseModoRestringido = (Boolean) pageAlumno.evaluate(
                "() => document.body.classList.contains('modo-restringido')"
        );
        assertTrue(Boolean.TRUE.equals(tieneClaseModoRestringido),
                "El elemento <body> no tiene aplicada la clase 'modo-restringido'.");

        Locator topbar = pageAlumno.locator("header.topbar");
        if (topbar.count() > 0) {
            assertFalse(topbar.first().isVisible(),
                    "La barra de navegación general (header.topbar) sigue visible durante el modo restringido.");
        }

        // Validar presencia visible y habilitada del botón SALIR junto al temporizador y estado de guardado
        Locator btnSalir = pageAlumno.locator("button.exam-exit:has-text('Salir')");
        btnSalir.waitFor();
        assertTrue(btnSalir.isVisible() && btnSalir.isEnabled(),
                "El botón 'Salir' no está visible y habilitado en el encabezado del examen.");
        assertTrue(pageAlumno.locator("[role='timer']").isVisible(),
                "El temporizador debe estar visible junto al botón Salir.");
        assertTrue(pageAlumno.locator(".exam-progress").isVisible(),
                "El indicador de progreso debe estar visible en el encabezado del examen.");
    }

    @Test
    @Tag("lote-modo-restringido")
    @Story("ID-079 - Detección de salida de pantalla completa como Incidente y bloqueo inmediato")
    @DisplayName("ID-079 - Salida de pantalla completa dispara bloqueo (< 500ms) y registra Incidente FULLSCREEN_EXIT")
    @Description("Con una respuesta guardada en un examen activo, provocar la salida de pantalla completa (document.exitFullscreen()). Validar bloqueo visual en < 500ms, ocultamiento de consignas y POST /incidente con causa FULLSCREEN_EXIT.")
    @Severity(SeverityLevel.BLOCKER)
    void testID079_DeteccionDeSalidaDePantallaCompletaComoIncidenteYBloqueoInmediato() {
        String titulo = "Parcial Incidente Fullscreen ID079 " + System.currentTimeMillis();
        String textoConsigna = "Consigna que debe ocultarse de inmediato al salir de pantalla completa.";
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of(textoConsigna)
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);

        StudentExamHelper.responderConsignaTexto(pageAlumno, 0, "Respuesta antes de presionar Escape.");

        final long[] duracionBloqueoUiMs = new long[1];
        Response respIncidente = pageAlumno.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/incidente") && r.request().method().equalsIgnoreCase("POST"),
                () -> {
                    long inicioSalida = System.currentTimeMillis();
                    pageAlumno.evaluate("() => document.exitFullscreen()");
                    pageAlumno.locator("section.restricted-block").waitFor();
                    duracionBloqueoUiMs[0] = System.currentTimeMillis() - inicioSalida;
                }
        );

        assertTrue(respIncidente.ok(), "Falló el registro del incidente en el backend: HTTP " + respIncidente.status());
        assertTrue(respIncidente.request().postData().contains("FULLSCREEN_EXIT"),
                "El payload del incidente no contiene la señal FULLSCREEN_EXIT: " + respIncidente.request().postData());
        assertTrue(duracionBloqueoUiMs[0] <= 500,
                "El bloqueo visual ante salida de pantalla completa tardó más de 500ms: " + duracionBloqueoUiMs[0] + " ms");

        assertEquals(0, pageAlumno.locator(".exam-content").count(),
                "El contenido del examen (.exam-content) sigue presente tras el bloqueo por incidente.");
        assertFalse(pageAlumno.locator("body").textContent().contains(textoConsigna),
                "El enunciado de la consigna sigue visible en la pantalla bloqueada.");
    }

    @Test
    @Tag("lote-modo-restringido")
    @Story("ID-080 - Detección de cambio de pestaña o pérdida de foco (visibilitychange / blur)")
    @DisplayName("ID-080 - Cambio de pestaña (visibilityState=hidden) o blur dispara bloqueo e Incidente")
    @Description("Durante un examen activo en modo restringido, simular un cambio de pestaña (visibilitychange con visibilityState='hidden'). Validar bloqueo inmediato de la interfaz y registro de incidente VISIBILITY_HIDDEN.")
    @Severity(SeverityLevel.BLOCKER)
    void testID080_DeteccionDeCambioDePestanaOPerdidaDeFoco() {
        String titulo = "Parcial Incidente Pestana ID080 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of("Consigna para probar detección de cambio de pestaña.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);

        StudentExamHelper.responderConsignaTexto(pageAlumno, 0, "Respuesta antes de cambiar de pestaña.");

        Response respIncidente = pageAlumno.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/incidente") && r.request().method().equalsIgnoreCase("POST"),
                () -> pageAlumno.evaluate(
                        "() => {" +
                        "  Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true });" +
                        "  document.dispatchEvent(new Event('visibilitychange'));" +
                        "}"
                )
        );

        pageAlumno.locator("section.restricted-block").waitFor();
        assertTrue(respIncidente.ok(), "Falló POST /incidente ante cambio de pestaña.");
        assertTrue(respIncidente.request().postData().contains("VISIBILITY_HIDDEN"),
                "El incidente registrado no indicó la causa VISIBILITY_HIDDEN: " + respIncidente.request().postData());
        assertTrue(pageAlumno.locator("section.restricted-block").textContent().contains("Examen interrumpido"),
                "No se mostró la pantalla de 'Examen interrumpido' tras el cambio de pestaña.");
    }

    @Test
    @Tag("lote-modo-restringido")
    @Story("ID-081 - Detección de cierre, recarga o navegación fuera del examen como Incidente")
    @DisplayName("ID-081 - Recarga o navegación fuera del examen bloquea la sesión como Incidente conservando respuestas")
    @Description("Con respuestas guardadas en un examen activo, ejecutar page.reload(). Validar que al recargar fuera de pantalla completa se registre el incidente y se muestre la pantalla de bloqueo sin perder las respuestas.")
    @Severity(SeverityLevel.CRITICAL)
    void testID081_DeteccionDeCierreRecargaONavegacionFueraDelExamenComoIncidente() {
        String titulo = "Parcial Incidente Reload ID081 " + System.currentTimeMillis();
        String respuestaPreReload = "Respuesta guardada antes de recargar la página (F5).";
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of("Consigna para probar detección de recarga de página.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);

        StudentExamHelper.responderConsignaTexto(pageAlumno, 0, respuestaPreReload);

        // Recargar la página: dispara pagehide (PAGE_EXIT) o al volver a montar sin fullscreen dispara PAGE_RELOAD
        pageAlumno.reload();

        Locator pantallaBloqueo = pageAlumno.locator("section.restricted-block");
        pantallaBloqueo.waitFor();
        String textoBloqueo = pantallaBloqueo.textContent();
        assertTrue(textoBloqueo.contains("Examen interrumpido"),
                "Al recargar la página no se mostró la pantalla de bloqueo 'Examen interrumpido'.");
        assertTrue(textoBloqueo.contains("Tus respuestas guardadas hasta este momento se conservan"),
                "La pantalla de bloqueo no confirmó la conservación de las respuestas.");
        assertEquals(0, pageAlumno.locator(".exam-content").count(),
                "El examen no debe seguir editable tras recargar la página.");
    }

    @Test
    @Tag("lote-modo-restringido")
    @Story("ID-082 - Consolidación de señales simultáneas en un único Incidente sin duplicados")
    @DisplayName("ID-082 - Ráfaga simultánea de fullscreenchange + visibilitychange + blur genera 1 solo Incidente")
    @Description("Disparar simultáneamente (< 100ms) las señales de salida de pantalla completa, ocultamiento de pestaña y pérdida de foco. Validar que se consoliden en una única petición POST /incidente sin duplicados.")
    @Severity(SeverityLevel.CRITICAL)
    void testID082_ConsolidacionDeSenalesSimultaneasEnUnUnicoIncidenteSinDuplicados() {
        String titulo = "Parcial Consolidacion Incidente ID082 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of("Consigna para validar deduplicación de señales simultáneas.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);

        AtomicInteger contadorPostIncidente = new AtomicInteger(0);
        pageAlumno.onRequest(req -> {
            if (req.url().contains("/api/examen/" + docId + "/incidente") && req.method().equalsIgnoreCase("POST")) {
                contadorPostIncidente.incrementAndGet();
            }
        });

        // Disparar ráfaga simultánea de las 3 señales en el mismo ciclo de ejecución
        Response respIncidente = pageAlumno.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/incidente") && r.request().method().equalsIgnoreCase("POST"),
                () -> pageAlumno.evaluate(
                        "() => {" +
                        "  document.exitFullscreen();" +
                        "  Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true });" +
                        "  document.dispatchEvent(new Event('visibilitychange'));" +
                        "  window.dispatchEvent(new Event('blur'));" +
                        "}"
                )
        );
        pageAlumno.locator("section.restricted-block").waitFor();

        String payload = respIncidente.request().postData();
        assertTrue(respIncidente.ok(), "Falló POST /incidente consolidado.");
        assertTrue(payload.contains("FULLSCREEN_EXIT") && payload.contains("VISIBILITY_HIDDEN"),
                "El incidente consolidado no agrupó las señales simultáneas en el arreglo 'senales': " + payload);

        // Verificar que tras la ventana de agrupación (250ms) solo se haya emitido 1 petición POST /incidente
        pageAlumno.waitForTimeout(400);
        assertEquals(1, contadorPostIncidente.get(),
                "Se generaron múltiples peticiones POST /incidente para un mismo abandono: " + contadorPostIncidente.get());
    }

    @Test
    @Tag("lote-modo-restringido")
    @Story("ID-083 - Flujo de salida voluntaria definitiva mediante el botón SALIR (Confirmación)")
    @DisplayName("ID-083 - Confirmar botón SALIR entrega definitivamente (< 2s) con estado 'Salió y se entregó' sin Incidente")
    @Description("Escribir una respuesta, presionar el botón rojo 'Salir', verificar las advertencias del modal y confirmar 'Salir y entregar'. Validar cierre en < 2s, salida de fullscreen y estado 'Salió y se entregó'.")
    @Severity(SeverityLevel.BLOCKER)
    void testID083_FlujoDeSalidaVoluntariaDefinitivaMedianteElBotonSalir() {
        String titulo = "Parcial Salida Voluntaria ID083 " + System.currentTimeMillis();
        String respuestaGuardada = "Respuesta parcial antes de decidir retirarse voluntariamente con SALIR.";
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of("Consigna para probar salida voluntaria con el botón SALIR.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);

        StudentExamHelper.responderConsignaTexto(pageAlumno, 0, respuestaGuardada);

        AtomicInteger incidentesDisparados = new AtomicInteger(0);
        pageAlumno.onRequest(req -> {
            if (req.url().contains("/api/examen/" + docId + "/incidente") && req.method().equalsIgnoreCase("POST")) {
                incidentesDisparados.incrementAndGet();
            }
        });

        // Abrir el modal del botón SALIR
        pageAlumno.locator("button.exam-exit:has-text('Salir')").click();
        Locator modalSalir = pageAlumno.locator("dialog.confirm-dialog.danger[open]");
        modalSalir.waitFor();

        String textoModal = modalSalir.innerText();
        assertTrue(textoModal.contains("¿Seguro que querés salir del examen?"),
                "El modal no muestra el título de confirmación de salida.");
        assertTrue(textoModal.contains("se entregará con todas las respuestas guardadas")
                        && textoModal.contains("no podrás volver a ingresar"),
                "El modal no advierte que la entrega es definitiva e irreversible: " + textoModal);

        // Confirmar 'Salir y entregar' y medir tiempo de respuesta
        long inicioSalida = System.currentTimeMillis();
        Response respSalir = pageAlumno.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/salir") && r.request().method().equalsIgnoreCase("POST"),
                () -> modalSalir.locator("button:has-text('Salir y entregar')").click()
        );
        Locator chipSalio = pageAlumno.locator(".card.notice span.chip.exited:has-text('Salió y se entregó')");
        chipSalio.waitFor();
        long duracionSalidaMs = System.currentTimeMillis() - inicioSalida;

        assertTrue(respSalir.ok(), "Falló POST /salir: HTTP " + respSalir.status());
        assertTrue(duracionSalidaMs <= Math.max(2000, perfToleranceMs),
                "La salida voluntaria superó los 2 segundos: " + duracionSalidaMs + " ms");
        assertEquals(0, incidentesDisparados.get(),
                "La salida voluntaria mediante SALIR NO debe registrarse como Incidente.");

        Boolean sigueEnFullscreen = (Boolean) pageAlumno.evaluate("() => document.fullscreenElement !== null");
        assertFalse(Boolean.TRUE.equals(sigueEnFullscreen),
                "El navegador no salió del modo pantalla completa tras confirmar SALIR.");
        assertEquals(respuestaGuardada, pageAlumno.locator("textarea[aria-label='Respuesta']").first().inputValue(),
                "La entrega por salida voluntaria no conservó la respuesta guardada.");
    }

    @Test
    @Tag("lote-modo-restringido")
    @Story("ID-084 - Cancelación del modal de salida voluntaria SALIR y continuidad del examen")
    @DisplayName("ID-084 - Cancelar modal SALIR mantiene el examen activo en pantalla completa sin entregar ni bloquear")
    @Description("Abrir el modal del botón 'Salir', hacer clic en 'Cancelar' y verificar que el modal se cierre, no se envíe el examen ni se genere incidente, y el alumno pueda seguir editando.")
    @Severity(SeverityLevel.NORMAL)
    void testID084_CancelacionDelModalDeSalidaVoluntariaSalirYContinuidadDelExamen() {
        String titulo = "Parcial Cancelar Salir ID084 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of("Consigna para probar cancelación del modal SALIR.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);

        pageAlumno.locator("button.exam-exit:has-text('Salir')").click();
        Locator modalSalir = pageAlumno.locator("dialog.confirm-dialog.danger[open]");
        modalSalir.waitFor();

        modalSalir.locator("button:has-text('Cancelar')").click();
        assertEquals(0, pageAlumno.locator("dialog.confirm-dialog.danger[open]").count(),
                "El modal de salida voluntaria no se cerró al presionar Cancelar.");

        // Verificar que sigue en pantalla completa y puede editar respuestas
        Boolean enFullscreen = (Boolean) pageAlumno.evaluate("() => document.fullscreenElement !== null");
        assertTrue(Boolean.TRUE.equals(enFullscreen), "Se perdió la pantalla completa al cancelar el modal SALIR.");

        String nuevoTexto = "Respuesta escrita normalmente después de cancelar el modal SALIR.";
        StudentExamHelper.responderConsignaTexto(pageAlumno, 0, nuevoTexto);
        assertEquals(nuevoTexto, pageAlumno.locator("textarea[aria-label='Respuesta']").first().inputValue());
    }

    @Test
    @Tag("lote-modo-restringido")
    @Story("ID-085 - Pantalla de bloqueo en espera de decisión docente tras un Incidente")
    @DisplayName("ID-085 - Persistencia del bloqueo 'Examen interrumpido' ante recargas mientras el docente no habilite")
    @Description("Tras ocurrir un Incidente, verificar que la pantalla de bloqueo informe que espera autorización del profesor, que las respuestas se conservan y que recargar la página no permita saltar el bloqueo.")
    @Severity(SeverityLevel.CRITICAL)
    void testID085_PantallaDeBloqueoEnEsperaDeDecisionDocenteTrasUnIncidente() {
        String titulo = "Parcial Espera Docente ID085 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of("Consigna bloqueada hasta decisión del docente.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);

        StudentExamHelper.responderConsignaTexto(pageAlumno, 0, "Respuesta resguardada antes del bloqueo.");

        // Provocar incidente
        pageAlumno.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/incidente"),
                () -> pageAlumno.evaluate("() => document.exitFullscreen()")
        );

        Locator bloqueado = pageAlumno.locator("section.restricted-block");
        bloqueado.waitFor();
        String texto = bloqueado.textContent();
        assertTrue(texto.contains("Examen interrumpido"), "Falta el encabezado 'Examen interrumpido'.");
        assertTrue(texto.contains("El examen fue marcado como Incidente"), "Falta indicar el estado de Incidente.");
        assertTrue(texto.contains("Tu profesor puede autorizar que continúes"), "Falta informar que requiere autorización del profesor.");
        assertTrue(texto.contains("Tus respuestas guardadas hasta este momento se conservan"), "Falta informar que las respuestas se conservan.");
        assertEquals(0, pageAlumno.locator("button:has-text('Volver al examen')").count(),
                "No debe mostrarse el botón 'Volver al examen' sin habilitación previa del profesor.");

        // Intentar evadir el bloqueo recargando la URL del examen
        pageAlumno.reload();
        pageAlumno.locator("section.restricted-block").waitFor();
        assertEquals(0, pageAlumno.locator(".exam-content").count(),
                "El alumno logró acceder a las consignas recargando la página sin autorización del docente.");
        assertEquals(0, pageAlumno.locator("button:has-text('Volver al examen')").count(),
                "Apareció 'Volver al examen' tras recargar sin que el docente hubiera reanudado.");
    }

    @Test
    @Tag("lote-modo-restringido")
    @Story("ID-086 - Reingreso del alumno al examen tras habilitación del profesor")
    @DisplayName("ID-086 - Habilitación docente actualiza sola la vista del alumno y permite reingresar en pantalla completa")
    @Description("Con el alumno bloqueado por Incidente, autorizar la reanudación desde la sesión del Profesor. Validar que la pantalla del alumno se actualice sola mostrando 'Volver al examen' y que al pulsarlo recupere sus respuestas y tiempo.")
    @Severity(SeverityLevel.BLOCKER)
    void testID086_ReingresoDelAlumnoAlExamenTrasHabilitacionDelProfesor() {
        String titulo = "Parcial Reingreso Docente ID086 " + System.currentTimeMillis();
        String respuestaConservada = "Respuesta clave que debe seguir intacta al reingresar tras incidente.";
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of("Consigna para validar flujo de reanudación por el profesor.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);

        StudentExamHelper.responderConsignaTexto(pageAlumno, 0, respuestaConservada);

        // El alumno sufre un incidente por salida de pantalla completa
        pageAlumno.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/incidente"),
                () -> pageAlumno.evaluate("() => document.exitFullscreen()")
        );
        pageAlumno.locator("section.restricted-block").waitFor();

        // El profesor abre el panel de Monitoreo (/monitoreo) y autoriza la reanudación de alumno1
        page.navigate(baseUrl + "/monitoreo");
        Locator filaAlumno = page.locator("tr.monitor-row.incidente:has-text('" + AuthHelper.ALUMNO_USER + "')").first();
        filaAlumno.waitFor();
        filaAlumno.locator("button.resume-button:has-text('Reanudar')").click();

        Locator modalReanudar = page.locator("dialog.confirm-dialog[open]");
        modalReanudar.waitFor();
        Response respReanudar = page.waitForResponse(
                r -> r.url().contains("/alumnos/" + AuthHelper.ALUMNO_USER + "/reanudar") && r.request().method().equalsIgnoreCase("POST"),
                () -> modalReanudar.locator("button:has-text('Reanudar')").click()
        );
        assertTrue(respReanudar.ok(), "Falló POST /reanudar del profesor: HTTP " + respReanudar.status());

        // La pantalla del alumno debe actualizarse sola (polling cada 2s) mostrando section.restricted-block.resumed
        Locator bloqueHabilitado = pageAlumno.locator("section.restricted-block.resumed");
        bloqueHabilitado.waitFor();
        assertTrue(bloqueHabilitado.textContent().contains("El profesor habilitó nuevamente tu examen"),
                "La pantalla del alumno no informó la habilitación del profesor.");

        Locator btnVolver = bloqueHabilitado.locator("button:has-text('Volver al examen')");
        Response respReingresar = pageAlumno.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/reingresar") && r.request().method().equalsIgnoreCase("POST"),
                btnVolver::click
        );
        assertTrue(respReingresar.ok(), "Falló POST /reingresar del alumno: HTTP " + respReingresar.status());

        pageAlumno.locator(".exam-content").first().waitFor();
        Boolean enFullscreenNuevamente = (Boolean) pageAlumno.evaluate("() => document.fullscreenElement !== null");
        assertTrue(Boolean.TRUE.equals(enFullscreenNuevamente),
                "El reingreso al examen no volvió a activar el modo pantalla completa.");
        assertEquals(respuestaConservada, pageAlumno.locator("textarea[aria-label='Respuesta']").first().inputValue(),
                "La respuesta previa al incidente no se conservó al reingresar al examen.");
        assertTrue(StudentExamHelper.obtenerSegundosRestantesUI(pageAlumno) > 0,
                "El temporizador no continuó activo con el tiempo restante tras el reingreso.");
    }

    @Test
    @Tag("lote-modo-restringido")
    @Story("ID-087 - Nuevo bloqueo automático ante reincidencia tras haber sido reanudado")
    @DisplayName("ID-087 - Reincidencia tras reanudación vuelve a bloquear el examen y registra un segundo Incidente")
    @Description("En un examen que ya fue reanudado una vez por el profesor, provocar una segunda salida del modo restringido. Validar nuevo bloqueo inmediato y registro independiente del segundo incidente.")
    @Severity(SeverityLevel.CRITICAL)
    void testID087_NuevoBloqueoAutomaticoAnteReincidenciaTrasHaberSidoReanudado() {
        String titulo = "Parcial Reincidencia ID087 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of("Consigna para validar bloqueo ante reincidencia.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);

        // 1er Incidente
        pageAlumno.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/incidente"),
                () -> pageAlumno.evaluate("() => document.exitFullscreen()")
        );
        pageAlumno.locator("section.restricted-block").waitFor();

        // Profesor reanuda desde /monitoreo
        page.navigate(baseUrl + "/monitoreo");
        Locator filaAlumno = page.locator("tr.monitor-row.incidente:has-text('" + AuthHelper.ALUMNO_USER + "')").first();
        filaAlumno.waitFor();
        filaAlumno.locator("button.resume-button:has-text('Reanudar')").click();
        Locator modalReanudar = page.locator("dialog.confirm-dialog[open]");
        modalReanudar.waitFor();
        page.waitForResponse(
                r -> r.url().contains("/alumnos/" + AuthHelper.ALUMNO_USER + "/reanudar"),
                () -> modalReanudar.locator("button:has-text('Reanudar')").click()
        );

        // Alumno vuelve al examen
        Locator btnVolver = pageAlumno.locator("section.restricted-block.resumed button:has-text('Volver al examen')");
        btnVolver.waitFor();
        pageAlumno.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/reingresar"),
                btnVolver::click
        );
        pageAlumno.locator(".exam-content").first().waitFor();

        // 2do Incidente (Reincidencia): el alumno vuelve a salir de pantalla completa
        Response respSegundoIncidente = pageAlumno.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/incidente") && r.request().method().equalsIgnoreCase("POST"),
                () -> pageAlumno.evaluate("() => document.exitFullscreen()")
        );
        assertTrue(respSegundoIncidente.ok(), "No se registró el segundo incidente en el servidor tras la reincidencia.");

        Locator bloqueoReincidente = pageAlumno.locator("section.restricted-block:not(.resumed)");
        bloqueoReincidente.waitFor();
        assertTrue(bloqueoReincidente.textContent().contains("Examen interrumpido"),
                "El examen no volvió a bloquearse tras la segunda salida del modo restringido.");
        assertEquals(0, pageAlumno.locator("button:has-text('Volver al examen')").count(),
                "La autorización anterior no debe reutilizarse para una segunda infracción.");
    }

    @Test
    @Tag("lote-modo-restringido")
    @Story("ID-093 - Registro de Incidente ocurrido mientras el alumno está Offline y sincronización al reconectar")
    @DisplayName("ID-093 - Incidente ocurrido sin conexión bloquea localmente y se sincroniza al recuperar la red")
    @Description("Desconectar la red del alumno (setOffline(true)), provocar una salida de pantalla completa y verificar el bloqueo inmediato en UI y persistencia en localStorage. Reconectar y validar el envío diferido del incidente al backend.")
    @Severity(SeverityLevel.CRITICAL)
    void testID093_RegistroDeIncidenteOcurridoMientrasElAlumnoEstaOfflineYSincronizacionAlReconectar() {
        String titulo = "Parcial Incidente Offline ID093 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of("Consigna para validar detección de incidente offline y reporte diferido.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);

        StudentExamHelper.responderConsignaTexto(pageAlumno, 0, "Respuesta antes de desconectar red y salir de fullscreen.");

        // Cortar la red del alumno y provocar salida de pantalla completa estando offline
        ctxAlumno.setOffline(true);
        pageAlumno.evaluate("() => document.exitFullscreen()");

        Locator pantallaBloqueo = pageAlumno.locator("section.restricted-block");
        pantallaBloqueo.waitFor();
        assertTrue(pantallaBloqueo.textContent().contains("Examen interrumpido"),
                "El examen no se bloqueó localmente al salir de pantalla completa estando offline.");

        // Verificar que el evento de incidente haya quedado encolado en localStorage (rendix_eventos_pendientes_alumno1)
        String keyEventos = "rendix_eventos_pendientes_" + AuthHelper.ALUMNO_USER;
        pageAlumno.waitForFunction(
                "(k) => { const v = window.localStorage.getItem(k); return v && v.includes('FULLSCREEN_EXIT'); }",
                keyEventos
        );

        // Reconectar la red y verificar que el incidente pendiente se sincronice con diferido: true
        Response respIncidenteDiferido = pageAlumno.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/incidente") && r.request().method().equalsIgnoreCase("POST") && r.ok(),
                () -> {
                    ctxAlumno.setOffline(false);
                    pageAlumno.evaluate("() => window.dispatchEvent(new Event('online'))");
                }
        );

        assertTrue(respIncidenteDiferido.ok(), "Falló la sincronización del incidente diferido al reconectar.");
        assertTrue(respIncidenteDiferido.request().postData().contains("\"diferido\":true"),
                "El incidente sincronizado tras reconexión no incluyó el flag 'diferido: true': "
                        + respIncidenteDiferido.request().postData());
    }
}
