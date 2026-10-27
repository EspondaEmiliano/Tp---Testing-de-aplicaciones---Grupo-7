import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

public class Main {
    public static void main(String[] args) {
        // Credenciales de acceso (puedes modificarlas con tu usuario y contraseña si tienes una cuenta propia)
        String usuario = "profesor1";
        String password = "rendix123";

        System.out.println("Iniciando Playwright...");

        try (Playwright playwright = Playwright.create()) {
            // Lanzar Chromium en modo visible (headless = false)
            // setSlowMo(300) agrega una pausa ligera entre acciones para que puedas apreciar cada paso en pantalla
            Browser browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                    .setHeadless(false)
                    .setSlowMo(300)
            );

            Page page = browser.newPage();

            // 1. Navegar a la página de login
            System.out.println("Navegando a https://rendix.vercel.app/login...");
            page.navigate("https://rendix.vercel.app/login");

            // 2. Ingresar usuario
            System.out.println("Completando usuario: " + usuario);
            Locator usuarioInput = page.locator("label:has-text('Usuario o mail') input, input:not([type='password'])").first();
            usuarioInput.fill(usuario);

            // 3. Ingresar contraseña
            System.out.println("Completando contraseña...");
            Locator passwordInput = page.locator("input[type='password']");
            passwordInput.fill(password);

            // 4. Presionar botón de ingreso
            System.out.println("Haciendo clic en 'Ingresar'...");
            Locator submitButton = page.locator("button[type='submit'], button:has-text('Ingresar')");
            submitButton.click();

            // 5. Validar que la sesión se inició correctamente
            // Verificamos si aparece algún mensaje de error en pantalla
            Locator errorMsg = page.locator("p.error");
            if (errorMsg.count() > 0 && errorMsg.first().isVisible()) {
                System.err.println("Error al iniciar sesión: " + errorMsg.first().innerText());
            } else {
                // Esperar a que la URL cambie (salga de /login)
                page.waitForURL(url -> !url.endsWith("/login"));
                System.out.println("¡Sesión iniciada con éxito! URL actual: " + page.url());
            }

            // 6. Espera de 6000 ms para visualizar el resultado en el navegador
            System.out.println("Esperando 6 segundos para visualizar la plataforma con la sesión iniciada...");
            page.waitForTimeout(60000);

            // 7. Cierre del navegador
            System.out.println("Cerrando navegador...");
            browser.close();
        }

        System.out.println("Automatización finalizada con éxito.");
    }
}
