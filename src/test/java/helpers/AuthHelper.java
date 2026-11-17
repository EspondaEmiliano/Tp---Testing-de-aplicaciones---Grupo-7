package helpers;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Helper reutilizable para autenticación, manejo de sesiones y registro de usuarios temporales.
 */
public final class AuthHelper {

    public static final String ALUMNO_USER = System.getProperty("alumnoUser", "alumno1");
    public static final String ALUMNO2_USER = System.getProperty("alumno2User", "alumno2");
    public static final String PROF_USER = System.getProperty("profUser", "profesor1");
    public static final String DEV_USER = System.getProperty("devUser", "dev1");

    public record UsuarioTemporal(String nombre, String usuario, String email, String password) {}

    private AuthHelper() {
    }

    public static String getProfUser() {
        return System.getProperty("profUser", "profesor1");
    }

    public static String getProfPass() {
        return System.getProperty("profPass", "rendix123");
    }

    public static String getAlumnoUser() {
        return System.getProperty("alumnoUser", "alumno1");
    }

    public static String getAlumnoPass() {
        return System.getProperty("alumnoPass", "rendix123");
    }

    public static String getAlumno2User() {
        return System.getProperty("alumno2User", "alumno2");
    }

    public static String getAlumno2Pass() {
        return System.getProperty("alumno2Pass", "rendix123");
    }

    public static String getDevUser() {
        return System.getProperty("devUser", "dev1");
    }

    public static String getDevPass() {
        return System.getProperty("devPass", "rendix123");
    }

    public static void irALoginLimpio(Page page, String baseUrl) {
        String loginUrl = baseUrl + "/login";
        page.navigate(loginUrl);
        Locator logoutBtn = page.locator("button:has-text('Cerrar sesión')");
        if (logoutBtn.count() > 0 && logoutBtn.first().isVisible()) {
            logoutBtn.first().click();
            page.waitForURL(url -> url.endsWith("/login"), new Page.WaitForURLOptions().setTimeout(5000));
        }
        page.locator("label:has-text('Usuario o mail') input, input:not([type='password'])").first().waitFor();
    }

    public static void ingresarCredenciales(Page page, String usuario, String password) {
        Locator usuarioInput = page.locator("label:has-text('Usuario o mail') input, input:not([type='password'])").first();
        Locator passwordInput = page.locator("input[type='password']");
        Locator submitButton = page.locator("button[type='submit'], button:has-text('Ingresar')");

        usuarioInput.fill(usuario);
        passwordInput.fill(password);
        submitButton.click();
    }

    public static void login(Page page, String baseUrl, String usuario, String password) {
        irALoginLimpio(page, baseUrl);
        ingresarCredenciales(page, usuario, password);
        page.waitForURL(url -> !url.endsWith("/login"), new Page.WaitForURLOptions().setTimeout(8000));
        page.locator("button:has-text('Cerrar sesión')").first().waitFor();
    }

    public static void loginProfesor(Page page, String baseUrl) {
        login(page, baseUrl, getProfUser(), getProfPass());
    }

    public static void loginAlumno(Page page, String baseUrl) {
        login(page, baseUrl, getAlumnoUser(), getAlumnoPass());
    }

    public static void loginDev(Page page, String baseUrl) {
        login(page, baseUrl, getDevUser(), getDevPass());
    }

    public static void logout(Page page) {
        Locator logoutBtn = page.locator("button:has-text('Cerrar sesión')");
        if (logoutBtn.count() > 0 && logoutBtn.first().isVisible()) {
            logoutBtn.first().click();
            page.waitForURL(url -> url.endsWith("/login"), new Page.WaitForURLOptions().setTimeout(5000));
        }
    }

    /**
     * Registra un usuario alumno descartable en /registro para pruebas que no deben afectar cuentas compartidas.
     */
    public static UsuarioTemporal registrarAlumnoTemporal(Page page, String baseUrl) {
        String registroUrl = baseUrl + "/registro";
        page.navigate(registroUrl);
        Locator logoutBtn = page.locator("button:has-text('Cerrar sesión')");
        if (logoutBtn.count() > 0 && logoutBtn.first().isVisible()) {
            logoutBtn.first().click();
            page.waitForURL(url -> url.endsWith("/login"), new Page.WaitForURLOptions().setTimeout(5000));
            page.navigate(registroUrl);
        }
        page.waitForSelector("form");

        long suffix = System.nanoTime() % 100000000L;
        String nombre = "Alumno Temp " + (suffix % 10000);
        String usuario = "tmpusr" + suffix;
        String email = usuario + "@test.com";
        String password = "TempPass123";

        page.locator("input[autocomplete='name']").fill(nombre);
        page.locator("input[autocomplete='username']").fill(usuario);
        page.locator("input[type='email']").fill(email);
        page.locator("input[type='password']").fill(password);
        page.locator("button[type='submit']").click();

        page.waitForURL(url -> url.contains("/alumno"), new Page.WaitForURLOptions().setTimeout(8000));
        return new UsuarioTemporal(nombre, usuario, email, password);
    }
}
