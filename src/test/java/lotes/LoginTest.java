package lotes;

import base.TestBase;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ============================================================================
 * Lote: Login (Casos ID-001 al ID-008)
 *
 * Comando para ejecutar en terminal:
 *   mvn test -Dgroups="lote-login"
 *
 * Con Maven Wrapper:
 *   .\mvnw.cmd test "-Dgroups=lote-login"
 * ============================================================================
 */
@Epic("Autenticación")
@Feature("Módulo Login")
@Tag("lote-login")
public class LoginTest extends TestBase {

    private static final String LOGIN_URL = "https://rendix.vercel.app/login";

    private void irALogin() {
        page.navigate(LOGIN_URL);
    }

    private void ingresarCredenciales(String usuario, String password) {
        Locator usuarioInput = page.locator("label:has-text('Usuario o mail') input, input:not([type='password'])").first();
        Locator passwordInput = page.locator("input[type='password']");
        Locator submitButton = page.locator("button[type='submit'], button:has-text('Ingresar')");

        usuarioInput.fill(usuario);
        passwordInput.fill(password);
        submitButton.click();
    }

    @Test
    @Tag("lote-login")
    @Story("ID-001 - Login Exitoso")
    @DisplayName("ID-001 - Login Exitoso")
    @Description("Validar redirección al panel tras ingresar credenciales válidas en <= 2 segundos")
    @Severity(SeverityLevel.BLOCKER)
    void testID001_LoginExitoso() {
        irALogin();
        long inicio = System.currentTimeMillis();
        ingresarCredenciales("profesor1", "rendix123");

        // Validar redirección al panel (profesor/alumno) en <= 2 segundos
        page.waitForURL(url -> !url.endsWith("/login"), new Page.WaitForURLOptions().setTimeout(2000));
        long duracion = System.currentTimeMillis() - inicio;

        assertTrue(duracion <= 2000, "La redirección al panel demoró más de 2 segundos: " + duracion + " ms");
        assertTrue(page.url().equals("https://rendix.vercel.app/") || page.url().contains("/dev"),
                "No redirigió al panel esperado. URL actual: " + page.url());
        assertTrue(page.locator("button:has-text('Cerrar sesión')").isVisible(),
                "No se encontró el botón de 'Cerrar sesión' en el panel.");
    }

    @Test
    @Tag("lote-login")
    @Story("ID-002 - Cierre de Sesión")
    @DisplayName("ID-002 - Cierre de Sesión")
    @Description("Validar que la sesión es inválida tras presionar cerrar sesión y navegar hacia atrás")
    @Severity(SeverityLevel.CRITICAL)
    void testID002_CierreDeSesion() {
        irALogin();
        ingresarCredenciales("profesor1", "rendix123");
        page.waitForURL(url -> !url.endsWith("/login"));

        // Navegar a una sección protegida para generar historial de navegación
        page.navigate("https://rendix.vercel.app/documentos");
        page.waitForURL(url -> url.contains("/documentos"));

        // Presionar "Cerrar sesión"
        Locator logoutButton = page.locator("button:has-text('Cerrar sesión')");
        logoutButton.click();
        page.waitForURL(url -> url.endsWith("/login"));

        // Utilizar navegación hacia atrás
        page.goBack();
        page.waitForTimeout(1000);

        // Validar que la sesión es inválida y exige autenticación (redirige a /login o muestra el formulario de login)
        boolean exigeAutenticacion = page.url().endsWith("/login") ||
                page.locator("button[type='submit'], button:has-text('Ingresar')").isVisible();
        assertTrue(exigeAutenticacion, "La sesión sigue activa o no se exigió autenticación tras navegar hacia atrás. URL: " + page.url());
        assertFalse(page.locator("button:has-text('Cerrar sesión')").isVisible(),
                "El botón de 'Cerrar sesión' no debe ser visible tras cerrar sesión.");
    }

    @Test
    @Tag("lote-login")
    @Story("ID-003 - Usuario No Registrado")
    @DisplayName("ID-003 - Usuario No Registrado")
    @Description("Validar rechazo con mensaje genérico al intentar ingresar con usuario no registrado")
    @Severity(SeverityLevel.NORMAL)
    void testID003_UsuarioNoRegistrado() {
        irALogin();
        ingresarCredenciales("usuario_inexistente_999", "passwordCualquiera");

        Locator errorMsg = page.locator("p.error");
        assertTrue(errorMsg.isVisible(), "No se mostró el mensaje de error para usuario no registrado.");
        String texto = errorMsg.innerText().toLowerCase();
        assertTrue(texto.contains("usuario o contraseña incorrectos"),
                "El mensaje no coincide con el mensaje genérico esperado. Texto obtenido: " + texto);
        assertTrue(page.url().endsWith("/login"), "El usuario no debe salir de la pantalla de login.");
    }

    @Test
    @Tag("lote-login")
    @Story("ID-004 - Espacios y Mayúsculas en Usuario")
    @DisplayName("ID-004 - Espacios y Mayúsculas en Usuario")
    @Description("Validar distinción de mayúsculas/minúsculas y recorte de espacios")
    @Severity(SeverityLevel.NORMAL)
    void testID004_EspaciosYMayusculasEnUsuario() {
        irALogin();
        // Ingresar usuario con espacios al inicio/fin y variaciones de mayúsculas
        ingresarCredenciales("  PROFESOR1  ", "rendix123");

        // Asertar comportamiento esperado: el sistema distingue mayúsculas/minúsculas y recorta espacios
        // (Nota: Existencia de Bug ID-B001 que incumple esto en producción convirtiendo a minúsculas, pero se aserta el esperado)
        assertFalse(page.locator("button:has-text('Cerrar sesión')").isVisible(),
                "[Bug ID-B001] El sistema permitió el acceso con mayúsculas en lugar de distinguir mayúsculas/minúsculas.");
        assertTrue(page.url().endsWith("/login"),
                "[Bug ID-B001] El sistema redirigió al panel cuando debió permanecer en /login.");
    }

    @Test
    @Tag("lote-login")
    @Story("ID-005 - Credenciales Inválidas")
    @DisplayName("ID-005 - Credenciales Inválidas")
    @Description("Validar mensaje de rechazo ante credenciales incorrectas")
    @Severity(SeverityLevel.CRITICAL)
    void testID005_CredencialesInvalidas() {
        irALogin();
        ingresarCredenciales("profesor1", "passwordErroneo123");

        Locator errorMsg = page.locator("p.error");
        assertTrue(errorMsg.isVisible(), "No se mostró mensaje de rechazo ante credenciales inválidas.");
        assertTrue(errorMsg.innerText().toLowerCase().contains("usuario o contraseña incorrectos"),
                "El mensaje de error no coincide con el esperado.");
        assertTrue(page.url().endsWith("/login"), "La URL debe permanecer en /login.");
        assertFalse(page.locator("button:has-text('Cerrar sesión')").isVisible(),
                "No debe haber sesión activa.");
    }

    @Test
    @Tag("lote-login")
    @Story("ID-006 - Campos Vacíos")
    @DisplayName("ID-006 - Campos Vacíos")
    @Description("Validar que el sistema señale los campos requeridos específicos al omitir valores")
    @Severity(SeverityLevel.NORMAL)
    void testID006_CamposVacios() {
        // Variante 1: Ambos vacíos
        irALogin();
        ingresarCredenciales("", "");
        String errorAmbos = page.locator("p.error, .field-error").allInnerTexts().toString().toLowerCase();
        boolean esGenerico1 = errorAmbos.contains("usuario o contraseña incorrectos");
        assertFalse(esGenerico1,
                "[Bug ID-B002] El sistema muestra un mensaje genérico ('Usuario o contraseña incorrectos') en lugar de señalar los campos específicos faltantes.");
        assertTrue(page.locator(".field-error, input:invalid").count() > 0 || errorAmbos.contains("obligatorio"),
                "[Bug ID-B002] El sistema no señaló los campos específicos faltantes.");

        // Variante 2: Solo usuario vacío
        irALogin();
        ingresarCredenciales("", "rendix123");
        String errorUsuario = page.locator("p.error, .field-error").allInnerTexts().toString().toLowerCase();
        boolean esGenerico2 = errorUsuario.contains("usuario o contraseña incorrectos");
        assertFalse(esGenerico2,
                "[Bug ID-B002] El sistema muestra un mensaje genérico en lugar de señalar específicamente el campo de usuario faltante.");

        // Variante 3: Solo contraseña vacía
        irALogin();
        ingresarCredenciales("profesor1", "");
        String errorPassword = page.locator("p.error, .field-error").allInnerTexts().toString().toLowerCase();
        boolean esGenerico3 = errorPassword.contains("usuario o contraseña incorrectos");
        assertFalse(esGenerico3,
                "[Bug ID-B002] El sistema muestra un mensaje genérico en lugar de señalar específicamente el campo de contraseña faltante.");
    }

    @Test
    @Tag("lote-login")
    @Story("ID-007 - Intentos Fallidos")
    @DisplayName("ID-007 - Intentos Fallidos")
    @Description("Validar restricción o bloqueo de acceso tras 5 intentos fallidos consecutivos")
    @Severity(SeverityLevel.CRITICAL)
    void testID007_IntentosFallidos() {
        irALogin();
        // Ingresar contraseña errónea repetidas veces (5 intentos)
        for (int i = 1; i <= 5; i++) {
            ingresarCredenciales("profesor1", "claveErronea" + i);
            page.waitForTimeout(300);
        }

        // Validar que el sistema restringe el acceso
        Locator submitButton = page.locator("button[type='submit'], button:has-text('Ingresar')");
        Locator errorMsg = page.locator("p.error");
        String textoError = errorMsg.isVisible() ? errorMsg.innerText().toLowerCase() : "";

        boolean accesoRestringido = submitButton.isDisabled() ||
                textoError.contains("bloqueado") ||
                textoError.contains("demasiados intentos") ||
                textoError.contains("intente más tarde") ||
                textoError.contains("restringid");

        assertTrue(accesoRestringido,
                "El sistema no restringió el acceso tras múltiples intentos fallidos consecutivos.");
    }

    @Test
    @Tag("lote-login")
    @Story("ID-008 - Contraseña Sensible a Mayúsculas")
    @DisplayName("ID-008 - Contraseña Sensible a Mayúsculas")
    @Description("Validar que la contraseña es estrictamente sensible a mayúsculas/minúsculas")
    @Severity(SeverityLevel.CRITICAL)
    void testID008_ContrasenaSensibleAMayusculas() {
        irALogin();
        // Contraseña válida es 'rendix123'. Invertimos mayúsculas/minúsculas: 'RENDIX123'
        ingresarCredenciales("profesor1", "RENDIX123");

        Locator errorMsg = page.locator("p.error");
        assertTrue(errorMsg.isVisible(), "No se mostró mensaje de acceso denegado.");
        assertTrue(page.url().endsWith("/login"), "La URL debe permanecer en /login.");
        assertFalse(page.locator("button:has-text('Cerrar sesión')").isVisible(),
                "No debe haber sesión activa con contraseña con mayúsculas invertidas.");
    }
}
