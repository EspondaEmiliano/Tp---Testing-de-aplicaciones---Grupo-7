package lotes;

import base.TestBase;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import helpers.AuthHelper;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private void irALogin() {
        AuthHelper.irALoginLimpio(page, baseUrl);
    }

    private void enviarFormularioLoginEsperandoRespuesta(String usuario, String password) {
        page.waitForResponse(
                r -> r.url().contains("/api/login") && r.request().method().equalsIgnoreCase("POST"),
                () -> AuthHelper.ingresarCredenciales(page, usuario, password)
        );
    }

    @Test
    @Tag("lote-login")
    @Story("ID-001 - Login Exitoso de Profesor y Alumno")
    @DisplayName("ID-001 - Login Exitoso de Profesor y Alumno")
    @Description("Validar redirección al panel correspondiente para profesor1 (/) y alumno1 (/alumno) en <= 2 segundos")
    @Severity(SeverityLevel.BLOCKER)
    void testID001_LoginExitoso() {
        // 1. Validación explícita para Profesor (debe redirigir exactamente a baseUrl + "/" y NO a /dev)
        irALogin();
        long inicioProf = System.currentTimeMillis();
        AuthHelper.ingresarCredenciales(page, AuthHelper.getProfUser(), AuthHelper.getProfPass());
        page.waitForURL(url -> !url.endsWith("/login"), new Page.WaitForURLOptions().setTimeout(2000));
        long duracionProf = System.currentTimeMillis() - inicioProf;

        Locator logoutBtnProf = page.locator("button:has-text('Cerrar sesión')").first();
        logoutBtnProf.waitFor(new Locator.WaitForOptions().setTimeout(2000));

        assertTrue(duracionProf <= 2000,
                "La redirección al panel de profesor demoró más de 2 segundos: " + duracionProf + " ms");
        assertEquals(baseUrl + "/", page.url(),
                "El profesor no fue redirigido exactamente al panel docente raíz (" + baseUrl + "/). URL actual: " + page.url());
        assertFalse(page.url().contains("/dev"),
                "El profesor no debe ser redirigido a /dev. URL actual: " + page.url());
        assertTrue(logoutBtnProf.isVisible(),
                "No se encontró el botón de 'Cerrar sesión' en el panel de profesor.");

        // Cerrar sesión antes de validar Alumno
        AuthHelper.logout(page);

        // 2. Validación explícita para Alumno (debe redirigir a /alumno)
        irALogin();
        long inicioAlumno = System.currentTimeMillis();
        AuthHelper.ingresarCredenciales(page, AuthHelper.getAlumnoUser(), AuthHelper.getAlumnoPass());
        page.waitForURL(url -> !url.endsWith("/login"), new Page.WaitForURLOptions().setTimeout(2000));
        long duracionAlumno = System.currentTimeMillis() - inicioAlumno;

        Locator logoutBtnAlumno = page.locator("button:has-text('Cerrar sesión')").first();
        logoutBtnAlumno.waitFor(new Locator.WaitForOptions().setTimeout(2000));

        assertTrue(duracionAlumno <= 2000,
                "La redirección al panel de alumno demoró más de 2 segundos: " + duracionAlumno + " ms");
        assertTrue(page.url().equals(baseUrl + "/alumno") || page.url().startsWith(baseUrl + "/alumno/"),
                "El alumno no fue redirigido a /alumno. URL actual: " + page.url());
        assertTrue(logoutBtnAlumno.isVisible(),
                "No se encontró el botón de 'Cerrar sesión' en el panel de alumno.");
    }

    @Test
    @Tag("lote-login")
    @Story("ID-002 - Cierre de sesion")
    @DisplayName("ID-002 - Cierre de sesion")
    @Description("Validar que la sesión es inválida tras presionar cerrar sesión y navegar hacia atrás")
    @Severity(SeverityLevel.CRITICAL)
    void testID002_CierreDeSesion() {
        AuthHelper.loginProfesor(page, baseUrl);

        // Navegar a una sección protegida para generar historial de navegación
        page.navigate(baseUrl + "/documentos");
        page.waitForURL(url -> url.contains("/documentos"));
        page.locator("h1:has-text('Parciales')").waitFor();

        // Presionar "Cerrar sesión"
        AuthHelper.logout(page);

        // Utilizar navegación hacia atrás y esperar redirección a /login o formulario de login
        page.goBack();
        page.waitForURL(url -> url.endsWith("/login"), new Page.WaitForURLOptions().setTimeout(3000));

        boolean exigeAutenticacion = page.url().endsWith("/login") &&
                page.locator("button[type='submit'], button:has-text('Ingresar')").first().isVisible();
        assertTrue(exigeAutenticacion,
                "La sesión sigue activa o no se exigió autenticación tras navegar hacia atrás. URL: " + page.url());
        assertFalse(page.locator("button:has-text('Cerrar sesión')").isVisible(),
                "El botón de 'Cerrar sesión' no debe ser visible tras cerrar sesión.");
    }

    @Test
    @Tag("lote-login")
    @Story("ID-003 - Usuario no registrado por la institucion")
    @DisplayName("ID-003 - Usuario no registrado por la institucion")
    @Description("Validar rechazo con mensaje genérico al intentar ingresar con usuario no registrado")
    @Severity(SeverityLevel.NORMAL)
    void testID003_UsuarioNoRegistrado() {
        irALogin();
        enviarFormularioLoginEsperandoRespuesta("usuario_inexistente_999", "passwordCualquiera");

        Locator errorMsg = page.locator("p.error");
        errorMsg.waitFor(new Locator.WaitForOptions().setTimeout(3000));

        assertTrue(errorMsg.isVisible(), "No se mostró el mensaje de error para usuario no registrado.");
        String texto = errorMsg.innerText().toLowerCase();
        assertTrue(texto.contains("usuario o contraseña incorrectos"),
                "El mensaje no coincide con el mensaje genérico esperado. Texto obtenido: " + texto);
        assertTrue(page.url().endsWith("/login"), "El usuario no debe salir de la pantalla de login.");
    }

    @Test
    @Tag("lote-login")
    @Story("ID-004 - Espacios y mayusculas en el usuario")
    @DisplayName("ID-004 - Espacios y mayusculas en el usuario")
    @Description("Validar recorte de espacios al inicio/final y sensibilidad a mayúsculas/minúsculas en el nombre de usuario")
    @Severity(SeverityLevel.NORMAL)
    void testID004_EspaciosYMayusculasEnUsuario() {
        // Parte 1: Recorte de espacios ("  profesor1  " con contraseña válida debe iniciar sesión)
        irALogin();
        AuthHelper.ingresarCredenciales(page, "  " + AuthHelper.getProfUser() + "  ", AuthHelper.getProfPass());
        page.waitForURL(url -> !url.endsWith("/login"), new Page.WaitForURLOptions().setTimeout(5000));
        page.locator("button:has-text('Cerrar sesión')").first().waitFor();
        assertTrue(page.locator("button:has-text('Cerrar sesión')").first().isVisible(),
                "El sistema no recortó los espacios al inicio y final del usuario válido.");

        AuthHelper.logout(page);

        // Parte 2: Sensibilidad a mayúsculas ("PROFESOR1" sin espacios debe ser rechazado en /login)
        irALogin();
        enviarFormularioLoginEsperandoRespuesta(AuthHelper.getProfUser().toUpperCase(), AuthHelper.getProfPass());

        assertFalse(page.locator("button:has-text('Cerrar sesión')").isVisible(),
                "[Bug ID-B001] El sistema permitió el acceso con mayúsculas ('PROFESOR1') en lugar de distinguir mayúsculas/minúsculas.");
        assertTrue(page.url().endsWith("/login"),
                "[Bug ID-B001] El sistema redirigió al panel cuando debió permanecer en /login ante usuario en mayúsculas.");
    }

    @Test
    @Tag("lote-login")
    @Story("ID-005 - Intento de login con credenciales invalidas")
    @DisplayName("ID-005 - Intento de login con credenciales invalidas")
    @Description("Validar mensaje de rechazo ante credenciales incorrectas")
    @Severity(SeverityLevel.CRITICAL)
    void testID005_CredencialesInvalidas() {
        irALogin();
        enviarFormularioLoginEsperandoRespuesta(AuthHelper.getProfUser(), "passwordErroneo123");

        Locator errorMsg = page.locator("p.error");
        errorMsg.waitFor(new Locator.WaitForOptions().setTimeout(3000));

        assertTrue(errorMsg.isVisible(), "No se mostró mensaje de rechazo ante credenciales inválidas.");
        assertTrue(errorMsg.innerText().toLowerCase().contains("usuario o contraseña incorrectos"),
                "El mensaje de error no coincide con el esperado.");
        assertTrue(page.url().endsWith("/login"), "La URL debe permanecer en /login.");
        assertFalse(page.locator("button:has-text('Cerrar sesión')").isVisible(),
                "No debe haber sesión activa.");
    }

    @Test
    @Tag("lote-login")
    @Story("ID-006 - Campos vacios")
    @DisplayName("ID-006 - Campos vacios")
    @Description("Validar que el sistema señale los campos requeridos específicos al omitir valores")
    @Severity(SeverityLevel.NORMAL)
    void testID006_CamposVacios() {
        // Variante 1: Ambos vacíos
        irALogin();
        enviarFormularioLoginEsperandoRespuesta("", "");
        page.locator("p.error, .field-error").first().waitFor();
        String errorAmbos = page.locator("p.error, .field-error").allInnerTexts().toString().toLowerCase();
        boolean esGenerico1 = errorAmbos.contains("usuario o contraseña incorrectos");
        assertFalse(esGenerico1,
                "[Bug ID-B002] El sistema muestra un mensaje genérico ('Usuario o contraseña incorrectos') en lugar de señalar los campos específicos faltantes.");
        assertTrue(page.locator(".field-error, input:invalid").count() > 0 || errorAmbos.contains("obligatorio"),
                "[Bug ID-B002] El sistema no señaló los campos específicos faltantes.");

        // Variante 2: Solo usuario vacío
        irALogin();
        enviarFormularioLoginEsperandoRespuesta("", AuthHelper.getProfPass());
        page.locator("p.error, .field-error").first().waitFor();
        String errorUsuario = page.locator("p.error, .field-error").allInnerTexts().toString().toLowerCase();
        boolean esGenerico2 = errorUsuario.contains("usuario o contraseña incorrectos");
        assertFalse(esGenerico2,
                "[Bug ID-B002] El sistema muestra un mensaje genérico en lugar de señalar específicamente el campo de usuario faltante.");

        // Variante 3: Solo contraseña vacía
        irALogin();
        enviarFormularioLoginEsperandoRespuesta(AuthHelper.getProfUser(), "");
        page.locator("p.error, .field-error").first().waitFor();
        String errorPassword = page.locator("p.error, .field-error").allInnerTexts().toString().toLowerCase();
        boolean esGenerico3 = errorPassword.contains("usuario o contraseña incorrectos");
        assertFalse(esGenerico3,
                "[Bug ID-B002] El sistema muestra un mensaje genérico en lugar de señalar específicamente el campo de contraseña faltante.");
    }

    @Test
    @Tag("lote-login")
    @Story("ID-007 - Intentos fallidos repetidos")
    @DisplayName("ID-007 - Intentos fallidos repetidos")
    @Description("Validar restricción o bloqueo de acceso tras 5 intentos fallidos consecutivos usando una cuenta temporal descartable")
    @Severity(SeverityLevel.CRITICAL)
    void testID007_IntentosFallidos() {
        // Registrar un alumno temporal descartable para NO bloquear cuentas compartidas (profesor1 / alumno1)
        AuthHelper.UsuarioTemporal temporal = AuthHelper.registrarAlumnoTemporal(page, baseUrl);
        AuthHelper.logout(page);

        irALogin();
        // Ejecutar 5 intentos fallidos consecutivos contra la cuenta temporal
        for (int i = 1; i <= 5; i++) {
            enviarFormularioLoginEsperandoRespuesta(temporal.usuario(), "claveErronea" + i);
            page.locator("p.error").first().waitFor();
        }

        // Validar que el sistema restringe el acceso al usuario tras los 5 intentos fallidos
        Locator submitButton = page.locator("button[type='submit'], button:has-text('Ingresar')");
        Locator errorMsg = page.locator("p.error");
        String textoError = errorMsg.isVisible() ? errorMsg.innerText().toLowerCase() : "";

        boolean accesoRestringido = submitButton.isDisabled() ||
                textoError.contains("bloqueado") ||
                textoError.contains("demasiados intentos") ||
                textoError.contains("intente más tarde") ||
                textoError.contains("restringid");

        assertTrue(accesoRestringido,
                "[Bug ID-007] El sistema no restringió ni bloqueó el acceso tras 5 intentos fallidos consecutivos sobre el usuario '" + temporal.usuario() + "'.");
    }

    @Test
    @Tag("lote-login")
    @Story("ID-008 - Contraseña sensible a mayusculas")
    @DisplayName("ID-008 - Contraseña sensible a mayusculas")
    @Description("Validar que la contraseña es estrictamente sensible a mayúsculas/minúsculas")
    @Severity(SeverityLevel.CRITICAL)
    void testID008_ContrasenaSensibleAMayusculas() {
        irALogin();
        enviarFormularioLoginEsperandoRespuesta(AuthHelper.getProfUser(), AuthHelper.getProfPass().toUpperCase());

        Locator errorMsg = page.locator("p.error");
        errorMsg.waitFor(new Locator.WaitForOptions().setTimeout(3000));

        assertTrue(errorMsg.isVisible(), "No se mostró mensaje de acceso denegado.");
        assertTrue(page.url().endsWith("/login"), "La URL debe permanecer en /login.");
        assertFalse(page.locator("button:has-text('Cerrar sesión')").isVisible(),
                "No debe haber sesión activa con contraseña con mayúsculas invertidas.");
    }
}
