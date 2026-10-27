package lotes;

import base.TestBase;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.PlaywrightException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ============================================================================
 * Lote: Validaciones de Examen (Casos ID-013, ID-014 e ID-015)
 *
 * Comando para ejecutar en terminal:
 *   mvn test -Dgroups="lote-validaciones-examen"
 *
 * Con Maven Wrapper:
 *   .\mvnw.cmd test "-Dgroups=lote-validaciones-examen"
 * ============================================================================
 */
@Tag("lote-validaciones-examen")
public class ValidacionesExamenTest extends TestBase {

    private static final String NUEVO_PARCIAL_URL = "https://rendix.vercel.app/documentos/nuevo";

    @BeforeEach
    void setUpNuevoParcial() {
        // Fijamos la hora de referencia al inicio del día para que horarios como 13:30 sean a futuro
        page.addInitScript("Date.now = () => new Date('2026-10-22T10:00:00').getTime();");

        // Iniciar sesión como profesor
        page.navigate("https://rendix.vercel.app/login");
        Locator logoutBtn = page.locator("button:has-text('Cerrar sesión')");
        if (logoutBtn.count() > 0 && logoutBtn.first().isVisible()) {
            logoutBtn.first().click();
            page.waitForURL(url -> url.endsWith("/login"));
        }
        page.locator("label:has-text('Usuario o mail') input, input:not([type='password'])").first().fill("profesor1");
        page.locator("input[type='password']").fill("rendix123");
        page.locator("button[type='submit'], button:has-text('Ingresar')").click();
        page.waitForURL(url -> !url.endsWith("/login"));

        // Navegar directamente a la vista de "Nuevo parcial", dejando el formulario listo
        page.navigate(NUEVO_PARCIAL_URL);
        page.waitForSelector("form");
    }

    @Test
    @Tag("lote-validaciones-examen")
    @DisplayName("ID-013 - Asignación de puntaje válido en consigna")
    void testID013_AsignacionPuntajeValidoEnConsigna() {
        // Agregar consigna al examen
        Locator consignaInput = page.locator("textarea[placeholder*='consigna'], textarea").first();
        consignaInput.fill("Consigna con asignación de puntaje.");

        // Localizar el campo de puntaje en la consigna
        Locator puntajeInput = page.locator("label:has-text('Puntaje') input, input[name*='puntaje'], input[placeholder*='puntaje'], input[name*='score'], .score-input, [aria-label*='puntaje']");

        boolean existeCampoPuntaje = puntajeInput.count() > 0 && puntajeInput.first().isVisible();
        if (existeCampoPuntaje) {
            // Asignar puntaje numérico válido (ej. 1 a 10)
            puntajeInput.first().fill("8");
            String valor = puntajeInput.first().inputValue();
            assertTrue(valor.equals("8"), "El sistema no aceptó el puntaje asignado.");

            // Validar que actualiza el puntaje total del examen sin arrojar errores
            Locator puntajeTotal = page.locator(".total-score, .puntaje-total, [aria-label*='Puntaje total']");
            if (puntajeTotal.count() > 0 && puntajeTotal.first().isVisible()) {
                assertTrue(puntajeTotal.first().innerText().contains("8"), "No se actualizó el puntaje total del examen.");
            }
        } else {
            // Asertar contra el resultado esperado documentado: la interfaz debe disponer del campo de puntaje
            assertTrue(existeCampoPuntaje,
                    "El sistema no dispone del campo para asignar puntaje numérico a la consigna.");
        }
    }

    @Test
    @Tag("lote-validaciones-examen")
    @DisplayName("ID-014 - Validación de puntaje fuera de rango o inválido")
    void testID014_ValidacionPuntajeFueraDeRangoOInvalido() {
        Locator consignaInput = page.locator("textarea[placeholder*='consigna'], textarea").first();
        consignaInput.fill("Consigna para prueba de puntaje inválido.");

        Locator puntajeInput = page.locator("label:has-text('Puntaje') input, input[name*='puntaje'], input[placeholder*='puntaje'], input[name*='score'], .score-input, [aria-label*='puntaje']");

        boolean existeCampoPuntaje = puntajeInput.count() > 0 && puntajeInput.first().isVisible();
        if (existeCampoPuntaje) {
            // 1. Intentar asignar caracteres alfanuméricos
            boolean rechazoAlfanumerico = false;
            try {
                puntajeInput.first().fill("abc");
            } catch (PlaywrightException e) {
                rechazoAlfanumerico = true;
            }
            if (!rechazoAlfanumerico) {
                String val = puntajeInput.first().inputValue();
                rechazoAlfanumerico = val.isEmpty() || !val.equals("abc");
            }

            // 2. Intentar asignar valor negativo
            puntajeInput.first().fill("-5");

            // 3. Intentar asignar valor fuera de rango
            puntajeInput.first().fill("99");

            Locator errorMsg = page.locator("p.error, .field-error");
            boolean muestraAlerta = errorMsg.count() > 0 && errorMsg.first().isVisible();
            boolean inputInvalido = Boolean.TRUE.equals(puntajeInput.first().evaluate("el => !el.checkValidity()"));

            assertTrue(rechazoAlfanumerico || muestraAlerta || inputInvalido,
                    "El sistema no restringió ni alertó sobre valores de puntaje fuera de rango o alfanuméricos.");
        } else {
            // Asertar contra el resultado esperado documentado
            assertTrue(existeCampoPuntaje,
                    "El sistema no dispone del campo de puntaje para validar restricciones de formato y rango.");
        }
    }

    @Test
    @Tag("lote-validaciones-examen")
    @DisplayName("ID-015 - Validación del campo tiempo límite - Rango y Formato")
    void testID015_ValidacionCampoTiempoLimite() {
        Locator duracionInput = page.locator("label:has-text('Duración') input[type='number']");
        assertTrue(duracionInput.isVisible(), "No se encontró el campo de duración en el formulario.");

        // 1. Validar restricción de formato no numérico (ej. texto)
        duracionInput.fill("");
        boolean rechazoTexto = false;
        try {
            duracionInput.fill("noventa");
        } catch (PlaywrightException e) {
            // El navegador o Playwright restringe la inserción de texto en inputs numéricos
            rechazoTexto = true;
        }

        if (!rechazoTexto) {
            duracionInput.pressSequentially("noventa");
            String valorActual = duracionInput.inputValue();
            rechazoTexto = valorActual.isEmpty() || !valorActual.equals("noventa") ||
                    Boolean.TRUE.equals(duracionInput.evaluate("el => !el.checkValidity()"));
        }
        assertTrue(rechazoTexto, "El campo de duración aceptó caracteres alfanuméricos indebidamente.");

        // 2. Validar valores fuera de rango permitido (ej. valor negativo, 0 o superior a 120)
        // Completar datos mínimos requeridos para probar la validación de tiempo límite
        page.locator("label:has-text('Clase') select").selectOption("matematicas");
        page.locator("label:has-text('Título') input").fill("Examen Validación Tiempo");
        page.locator("label:has-text('Tema') select").selectOption("Tema 1");

        // Ingresar duración lógicamente inválida: 0 minutos (el rango permitido es 1 a 120)
        duracionInput.fill("0");

        // Intentar guardar con duración fuera de rango
        page.locator("button[type='submit']:has-text('Crear')").click();
        page.waitForTimeout(500);

        // Aserción: Validar que el campo no acepta el formato o que el sistema despliega un mensaje advirtiendo sobre el error en el tiempo límite
        Locator errorMsg = page.locator("p.error");
        boolean muestraAlertaDuracion = errorMsg.isVisible() &&
                (errorMsg.innerText().toLowerCase().contains("duración") ||
                 errorMsg.innerText().toLowerCase().contains("minutos") ||
                 errorMsg.innerText().toLowerCase().contains("1 y 120"));
        boolean html5Invalido = Boolean.TRUE.equals(duracionInput.evaluate("el => !el.checkValidity()"));
        boolean impidioGuardado = page.url().contains("/documentos/nuevo");

        assertTrue(impidioGuardado && (muestraAlertaDuracion || html5Invalido),
                "El sistema no advirtió ni restringió la duración cuando el valor estaba fuera del rango permitido.");
    }
}
