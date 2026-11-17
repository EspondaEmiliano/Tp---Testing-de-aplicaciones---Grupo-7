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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestBase implements TestWatcher {

    protected Playwright playwright;
    protected Browser browser;
    protected BrowserContext context;
    protected Page page;

    protected final String baseUrl = System.getProperty("baseUrl", "https://rendix.vercel.app").replaceAll("/+$", "");
    protected final boolean isHeadless = Boolean.parseBoolean(System.getProperty("headless", "false"));
    protected final String browserChannel = System.getProperty("browserChannel", "").trim();
    protected final long perfToleranceMs = Long.parseLong(System.getProperty("perfToleranceMs", "1000"));

    private final Map<String, BrowserContext> contextosSecundarios = new LinkedHashMap<>();

    @RegisterExtension
    public final TestWatcher testWatcher = this;

    @BeforeAll
    void setUpAll() {
        playwright = Playwright.create();
        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(isHeadless);
        if (!browserChannel.isEmpty()) {
            launchOptions.setChannel(browserChannel);
        }
        browser = playwright.chromium().launch(launchOptions);
    }

    @BeforeEach
    void setUp() {
        contextosSecundarios.clear();
        context = browser.newContext();
        iniciarTracing(context);
        page = context.newPage();
    }

    /**
     * Crea un BrowserContext secundario aislado (ej. sesión simultánea de alumno o profesor),
     * lo registra para captura automática de screenshots y trazas en Allure/resultados y lo cierra al finalizar el test.
     */
    protected BrowserContext crearContextoSecundario(String nombre) {
        String claveBase = (nombre == null || nombre.isBlank()) ? "secundario" : nombre.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        String clave = claveBase;
        int contador = 2;
        while (contextosSecundarios.containsKey(clave)) {
            clave = claveBase + "_" + contador++;
        }

        BrowserContext nuevoContexto = browser.newContext();
        iniciarTracing(nuevoContexto);
        contextosSecundarios.put(clave, nuevoContexto);
        return nuevoContexto;
    }

    private void iniciarTracing(BrowserContext ctx) {
        ctx.tracing().start(new Tracing.StartOptions()
                .setScreenshots(true)
                .setSnapshots(true)
                .setSources(true));
    }

    @Override
    public void testFailed(ExtensionContext extensionContext, Throwable cause) {
        String testName = obtenerNombrePrueba(extensionContext);

        // Si el estado de la prueba es FALLIDO, tomar captura de pantalla del contexto principal y adjuntarla a Allure
        capturarScreenshotParaAllure(page, "Captura de pantalla de fallo - " + testName);

        // Capturar screenshots de todas las páginas abiertas en contextos secundarios
        for (Map.Entry<String, BrowserContext> entry : contextosSecundarios.entrySet()) {
            String ctxNombre = entry.getKey();
            BrowserContext secCtx = entry.getValue();
            try {
                List<Page> paginas = secCtx.pages();
                for (int i = 0; i < paginas.size(); i++) {
                    String etiqueta = paginas.size() > 1 ? ctxNombre + "_p" + (i + 1) : ctxNombre;
                    capturarScreenshotParaAllure(paginas.get(i),
                            "Captura de pantalla de fallo (" + etiqueta + ") - " + testName);
                }
            } catch (Exception e) {
                System.err.println("Error al inspeccionar páginas del contexto secundario '" + ctxNombre + "': " + e.getMessage());
            }
        }

        finalizarContextoYTrace(testName);
    }

    private void capturarScreenshotParaAllure(Page targetPage, String attachmentName) {
        if (targetPage != null && !targetPage.isClosed()) {
            try {
                byte[] screenshot = targetPage.screenshot(new Page.ScreenshotOptions().setFullPage(true));
                Allure.addAttachment(attachmentName, "image/png", new ByteArrayInputStream(screenshot), "png");
            } catch (Exception e) {
                System.err.println("Error al capturar pantalla para Allure (" + attachmentName + "): " + e.getMessage());
            }
        }
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
        cerrarYGuardarTrace(context, Paths.get("resultados", testName + ".zip"));
        context = null;

        for (Map.Entry<String, BrowserContext> entry : contextosSecundarios.entrySet()) {
            String ctxNombre = entry.getKey();
            BrowserContext secCtx = entry.getValue();
            cerrarYGuardarTrace(secCtx, Paths.get("resultados", testName + "_" + ctxNombre + ".zip"));
        }
        contextosSecundarios.clear();
    }

    private void cerrarYGuardarTrace(BrowserContext ctx, Path tracePath) {
        if (ctx != null) {
            try {
                Files.createDirectories(tracePath.getParent());
                ctx.tracing().stop(new Tracing.StopOptions().setPath(tracePath));
            } catch (IOException e) {
                System.err.println("Error al guardar el trace en " + tracePath + ": " + e.getMessage());
            } catch (Exception e) {
                if (e.getMessage() == null || !e.getMessage().contains("Target page, context or browser has been closed")) {
                    System.err.println("Error al detener tracing en " + tracePath + ": " + e.getMessage());
                }
            } finally {
                try {
                    ctx.close();
                } catch (Exception e) {
                    System.err.println("Error al cerrar contexto: " + e.getMessage());
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
