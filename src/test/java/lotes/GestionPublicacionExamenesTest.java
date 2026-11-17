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

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ============================================================================
 * Lote: Gestión y Publicación de Exámenes (Casos ID-011 al ID-014)
 *
 * Comando para ejecutar en terminal:
 *   mvn test -Dgroups="lote-gestion-examenes"
 *
 * Con Maven Wrapper:
 *   .\mvnw.cmd test "-Dgroups=lote-gestion-examenes"
 * ============================================================================
 */
@Epic("Gestión de Exámenes")
@Feature("Gestión y Publicación de Exámenes")
@Tag("lote-gestion-examenes")
public class GestionPublicacionExamenesTest extends TestBase {

    @BeforeEach
    void setUpGestionExamenes() {
        ClockMockHelper.instalarRelojSimulado(page);
        AuthHelper.loginProfesor(page, baseUrl);
        ExamFormHelper.navegarANuevoExamen(page, baseUrl);
    }

    @AfterEach
    void tearDownGestionExamenes() {
        ExamFormHelper.limpiarExamenesCreados(baseUrl);
    }

    @Test
    @Tag("lote-gestion-examenes")
    @Story("ID-011 - Transición de Borrador a Publicado y visibilidad de alumno")
    @DisplayName("ID-011 - Transición de Borrador a Publicado y visibilidad de alumno")
    @Description("Verificar que al publicar un examen borrador desde el profesor, este cambie a estado 'Publicado' y pase a ser visible en el panel del alumno.")
    @Severity(SeverityLevel.BLOCKER)
    void testID011_TransicionDeBorradorAPublicadoYVisibilidadDeAlumno() {
        String tituloExamen = "Parcial ID011 " + System.currentTimeMillis();

        // 1. Contexto del Profesor: Crear examen con datos válidos en estado Borrador
        ExamFormHelper.completarCamposBase(page, tituloExamen, "Tema 1", "60", "Resolver consigna de examen publicado.");
        ExamFormHelper.guardarFormularioNuevoExamen(page);

        // Validar que se encuentre inicialmente en estado "Sin publicar" / Borrador
        Locator botonPublicar = page.locator("button:has-text('Publicar parcial'), button:has-text('Publicar')").first();
        botonPublicar.waitFor();
        assertTrue(botonPublicar.isVisible(), "El examen creado no ofrece el botón 'Publicar' para salir de borrador.");

        // 2. Cambiar estado a 'Publicado'
        ExamFormHelper.publicarExamenActual(page);

        // Aserción 1: El estado del examen cambia a 'Publicado' en la vista del profesor
        Locator chipPublicado = page.locator("span.chip.published, span.chip:has-text('Publicado')").first();
        assertTrue(chipPublicado.isVisible(),
                "El examen no cambió visualmente a estado 'Publicado' en el panel del profesor.");

        // 3. Contexto secundario del Alumno registrado en TestBase para trazas y capturas automáticas
        BrowserContext contextAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = contextAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);

        pageAlumno.navigate(baseUrl + "/alumno/parciales");
        pageAlumno.locator("h1:has-text('Parciales')").waitFor();

        // Aserción 2: El examen recién publicado aparece listado y visible para el alumno
        Locator examenEnPanelAlumno = pageAlumno.locator("li.student-exam:has-text('" + tituloExamen + "'), .card:has-text('" + tituloExamen + "')").first();
        examenEnPanelAlumno.waitFor(new Locator.WaitForOptions().setTimeout(5000));
        assertTrue(examenEnPanelAlumno.isVisible(),
                "El examen publicado '" + tituloExamen + "' no aparece visible en el panel de parciales del alumno.");
    }

    @Test
    @Tag("lote-gestion-examenes")
    @Story("ID-012 - Restricción de publicación sin requisitos mínimos")
    @DisplayName("ID-012 - Restricción de publicación sin requisitos mínimos")
    @Description("Impedir la publicación de un examen si carece de enunciado o consignas cargadas.")
    @Severity(SeverityLevel.CRITICAL)
    void testID012_RestriccionDePublicacionSinRequisitosMinimos() {
        String tituloIncompleto = "Incompleto ID012 " + System.currentTimeMillis();

        // 1. Crear examen que carece de consignas
        ExamFormHelper.completarCamposBase(page, tituloIncompleto, "Tema 1", "60", null);

        // Eliminar el apartado por defecto vacío para dejar 0 consignas
        Locator eliminarBtn = page.locator(".block button:has-text('Eliminar')").first();
        if (eliminarBtn.isVisible()) {
            eliminarBtn.click();
        }

        // 2. Guardar el examen en borrador sin consignas
        ExamFormHelper.guardarFormularioNuevoExamen(page);

        // 3. Verificar restricción de publicación en la vista de detalle
        Locator publicarBtn = page.locator("button:has-text('Publicar parcial'), button:has-text('Publicar')").first();
        publicarBtn.waitFor();

        boolean botonDeshabilitado = publicarBtn.isDisabled();
        AtomicReference<String> dialogMensaje = new AtomicReference<>("");
        if (!botonDeshabilitado) {
            page.onceDialog(dialog -> {
                dialogMensaje.set(dialog.message());
                dialog.accept();
            });
            publicarBtn.click();
        }

        boolean sigueSinPublicar = page.locator("span.chip:has-text('Sin publicar')").first().isVisible();
        String textoPublishBar = page.locator(".publish-bar").isVisible()
                ? page.locator(".publish-bar").innerText().toLowerCase() : "";
        boolean informaMotivo = textoPublishBar.contains("necesita al menos una consigna") ||
                dialogMensaje.get().toLowerCase().contains("consigna") ||
                dialogMensaje.get().toLowerCase().contains("no se pudo");

        assertTrue(sigueSinPublicar && (botonDeshabilitado || informaMotivo),
                "[Bug ID-012] El sistema permitió publicar un examen sin consignas o no informó el motivo de restricción.");
    }

    @Test
    @Tag("lote-gestion-examenes")
    @Story("ID-013 - Validación de campos obligatorios para el guardado")
    @DisplayName("ID-013 - Validación de campos obligatorios para el guardado")
    @Description("Validar por separado que el sistema impida el guardado de un nuevo examen cuando falta el título (Variante A) o cuando no se agregan consignas (Variante B).")
    @Severity(SeverityLevel.CRITICAL)
    void testID013_ValidacionDeCamposObligatoriosParaElGuardado() {
        // ====================================================================
        // Variante A: Título vacío + consigna válida -> bloquea guardado y señala título faltante
        // ====================================================================
        ExamFormHelper.completarCamposBase(page, "", "Tema 1", "60", "Consigna válida para Variante A.");
        page.locator("button[type='submit']:has-text('Crear')").click();

        Locator errorTitulo = page.locator("p.error, .field-error").first();
        errorTitulo.waitFor();

        boolean impidioSinTitulo = page.url().contains("/documentos/nuevo");
        String textoErrorTitulo = String.join(" ", page.locator("p.error, .field-error").allInnerTexts()).toLowerCase();
        boolean senalaTitulo = textoErrorTitulo.contains("título") || textoErrorTitulo.contains("titulo") || textoErrorTitulo.contains("obligatorio");

        assertTrue(impidioSinTitulo && senalaTitulo,
                "Variante A falló: El sistema no bloqueó el guardado o no señaló el título obligatorio faltante. Mensaje: " + textoErrorTitulo);

        // ====================================================================
        // Variante B: Título válido + sin consignas (eliminando el apartado) -> debe bloquear guardado y señalar consigna faltante
        // ====================================================================
        String tituloSinConsignas = "Parcial Sin Consignas ID013 " + System.currentTimeMillis();
        ExamFormHelper.navegarANuevoExamen(page, baseUrl);
        ExamFormHelper.completarCamposBase(page, tituloSinConsignas, "Tema 1", "60", null);

        Locator eliminarBtn = page.locator(".block button:has-text('Eliminar')").first();
        if (eliminarBtn.isVisible()) {
            eliminarBtn.click();
        }

        page.locator("button[type='submit']:has-text('Crear')").click();
        // Esperar o bien un mensaje de error en /nuevo o bien la redirección (si ocurre el Bug ID-B003)
        page.waitForCondition(() -> !page.url().endsWith("/nuevo") || page.locator("p.error, .field-error").count() > 0,
                new Page.WaitForConditionOptions().setTimeout(5000));

        if (!page.url().endsWith("/nuevo") && page.url().contains("/documentos/")) {
            ExamFormHelper.registrarExamenParaLimpieza(page.url());
        }

        boolean impidioSinConsignas = page.url().contains("/documentos/nuevo");
        String textoErrorConsigna = String.join(" ", page.locator("p.error, .field-error").allInnerTexts()).toLowerCase();
        boolean senalaConsigna = textoErrorConsigna.contains("consigna") || textoErrorConsigna.contains("apartado");

        assertTrue(impidioSinConsignas && senalaConsigna,
                "[Bug ID-B003] El sistema permitió guardar un examen en borrador sin ninguna consigna en lugar de exigir al menos una consigna.");
    }

    @Test
    @Tag("lote-gestion-examenes")
    @Story("ID-014 - Configuración y selección de herramientas de trabajo")
    @DisplayName("ID-014 - Configuración y selección de herramientas de trabajo")
    @Description("Validar que el profesor pueda marcar y desmarcar las casillas de herramientas (Calculadora, GeoGebra), guardando dentro de la tolerancia y persistiendo su estado.")
    @Severity(SeverityLevel.NORMAL)
    void testID014_ConfiguracionYSeleccionDeHerramientasDeTrabajo() {
        String tituloHerramientas = "Herramientas ID014 " + System.currentTimeMillis();
        ExamFormHelper.completarCamposBase(page, tituloHerramientas, "Tema 1", "60", "Consigna con herramientas configuradas.");

        // 1. Marcar Calculadora: Activado y GeoGebra: Desactivado según matriz oficial
        Locator calcCheckbox = page.locator("label:has-text('Calculadora') input[type='checkbox']");
        Locator geoCheckbox = page.locator("label:has-text('GeoGebra') input[type='checkbox']");

        calcCheckbox.check();
        geoCheckbox.uncheck();

        // 2. Guardar el examen interceptando POST /api/documentos y midiendo el tiempo de respuesta
        long inicio = System.currentTimeMillis();
        Response resp = page.waitForResponse(
                r -> r.url().contains("/api/documentos") && r.request().method().equalsIgnoreCase("POST"),
                () -> page.locator("button[type='submit']:has-text('Crear')").click()
        );
        long duracionMs = System.currentTimeMillis() - inicio;

        page.waitForURL(url -> !url.endsWith("/nuevo") && url.contains("/documentos/"),
                new Page.WaitForURLOptions().setTimeout(5000));
        ExamFormHelper.registrarExamenParaLimpieza(page.url());

        // Aserción 1: El guardado es exitoso y se realiza dentro de perfToleranceMs
        assertTrue(resp.ok(), "La respuesta HTTP al guardar el examen no fue exitosa: " + resp.status());
        assertTrue(duracionMs <= perfToleranceMs,
                "El guardado de la configuración demoró " + duracionMs + " ms (límite configurado: " + perfToleranceMs + " ms).");

        // 3. Acceder a la vista de edición del examen guardado para comprobar persistencia
        page.locator("a:has-text('Editar')").first().click();
        page.waitForURL(url -> url.endsWith("/editar"));
        page.locator("form").first().waitFor();

        // Aserción 2: Las casillas mantienen fielmente su estado tras el guardado
        assertTrue(page.locator("label:has-text('Calculadora') input[type='checkbox']").isChecked(),
                "La casilla 'Calculadora' no se mantuvo marcada tras guardar la configuración.");
        assertFalse(page.locator("label:has-text('GeoGebra') input[type='checkbox']").isChecked(),
                "La casilla 'GeoGebra' no se mantuvo desmarcada tras guardar la configuración.");
    }
}
