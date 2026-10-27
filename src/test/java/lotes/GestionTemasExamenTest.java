package lotes;

import base.TestBase;
import com.microsoft.playwright.Locator;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ============================================================================
 * Lote: Gestión de Temas y Puntajes (Casos ID-019 al ID-026)
 *
 * Comando para ejecutar en terminal:
 *   mvn test -Dgroups="lote-temas-examen"
 *
 * Con Maven Wrapper:
 *   .\mvnw.cmd test "-Dgroups=lote-temas-examen"
 * ============================================================================
 */
@Epic("Gestión de Evaluaciones")
@Feature("Gestión de Temas y Puntajes")
@Tag("lote-temas-examen")
public class GestionTemasExamenTest extends TestBase {

    private static final String NUEVO_PARCIAL_URL = "https://rendix.vercel.app/documentos/nuevo";

    @BeforeEach
    void setUpGestionTemas() {
        // Fijamos fecha y hora de referencia a las 10:00 para validar horarios vespertinos futuros
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

        // Navegar a la vista de creación de examen
        page.navigate(NUEVO_PARCIAL_URL);
        page.waitForSelector("form");
    }

    // ========================================================================
    // 1. GESTIÓN DE TEMAS (ID-019 a ID-021)
    // ========================================================================

    @Test
    @Tag("lote-temas-examen")
    @Story("ID-019 - Creación de examen con múltiples temas simétricos en puntaje")
    @DisplayName("ID-019 - Creación de examen con múltiples temas simétricos en puntaje")
    @Description("Crea un Tema 1 con 2 consignas de 5 puntos c/u, y un Tema 2 con 1 consigna de 10 puntos. Valida que el examen se crea correctamente con ambos temas.")
    @Severity(SeverityLevel.CRITICAL)
    void testID019_CrearExamenConMultiplesTemasSimetricos() {
        String titulo = "Parcial Multi-Tema ID-019 " + System.currentTimeMillis();

        // 1. Completar campos base del examen
        page.locator("label:has-text('Clase') select").selectOption("matematicas");
        page.locator("label:has-text('Título') input").fill(titulo);
        page.locator("label:has-text('Duración') input[type='number']").fill("60");
        page.locator("label:has-text('Turno') select").selectOption("tarde");
        page.locator("input[type='date']").fill("2026-10-22");
        page.locator("input[type='time']").fill("13:30");

        // 2. Configurar Tema 1
        Locator selectorTema = page.locator("label:has-text('Tema') select, input[placeholder*='tema'], input[name*='tema']").first();
        if (selectorTema.isVisible()) {
            if ("select".equalsIgnoreCase(selectorTema.evaluate("el => el.tagName").toString())) {
                selectorTema.selectOption("Tema 1");
            } else {
                selectorTema.fill("Tema 1");
            }
        }

        // Consigna 1 de Tema 1 con 5 puntos
        Locator consignas = page.locator("textarea[placeholder*='consigna'], textarea");
        consignas.first().fill("Consigna 1 del Tema 1 (5 puntos)");
        Locator puntajeInputs = page.locator("label:has-text('Puntaje') input, input[name*='puntaje'], input[placeholder*='puntaje'], .score-input");
        if (puntajeInputs.count() > 0 && puntajeInputs.first().isVisible()) {
            puntajeInputs.first().fill("5");
        }

        // Agregar Consigna 2 a Tema 1 con 5 puntos
        Locator btnAgregarConsigna = page.locator("button:has-text('Agregar consigna'), button:has-text('+ Consigna'), .add-question");
        if (btnAgregarConsigna.count() > 0 && btnAgregarConsigna.first().isVisible()) {
            btnAgregarConsigna.first().click();
            if (consignas.count() > 1) {
                consignas.nth(1).fill("Consigna 2 del Tema 1 (5 puntos)");
            }
            if (puntajeInputs.count() > 1) {
                puntajeInputs.nth(1).fill("5");
            }
        }

        // 3. Configurar Tema 2 con 1 consigna de 10 puntos
        Locator btnAgregarTema = page.locator("button:has-text('Agregar tema'), button:has-text('+ Tema'), .add-theme");
        boolean permiteMultiplesTemas = btnAgregarTema.count() > 0 && btnAgregarTema.first().isVisible();

        if (permiteMultiplesTemas) {
            btnAgregarTema.first().click();
            Locator inputTema2 = page.locator("input[placeholder*='tema'], select[name*='tema']").last();
            if ("select".equalsIgnoreCase(inputTema2.evaluate("el => el.tagName").toString())) {
                inputTema2.selectOption("Tema 2");
            } else {
                inputTema2.fill("Tema 2");
            }

            Locator consignasTema2 = page.locator(".tema-2 textarea, textarea").last();
            consignasTema2.fill("Consigna 1 del Tema 2 (10 puntos)");
            Locator puntajesTema2 = page.locator(".tema-2 input[type='number'], input[placeholder*='puntaje']").last();
            if (puntajesTema2.isVisible()) {
                puntajesTema2.fill("10");
            }

            // Guardar examen
            page.locator("button[type='submit']:has-text('Crear')").click();
            page.waitForURL(url -> url.matches(".*/documentos/[a-zA-Z0-9-]+$"));

            // Validar que se creó correctamente con ambos temas
            boolean tema1Presente = page.locator("text='Tema 1'").isVisible();
            boolean tema2Presente = page.locator("text='Tema 2'").isVisible();
            assertTrue(tema1Presente && tema2Presente,
                    "El examen no se creó correctamente con ambos temas ('Tema 1' y 'Tema 2').");
        } else {
            // Asertar contra la regla de negocio esperada documentada
            assertTrue(permiteMultiplesTemas,
                    "La interfaz no permite crear un examen con múltiples temas ('Tema 1' con 2 consignas de 5 pts y 'Tema 2' con 1 consigna de 10 pts).");
        }
    }

    @Test
    @Tag("lote-temas-examen")
    @Story("ID-020 - Restricción de temas con nombres repetidos")
    @DisplayName("ID-020 - Restricción de temas con nombres repetidos")
    @Description("Intenta crear dos temas distintos pero ambos con el nombre 'Tema 1'. Aserta que el sistema impide la creación e indica que el nombre está repetido.")
    @Severity(SeverityLevel.NORMAL)
    void testID020_TemasConNombreRepetido() {
        // 1. Completar campos base
        page.locator("label:has-text('Clase') select").selectOption("matematicas");
        page.locator("label:has-text('Título') input").fill("Parcial Temas Repetidos");
        page.locator("label:has-text('Duración') input[type='number']").fill("60");
        page.locator("label:has-text('Turno') select").selectOption("tarde");
        page.locator("input[type='date']").fill("2026-10-22");
        page.locator("input[type='time']").fill("13:30");
        page.locator("textarea[placeholder*='consigna'], textarea").first().fill("Consigna para test de temas repetidos.");

        // 2. Establecer el primer tema como 'Tema 1'
        Locator selectorTema = page.locator("label:has-text('Tema') select, input[placeholder*='tema'], input[name*='tema']").first();
        if (selectorTema.isVisible()) {
            if ("select".equalsIgnoreCase(selectorTema.evaluate("el => el.tagName").toString())) {
                selectorTema.selectOption("Tema 1");
            } else {
                selectorTema.fill("Tema 1");
            }
        }

        // 3. Intentar agregar un segundo tema también con el nombre 'Tema 1'
        Locator btnAgregarTema = page.locator("button:has-text('Agregar tema'), button:has-text('+ Tema'), .add-theme");
        boolean permiteMultiplesTemas = btnAgregarTema.count() > 0 && btnAgregarTema.first().isVisible();

        if (permiteMultiplesTemas) {
            btnAgregarTema.first().click();
            Locator inputTema2 = page.locator("input[placeholder*='tema'], select[name*='tema']").last();
            if ("select".equalsIgnoreCase(inputTema2.evaluate("el => el.tagName").toString())) {
                inputTema2.selectOption("Tema 1");
            } else {
                inputTema2.fill("Tema 1");
            }

            // Intentar crear el examen
            page.locator("button[type='submit']:has-text('Crear')").click();
            page.waitForTimeout(500);

            // Validar que el sistema impide la creación e indica que el nombre está repetido
            Locator errorMsg = page.locator("p.error, .field-error, .toast-error");
            String textoError = errorMsg.isVisible() ? errorMsg.innerText().toLowerCase() : "";
            boolean indicaNombreRepetido = textoError.contains("repetido") ||
                    textoError.contains("duplicado") ||
                    textoError.contains("mismo nombre") ||
                    textoError.contains("ya existe");
            boolean impidioCreacion = page.url().contains("/documentos/nuevo");

            assertTrue(impidioCreacion && indicaNombreRepetido,
                    "El sistema no impidió la creación ni alertó sobre nombres de tema repetidos ('Tema 1'). Texto de error: " + textoError);
        } else {
            // Si la interfaz no soporta múltiples temas para poder duplicar sus nombres
            assertTrue(permiteMultiplesTemas,
                    "La interfaz no permite agregar temas múltiples para validar la restricción por nombres repetidos ('Tema 1').");
        }
    }

    @Test
    @Tag("lote-temas-examen")
    @Story("ID-021 - Examen con campo de tema vacío")
    @DisplayName("ID-021 - Examen con campo de tema vacío")
    @Description("Intenta crear el parcial dejando el nombre del tema vacío y aserta el mensaje de campo obligatorio.")
    @Severity(SeverityLevel.NORMAL)
    void testID021_ExamenConCampoDeTemaVacio() {
        // Completar campos mínimos requeridos
        page.locator("label:has-text('Clase') select").selectOption("matematicas");
        page.locator("label:has-text('Título') input").fill("Parcial Validación Tema Vacío");
        page.locator("label:has-text('Duración') input[type='number']").fill("60");
        page.locator("label:has-text('Turno') select").selectOption("tarde");
        page.locator("input[type='date']").fill("2026-10-22");
        page.locator("input[type='time']").fill("13:30");
        page.locator("textarea[placeholder*='consigna'], textarea").first().fill("Consigna para examen con tema vacío.");

        // Dejar explícitamente el campo de tema vacío
        Locator selectorTema = page.locator("label:has-text('Tema') select, input[name*='tema'], input[placeholder*='tema']").first();
        if ("select".equalsIgnoreCase(selectorTema.evaluate("el => el.tagName").toString())) {
            selectorTema.selectOption("");
        } else {
            selectorTema.fill("");
        }

        // Intentar guardar el examen sin tema
        page.locator("button[type='submit']:has-text('Crear')").click();
        page.waitForTimeout(500);

        // Validar que el sistema lo impide y despliega mensaje de campo obligatorio
        Locator errorMsg = page.locator("p.error, .field-error");
        String textoError = errorMsg.isVisible() ? errorMsg.innerText().toLowerCase() : "";
        boolean impidioGuardado = page.url().contains("/documentos/nuevo");
        boolean esInvalidoHtml5 = Boolean.TRUE.equals(selectorTema.evaluate("el => !el.checkValidity()"));
        boolean mensajeCampoObligatorio = textoError.contains("tema") ||
                textoError.contains("obligatorio") ||
                textoError.contains("requerido") ||
                esInvalidoHtml5;

        assertTrue(impidioGuardado && mensajeCampoObligatorio,
                "El sistema no impidió el guardado ni alertó sobre el campo obligatorio de tema vacío.");
    }

    // ========================================================================
    // 2. LÍMITES Y SIMETRÍA DE PUNTAJES (ID-022 a ID-024)
    // ========================================================================

    @Test
    @Tag("lote-temas-examen")
    @Story("ID-022 - Examen con consignas con puntaje total menor a 0")
    @DisplayName("ID-022 - Examen con consignas con puntaje total menor a 0")
    @Description("Ingresa consignas cuyo puntaje total sea negativo (ej. -1). Aserta que el sistema arroja error indicando que el puntaje es menor a 0.")
    @Severity(SeverityLevel.NORMAL)
    void testID022_ConsignasConPuntajeTotalMenorACero() {
        // Completar campos base
        page.locator("label:has-text('Clase') select").selectOption("matematicas");
        page.locator("label:has-text('Título') input").fill("Parcial Puntaje Negativo");
        page.locator("label:has-text('Tema') select").selectOption("Tema 1");
        page.locator("label:has-text('Duración') input[type='number']").fill("60");
        page.locator("label:has-text('Turno') select").selectOption("tarde");
        page.locator("input[type='date']").fill("2026-10-22");
        page.locator("input[type='time']").fill("13:30");
        page.locator("textarea[placeholder*='consigna'], textarea").first().fill("Consigna con puntaje negativo.");

        Locator puntajeInput = page.locator("label:has-text('Puntaje') input, input[name*='puntaje'], input[placeholder*='puntaje'], .score-input");
        boolean existePuntaje = puntajeInput.count() > 0 && puntajeInput.first().isVisible();

        if (existePuntaje) {
            puntajeInput.first().fill("-1");
            page.locator("button[type='submit']:has-text('Crear')").click();
            page.waitForTimeout(500);

            Locator errorMsg = page.locator("p.error, .field-error");
            String textoError = errorMsg.isVisible() ? errorMsg.innerText().toLowerCase() : "";
            boolean esInvalidoHtml5 = Boolean.TRUE.equals(puntajeInput.first().evaluate("el => !el.checkValidity()"));
            boolean errorMenorACero = textoError.contains("menor a 0") ||
                    textoError.contains("menor que 0") ||
                    textoError.contains("negativo") ||
                    textoError.contains("mayor o igual a 0") ||
                    textoError.contains("0") ||
                    esInvalidoHtml5;
            boolean bloqueoGuardado = page.url().contains("/documentos/nuevo");

            assertTrue(bloqueoGuardado && errorMenorACero,
                    "El sistema no arrojó error indicando que el puntaje es menor a 0 al ingresar puntaje -1. Texto obtenido: " + textoError);
        } else {
            assertTrue(existePuntaje,
                    "El sistema no dispone del motor de puntaje de consignas para validar puntaje menor a 0.");
        }
    }

    @Test
    @Tag("lote-temas-examen")
    @Story("ID-023 - Examen con consignas con puntaje total mayor a 10")
    @DisplayName("ID-023 - Examen con consignas con puntaje total mayor a 10")
    @Description("Ingresa consignas cuyo puntaje total sea mayor a 10 (ej. 11). Aserta que el sistema arroja error.")
    @Severity(SeverityLevel.NORMAL)
    void testID023_ConsignasConPuntajeTotalMayorADiez() {
        // Completar campos base
        page.locator("label:has-text('Clase') select").selectOption("matematicas");
        page.locator("label:has-text('Título') input").fill("Parcial Puntaje Excedido");
        page.locator("label:has-text('Tema') select").selectOption("Tema 1");
        page.locator("label:has-text('Duración') input[type='number']").fill("60");
        page.locator("label:has-text('Turno') select").selectOption("tarde");
        page.locator("input[type='date']").fill("2026-10-22");
        page.locator("input[type='time']").fill("13:30");
        page.locator("textarea[placeholder*='consigna'], textarea").first().fill("Consigna con puntaje excedido.");

        Locator puntajeInput = page.locator("label:has-text('Puntaje') input, input[name*='puntaje'], input[placeholder*='puntaje'], .score-input");
        boolean existePuntaje = puntajeInput.count() > 0 && puntajeInput.first().isVisible();

        if (existePuntaje) {
            puntajeInput.first().fill("11");
            page.locator("button[type='submit']:has-text('Crear')").click();
            page.waitForTimeout(500);

            Locator errorMsg = page.locator("p.error, .field-error");
            String textoError = errorMsg.isVisible() ? errorMsg.innerText().toLowerCase() : "";
            boolean esInvalidoHtml5 = Boolean.TRUE.equals(puntajeInput.first().evaluate("el => !el.checkValidity()"));
            boolean errorExceso = textoError.contains("10") ||
                    textoError.contains("máximo") ||
                    textoError.contains("maximo") ||
                    textoError.contains("supera") ||
                    esInvalidoHtml5;
            boolean bloqueoGuardado = page.url().contains("/documentos/nuevo");

            assertTrue(bloqueoGuardado && errorExceso,
                    "El sistema no arrojó error al ingresar consignas con puntaje total mayor a 10 (ej. 11). Texto obtenido: " + textoError);
        } else {
            assertTrue(existePuntaje,
                    "El sistema no dispone del motor de puntaje de consignas para bloquear excesos de puntaje (> 10).");
        }
    }

    @Test
    @Tag("lote-temas-examen")
    @Story("ID-024 - Bloqueo de creación por disparidad en el puntaje total entre temas")
    @DisplayName("ID-024 - Bloqueo de creación por disparidad en el puntaje total entre temas")
    @Description("Intenta crear Tema 1 con consignas que sumen 7 puntos y Tema 2 con consignas que sumen 8 puntos. Aserta que el sistema bloquea la creación por disparidad.")
    @Severity(SeverityLevel.CRITICAL)
    void testID024_DisparidadPuntajeEntreTemas() {
        // 1. Completar campos base
        page.locator("label:has-text('Clase') select").selectOption("matematicas");
        page.locator("label:has-text('Título') input").fill("Parcial Disparidad Temas");
        page.locator("label:has-text('Duración') input[type='number']").fill("60");
        page.locator("label:has-text('Turno') select").selectOption("tarde");
        page.locator("input[type='date']").fill("2026-10-22");
        page.locator("input[type='time']").fill("13:30");

        // 2. Configurar Tema 1 con 7 puntos
        Locator selectorTema = page.locator("label:has-text('Tema') select, input[placeholder*='tema'], input[name*='tema']").first();
        if (selectorTema.isVisible()) {
            if ("select".equalsIgnoreCase(selectorTema.evaluate("el => el.tagName").toString())) {
                selectorTema.selectOption("Tema 1");
            } else {
                selectorTema.fill("Tema 1");
            }
        }
        page.locator("textarea[placeholder*='consigna'], textarea").first().fill("Consigna de Tema 1 (7 puntos)");
        Locator puntajeInputs = page.locator("label:has-text('Puntaje') input, input[name*='puntaje'], input[placeholder*='puntaje'], .score-input");
        if (puntajeInputs.count() > 0 && puntajeInputs.first().isVisible()) {
            puntajeInputs.first().fill("7");
        }

        // 3. Configurar Tema 2 con 8 puntos
        Locator btnAgregarTema = page.locator("button:has-text('Agregar tema'), button:has-text('+ Tema'), .add-theme");
        boolean permiteMultiplesTemas = btnAgregarTema.count() > 0 && btnAgregarTema.first().isVisible();

        if (permiteMultiplesTemas) {
            btnAgregarTema.first().click();
            Locator inputTema2 = page.locator("input[placeholder*='tema'], select[name*='tema']").last();
            if (inputTema2.isVisible()) {
                inputTema2.fill("Tema 2");
            }
            Locator consignasTema2 = page.locator(".tema-2 textarea, textarea").last();
            consignasTema2.fill("Consigna de Tema 2 (8 puntos)");
            Locator puntajeTema2 = page.locator(".tema-2 input[type='number'], input[placeholder*='puntaje']").last();
            if (puntajeTema2.isVisible()) {
                puntajeTema2.fill("8");
            }

            // 4. Intentar crear el examen
            page.locator("button[type='submit']:has-text('Crear')").click();
            page.waitForTimeout(500);

            // 5. Validar que el sistema bloquea la creación por disparidad en el puntaje total
            Locator errorMsg = page.locator("p.error, .field-error, .theme-score-warning, .score-mismatch");
            String textoError = errorMsg.isVisible() ? errorMsg.innerText().toLowerCase() : "";
            boolean bloqueoCreacion = page.url().contains("/documentos/nuevo");
            boolean alertaDisparidad = textoError.contains("disparidad") ||
                    textoError.contains("puntaje") ||
                    textoError.contains("no coinciden") ||
                    textoError.contains("mismo puntaje") ||
                    textoError.contains("igual");

            assertTrue(bloqueoCreacion && alertaDisparidad,
                    "El sistema no bloqueó la creación ni alertó sobre la disparidad de puntajes entre Tema 1 (7 pts) y Tema 2 (8 pts). Texto obtenido: " + textoError);
        } else {
            // Asertar contra la regla de negocio documentada
            assertTrue(permiteMultiplesTemas,
                    "El sistema no dispone de control multi-tema para configurar Tema 1 (7 pts) y Tema 2 (8 pts) y validar el bloqueo por disparidad.");
        }
    }

    // ========================================================================
    // 3. CAMBIO DE ALCANCE EN EDICIÓN (ID-025 y ID-026)
    // ========================================================================

    @Test
    @Tag("lote-temas-examen")
    @Story("ID-025 - Edición permitida en examen no iniciado")
    @DisplayName("ID-025 - Edición permitida en examen no iniciado")
    @Description("Navega a un examen ya creado pero no iniciado por alumnos. Edita cualquier campo válido y guarda. Aserta que el sistema permite la edición.")
    @Severity(SeverityLevel.CRITICAL)
    void testID025_EdicionPermitidaExamenNoIniciado() {
        String tituloOriginal = "Parcial Edicion ID-025 " + System.currentTimeMillis();
        String tituloModificado = tituloOriginal + " - Modificado";

        // 1. Crear un examen que queda en estado borrador (no iniciado por alumnos)
        page.locator("label:has-text('Clase') select").selectOption("matematicas");
        page.locator("label:has-text('Título') input").fill(tituloOriginal);
        page.locator("label:has-text('Tema') select").selectOption("Tema 1");
        page.locator("label:has-text('Duración') input[type='number']").fill("60");
        page.locator("label:has-text('Turno') select").selectOption("tarde");
        page.locator("input[type='date']").fill("2026-10-22");
        page.locator("input[type='time']").fill("13:30");
        page.locator("textarea[placeholder*='consigna'], textarea").first().fill("Consigna inicial para prueba de edición de examen.");
        page.locator("button[type='submit']:has-text('Crear')").click();
        page.waitForURL(url -> url.matches(".*/documentos/[a-zA-Z0-9-]+$"));

        // 2. Acceder a la edición del examen
        Locator btnEditar = page.locator("a:has-text('Editar'), button:has-text('Editar')").first();
        assertTrue(btnEditar.isVisible(), "No se encontró el botón o enlace 'Editar' en el examen no iniciado.");
        btnEditar.click();
        page.waitForURL(url -> url.contains("/editar"));

        // 3. Editar un campo válido del examen (Título y Duración)
        Locator inputTitulo = page.locator("label:has-text('Título') input, input[name='titulo']").first();
        inputTitulo.fill(tituloModificado);

        Locator inputDuracion = page.locator("label:has-text('Duración') input[type='number']").first();
        if (inputDuracion.isVisible()) {
            inputDuracion.fill("90");
        }

        // 4. Guardar cambios
        page.locator("button[type='submit']:has-text('Guardar cambios'), button:has-text('Guardar')").first().click();
        page.waitForURL(url -> !url.endsWith("/editar"));

        // 5. Asertar que el sistema permite la edición y refleja los cambios
        boolean edicionReflejada = page.locator("text='" + tituloModificado + "'").isVisible() ||
                page.locator("h1:has-text('" + tituloModificado + "')").isVisible() ||
                page.locator("text='90 min'").isVisible() ||
                page.locator("text='90'").isVisible();

        assertTrue(edicionReflejada, "El sistema no reflejó los cambios editados del examen tras guardar.");
    }

    @Test
    @Tag("lote-temas-examen")
    @Story("ID-026 - Edición bloqueada en examen iniciado por un alumno")
    @DisplayName("ID-026 - Edición bloqueada en examen iniciado por un alumno")
    @Description("Navega a un examen que ya tiene estado 'iniciado' por un alumno. Intenta acceder a la edición. Aserta que el sistema rechaza la solicitud indicando que el examen ya está en curso.")
    @Severity(SeverityLevel.BLOCKER)
    void testID026_EdicionBloqueadaExamenIniciado() {
        String tituloExamen = "Parcial Iniciado Protegido ID-026 " + System.currentTimeMillis();

        // 1. Crear examen como Profesor
        page.locator("label:has-text('Clase') select").selectOption("matematicas");
        page.locator("label:has-text('Título') input").fill(tituloExamen);
        page.locator("label:has-text('Tema') select").selectOption("Tema 1");
        page.locator("label:has-text('Duración') input[type='number']").fill("60");
        page.locator("label:has-text('Turno') select").selectOption("tarde");
        page.locator("input[type='date']").fill("2026-10-22");
        page.locator("input[type='time']").fill("13:30");
        page.locator("textarea[placeholder*='consigna'], textarea").first().fill("Consigna para examen en curso.");
        page.locator("button[type='submit']:has-text('Crear')").click();
        page.waitForURL(url -> url.matches(".*/documentos/[a-zA-Z0-9-]+$"));
        String docUrl = page.url();

        // 2. Publicar el examen
        Locator btnPublicar = page.locator("button:has-text('Publicar')");
        if (btnPublicar.count() > 0 && btnPublicar.first().isVisible()) {
            page.onceDialog(dialog -> dialog.accept());
            btnPublicar.first().click();
            page.waitForTimeout(500);
        }

        // 3. Simular inicio por parte del alumno
        // Cerrar sesión profesor
        page.locator("button:has-text('Cerrar sesión')").first().click();
        page.waitForURL(url -> url.endsWith("/login"));

        // Iniciar sesión como alumno
        page.locator("label:has-text('Usuario o mail') input, input:not([type='password'])").first().fill("alumno1");
        page.locator("input[type='password']").fill("rendix123");
        page.locator("button[type='submit'], button:has-text('Ingresar')").click();
        page.waitForURL(url -> !url.endsWith("/login"));

        // Acceder a parciales del alumno e iniciar el examen si la interfaz lo permite
        page.navigate("https://rendix.vercel.app/alumno/parciales");
        page.waitForTimeout(500);
        Locator btnRendir = page.locator("a:has-text('Rendir'), button:has-text('Rendir'), button:has-text('Comenzar'), button:has-text('Iniciar')");
        if (btnRendir.count() > 0 && btnRendir.first().isVisible()) {
            btnRendir.first().click();
            page.waitForTimeout(1000);
        }

        // 4. Volver a iniciar sesión como Profesor
        Locator logoutAlumno = page.locator("button:has-text('Cerrar sesión')");
        if (logoutAlumno.count() > 0 && logoutAlumno.first().isVisible()) {
            logoutAlumno.first().click();
            page.waitForURL(url -> url.endsWith("/login"));
        } else {
            page.navigate("https://rendix.vercel.app/login");
        }
        page.locator("label:has-text('Usuario o mail') input, input:not([type='password'])").first().fill("profesor1");
        page.locator("input[type='password']").fill("rendix123");
        page.locator("button[type='submit'], button:has-text('Ingresar')").click();
        page.waitForURL(url -> !url.endsWith("/login"));

        // 5. Navegar al examen ya publicado/iniciado
        page.navigate(docUrl);
        page.waitForSelector(".container, .documento-view, main");

        // 6. Intentar acceder a la edición
        Locator btnEditar = page.locator("a:has-text('Editar'), button:has-text('Editar')");
        boolean btnEditarAusenteODeshabilitado = btnEditar.count() == 0 ||
                !btnEditar.first().isVisible() ||
                btnEditar.first().getAttribute("disabled") != null;

        // Intentar ingresar directamente por URL a /editar
        page.navigate(docUrl + "/editar");
        page.waitForTimeout(1000);

        Locator errorMsg = page.locator("p.error, .notice, .alert, .field-error, .toast");
        String mensajeRechazo = errorMsg.count() > 0 && errorMsg.first().isVisible() ? errorMsg.first().innerText().toLowerCase() : "";

        boolean navegacionRechazada = !page.url().endsWith("/editar") ||
                mensajeRechazo.contains("en curso") ||
                mensajeRechazo.contains("iniciado") ||
                mensajeRechazo.contains("no se puede editar") ||
                mensajeRechazo.contains("bloquead");

        assertTrue(btnEditarAusenteODeshabilitado || navegacionRechazada,
                "El sistema no rechazó la edición ni indicó que el examen ya está en curso tras haber sido iniciado. Mensaje: " + mensajeRechazo);
    }
}
