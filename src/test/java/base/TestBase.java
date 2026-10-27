package base;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestBase implements TestWatcher {

    protected Playwright playwright;
    protected Browser browser;
    protected BrowserContext context;
    protected Page page;

    @RegisterExtension
    public final TestWatcher testWatcher = this;

    @BeforeAll
    void setUpAll() {
        playwright = Playwright.create();
        boolean isHeadless = Boolean.parseBoolean(System.getProperty("headless", "false"));
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(isHeadless)
        );
    }

    @BeforeEach
    void setUp() {
        context = browser.newContext();
        context.tracing().start(new Tracing.StartOptions()
                .setScreenshots(true)
                .setSnapshots(true)
                .setSources(true));
        page = context.newPage();
    }

    @Override
    public void testFailed(ExtensionContext extensionContext, Throwable cause) {
        String testName = obtenerNombrePrueba(extensionContext);

        // Si el estado de la prueba es FALLIDO, tomar captura de pantalla y adjuntarla a Allure
        if (page != null && !page.isClosed()) {
            try {
                byte[] screenshot = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
                Allure.addAttachment("Captura de pantalla de fallo - " + testName, "image/png", new ByteArrayInputStream(screenshot), "png");
            } catch (Exception e) {
                System.err.println("Error al capturar pantalla para Allure: " + e.getMessage());
            }
        }

        finalizarContextoYTrace(testName);
    }

    @Override
    public void testSuccessful(ExtensionContext extensionContext) {
        finalizarContextoYTrace(obtenerNombrePrueba(extensionContext));
    }

    @Override
    public void testAborted(ExtensionContext extensionContext, Throwable cause) {
        finalizarContextoYTrace(obtenerNombrePrueba(extensionContext));
    }

    @Override
    public void testDisabled(ExtensionContext extensionContext, Optional<String> reason) {
        // No requiere limpieza de contexto al no haberse iniciado
    }

    private String obtenerNombrePrueba(ExtensionContext extensionContext) {
        return extensionContext.getTestMethod()
                .map(Method::getName)
                .orElseGet(() -> extensionContext.getDisplayName().replace("()", "").replaceAll("[^a-zA-Z0-9._-]", "_"));
    }

    private void finalizarContextoYTrace(String testName) {
        if (context != null) {
            Path tracePath = Paths.get("resultados", testName + ".zip");
            try {
                Files.createDirectories(tracePath.getParent());
                context.tracing().stop(new Tracing.StopOptions().setPath(tracePath));
            } catch (IOException e) {
                System.err.println("Error al guardar el trace: " + e.getMessage());
            } finally {
                try {
                    context.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    @AfterAll
    void tearDownAll() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }
}
