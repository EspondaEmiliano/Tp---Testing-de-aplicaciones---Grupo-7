package lotes;

import base.TestBase;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ============================================================================
 * Lote: Crear Exámen (Casos ID-009, ID-010 e ID-011)
 *
 * Comando para ejecutar en terminal:
 *   mvn test -Dgroups="lote-crear-examen"
 *
 * Con Maven Wrapper:
 *   .\mvnw.cmd test "-Dgroups=lote-crear-examen"
 * ============================================================================
 */
@Tag("lote-crear-examen")
public class CrearExamenTest extends TestBase {

    private static final String LOGIN_URL = "https://rendix.vercel.app/login";
    private static final String TITULO_EXAMEN = "Primer parcial de Matematicas";

    private void iniciarSesionComo(String usuario, String password) {
        // Fijamos la hora de referencia al inicio del día 22/10/2026 para que las 13:30 sea un horario a futuro válido
        page.addInitScript("Date.now = () => new Date('2026-10-22T10:00:00').getTime();");

        page.navigate(LOGIN_URL);
        Locator logoutBtn = page.locator("button:has-text('Cerrar sesión')");
        if (logoutBtn.count() > 0 && logoutBtn.first().isVisible()) {
            logoutBtn.first().click();
            page.waitForURL(url -> url.endsWith("/login"));
        }
        page.locator("label:has-text('Usuario o mail') input, input:not([type='password'])").first().fill(usuario);
        page.locator("input[type='password']").fill(password);
        page.locator("button[type='submit'], button:has-text('Ingresar')").click();
        page.waitForURL(url -> !url.endsWith("/login"));
    }

    private void loginProfesor() {
        iniciarSesionComo("profesor1", "rendix123");
    }

    private void loginAlumno() {
        iniciarSesionComo("alumno1", "rendix123");
    }

    @Test
    @Tag("lote-crear-examen")
    @DisplayName("ID-009 - Crear exámen en borrador")
    void testID009_CrearExamenEnBorrador() {
        loginProfesor();

        // Acceder a Nuevo parcial
        page.navigate("https://rendix.vercel.app/documentos/nuevo");
        page.waitForSelector("form");

        // 1. Seleccionar Clase: Matemáticas
        page.locator("label:has-text('Clase') select").selectOption("matematicas");

        // 2. Título: Primer parcial de Matematicas
        page.locator("label:has-text('Título') input").fill(TITULO_EXAMEN);

        // 3. Tema: Tema 1
        page.locator("label:has-text('Tema') select").selectOption("Tema 1");

        // 4. Duración: 120 minutos
        page.locator("label:has-text('Duración') input[type='number']").fill("120");

        // 5. Turno: Turno tarde (13:30 a 17:30)
        page.locator("label:has-text('Turno') select").selectOption("tarde");

        // 6. Fecha: 22/10/2026
        page.locator("input[type='date']").fill("2026-10-22");

        // 7. Hora de inicio: 13:30
        page.locator("input[type='time']").fill("13:30");

        // 8. Activar herramientas: Calculadora y GeoGebra
        page.locator("label:has-text('Calculadora') input[type='checkbox']").check();
        page.locator("label:has-text('GeoGebra') input[type='checkbox']").check();

        // 9. Agregar al menos una consigna
        Locator consignaInput = page.locator("textarea[placeholder*='consigna'], textarea").first();
        consignaInput.fill("Resolver los ejercicios de álgebra y funciones planteados a continuación.");

        // 10. Accionar el guardado sin publicar (botón 'Crear')
        page.locator("button[type='submit']:has-text('Crear')").click();
        page.waitForURL(url -> url.matches(".*/documentos/[a-zA-Z0-9-]+$"));

        // 11. Aserción: Validar que el examen se encuentre en estado "Borrador" / "Sin publicar"
        // Validamos primero en la pantalla de detalle del parcial
        Locator noticeSinPublicar = page.locator(".notice:has-text('Sin publicar'), span.chip:has-text('Sin publicar')");
        assertTrue(noticeSinPublicar.isVisible(), "El examen no muestra el estado 'Sin publicar' en su vista de detalle.");

        // Y también validamos en la lista general de parciales del profesor
        page.navigate("https://rendix.vercel.app/documentos");
        page.waitForSelector(".doc-list, .tabs");

        Locator examenCard = page.locator("li.card:has-text('" + TITULO_EXAMEN + "')");
        assertTrue(examenCard.isVisible(), "El examen creado no aparece en la lista de parciales del profesor.");
    }

    @Test
    @Tag("lote-crear-examen")
    @DisplayName("ID-010 - Examen en borrador no visible para los alumnos")
    void testID010_ExamenEnBorradorNoVisibleParaAlumnos() {
        loginAlumno();

        // Acceder a la lista de parciales del alumno
        page.navigate("https://rendix.vercel.app/alumno/parciales");
        page.waitForTimeout(1500);

        // Validar que el examen en borrador NO aparece en la lista de exámenes disponibles
        Locator examenParaAlumno = page.locator("text='" + TITULO_EXAMEN + "'");
        assertFalse(examenParaAlumno.isVisible(),
                "El examen en borrador '" + TITULO_EXAMEN + "' no debería ser visible para los alumnos.");
    }

    @Test
    @Tag("lote-crear-examen")
    @DisplayName("ID-011 - Restricción de publicación sin requisitos mínimos")
    void testID011_RestriccionPublicacionSinRequisitosMinimos() {
        loginProfesor();

        page.navigate("https://rendix.vercel.app/documentos/nuevo");
        page.waitForSelector("form");

        // Completar clase, tema, duración y turno para evaluar la restricción sin consignas ni título
        page.locator("label:has-text('Clase') select").selectOption("matematicas");
        page.locator("label:has-text('Tema') select").selectOption("Tema 1");
        page.locator("label:has-text('Duración') input[type='number']").fill("60");
        page.locator("label:has-text('Turno') select").selectOption("tarde");
        page.locator("input[type='date']").fill("2026-10-22");
        page.locator("input[type='time']").fill("13:30");

        // Dejar título vacío
        page.locator("label:has-text('Título') input").fill("");

        // Intentar eliminar la consigna o dejarla completamente vacía
        Locator eliminarBtn = page.locator("button:has-text('Eliminar')");
        if (eliminarBtn.count() > 0 && eliminarBtn.first().isVisible()) {
            page.onceDialog(dialog -> dialog.accept());
            eliminarBtn.first().click();
        }
        Locator consignaInput = page.locator("textarea[placeholder*='consigna']");
        if (consignaInput.count() > 0) {
            consignaInput.fill("");
        }

        // Accionar el intento de guardado
        page.locator("button[type='submit']:has-text('Crear')").click();
        page.waitForTimeout(1000);

        // Aserción: Validar que el sistema impida el guardado y despliegue un mensaje informando el motivo
        // (Nota: Bug ID-B003 documentado donde el sistema permite el guardado sin consignas. La aserción debe fallar orgánicamente)
        Locator errorMsg = page.locator("p.error");
        boolean impidioGuardado = page.url().contains("/documentos/nuevo");
        boolean desplegoErrorConsigna = errorMsg.isVisible() && errorMsg.innerText().toLowerCase().contains("consigna");

        assertTrue(impidioGuardado && desplegoErrorConsigna,
                "[Bug ID-B003] El sistema permite guardar o no despliega el mensaje de restricción cuando faltan consignas mínimas obligatorias.");
    }
}
