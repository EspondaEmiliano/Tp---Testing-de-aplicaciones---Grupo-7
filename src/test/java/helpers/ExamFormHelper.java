package helpers;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper reutilizable para completar el formulario de exámenes, publicar, iniciar turnos ("Tomar ahora")
 * y eliminar los exámenes de prueba creados en @AfterEach para no contaminar el ambiente.
 */
public final class ExamFormHelper {

    private static final Pattern DOC_ID_PATTERN = Pattern.compile("/documentos/([a-zA-Z0-9-]+)(?:/|$|\\?)");

    private static final Map<String, String> FECHA_POR_DIA = Map.of(
            "lunes", "2028-03-20",
            "martes", "2028-03-21",
            "miércoles", "2028-03-15",
            "miercoles", "2028-03-15",
            "jueves", "2028-03-16",
            "viernes", "2028-03-17",
            "sábado", "2028-03-18",
            "sabado", "2028-03-18"
    );

    private static final List<String> examenesCreadosIds = new ArrayList<>();

    private ExamFormHelper() {
    }

    public static void registrarExamenParaLimpieza(String urlOrId) {
        if (urlOrId == null || urlOrId.isBlank()) {
            return;
        }
        String id = extraerDocId(urlOrId);
        if (id != null && !id.equals("nuevo") && !examenesCreadosIds.contains(id)) {
            examenesCreadosIds.add(id);
        }
    }

    public static String extraerDocId(String urlOrId) {
        if (urlOrId == null) {
            return null;
        }
        Matcher matcher = DOC_ID_PATTERN.matcher(urlOrId);
        if (matcher.find()) {
            return matcher.group(1);
        }
        if (!urlOrId.contains("/") && !urlOrId.isBlank()) {
            return urlOrId.trim();
        }
        return null;
    }

    public static void navegarANuevoExamen(Page page, String baseUrl) {
        page.navigate(baseUrl + "/documentos/nuevo");
        page.locator("form").first().waitFor();
    }

    /**
     * Completa los campos base del formulario de examen respetando el día y turno reales de la clase seleccionada.
     */
    public static void completarCamposBase(Page page, String titulo, String tema, String duracion, String consignaTexto) {
        Locator claseSelect = page.locator("label:has-text('Clase') select");
        claseSelect.selectOption("matematicas");

        if (titulo != null) {
            page.locator("label:has-text('Título') input").fill(titulo);
        }
        if (tema != null) {
            page.locator("label:has-text('Tema') select").selectOption(tema);
        }
        if (duracion != null) {
            page.locator("label:has-text('Duración') input[type='number']").fill(duracion);
        }

        configurarHorarioCompatibleConClase(page);

        Locator consignaInput = page.locator("textarea[placeholder*='consigna'], .block textarea").first();
        if (consignaTexto != null) {
            consignaInput.fill(consignaTexto);
        } else if (consignaInput.count() > 0) {
            consignaInput.fill("");
        }
    }

    /**
     * Ajusta Turno, Fecha y Hora de inicio de acuerdo con la configuración de la clase seleccionada.
     */
    public static void configurarHorarioCompatibleConClase(Page page) {
        Locator turnoSelect = page.locator("label:has-text('Turno') select").first();
        String turnoActual = turnoSelect.inputValue();
        if (turnoActual == null || turnoActual.isBlank()) {
            turnoSelect.selectOption("manana");
            turnoActual = "manana";
        }

        // Verificar si al seleccionar el turno aparece error de compatibilidad con la clase
        Locator turnoError = page.locator("label:has-text('Turno') .field-error");
        if (turnoError.count() > 0 && turnoError.first().isVisible()) {
            String msg = turnoError.first().innerText().toLowerCase();
            if (msg.contains("mañana")) {
                turnoSelect.selectOption("manana");
                turnoActual = "manana";
            } else if (msg.contains("tarde")) {
                turnoSelect.selectOption("tarde");
                turnoActual = "tarde";
            } else if (msg.contains("noche")) {
                turnoSelect.selectOption("noche");
                turnoActual = "noche";
            }
        }

        String fechaElegida = "2028-03-20"; // Lunes por defecto para Matemáticas
        Locator fechaInput = page.locator("input[type='date']").first();
        fechaInput.fill(fechaElegida);

        Locator fechaError = page.locator("label:has-text('Fecha') .field-error");
        if (fechaError.count() > 0 && fechaError.first().isVisible()) {
            String msgFecha = fechaError.first().innerText().toLowerCase();
            for (Map.Entry<String, String> entry : FECHA_POR_DIA.entrySet()) {
                if (msgFecha.contains(entry.getKey())) {
                    fechaElegida = entry.getValue();
                    fechaInput.fill(fechaElegida);
                    break;
                }
            }
        }

        String horaInicio = switch (turnoActual) {
            case "tarde" -> "13:30";
            case "noche" -> "18:30";
            default -> "08:30";
        };
        String horaEnCurso = switch (turnoActual) {
            case "tarde" -> "13:35:00";
            case "noche" -> "18:35:00";
            default -> "08:35:00";
        };

        Locator horaInput = page.locator(".card.horario input[placeholder*='Ej:'], .card.horario input[placeholder='HH:MM'], .card.horario input[list], input[type='time']").first();
        if (horaInput.isVisible() && !horaInput.isDisabled()) {
            horaInput.fill(horaInicio);
            horaInput.blur();
        }

        String simInProgress = fechaElegida + "T" + horaEnCurso + "-03:00";
        page.evaluate("(iso) => { try { window.localStorage.setItem('__examInProgressTime', iso); } catch(e){} }", simInProgress);
    }

    /**
     * Crea un examen en estado borrador desde cualquier pantalla (con sesión de profesor activa)
     * y devuelve la URL de detalle del examen creado (/documentos/:id).
     */
    public static String crearExamenEnBorrador(Page page, String baseUrl, String titulo, String consignaTexto) {
        navegarANuevoExamen(page, baseUrl);
        completarCamposBase(page, titulo, "Tema 1", "60", consignaTexto);
        return guardarFormularioNuevoExamen(page);
    }

    /**
     * Envía el formulario de nuevo examen, espera la respuesta POST /api/documentos y la redirección a /documentos/:id.
     */
    public static String guardarFormularioNuevoExamen(Page page) {
        page.locator("button[type='submit']:has-text('Crear')").click();
        page.waitForURL(url -> !url.endsWith("/nuevo") && url.contains("/documentos/"),
                new Page.WaitForURLOptions().setTimeout(8000));
        page.locator("h1").first().waitFor();
        String docUrl = page.url();
        registrarExamenParaLimpieza(docUrl);
        return docUrl;
    }

    /**
     * Abre la vista de edición (/documentos/:id/editar) desde el detalle del examen y espera a que concluya
     * la sincronización inicial de GET /api/datos para evitar condiciones de carrera con 'lr()' al guardar.
     */
    public static void abrirEdicionExamen(Page page) {
        Locator btnEditar = page.locator("a:has-text('Editar'), button:has-text('Editar')").first();
        btnEditar.waitFor();
        page.waitForResponse(
                r -> r.url().contains("/api/datos") && r.request().method().equalsIgnoreCase("GET"),
                btnEditar::click
        );
        page.waitForURL(url -> url.endsWith("/editar"), new Page.WaitForURLOptions().setTimeout(5000));
        page.locator("form").first().waitFor();
    }

    /**
     * Guarda los cambios en la vista de edición (/documentos/:id/editar) esperando el PUT /api/documentos/:id
     * y el GET /api/datos posterior antes de retornar.
     */
    public static void guardarCambiosEdicionExamen(Page page) {
        Response putResp = page.waitForResponse(
                r -> r.url().contains("/api/documentos/") && r.request().method().equalsIgnoreCase("PUT"),
                () -> page.locator("button[type='submit']:has-text('Guardar cambios'), button:has-text('Guardar')").first().click()
        );
        if (!putResp.ok()) {
            throw new IllegalStateException("Fallo al guardar edición del examen (PUT). HTTP " + putResp.status());
        }
        page.waitForURL(url -> !url.endsWith("/editar"), new Page.WaitForURLOptions().setTimeout(8000));
    }

    /**
     * Publica el examen en cuya vista de detalle (/documentos/:id) se encuentra el navegador.
     */
    public static void publicarExamenActual(Page page) {
        Locator botonPublicar = page.locator("button:has-text('Publicar parcial'), button:has-text('Publicar')").first();
        botonPublicar.waitFor();
        page.onceDialog(dialog -> dialog.accept());
        Response resp = page.waitForResponse(
                r -> r.url().contains("/publicar") && r.request().method().equalsIgnoreCase("POST"),
                botonPublicar::click
        );
        if (!resp.ok()) {
            throw new IllegalStateException("Fallo al publicar el examen. HTTP " + resp.status());
        }
        page.locator("span.chip.published, span.chip:has-text('Publicado')").first().waitFor();
    }

    /**
     * Ejecuta "Tomar ahora" en la vista de detalle del examen, lo cual publica el examen y crea un turno activo
     * en este mismo instante con todos los alumnos de la clase asignados.
     */
    public static void tomarExamenAhora(Page page) {
        Locator botonTomarAhora = page.locator("button:has-text('Tomar ahora')").first();
        botonTomarAhora.waitFor();
        page.onceDialog(dialog -> dialog.accept());
        Response resp = page.waitForResponse(
                r -> r.url().contains("/tomar-ahora") && r.request().method().equalsIgnoreCase("POST"),
                botonTomarAhora::click
        );
        if (!resp.ok()) {
            throw new IllegalStateException("Fallo al ejecutar 'Tomar ahora'. HTTP " + resp.status());
        }
        page.evaluate("() => { try { window.localStorage.removeItem('__examInProgressTime'); window.localStorage.removeItem('__simTime'); } catch(e){} }");
        page.locator("span.chip.published, span.chip:has-text('Publicado')").first().waitFor();
    }

    /**
     * Elimina vía API todos los exámenes registrados durante el test para mantener limpio el servidor.
     */
    public static void limpiarExamenesCreados(String baseUrl) {
        if (examenesCreadosIds.isEmpty()) {
            return;
        }
        List<String> idsAEliminar = new ArrayList<>(examenesCreadosIds);
        examenesCreadosIds.clear();

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            String loginBody = String.format("{\"usuario\":\"%s\",\"password\":\"%s\"}",
                    AuthHelper.getProfUser(), AuthHelper.getProfPass());
            HttpRequest loginReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(loginBody))
                    .build();

            HttpResponse<String> loginResp = client.send(loginReq, HttpResponse.BodyHandlers.ofString());
            if (loginResp.statusCode() != 200) {
                return;
            }
            Matcher tokenMatcher = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(loginResp.body());
            if (!tokenMatcher.find()) {
                return;
            }
            String token = tokenMatcher.group(1);

            for (String docId : idsAEliminar) {
                try {
                    HttpRequest delReq = HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + "/api/documentos/" + docId))
                            .header("Authorization", "Bearer " + token)
                            .DELETE()
                            .build();
                    client.send(delReq, HttpResponse.BodyHandlers.discarding());
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }
}
