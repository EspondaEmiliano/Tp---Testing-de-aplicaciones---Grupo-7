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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Epic("Módulo de Estudiante y Profesor - Herramienta GeoGebra")
@Feature("Despliegue de GeoGebra (< 3s), Interacción Gráfica/Algebraica y Entrega al Profesor")
@Tag("lote-geogebra")
public class GeoGebraTest extends TestBase {

    @BeforeEach
    void iniciarSesionProfesor() {
        instalarAppletGeoGebraDeterminista(page);
        AuthHelper.loginProfesor(page, baseUrl);
    }

    @AfterEach
    void limpiarExamenes() {
        ExamFormHelper.limpiarExamenesCreados(baseUrl);
    }

    /**
     * Instala el proveedor determinista de window.GGBApplet compatible con el contrato exacto de Rendix
     * (inject, setSize, getBase64, evalCommand, registerAddListener, registerUpdateListener, registerRemoveListener,
     * ggbBase64 inicial y modo readOnly) para evitar bloqueos de red externa contra www.geogebra.org.
     */
    private static void instalarAppletGeoGebraDeterminista(Page targetPage) {
        targetPage.addInitScript(
                "(() => {" +
                "  window.GGBApplet = function(params) {" +
                "    this.inject = function(container) {" +
                "      const objetos = [];" +
                "      if (params.ggbBase64) {" +
                "        try {" +
                "          const decoded = JSON.parse(decodeURIComponent(escape(window.atob(params.ggbBase64))));" +
                "          if (Array.isArray(decoded.objetos)) decoded.objetos.forEach(o => objetos.push(o));" +
                "        } catch (e) {}" +
                "      }" +
                "      const addListeners = [];" +
                "      const updateListeners = [];" +
                "      const removeListeners = [];" +
                "      const root = document.createElement('div');" +
                "      root.className = 'ggb-suite-container';" +
                "      root.setAttribute('data-ggb-id', params.id || 'ggb');" +
                "      root.setAttribute('data-readonly', String(!params.showToolBar));" +
                "      if (params.showToolBar) {" +
                "        const toolbar = document.createElement('div');" +
                "        toolbar.className = 'ggb-toolbar';" +
                "        ['Punto', 'Recta', ' Parábola', 'Circunferencia'].forEach(toolName => {" +
                "          const btn = document.createElement('button');" +
                "          btn.type = 'button';" +
                "          btn.className = 'ggb-tool-btn';" +
                "          btn.textContent = toolName.trim();" +
                "          btn.addEventListener('click', () => {" +
                "            api.evalCommand(toolName.trim() + '_' + (objetos.length + 1) + ' = (1, 2)');" +
                "          });" +
                "          toolbar.appendChild(btn);" +
                "        });" +
                "        root.appendChild(toolbar);" +
                "      }" +
                "      if (params.showAlgebraInput) {" +
                "        const input = document.createElement('input');" +
                "        input.type = 'text';" +
                "        input.className = 'ggb-algebra-input';" +
                "        input.setAttribute('aria-label', 'Entrada algebraica GeoGebra');" +
                "        input.placeholder = 'Entrada algebraica (ej: f(x) = 2x + 1)…';" +
                "        input.addEventListener('keydown', (ev) => {" +
                "          if (ev.key === 'Enter' && input.value.trim()) {" +
                "            ev.preventDefault();" +
                "            api.evalCommand(input.value.trim());" +
                "            input.value = '';" +
                "          }" +
                "        });" +
                "        root.appendChild(input);" +
                "      }" +
                "      const canvas = document.createElement('canvas');" +
                "      canvas.className = 'ggb-canvas';" +
                "      canvas.width = params.width || 600;" +
                "      canvas.height = params.height || 400;" +
                "      const list = document.createElement('ul');" +
                "      list.className = 'ggb-object-list';" +
                "      const renderObjetos = () => {" +
                "        list.replaceChildren();" +
                "        objetos.forEach(obj => {" +
                "          const li = document.createElement('li');" +
                "          li.className = 'ggb-object-item';" +
                "          li.textContent = obj;" +
                "          list.appendChild(li);" +
                "        });" +
                "      };" +
                "      renderObjetos();" +
                "      root.appendChild(canvas);" +
                "      root.appendChild(list);" +
                "      container.replaceChildren(root);" +
                "      const api = {" +
                "        setSize: (w, h) => { canvas.width = w; canvas.height = h; }," +
                "        getBase64: () => window.btoa(unescape(encodeURIComponent(JSON.stringify({ app: 'suite', objetos }))))," +
                "        evalCommand: (cmd) => {" +
                "          objetos.push(cmd);" +
                "          renderObjetos();" +
                "          addListeners.forEach(fn => { try { fn(cmd); } catch (e) {} });" +
                "          updateListeners.forEach(fn => { try { fn(cmd); } catch (e) {} });" +
                "          return true;" +
                "        }," +
                "        registerAddListener: (fn) => addListeners.push(fn)," +
                "        registerUpdateListener: (fn) => updateListeners.push(fn)," +
                "        registerRemoveListener: (fn) => removeListeners.push(fn)" +
                "      };" +
                "      window.__lastGgbApi = api;" +
                "      setTimeout(() => { if (params.appletOnLoad) params.appletOnLoad(api); }, 10);" +
                "    };" +
                "  };" +
                "})();"
        );
    }

    @Test
    @Tag("lote-geogebra")
    @Story("ID-024 - Apertura y carga de la herramienta GeoGebra en el examen (< 3s)")
    @DisplayName("ID-024 - Despliegue del panel flotante GeoGebra en < 3s sin recargar ni perder respuestas")
    @Description("En un examen con la herramienta GeoGebra habilitada, redactar una respuesta en una consigna, abrir el panel de GeoGebra desde el dock de herramientas y validar que cargue en < 3s sin recargar la página ni perder lo escrito.")
    @Severity(SeverityLevel.CRITICAL)
    void testID024_AperturaYCargaDeLaHerramientaGeoGebraEnElExamen() {
        String titulo = "Parcial Herramienta GeoGebra ID024 " + System.currentTimeMillis();
        String textoConsignaPrevia = "Desarrollo escrito antes de abrir la ventana de GeoGebra.";
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                true,
                List.of("Consigna acompañada por la herramienta global GeoGebra.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        instalarAppletGeoGebraDeterminista(pageAlumno);
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);

        StudentExamHelper.responderConsignaTexto(pageAlumno, 0, textoConsignaPrevia);

        // Hacer clic en el botón de la barra de herramientas 'GeoGebra' y medir el tiempo de carga del applet
        Locator tabGeoGebra = pageAlumno.locator("aside.tools-dock button.tools-tab:has-text('GeoGebra')");
        tabGeoGebra.waitFor();

        long inicioApertura = System.currentTimeMillis();
        tabGeoGebra.click();
        Locator ventanaGeoGebra = pageAlumno.locator("section.tools-window-geogebra");
        ventanaGeoGebra.waitFor();
        ventanaGeoGebra.locator("canvas.ggb-canvas").waitFor();
        long duracionCargaMs = System.currentTimeMillis() - inicioApertura;

        assertTrue(duracionCargaMs <= Math.max(3000, perfToleranceMs),
                "El despliegue y carga de la herramienta GeoGebra superó los 3 segundos: " + duracionCargaMs + " ms");
        assertEquals(0, ventanaGeoGebra.locator(".geogebra-overlay.error").count(),
                "Se mostró un mensaje de error al cargar el applet de GeoGebra.");
        assertEquals(textoConsignaPrevia, pageAlumno.locator("textarea[aria-label='Respuesta']").first().inputValue(),
                "Abrir la herramienta GeoGebra borró o alteró la respuesta escrita en la consigna.");
    }

    @Test
    @Tag("lote-geogebra")
    @Story("ID-062 - Interacción con el lienzo/entrada de GeoGebra y persistencia al minimizar/reabrir")
    @DisplayName("ID-062 - Construcción de funciones y puntos en GeoGebra y conservación al cerrar y reabrir el panel")
    @Description("Trazar puntos y funciones matemáticas en la herramienta GeoGebra y en un apartado de tipo GeoGebra. Validar que al minimizar y volver a abrir la herramienta (y al autoguardar el apartado) la construcción permanezca intacta.")
    @Severity(SeverityLevel.CRITICAL)
    void testID062_InteraccionConLienzoDeGeoGebraYPersistenciaAlMinimizarReabrir() {
        String titulo = "Parcial Interaccion GeoGebra ID062 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                true,
                List.of("Consigna para probar persistencia de construcción en GeoGebra.")
        );

        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        instalarAppletGeoGebraDeterminista(pageAlumno);
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);

        // Abrir la herramienta GeoGebra desde el dock
        Locator tabGeoGebra = pageAlumno.locator("aside.tools-dock button.tools-tab:has-text('GeoGebra')");
        tabGeoGebra.click();

        Locator ventanaGeoGebra = pageAlumno.locator("section.tools-window-geogebra");
        Locator inputAlgebra = ventanaGeoGebra.locator("input[aria-label='Entrada algebraica GeoGebra']");
        inputAlgebra.waitFor();

        // Ingresar una función lineal y un punto mediante la entrada algebraica y la barra de herramientas
        inputAlgebra.fill("f(x) = 2x + 3");
        inputAlgebra.press("Enter");
        ventanaGeoGebra.locator("button.ggb-tool-btn:has-text('Punto')").click();

        Locator listaObjetos = ventanaGeoGebra.locator("ul.ggb-object-list");
        assertTrue(listaObjetos.textContent().contains("f(x) = 2x + 3")
                        && listaObjetos.textContent().contains("Punto_2 = (1, 2)"),
                "El applet de GeoGebra no registró los objetos construidos: " + listaObjetos.textContent());

        // Cerrar/minimizar la ventana de GeoGebra y volver a abrirla
        ventanaGeoGebra.locator("button[aria-label='Cerrar herramienta']").click();
        assertTrue(ventanaGeoGebra.isHidden(), "La ventana de GeoGebra no se ocultó al cerrarla.");

        tabGeoGebra.click();
        assertTrue(ventanaGeoGebra.isVisible(), "La ventana de GeoGebra no volvió a desplegarse.");
        assertTrue(listaObjetos.textContent().contains("f(x) = 2x + 3")
                        && listaObjetos.textContent().contains("Punto_2 = (1, 2)"),
                "La construcción realizada en GeoGebra se perdió al minimizar y reabrir la herramienta.");
    }

    @Test
    @Tag("lote-geogebra")
    @Story("ID-031 - Envío de construcción en apartado GeoGebra y visualización en la vista del profesor")
    @DisplayName("ID-031 - Persistencia E2E de apartado GeoGebra (ggbBase64) desde el alumno hasta la revisión del profesor")
    @Description("Crear un examen con un apartado de tipo GeoGebra. Como alumno1, realizar una construcción geométrica/algebraica, esperar su autoguardado en Base64, entregar el parcial y verificar que el profesor visualice la construcción exacta en modo solo lectura.")
    @Severity(SeverityLevel.CRITICAL)
    void testID031_EnvioDeConstruccionEnApartadoGeoGebraYVisualizacionEnVistaDelProfesor() {
        // 1) El profesor crea un examen que incluye un apartado específico de tipo 'geogebra'
        ExamFormHelper.navegarANuevoExamen(page, baseUrl);
        String titulo = "Parcial Apartado GeoGebra ID031 " + System.currentTimeMillis();
        ExamFormHelper.completarCamposBase(page, titulo, "Tema 1", "60", "Consigna teórica previa.");

        Locator selectAgregar = page.locator("select.block-add, select[aria-label='Agregar apartado']").first();
        selectAgregar.selectOption("geogebra");
        page.locator("textarea[placeholder*='consigna']").nth(1).fill("Construir la parábola g(x) = x^2 - 4 y marcar su vértice V = (0, -4).");

        String docUrl = ExamFormHelper.guardarFormularioNuevoExamen(page);
        String docId = ExamFormHelper.extraerDocId(docUrl);
        ExamFormHelper.tomarExamenAhora(page);

        // 2) El alumno1 abre el examen, realiza la construcción en el apartado GeoGebra y entrega
        BrowserContext ctxAlumno = crearContextoSecundario("alumno1");
        Page pageAlumno = ctxAlumno.newPage();
        instalarAppletGeoGebraDeterminista(pageAlumno);
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);
        StudentExamHelper.abrirEIniciarExamenComoAlumno(pageAlumno, baseUrl, docId);

        StudentExamHelper.responderConsignaTexto(pageAlumno, 0, "Explicación analítica de las raíces.");

        Locator bloqueGeoGebraAlumno = pageAlumno.locator("section.card.block[aria-label='Apartado 2'] .geogebra-applet");
        Locator inputAlgebraBloque = bloqueGeoGebraAlumno.locator("input[aria-label='Entrada algebraica GeoGebra']");
        inputAlgebraBloque.waitFor();

        // Trazar la función y el vértice y esperar el PUT /respuestas disparado por el listener de GeoGebra (500ms debounce)
        Response respPutGgb = pageAlumno.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/respuestas") && r.request().method().equalsIgnoreCase("PUT"),
                () -> {
                    inputAlgebraBloque.fill("g(x) = x^2 - 4");
                    inputAlgebraBloque.press("Enter");
                    inputAlgebraBloque.fill("V = (0, -4)");
                    inputAlgebraBloque.press("Enter");
                }
        );
        assertTrue(respPutGgb.ok(), "Falló el autoguardado del bloque GeoGebra en PUT /respuestas.");

        StudentExamHelper.abrirModalEnviarParcial(pageAlumno);
        Response respEntrega = StudentExamHelper.confirmarEnvioEnModal(pageAlumno);
        assertTrue(respEntrega.ok(), "Falló la entrega del examen con bloque GeoGebra.");

        // Verificar que el payload enviado al servidor contenga la construcción codificada en Base64
        String jsonEsperado = "{\"app\":\"suite\",\"objetos\":[\"g(x) = x^2 - 4\",\"V = (0, -4)\"]}";
        String base64Esperado = Base64.getEncoder().encodeToString(jsonEsperado.getBytes(StandardCharsets.UTF_8));
        assertTrue(respEntrega.request().postData().contains(base64Esperado),
                "El payload de entrega no incluyó el ggbBase64 de la construcción realizada.");

        // 3) El profesor abre la entrega del alumno y verifica que la construcción GeoGebra se cargue en modo solo lectura
        page.navigate(baseUrl + "/documentos/" + docId);
        Locator itemEntrega = page.locator("section.submissions li.card:has-text('Alumno 1')").first();
        itemEntrega.waitFor();
        itemEntrega.locator("a:has-text('Ver entrega')").click();
        page.waitForURL(url -> url.contains("/documentos/" + docId + "/entregas/"));

        Locator appletProfesor = page.locator(".geogebra-applet .ggb-suite-container").first();
        appletProfesor.waitFor();
        assertEquals("true", appletProfesor.getAttribute("data-readonly"),
                "El applet GeoGebra en la vista de revisión del profesor debe abrirse en modo solo lectura.");

        String objetosRecibidosProfesor = appletProfesor.locator("ul.ggb-object-list").textContent();
        assertTrue(objetosRecibidosProfesor.contains("g(x) = x^2 - 4")
                        && objetosRecibidosProfesor.contains("V = (0, -4)"),
                "El profesor no visualizó la construcción de GeoGebra enviada por el alumno: " + objetosRecibidosProfesor);
    }
}
