package helpers;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.util.List;

/**
 * Helper reutilizable para la preparación y resolución de exámenes desde la perspectiva del alumno
 * (aceptación de reglas de modo restringido, inicio del examen, carga de respuestas y modales de envío/salida).
 */
public final class StudentExamHelper {

    private StudentExamHelper() {
    }

    /**
     * Crea un examen desde la sesión de profesor con las herramientas y consignas indicadas,
     * ejecuta "Tomar ahora" para dejarlo activo en curso para los alumnos y devuelve el ID del documento.
     */
    public static String prepararExamenEnCurso(
            Page pageProfesor,
            String baseUrl,
            String titulo,
            String duracionMinutos,
            boolean calculadora,
            boolean geogebra,
            List<String> consignas
    ) {
        ExamFormHelper.navegarANuevoExamen(pageProfesor, baseUrl);
        String primeraConsigna = (consignas != null && !consignas.isEmpty()) ? consignas.get(0) : "Consigna 1";
        ExamFormHelper.completarCamposBase(pageProfesor, titulo, "Tema 1", duracionMinutos, primeraConsigna);

        Locator calcCheckbox = pageProfesor.locator("label:has-text('Calculadora') input[type='checkbox']");
        Locator geoCheckbox = pageProfesor.locator("label:has-text('GeoGebra') input[type='checkbox']");
        if (calculadora) {
            calcCheckbox.check();
        } else {
            calcCheckbox.uncheck();
        }
        if (geogebra) {
            geoCheckbox.check();
        } else {
            geoCheckbox.uncheck();
        }

        if (consignas != null && consignas.size() > 1) {
            Locator selectAgregar = pageProfesor.locator("select.block-add, select[aria-label='Agregar apartado']").first();
            for (int i = 1; i < consignas.size(); i++) {
                selectAgregar.selectOption("texto");
                pageProfesor.locator("textarea[placeholder*='consigna']").nth(i).fill(consignas.get(i));
            }
        }

        String docUrl = ExamFormHelper.guardarFormularioNuevoExamen(pageProfesor);
        ExamFormHelper.tomarExamenAhora(pageProfesor);
        return ExamFormHelper.extraerDocId(docUrl);
    }

    /**
     * Navega a la vista del examen del alumno (/alumno/parciales/:docId), acepta las reglas del modo restringido,
     * presiona "Ingresar al examen" esperando el POST /comenzar y espera a que cargue .exam-content.
     */
    public static Response abrirEIniciarExamenComoAlumno(Page pageAlumno, String baseUrl, String docId) {
        pageAlumno.navigate(baseUrl + "/alumno/parciales/" + docId);
        Locator rulesSection = pageAlumno.locator("section.restricted-rules");
        rulesSection.waitFor(new Locator.WaitForOptions().setTimeout(8000));

        Locator checkAceptar = pageAlumno.locator("label.restricted-accept input[type='checkbox']");
        checkAceptar.check();

        Response respComenzar = pageAlumno.waitForResponse(
                r -> r.url().contains("/api/examen/" + docId + "/comenzar") && r.request().method().equalsIgnoreCase("POST"),
                () -> pageAlumno.locator("button:has-text('Ingresar al examen')").click()
        );

        pageAlumno.locator(".exam-content").first().waitFor(new Locator.WaitForOptions().setTimeout(8000));
        return respComenzar;
    }

    /**
     * Completa la respuesta de texto en el apartado indicado (0-based) y espera a que el autoguardado
     * confirme estado 'Guardado' (o 'Sin conexión' si está offline).
     */
    public static void responderConsignaTexto(Page pageAlumno, int index, String textoRespuesta) {
        Locator textarea = pageAlumno.locator("textarea[aria-label='Respuesta']").nth(index);
        textarea.waitFor();
        textarea.fill(textoRespuesta);
        pageAlumno.locator(".save-status.guardado, .save-status.sin-conexion").first().waitFor();
    }

    /**
     * Lee el texto HH:MM:SS del temporizador ([role='timer'] strong) y devuelve el total en segundos.
     */
    public static int obtenerSegundosRestantesUI(Page pageAlumno) {
        Locator timerStrong = pageAlumno.locator("[role='timer'] strong").first();
        timerStrong.waitFor();
        String texto = timerStrong.innerText().trim();
        String[] partes = texto.split(":");
        if (partes.length != 3) {
            throw new IllegalStateException("Formato de temporizador inesperado: " + texto);
        }
        return Integer.parseInt(partes[0]) * 3600
                + Integer.parseInt(partes[1]) * 60
                + Integer.parseInt(partes[2]);
    }

    /**
     * Abre el modal de confirmación de envío haciendo clic en "Enviar parcial".
     */
    public static Locator abrirModalEnviarParcial(Page pageAlumno) {
        Locator btnEnviar = pageAlumno.locator(".exam-submit button:has-text('Enviar parcial')").first();
        btnEnviar.waitFor();
        btnEnviar.click();
        Locator dialog = pageAlumno.locator("dialog.confirm-dialog[open]");
        dialog.waitFor();
        return dialog;
    }

    /**
     * Confirma el envío en el modal abierto y espera la respuesta HTTP POST /entregar.
     */
    public static Response confirmarEnvioEnModal(Page pageAlumno) {
        Locator btnConfirmar = pageAlumno.locator("dialog.confirm-dialog[open] .confirm-dialog-actions button:has-text('Enviar parcial')").first();
        btnConfirmar.waitFor();
        return pageAlumno.waitForResponse(
                r -> r.url().contains("/entregar") && r.request().method().equalsIgnoreCase("POST"),
                btnConfirmar::click
        );
    }
}
