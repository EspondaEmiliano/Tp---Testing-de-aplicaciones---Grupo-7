package lotes;

import base.TestBase;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Dialog;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import helpers.AuthHelper;
import helpers.ClockMockHelper;
import helpers.ExamFormHelper;
import helpers.StudentExamHelper;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Epic("Administración y Planificación Académica")
@Feature("Gestión de Clases (Rol Dev) y Planificación de Turnos de Examen (Rol Profesor)")
@Tag("lote-gestion-clases")
public class GestionClasesTurnosTest extends TestBase {

    private final List<String> clasesCreadasParaAislar = new ArrayList<>();

    @AfterEach
    void limpiarDatosDePrueba() {
        ExamFormHelper.limpiarExamenesCreados(baseUrl);
        // Desvincular profesor1 y alumno1 de cualquier clase temporal creada durante el test
        if (!clasesCreadasParaAislar.isEmpty()) {
            try {
                AuthHelper.loginDev(page, baseUrl);
                for (String claseId : clasesCreadasParaAislar) {
                    page.evaluate(
                            "async (id) => {" +
                            "  const token = sessionStorage.getItem('rendix_token');" +
                            "  await fetch('/api/clases/' + encodeURIComponent(id), {" +
                            "    method: 'PUT'," +
                            "    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token }," +
                            "    body: JSON.stringify({" +
                            "      nombre: 'Clase Archivada QA ' + id," +
                            "      dia: 'sabado'," +
                            "      turno: 'noche'," +
                            "      profesores: ['profesor2']," +
                            "      alumnos: ['alumno10']" +
                            "    })" +
                            "  });" +
                            "}",
                            claseId
                    );
                }
            } catch (Exception e) {
                System.err.println("Aviso al aislar clases temporales: " + e.getMessage());
            } finally {
                clasesCreadasParaAislar.clear();
            }
        }
    }

    // =========================================================================
    // SUB-BLOQUE A: Administración de Clases desde el Rol Desarrollador (dev)
    // =========================================================================

    @Test
    @Tag("lote-gestion-clases")
    @Story("ID-044 - Visualización de las 6 clases predefinidas por día y turno")
    @DisplayName("ID-044 - Listado de las 6 clases predefinidas en /dev con día, turno, profesores, alumnos y chip Predefinida")
    @Description("Iniciar sesión con el rol dev (dev1) y verificar que el panel /dev muestre las 6 clases predefinidas con su nombre, día, turno, rango horario, profesores y cantidad de alumnos.")
    @Severity(SeverityLevel.CRITICAL)
    void testID044_VisualizacionDeLasSeisClasesPredefinidasPorDiaYTurno() {
        AuthHelper.loginDev(page, baseUrl);
        page.navigate(baseUrl + "/dev");

        Locator tarjetasClase = page.locator("ul.clases-grid li a.card.clase-card");
        tarjetasClase.first().waitFor();
        assertTrue(tarjetasClase.count() >= 6,
                "El panel /dev debe listar al menos las 6 clases predefinidas, pero mostró: " + tarjetasClase.count());

        // Verificar que la clase predefinida 'Matemáticas' muestre todos sus metadatos en la grilla
        Locator cardMatematicas = page.locator("ul.clases-grid li a.card.clase-card:has-text('Matemáticas')").first();
        cardMatematicas.waitFor();
        String textoCard = cardMatematicas.textContent();
        assertTrue(textoCard.contains("Lunes") && textoCard.contains("Turno mañana") && textoCard.contains("07:45 a 11:45"),
                "La tarjeta de Matemáticas no muestra el día y rango horario correctos: " + textoCard);
        assertTrue(textoCard.contains("Profesor"), "La tarjeta no indica los profesores asignados.");
        assertTrue(textoCard.contains("alumnos"), "La tarjeta no indica la cantidad de alumnos.");

        // Ingresar al detalle de Matemáticas y verificar el chip 'Predefinida' y las listas de integrantes
        cardMatematicas.click();
        page.waitForURL(url -> url.endsWith("/dev/clases/matematicas"));
        page.locator("span.chip:has-text('Predefinida')").waitFor();
        assertTrue(page.locator("section.clase-integrantes:has-text('Profesores')").isVisible(),
                "No se visualiza la sección de Profesores en el detalle de la clase.");
        assertTrue(page.locator("section.clase-integrantes:has-text('Alumnos')").isVisible(),
                "No se visualiza la sección de Alumnos en el detalle de la clase.");
    }

    @Test
    @Tag("lote-gestion-clases")
    @Story("ID-045 - Creación exitosa de una nueva clase desde el rol dev")
    @DisplayName("ID-045 - Creación exitosa de clase en /dev/clases/nueva y visibilidad para profesor y alumno asignados")
    @Description("Desde el rol dev, crear una nueva clase con nombre, día, turno, profesor1 y alumno1. Validar guardado < 1s, redirección al detalle y visibilidad inmediata en los paneles de profesor1 y alumno1.")
    @Severity(SeverityLevel.CRITICAL)
    void testID045_CreacionExitosaDeUnaNuevaClaseDesdeElRolDev() {
        AuthHelper.loginDev(page, baseUrl);
        page.navigate(baseUrl + "/dev/clases/nueva");

        String nombreClase = "Física Experimental QA " + (System.currentTimeMillis() % 100000);
        page.locator("label:has-text('Nombre') input").fill(nombreClase);
        page.locator("label:has-text('Día') select").selectOption("miercoles");
        page.locator("label:has-text('Turno') select").selectOption("tarde");

        // Seleccionar profesor1 y alumno1
        page.locator("fieldset:has(legend:has-text('Profesores')) label.assign-option:has-text('profesor1') input[type='checkbox']").check();
        page.locator("fieldset.clase-alumnos label.assign-option:has-text('alumno1') input[type='checkbox']").first().check();

        long inicio = System.currentTimeMillis();
        Response respPost = page.waitForResponse(
                r -> r.url().endsWith("/api/clases") && r.request().method().equalsIgnoreCase("POST"),
                () -> page.locator("button[type='submit']:has-text('Crear clase')").click()
        );
        page.waitForURL(url -> url.contains("/dev/clases/") && !url.endsWith("/nueva"));
        long duracionMs = System.currentTimeMillis() - inicio;

        String urlDetalle = page.url();
        String claseId = urlDetalle.substring(urlDetalle.lastIndexOf('/') + 1);
        clasesCreadasParaAislar.add(claseId);

        assertTrue(respPost.ok(), "Falló POST /api/clases: HTTP " + respPost.status());
        assertTrue(duracionMs <= Math.max(1000, perfToleranceMs),
                "La creación de la clase superó el tiempo esperado: " + duracionMs + " ms");

        assertTrue(page.locator("h1").first().textContent().contains(nombreClase),
                "El detalle de la clase creada no muestra el nombre asignado.");
        assertTrue(page.locator("p.chips-line").textContent().contains("Miércoles · Turno tarde (13:30 a 17:30)"),
                "El horario mostrado en el detalle de la clase no coincide con el configurado.");

        // Verificar visibilidad en el panel del Profesor (profesor1)
        BrowserContext ctxProf = crearContextoSecundario("profesor");
        Page pageProf = ctxProf.newPage();
        AuthHelper.loginProfesor(pageProf, baseUrl);
        pageProf.locator("ul.clases-grid a.clase-card:has-text('" + nombreClase + "')").first().waitFor();

        // Verificar visibilidad en el panel del Alumno (alumno1)
        BrowserContext ctxAlu = crearContextoSecundario("alumno");
        Page pageAlu = ctxAlu.newPage();
        AuthHelper.loginAlumno(pageAlu, baseUrl);
        pageAlu.locator("ul.clases-grid a.clase-card:has-text('" + nombreClase + "')").first().waitFor();
    }

    @Test
    @Tag("lote-gestion-clases")
    @Story("ID-046 - Edición de una clase existente y actualización de integrantes")
    @DisplayName("ID-046 - Edición de nombre, turno e integrantes de una clase desde /dev/clases/:id/editar")
    @Description("Crear una clase de prueba desde el rol dev, abrir su vista de edición, modificar su nombre, día/turno y agregar un nuevo alumno. Validar que PUT /api/clases/:id persista todos los cambios.")
    @Severity(SeverityLevel.CRITICAL)
    void testID046_EdicionDeUnaClaseExistenteYActualizacionDeIntegrantes() {
        AuthHelper.loginDev(page, baseUrl);
        page.navigate(baseUrl + "/dev/clases/nueva");

        String nombreInicial = "Química Base QA " + (System.currentTimeMillis() % 100000);
        page.locator("label:has-text('Nombre') input").fill(nombreInicial);
        page.locator("label:has-text('Día') select").selectOption("martes");
        page.locator("label:has-text('Turno') select").selectOption("manana");
        page.locator("fieldset:has(legend:has-text('Profesores')) label.assign-option:has-text('profesor2') input[type='checkbox']").check();
        page.locator("fieldset.clase-alumnos label.assign-option:has-text('alumno2') input[type='checkbox']").first().check();

        page.waitForResponse(
                r -> r.url().endsWith("/api/clases") && r.request().method().equalsIgnoreCase("POST"),
                () -> page.locator("button[type='submit']:has-text('Crear clase')").click()
        );
        page.waitForURL(url -> url.contains("/dev/clases/") && !url.endsWith("/nueva"));
        String claseId = page.url().substring(page.url().lastIndexOf('/') + 1);
        clasesCreadasParaAislar.add(claseId);

        // Abrir edición de la clase recién creada
        page.locator("a:has-text('Editar')").click();
        page.waitForURL(url -> url.endsWith("/dev/clases/" + claseId + "/editar"));

        String nombreEditado = nombreInicial + " Editada";
        page.locator("label:has-text('Nombre') input").fill(nombreEditado);
        page.locator("label:has-text('Día') select").selectOption("jueves");
        page.locator("label:has-text('Turno') select").selectOption("noche");
        page.locator("fieldset.clase-alumnos label.assign-option:has-text('alumno3') input[type='checkbox']").first().check();

        Response respPut = page.waitForResponse(
                r -> r.url().contains("/api/clases/" + claseId) && r.request().method().equalsIgnoreCase("PUT"),
                () -> page.locator("button[type='submit']:has-text('Guardar cambios')").click()
        );
        assertTrue(respPut.ok(), "Falló PUT /api/clases/:id al editar la clase: HTTP " + respPut.status());
        page.waitForURL(url -> url.endsWith("/dev/clases/" + claseId));

        assertTrue(page.locator("h1").first().textContent().contains(nombreEditado),
                "El título de la clase no reflejó el nombre editado.");
        assertTrue(page.locator("p.chips-line").textContent().contains("Jueves · Turno noche (18:30 a 22:30)"),
                "El día y turno de la clase no se actualizaron tras editar.");
        Locator secAlumnos = page.locator("section.clase-integrantes:has-text('Alumnos')");
        assertTrue(secAlumnos.textContent().contains("Alumnos (2)")
                        && secAlumnos.textContent().contains("alumno2")
                        && secAlumnos.textContent().contains("alumno3"),
                "La nómina de alumnos de la clase no reflejó el nuevo integrante: " + secAlumnos.textContent());
    }

    @Test
    @Tag("lote-gestion-clases")
    @Story("ID-047 - Validación de campos obligatorios al crear o editar una clase")
    @DisplayName("ID-047 - Bloqueo y mensajes de error por campos obligatorios faltantes en el formulario de clase")
    @Description("En /dev/clases/nueva, intentar crear una clase dejando todos los campos en blanco y luego dejando sin seleccionar profesores o alumnos. Validar los mensajes de error por campo y que no se envíe petición al servidor.")
    @Severity(SeverityLevel.NORMAL)
    void testID047_ValidacionDeCamposObligatoriosAlCrearOEditarUnaClase() {
        AuthHelper.loginDev(page, baseUrl);
        page.navigate(baseUrl + "/dev/clases/nueva");

        page.locator("button[type='submit']:has-text('Crear clase')").click();

        // Validar los 5 mensajes de error obligatorios definidos en la función oi()
        assertTrue(page.locator(".field-error:has-text('El nombre es obligatorio.')").isVisible(),
                "Falta validación de nombre obligatorio.");
        assertTrue(page.locator(".field-error:has-text('Elegí un día.')").isVisible(),
                "Falta validación de día obligatorio.");
        assertTrue(page.locator(".field-error:has-text('Elegí un turno.')").isVisible(),
                "Falta validación de turno obligatorio.");
        assertTrue(page.locator(".field-error:has-text('Asigná al menos un profesor.')").isVisible(),
                "Falta validación de al menos un profesor.");
        assertTrue(page.locator(".field-error:has-text('Asigná al menos un alumno.')").isVisible(),
                "Falta validación de al menos un alumno.");
        assertTrue(page.url().endsWith("/dev/clases/nueva"),
                "No debe redirigir cuando faltan campos obligatorios en el formulario de clase.");
    }

    @Test
    @Tag("lote-gestion-clases")
    @Story("ID-048 - Restricción de acceso a /dev para roles no autorizados (Profesor y Alumno)")
    @DisplayName("ID-048 - Bloqueo y redirección al intentar acceder a rutas /dev con sesión de Profesor o Alumno")
    @Description("Intentar navegar a /dev y /dev/clases/nueva desde una sesión activa de Profesor y desde una sesión de Alumno. Validar redirección automática a su panel respectivo sin exponer las vistas de administración.")
    @Severity(SeverityLevel.CRITICAL)
    void testID048_RestriccionDeAccesoADevParaRolesNoAutorizados() {
        // 1) Intento con rol Profesor
        AuthHelper.loginProfesor(page, baseUrl);
        page.navigate(baseUrl + "/dev/clases/nueva");
        page.waitForURL(url -> !url.contains("/dev"));
        assertFalse(page.url().contains("/dev"),
                "El rol Profesor pudo permanecer en una ruta exclusiva de /dev: " + page.url());
        assertEquals(0, page.locator("button:has-text('Crear clase')").count(),
                "Se expuso el formulario de creación de clases al rol Profesor.");

        // 2) Intento con rol Alumno
        BrowserContext ctxAlumno = crearContextoSecundario("alumno");
        Page pageAlumno = ctxAlumno.newPage();
        AuthHelper.loginAlumno(pageAlumno, baseUrl);
        pageAlumno.navigate(baseUrl + "/dev");
        pageAlumno.waitForURL(url -> url.contains("/alumno") && !url.contains("/dev"));
        assertFalse(pageAlumno.url().contains("/dev"),
                "El rol Alumno pudo acceder a la ruta /dev: " + pageAlumno.url());
    }

    // =========================================================================
    // SUB-BLOQUE B: Planificación de Turnos y Asignación de Alumnos (Profesor)
    // =========================================================================

    @Test
    @Tag("lote-gestion-clases")
    @Story("ID-049 - Configuración de múltiples turnos de evaluación al crear o editar un examen")
    @DisplayName("ID-049 - Creación de examen con múltiples turnos ('+ Asignar otro turno') y listado en el detalle")
    @Description("En el formulario de nuevo parcial para la clase Matemáticas, configurar un primer turno válido, presionar '+ Asignar otro turno', configurar un segundo turno en otra fecha válida y guardar. Validar que ambos turnos se creen y listen.")
    @Severity(SeverityLevel.CRITICAL)
    void testID049_ConfiguracionDeMultiplesTurnosDeEvaluacionAlCrearOEditarExamen() {
        AuthHelper.loginProfesor(page, baseUrl);
        ExamFormHelper.navegarANuevoExamen(page, baseUrl);

        String titulo = "Parcial MultiTurno ID049 " + System.currentTimeMillis();
        ExamFormHelper.completarCamposBase(page, titulo, "Tema 1", "60", "Consigna para múltiples turnos.");

        // Agregar un segundo turno mediante el botón '+ Asignar otro turno'
        page.locator("button.horario-add:has-text('+ Asignar otro turno')").click();
        Locator tarjetasHorario = page.locator(".card.horario");
        assertEquals(2, tarjetasHorario.count(), "Deberían mostrarse 2 bloques de configuración de turno.");

        // Configurar el segundo turno en el lunes siguiente (2028-03-27 a las 09:30)
        Locator segundoHorario = tarjetasHorario.nth(1);
        segundoHorario.locator("label:has-text('Turno') select").selectOption("manana");
        segundoHorario.locator("label:has-text('Fecha') input[type='date']").fill("2028-03-27");
        segundoHorario.locator("label:has-text('Hora de inicio') input").fill("09:30");

        String docUrl = ExamFormHelper.guardarFormularioNuevoExamen(page);
        assertNotNull(docUrl, "No se guardó el examen con múltiples turnos.");

        Locator seccionTurnos = page.locator("section.submissions");
        seccionTurnos.waitFor();
        assertTrue(seccionTurnos.textContent().contains("Turnos (2)"),
                "El detalle del examen no muestra 'Turnos (2)': " + seccionTurnos.textContent());
        assertEquals(2, seccionTurnos.locator("a:has-text('Seguimiento y notas')").count(),
                "Deben listarse las 2 tarjetas de turno con su acceso a 'Seguimiento y notas'.");
    }

    @Test
    @Tag("lote-gestion-clases")
    @Story("ID-050 - Validación de compatibilidad de horario del turno con el día, turno y rango de la clase")
    @DisplayName("ID-050 - Rechazo de turno incompatible con el turno de cursada, día de semana o rango horario de la clase")
    @Description("Seleccionar la clase Matemáticas (Lunes, Turno mañana 07:45 a 11:45, duración 60 min) y probar: 1) turno tarde, 2) fecha que no es lunes, y 3) hora de inicio que excede las 11:45. Validar los mensajes de error específicos.")
    @Severity(SeverityLevel.CRITICAL)
    void testID050_ValidacionDeCompatibilidadDeHorarioDelTurnoConLaClase() {
        AuthHelper.loginProfesor(page, baseUrl);
        ExamFormHelper.navegarANuevoExamen(page, baseUrl);

        String titulo = "Parcial Horario Incompatible ID050 " + System.currentTimeMillis();
        ExamFormHelper.completarCamposBase(page, titulo, "Tema 1", "60", "Consigna para validar reglas de turno.");

        Locator cardHorario = page.locator(".card.horario").first();

        // Caso 1: Turno distinto al de la clase (Matemáticas es Turno mañana -> elegimos Turno tarde)
        cardHorario.locator("label:has-text('Turno') select").selectOption("tarde");
        cardHorario.locator("label:has-text('Fecha') input[type='date']").fill("2028-03-20");
        cardHorario.locator("label:has-text('Hora de inicio') input").fill("14:00");
        page.locator("button[type='submit']:has-text('Crear')").click();

        Locator errTurno = cardHorario.locator(".field-error:has-text('La clase se dicta en el turno mañana.')");
        errTurno.waitFor();
        assertTrue(errTurno.isVisible(), "No se mostró el error de incompatibilidad de turno con la clase.");

        // Caso 2: Día de la semana distinto al de la clase (2028-03-21 es Martes, pero Matemáticas se dicta los Lunes)
        cardHorario.locator("label:has-text('Turno') select").selectOption("manana");
        cardHorario.locator("label:has-text('Fecha') input[type='date']").fill("2028-03-21");
        cardHorario.locator("label:has-text('Hora de inicio') input").fill("08:30");
        page.locator("button[type='submit']:has-text('Crear')").click();

        Locator errDia = cardHorario.locator(".field-error:has-text('La clase se dicta los lunes')");
        errDia.waitFor();
        assertTrue(errDia.isVisible(), "No se mostró el error por elegir una fecha que no cae en el día de la clase.");

        // Caso 3: Hora de inicio que sumada a la duración (60 min) excede el fin del turno mañana (11:45) -> ej. 11:15
        cardHorario.locator("label:has-text('Fecha') input[type='date']").fill("2028-03-20");
        cardHorario.locator("label:has-text('Hora de inicio') input").fill("11:15");
        page.locator("button[type='submit']:has-text('Crear')").click();

        Locator errRango = cardHorario.locator(".field-error:has-text('tiene que empezar entre las 07:45 y las 10:45')");
        errRango.waitFor();
        assertTrue(errRango.isVisible(), "No se mostró el error por exceder el horario de finalización del turno.");
    }

    @Test
    @Tag("lote-gestion-clases")
    @Story("ID-051 - Asignación y desasignación de alumnos en un turno próximo")
    @DisplayName("ID-051 - Asignar y quitar alumnos en la vista de gestión de un turno próximo")
    @Description("En un examen con turno próximo (/documentos/:id/turnos/:turnoId), asignar a alumno1 y alumno2 al turno, verificar que aparezcan en la tabla de alumnos asignados, y luego quitar a alumno2 verificando que regrese a la lista de disponibles.")
    @Severity(SeverityLevel.CRITICAL)
    void testID051_AsignacionYDesasignacionDeAlumnosEnUnTurnoProximo() {
        AuthHelper.loginProfesor(page, baseUrl);
        ExamFormHelper.navegarANuevoExamen(page, baseUrl);

        String titulo = "Parcial Asignar Alumnos ID051 " + System.currentTimeMillis();
        ExamFormHelper.completarCamposBase(page, titulo, "Tema 1", "60", "Consigna para gestión de alumnos del turno.");
        ExamFormHelper.guardarFormularioNuevoExamen(page);

        page.locator("a:has-text('Seguimiento y notas')").first().click();
        page.waitForURL(url -> url.contains("/turnos/"));

        Locator seccionAsignar = page.locator("section.card.assign");
        seccionAsignar.waitFor();

        // Si el turno inicia sin alumnos asignados, asignar a alumno1 y alumno2
        Locator checkAlumno1 = seccionAsignar.locator("label.assign-option:has-text('alumno1') input[type='checkbox']");
        Locator checkAlumno2 = seccionAsignar.locator("label.assign-option:has-text('alumno2') input[type='checkbox']");
        if (checkAlumno1.count() > 0) {
            checkAlumno1.check();
        }
        if (checkAlumno2.count() > 0) {
            checkAlumno2.check();
        }
        Locator btnAsignar = seccionAsignar.locator("button:has-text('Asignar')");
        if (btnAsignar.isEnabled()) {
            Response respAsignar = page.waitForResponse(
                    r -> r.url().contains("/alumnos") && r.request().method().equalsIgnoreCase("POST"),
                    btnAsignar::click
            );
            assertTrue(respAsignar.ok(), "Falló POST /api/turnos/:id/alumnos.");
        }

        Locator tablaAsignados = page.locator("table.tracking-table");
        tablaAsignados.waitFor();
        assertTrue(tablaAsignados.textContent().contains("alumno1") && tablaAsignados.textContent().contains("alumno2"),
                "alumno1 y alumno2 deben figurar en la tabla de alumnos asignados al turno.");

        // Quitar a alumno2 aceptando el diálogo de confirmación nativa
        Page.DialogConsumer aceptarConfirm = Dialog::accept;
        page.onDialog(aceptarConfirm);
        try {
            Locator filaAlumno2 = tablaAsignados.locator("tbody tr:has-text('alumno2')").first();
            Response respDelete = page.waitForResponse(
                    r -> r.url().contains("/alumnos/alumno2") && r.request().method().equalsIgnoreCase("DELETE"),
                    () -> filaAlumno2.locator("button.danger:has-text('Quitar')").click()
            );
            assertTrue(respDelete.ok(), "Falló DELETE /api/turnos/:id/alumnos/alumno2.");
        } finally {
            page.offDialog(aceptarConfirm);
        }

        // Verificar que alumno2 ya no está en la tabla de asignados y volvió al listado de disponibles para asignar
        assertEquals(0, tablaAsignados.locator("tbody tr:has-text('alumno2')").count(),
                "alumno2 sigue apareciendo en la tabla de asignados tras haber sido quitado.");
        assertTrue(seccionAsignar.locator("label.assign-option:has-text('alumno2')").isVisible(),
                "alumno2 no regresó a la lista de alumnos disponibles para asignar.");
    }

    @Test
    @Tag("lote-gestion-clases")
    @Story("ID-052 - Reasignación automática de un alumno de un turno futuro a otro turno del mismo examen")
    @DisplayName("ID-052 - Mover automáticamente a un alumno de un turno próximo a otro turno del mismo parcial")
    @Description("Crear un examen con 2 turnos futuros (Turno 1 y Turno 2). Asignar a alumno1 en el Turno 1, luego ir al Turno 2 y asignar a alumno1 allí. Validar el aviso de traspaso y que alumno1 quede únicamente en el Turno 2.")
    @Severity(SeverityLevel.CRITICAL)
    void testID052_ReasignacionAutomaticaDeUnAlumnoDeUnTurnoFuturoAOtroTurno() {
        AuthHelper.loginProfesor(page, baseUrl);
        ExamFormHelper.navegarANuevoExamen(page, baseUrl);

        String titulo = "Parcial Reasignar Turno ID052 " + System.currentTimeMillis();
        ExamFormHelper.completarCamposBase(page, titulo, "Tema 1", "60", "Consigna para reasignación entre turnos.");

        // Agregar segundo turno futuro (Lunes 27/03/2028 09:30)
        page.locator("button.horario-add:has-text('+ Asignar otro turno')").click();
        Locator segundoHorario = page.locator(".card.horario").nth(1);
        segundoHorario.locator("label:has-text('Turno') select").selectOption("manana");
        segundoHorario.locator("label:has-text('Fecha') input[type='date']").fill("2028-03-27");
        segundoHorario.locator("label:has-text('Hora de inicio') input").fill("09:30");

        String docUrl = ExamFormHelper.guardarFormularioNuevoExamen(page);
        String docId = ExamFormHelper.extraerDocId(docUrl);

        // 1) Abrir el Turno 1 y asignar a alumno1
        page.locator("a:has-text('Seguimiento y notas')").nth(0).click();
        page.waitForURL(url -> url.contains("/turnos/"));
        String urlTurno1 = page.url();

        Locator checkAlu1EnT1 = page.locator("section.card.assign label.assign-option:has-text('alumno1') input[type='checkbox']");
        if (checkAlu1EnT1.count() > 0) {
            checkAlu1EnT1.check();
            page.waitForResponse(
                    r -> r.url().contains("/alumnos") && r.request().method().equalsIgnoreCase("POST"),
                    () -> page.locator("section.card.assign button:has-text('Asignar')").click()
            );
        }
        assertTrue(page.locator("table.tracking-table").textContent().contains("alumno1"),
                "alumno1 debe quedar asignado inicialmente en el Turno 1.");

        // 2) Volver al examen y abrir el Turno 2
        page.navigate(baseUrl + "/documentos/" + docId);
        page.locator("a:has-text('Seguimiento y notas')").nth(1).click();
        page.waitForURL(url -> url.contains("/turnos/") && !url.equals(urlTurno1));

        Locator opcionAlu1EnT2 = page.locator("section.card.assign label.assign-option:has-text('alumno1')");
        opcionAlu1EnT2.waitFor();
        assertTrue(opcionAlu1EnT2.textContent().contains("ya está en el"),
                "La lista de asignación del Turno 2 debe indicar que alumno1 ya está en otro turno: " + opcionAlu1EnT2.textContent());

        opcionAlu1EnT2.locator("input[type='checkbox']").check();
        page.waitForResponse(
                r -> r.url().contains("/alumnos") && r.request().method().equalsIgnoreCase("POST"),
                () -> page.locator("section.card.assign button:has-text('Asignar')").click()
        );

        Locator avisoMovimiento = page.locator("section.card.assign p.notice[role='status']");
        avisoMovimiento.waitFor();
        assertTrue(avisoMovimiento.textContent().contains("pasó a este turno y salió del"),
                "No se mostró la notificación de reasignación entre turnos: " + avisoMovimiento.textContent());
        assertTrue(page.locator("table.tracking-table").textContent().contains("alumno1"),
                "alumno1 no apareció en la tabla del Turno 2 tras la reasignación.");

        // 3) Confirmar que alumno1 fue removido automáticamente del Turno 1
        page.navigate(urlTurno1);
        page.locator("section.card.assign").waitFor();
        assertEquals(0, page.locator("table.tracking-table tbody tr:has-text('alumno1')").count(),
                "alumno1 no fue desasignado del Turno 1 tras moverse al Turno 2.");
    }

    @Test
    @Tag("lote-gestion-clases")
    @Story("ID-053 - Bloqueo de edición de horario/duración y bloqueo de quita cuando el turno ya comenzó")
    @DisplayName("ID-053 - Inmutabilidad de horario y duración y prohibición de quitar alumnos en un turno ya iniciado")
    @Description("Iniciar un examen mediante 'Tomar ahora' para que su turno quede 'en-curso'. Validar que en /editar la duración y el horario del turno en curso estén bloqueados, y que en la vista del turno no exista la opción 'Quitar'.")
    @Severity(SeverityLevel.CRITICAL)
    void testID053_BloqueoDeEdicionDeHorarioYDuracionYBloqueoDeQuitaCuandoElTurnoYaComenzo() {
        AuthHelper.loginProfesor(page, baseUrl);
        String titulo = "Parcial Turno Iniciado ID053 " + System.currentTimeMillis();
        String docId = StudentExamHelper.prepararExamenEnCurso(
                page,
                baseUrl,
                titulo,
                "60",
                false,
                false,
                List.of("Consigna con turno en curso bloqueado.")
        );

        // 1) En la vista de seguimiento del turno en curso, verificar que NO exista el botón 'Quitar'
        TeacherMonitorHelper.abrirSeguimientoDelTurno(page, baseUrl, docId);
        assertEquals(0, page.locator("table.tracking-table button:has-text('Quitar')").count(),
                "No debe permitirse quitar alumnos de un turno que ya comenzó.");

        // 2) En la vista de edición del parcial (/documentos/:id/editar), verificar bloqueo de duración y del turno iniciado
        page.navigate(baseUrl + "/documentos/" + docId);
        ExamFormHelper.abrirEdicionExamen(page);

        Locator inputDuracion = page.locator("input.duration-input");
        inputDuracion.waitFor();
        assertTrue(inputDuracion.isDisabled(),
                "El campo Duración debe estar deshabilitado cuando uno de los turnos ya comenzó.");
        assertTrue(page.locator("span.hint:has-text('Uno de los turnos ya empezó: la duración no se puede cambiar.')").isVisible(),
                "No se muestra la leyenda explicativa de bloqueo de duración.");

        Locator turnoBloqueado = page.locator(".card.horario.horario-locked").first();
        turnoBloqueado.waitFor();
        assertTrue(turnoBloqueado.textContent().contains("Ya empezó: no se puede modificar."),
                "El turno en curso no se muestra bloqueado en el formulario de edición: " + turnoBloqueado.textContent());
    }
}
