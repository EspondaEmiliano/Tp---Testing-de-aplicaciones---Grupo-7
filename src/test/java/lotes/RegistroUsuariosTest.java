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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ============================================================================
 * Lote: Registro de Usuarios (Casos ID-032 al ID-035)
 *
 * Comando para ejecutar en terminal:
 *   mvn test -Dgroups="lote-registro-usuarios"
 *
 * Con Maven Wrapper:
 *   .\mvnw.cmd test "-Dgroups=lote-registro-usuarios"
 * ============================================================================
 */
@Epic("Autenticación y Cuentas")
@Feature("Registro de Usuarios")
@Tag("lote-registro-usuarios")
public class RegistroUsuariosTest extends TestBase {

    private String getRegistroUrl() {
        return baseUrl + "/registro";
    }

    @BeforeEach
    void setUpRegistro() {
        page.navigate(getRegistroUrl());
        AuthHelper.logout(page);
        page.evaluate("() => { try { sessionStorage.clear(); localStorage.clear(); } catch(e){} }");
        page.navigate(getRegistroUrl());
        page.locator("form").first().waitFor();
    }

    private void limpiarFormulario() {
        page.locator("input[autocomplete='name']").fill("");
        page.locator("input[autocomplete='username']").fill("");
        page.locator("input[type='email']").fill("");
        page.locator("input[type='password']").fill("");
    }

    private void completarRegistro(String nombre, String usuario, String email, String password) {
        if (nombre != null) {
            page.locator("input[autocomplete='name']").fill(nombre);
        }
        if (usuario != null) {
            page.locator("input[autocomplete='username']").fill(usuario);
        }
        if (email != null) {
            page.locator("input[type='email']").fill(email);
        }
        if (password != null) {
            page.locator("input[type='password']").fill(password);
        }
    }

    private void clickCrearCuenta() {
        page.locator("button[type='submit']").click();
    }

    @Test
    @Tag("lote-registro-usuarios")
    @Story("ID-032 - Registro exitoso de nueva cuenta de alumno (Happy Path)")
    @DisplayName("ID-032 - Registro exitoso de nueva cuenta de alumno (Happy Path)")
    @Description("Verificar que un nuevo usuario pueda registrarse exitosamente completando los campos obligatorios, obteniendo rol de alumno en <3s y pudiendo iniciar sesión de inmediato.")
    @Severity(SeverityLevel.BLOCKER)
    void testID032_RegistroExitosoDeNuevaCuentaAlumnoHappyPath() {
        long timestamp = System.currentTimeMillis();
        String nombre = "Alumno Automatizado";
        String usuarioDinamico = "alumno" + (timestamp % 10000000);
        String emailDinamico = "nuevo_alumno_" + timestamp + "@test.com";
        String password = "PasswordSecure123";

        // 1. Completar formulario de registro con datos válidos y correo dinámico
        completarRegistro(nombre, usuarioDinamico, emailDinamico, password);

        // 2. Enviar el formulario y medir tiempo de creación (< 3 segundos)
        long inicio = System.currentTimeMillis();
        clickCrearCuenta();

        // 3. Validar redirección exitosa
        page.waitForURL(url -> !url.contains("/registro"), new Page.WaitForURLOptions().setTimeout(3000));
        long duracion = System.currentTimeMillis() - inicio;

        page.locator("button:has-text('Cerrar sesión')").first().waitFor();

        assertTrue(duracion <= 3000, "La creación de la cuenta demoró más de 3 segundos: " + duracion + " ms");
        assertTrue(page.url().contains("/alumno"), "No redirigió al panel de alumno tras el registro. URL: " + page.url());

        // 4. Validar asignación de rol de Alumno (0 permisos de profesor visibles)
        assertFalse(page.locator("a[href*='/documentos/nuevo']").isVisible(),
                "El usuario registrado tiene enlaces de profesor ('Nuevo parcial'); se esperaba rol de Alumno.");

        // 5. Encadenar inicio de sesión exitoso con las credenciales recién creadas
        AuthHelper.logout(page);
        AuthHelper.login(page, baseUrl, usuarioDinamico, password);

        // Validar que accede de inmediato al panel de alumno con sesión válida
        assertTrue(page.url().contains("/alumno"), "El login encadenado del nuevo alumno no redirigió a /alumno. URL: " + page.url());
        assertTrue(page.locator("button:has-text('Cerrar sesión')").isVisible(), "No se visualiza la sesión activa del alumno.");
    }

    @Test
    @Tag("lote-registro-usuarios")
    @Story("ID-033 - Validación de campos obligatorios en el registro")
    @DisplayName("ID-033 - Validación de campos obligatorios en el registro")
    @Description("Verificar que el sistema rechace el registro y marque los campos si falta alguno de los datos obligatorios (nombre, usuario, email o contraseña).")
    @Severity(SeverityLevel.CRITICAL)
    void testID033_ValidacionCamposObligatoriosEnElRegistro() {
        // Variante 1: Enviar formulario con todos los campos en blanco
        limpiarFormulario();
        clickCrearCuenta();
        page.locator(".field-error").first().waitFor();

        assertTrue(page.url().contains("/registro"), "El sistema envió el formulario con todos los campos vacíos.");
        assertTrue(page.locator(".field-error").count() >= 3, "El sistema no marcó visualmente los campos obligatorios al enviar en blanco.");

        // Variante 2: Nombre vacío
        limpiarFormulario();
        completarRegistro("", "userPruebaVar2", "prueba2@test.com", "PasswordSecure123");
        clickCrearCuenta();
        page.locator("label:has(input[autocomplete='name']) .field-error").waitFor();
        assertTrue(page.url().contains("/registro"), "El sistema permitió el registro con 'Nombre' vacío.");
        String errorNombre = page.locator("label:has(input[autocomplete='name']) .field-error").innerText().toLowerCase();
        assertTrue(errorNombre.contains("nombre") || errorNombre.contains("obligatorio"),
                "No se marcó el error de campo obligatorio en Nombre. Texto: " + errorNombre);

        // Variante 3: Usuario vacío
        limpiarFormulario();
        completarRegistro("Juan Pérez", "", "prueba3@test.com", "PasswordSecure123");
        clickCrearCuenta();
        page.locator("label:has(input[autocomplete='username']) .field-error").waitFor();
        assertTrue(page.url().contains("/registro"), "El sistema permitió el registro con 'Usuario' vacío.");
        String errorUsuario = page.locator("label:has(input[autocomplete='username']) .field-error").innerText().toLowerCase();
        assertTrue(errorUsuario.contains("usuario") || errorUsuario.contains("obligatorio"),
                "No se marcó el error de campo obligatorio en Usuario. Texto: " + errorUsuario);

        // Variante 4: Mail vacío
        limpiarFormulario();
        completarRegistro("Juan Pérez", "userPruebaVar4", "", "PasswordSecure123");
        clickCrearCuenta();
        page.locator("label:has(input[type='email']) .field-error").waitFor();
        assertTrue(page.url().contains("/registro"), "El sistema permitió el registro con 'Mail' vacío.");
        String errorMail = page.locator("label:has(input[type='email']) .field-error").innerText().toLowerCase();
        assertTrue(errorMail.contains("mail") || errorMail.contains("obligatorio"),
                "No se marcó el error de campo obligatorio en Mail. Texto: " + errorMail);

        // Variante 5: Contraseña vacía
        limpiarFormulario();
        completarRegistro("Juan Pérez", "userPruebaVar5", "prueba5@test.com", "");
        clickCrearCuenta();
        page.locator("label:has(input[type='password']) .field-error").waitFor();
        assertTrue(page.url().contains("/registro"), "El sistema permitió el registro con 'Contraseña' vacía.");
        String errorPassword = page.locator("label:has(input[type='password']) .field-error").innerText().toLowerCase();
        assertTrue(errorPassword.contains("contraseña") || errorPassword.contains("caracteres") || errorPassword.contains("obligatorio"),
                "No se marcó el error de campo obligatorio en Contraseña. Texto: " + errorPassword);
    }

    @Test
    @Tag("lote-registro-usuarios")
    @Story("ID-034 - Validación de formato de email y duplicidad")
    @DisplayName("ID-034 - Validación de formato de email y duplicidad")
    @Description("Asegurar que el sistema rechace emails con formato incorrecto y evite la creación de cuentas duplicadas con el mismo correo.")
    @Severity(SeverityLevel.NORMAL)
    void testID034_ValidacionFormatoDeEmailYDuplicidad() {
        // --------------------------------------------------------------------
        // Parte 1 - Formato de email mal formado (sin @)
        // --------------------------------------------------------------------
        limpiarFormulario();
        completarRegistro("Juan Pérez", "userMailErr", "correo_sin_arroba.com", "PasswordSecure123");
        clickCrearCuenta();
        page.locator("label:has(input[type='email']) .field-error").waitFor();

        assertTrue(page.url().contains("/registro"), "El sistema no bloqueó el registro con email mal formado.");
        String errorFormato = page.locator("label:has(input[type='email']) .field-error").innerText().toLowerCase();
        boolean indicaFormatoInvalido = errorFormato.contains("no es válido") ||
                errorFormato.contains("formato") ||
                errorFormato.contains("mail");
        assertTrue(indicaFormatoInvalido, "El sistema no señaló el formato inválido del email. Texto: " + errorFormato);

        // --------------------------------------------------------------------
        // Parte 2 - Email duplicado (usuario ya registrado en BD)
        // --------------------------------------------------------------------
        limpiarFormulario();
        String emailExistente = "existente@rendix.com";
        String nuevoUsuario = "userdup" + (System.currentTimeMillis() % 1000000);
        completarRegistro("Alumno Prueba", nuevoUsuario, emailExistente, "PasswordSecure123");

        page.waitForResponse(
                r -> r.url().contains("/api/registro") && r.request().method().equalsIgnoreCase("POST"),
                this::clickCrearCuenta
        );

        // Si la base se hubiera reiniciado y se permitió el primer registro, volver a intentar con ese mismo correo
        if (!page.url().contains("/registro")) {
            AuthHelper.logout(page);
            page.navigate(getRegistroUrl());
            page.locator("form").first().waitFor();
            completarRegistro("Alumno Prueba 2", "userdup2_" + (System.currentTimeMillis() % 1000000), emailExistente, "PasswordSecure123");
            page.waitForResponse(
                    r -> r.url().contains("/api/registro") && r.request().method().equalsIgnoreCase("POST"),
                    this::clickCrearCuenta
            );
        }

        page.locator("p.error, .field-error").first().waitFor();
        String errorDuplicidad = String.join(" ", page.locator("p.error, .field-error").allInnerTexts()).toLowerCase();
        boolean bloqueoDuplicado = page.url().contains("/registro");
        boolean indicaCuentaDuplicada = errorDuplicidad.contains("ya hay una cuenta") ||
                errorDuplicidad.contains("duplicad") ||
                errorDuplicidad.contains("en uso") ||
                errorDuplicidad.contains("ya existe") ||
                errorDuplicidad.contains("registrad");

        assertTrue(bloqueoDuplicado && indicaCuentaDuplicada,
                "El sistema no bloqueó la cuenta duplicada ni alertó sobre su existencia previa. Texto obtenido: " + errorDuplicidad);
    }

    @Test
    @Tag("lote-registro-usuarios")
    @Story("ID-035 - Validación de longitud mínima de contraseña (8 caracteres)")
    @DisplayName("ID-035 - Validación de longitud mínima de contraseña (8 caracteres)")
    @Description("Verificar que el sistema rechace contraseñas menores a 8 caracteres (Boundary Testing: 7 caracteres vs 8 caracteres).")
    @Severity(SeverityLevel.NORMAL)
    void testID035_ValidacionLongitudMinimaDeContrasena() {
        long ts = System.currentTimeMillis();
        String usuarioBorde = "borde" + (ts % 1000000);
        String emailBorde = "borde_" + ts + "@test.com";

        // --------------------------------------------------------------------
        // Parte 1 - Contraseña de borde inválido: 7 caracteres ("Abc1234")
        // --------------------------------------------------------------------
        limpiarFormulario();
        completarRegistro("Juan Pérez", usuarioBorde, emailBorde, "Abc1234");
        clickCrearCuenta();

        Locator errorPass = page.locator("label:has(input[type='password']) .field-error, .field-error, p.error").first();
        errorPass.waitFor();

        String errorBordeInvalido = String.join(" ", page.locator("label:has(input[type='password']) .field-error, .field-error, p.error").allInnerTexts()).toLowerCase();
        boolean bloqueoInvalido = page.url().contains("/registro");
        boolean indicaLongitudMinima = errorBordeInvalido.contains("8 caracteres") ||
                errorBordeInvalido.contains("al menos 8") ||
                errorBordeInvalido.contains("mínimo");

        assertTrue(bloqueoInvalido && indicaLongitudMinima,
                "[Bug ID-035] El sistema no rechazó la contraseña de 7 caracteres ('Abc1234') o no mostró la alerta de longitud mínima de 8 caracteres. Error: " + errorBordeInvalido);

        // --------------------------------------------------------------------
        // Parte 2 - Contraseña de borde válido: 8 caracteres ("Abc12345")
        // --------------------------------------------------------------------
        Locator passwordInput = page.locator("input[type='password']");
        passwordInput.fill("Abc12345");

        page.waitForResponse(
                r -> r.url().contains("/api/registro") && r.request().method().equalsIgnoreCase("POST"),
                this::clickCrearCuenta
        );
        page.waitForURL(url -> url.contains("/alumno"), new Page.WaitForURLOptions().setTimeout(5000));
        assertTrue(page.url().contains("/alumno"),
                "El sistema no permitió completar el registro al ingresar exactamente 8 caracteres válidos en la contraseña.");
    }
}
