package helpers;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

/**
 * Helper reutilizable para las vistas de supervisión docente:
 * - Seguimiento del turno (/documentos/:id/turnos/:turnoId)
 * - Monitoreo en vivo (/monitoreo)
 * - Drawer de bitácora de actividad y modal de reanudación de examen.
 */
public final class TeacherMonitorHelper {

    private TeacherMonitorHelper() {
    }

    /**
     * Desde la vista de detalle del documento (/documentos/:id), navega a la vista de
     * "Seguimiento y notas" del turno activo (/documentos/:id/turnos/:turnoId).
     */
    public static void abrirSeguimientoDelTurno(Page pageProfesor, String baseUrl, String docId) {
        if (!pageProfesor.url().endsWith("/documentos/" + docId)) {
            pageProfesor.navigate(baseUrl + "/documentos/" + docId);
        }
        Locator btnSeguimiento = pageProfesor.locator("a:has-text('Seguimiento y notas')").first();
        btnSeguimiento.waitFor();
        btnSeguimiento.click();
        pageProfesor.waitForURL(url -> url.contains("/documentos/" + docId + "/turnos/"));
        pageProfesor.locator("table.tracking-table").first().waitFor();
    }

    /**
     * Navega al panel de Monitoreo en vivo (/monitoreo) y espera la tarjeta del examen indicado.
     */
    public static Locator abrirMonitoreoEnVivo(Page pageProfesor, String baseUrl, String tituloExamen) {
        pageProfesor.navigate(baseUrl + "/monitoreo");
        Locator cardMonitor = pageProfesor.locator("section.card.monitor:has-text('" + tituloExamen + "')").first();
        cardMonitor.waitFor();
        return cardMonitor;
    }

    /**
     * Obtiene la fila de un alumno (por su username, ej. 'alumno1') dentro de la tabla de monitoreo.
     */
    public static Locator obtenerFilaAlumnoEnMonitoreo(Locator cardMonitor, String username) {
        return cardMonitor.locator("tr.monitor-row:has(span.table-sub:text-is('" + username + "'))").first();
    }

    /**
     * Obtiene la fila de un alumno (por su username, ej. 'alumno1') dentro de la tabla de seguimiento del turno.
     */
    public static Locator obtenerFilaAlumnoEnSeguimiento(Page pageProfesor, String username) {
        return pageProfesor.locator("table.tracking-table tbody tr:has(span.table-sub:text-is('" + username + "'))").first();
    }

    /**
     * Autoriza la reanudación de un alumno en estado 'incidente' desde su fila (en monitoreo o seguimiento),
     * confirmando el diálogo modal y esperando la respuesta HTTP POST /reanudar.
     */
    public static Response reanudarExamenDeAlumno(Page pageProfesor, Locator filaAlumno, String username) {
        Locator btnReanudar = filaAlumno.locator("button.resume-button:has-text('Reanudar')");
        btnReanudar.waitFor();
        btnReanudar.click();

        Locator modal = pageProfesor.locator("dialog.confirm-dialog[open]");
        modal.waitFor();

        return pageProfesor.waitForResponse(
                r -> r.url().contains("/alumnos/" + username + "/reanudar") && r.request().method().equalsIgnoreCase("POST"),
                () -> modal.locator(".confirm-dialog-actions button:has-text('Reanudar')").click()
        );
    }

    /**
     * Abre el drawer lateral de actividad ('Ver actividad') para el alumno indicado y devuelve el Locator del dialog.
     */
    public static Locator abrirDrawerActividad(Page pageProfesor, Locator filaAlumno) {
        Locator btnVerActividad = filaAlumno.locator("button:has-text('Ver actividad')");
        btnVerActividad.waitFor();
        btnVerActividad.click();

        Locator drawer = pageProfesor.locator("dialog.drawer[open]");
        drawer.waitFor();
        drawer.locator("h3.drawer-subtitle:has-text('Actividad')").waitFor();
        return drawer;
    }
}
