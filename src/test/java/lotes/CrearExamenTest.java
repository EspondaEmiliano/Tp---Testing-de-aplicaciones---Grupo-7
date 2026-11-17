package lotes;

import base.TestBase;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
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
 * Lote: Crear Examen (Casos ID-009 e ID-010)
 *
 * Comando para ejecutar en terminal:
 *   mvn test -Dgroups="lote-crear-examen"
 *
 * Con Maven Wrapper:
 *   .\mvnw.cmd test "-Dgroups=lote-crear-examen"
 * ============================================================================
 */
@Epic("Gestión de Evaluaciones")
@Feature("Crear Examen")
@Tag("lote-crear-examen")
public class CrearExamenTest extends TestBase {

    @BeforeEach
    void setUpCrearExamen() {
        ClockMockHelper.instalarRelojSimulado(page);
    }

    @AfterEach
    void tearDownCrearExamen() {
        ExamFormHelper.limpiarExamenesCreados(baseUrl);
    }

    @Test
    @Tag("lote-crear-examen")
    @Story("ID-009 - Crear exámen en borrador")
    @DisplayName("ID-009 - Crear exámen en borrador")
    @Description("Validar que al seguir una serie de pasos concreta, el sistema crea el parcial con los datos correctos y el curso de alumnos indicado en estado borrador.")
    @Severity(SeverityLevel.BLOCKER)
    void testID009_CrearExamenEnBorrador() {
        String tituloUnico = "Primer parcial de Matematicas " + System.currentTimeMillis();

        AuthHelper.loginProfesor(page, baseUrl);
        ExamFormHelper.navegarANuevoExamen(page, baseUrl);

        // Completar campos base compatibles con la clase Matemáticas (duración 120 min, Tema 1)
        ExamFormHelper.completarCamposBase(
                page,
                tituloUnico,
                "Tema 1",
                "120",
                "Resolver los ejercicios de álgebra y funciones planteados a continuación."
        );

        // Activar herramientas: Calculadora y GeoGebra
        page.locator("label:has-text('Calculadora') input[type='checkbox']").check();
        page.locator("label:has-text('GeoGebra') input[type='checkbox']").check();

        // Guardar sin publicar (botón 'Crear')
        ExamFormHelper.guardarFormularioNuevoExamen(page);

        // Aserción 1: Validar que el examen se encuentre en estado "Sin publicar" / Borrador en su vista de detalle
        Locator chipSinPublicar = page.locator("span.chip:has-text('Sin publicar'), button:has-text('Publicar parcial')").first();
        chipSinPublicar.waitFor();
        assertTrue(chipSinPublicar.isVisible(),
                "El examen recién creado no muestra el estado 'Sin publicar' ni el botón 'Publicar parcial'.");

        // Aserción 2: Validar que aparece listado en la pestaña 'Creados' (sin publicar) del profesor
        page.navigate(baseUrl + "/documentos");
        page.locator(".doc-list, .tabs").first().waitFor();

        Locator examenCard = page.locator("li.card:has-text('" + tituloUnico + "')").first();
        examenCard.waitFor();
        assertTrue(examenCard.isVisible(),
                "El examen en borrador '" + tituloUnico + "' no aparece en la lista de parciales creados del profesor.");
    }

    @Test
    @Tag("lote-crear-examen")
    @Story("ID-010 - Examen en borrador no visible para los alumnos")
    @DisplayName("ID-010 - Examen en borrador no visible para los alumnos")
    @Description("Validar de forma autónoma que un examen recién creado en estado borrador por el profesor no sea visible para ningún usuario alumno.")
    @Severity(SeverityLevel.CRITICAL)
    void testID010_ExamenEnBorradorNoVisibleParaAlumnos() {
        String tituloBorrador = "Borrador ID010 " + System.currentTimeMillis();

        // 1. Crear un examen propio en estado borrador como profesor
        AuthHelper.loginProfesor(page, baseUrl);
        ExamFormHelper.crearExamenEnBorrador(page, baseUrl, tituloBorrador, "Consigna exclusiva de examen en borrador.");

        // 2. Abrir contexto secundario aislado para el alumno y consultar /alumno/parciales
        BrowserContext contextoAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = contextoAlumno.newPage();
        ClockMockHelper.instalarRelojSimulado(pageAlumno);
        AuthHelper.loginAlumno(pageAlumno, baseUrl);

        pageAlumno.navigate(baseUrl + "/alumno/parciales");
        pageAlumno.locator("h1:has-text('Parciales')").waitFor();

        // Aserción: 0 apariciones del examen en borrador en el listado del alumno
        Locator examenParaAlumno = pageAlumno.locator("text='" + tituloBorrador + "'");
        assertEquals(0, examenParaAlumno.count(),
                "El examen en borrador '" + tituloBorrador + "' no debería ser visible para los alumnos.");
    }
}
