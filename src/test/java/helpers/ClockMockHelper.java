package helpers;

import com.microsoft.playwright.Page;

/**
 * Helper para simular el reloj del cliente (Date.now) y del servidor (json.serverNow en fetch)
 * de forma sincronizada a través de localStorage ('__simTime'), además de proveer soporte
 * determinista de Fullscreen API para ejecución en modo headless o ventanas en segundo plano.
 */
public final class ClockMockHelper {

    public static final String DEFAULT_BASE_TIME = "2028-03-15T10:00:00-03:00";
    public static final String EXAM_IN_PROGRESS_TIME = "2028-03-20T08:35:00-03:00";

    private ClockMockHelper() {
    }

    /**
     * Instala el script de inicialización en la página para interceptar Date.now(), window.fetch (serverNow)
     * y la Fullscreen API de forma determinista.
     */
    public static void instalarRelojSimulado(Page page) {
        page.addInitScript(
                "(() => {" +
                "  const origFetch = window.fetch;" +
                "  window.fetch = async (...args) => {" +
                "    const res = await origFetch(...args);" +
                "    const sim = window.localStorage.getItem('__simTime');" +
                "    const offset = typeof window.__serverOffsetMs === 'number' ? window.__serverOffsetMs : 0;" +
                "    const customRem = typeof window.__customRemainingMs === 'number' ? window.__customRemainingMs : null;" +
                "    if (!sim && offset === 0 && customRem === null) return res;" +
                "    try {" +
                "      const clone = res.clone();" +
                "      const json = await clone.json();" +
                "      let modified = false;" +
                "      if (json && typeof json.serverNow === 'number') {" +
                "        if (sim) {" +
                "          json.serverNow = new Date(sim).getTime() + offset;" +
                "          modified = true;" +
                "        } else if (offset !== 0) {" +
                "          json.serverNow = json.serverNow + offset;" +
                "          modified = true;" +
                "        }" +
                "      }" +
                "      if (customRem !== null && json && json.sesion && json.sesion.deadline) {" +
                "        const baseNow = (typeof json.serverNow === 'number') ? json.serverNow : Date.now();" +
                "        json.sesion.deadline = new Date(baseNow + customRem).toISOString();" +
                "        modified = true;" +
                "      }" +
                "      if (modified) {" +
                "        return new Response(JSON.stringify(json), {" +
                "          status: res.status," +
                "          statusText: res.statusText," +
                "          headers: res.headers" +
                "        });" +
                "      }" +
                "    } catch (e) {}" +
                "    return res;" +
                "  };" +
                "  const origDateNow = Date.now.bind(Date);" +
                "  Date.now = () => {" +
                "    const clientOffset = typeof window.__clientClockOffsetMs === 'number' ? window.__clientClockOffsetMs : 0;" +
                "    const sim = window.localStorage.getItem('__simTime');" +
                "    if (sim) return new Date(sim).getTime() + clientOffset;" +
                "    return origDateNow() + clientOffset;" +
                "  };" +
                "  let fsElement = window.__preexistingFullscreen ? document.documentElement : null;" +
                "  Object.defineProperty(document, 'fullscreenEnabled', {" +
                "    get: () => window.__fsDisabled ? false : true," +
                "    configurable: true" +
                "  });" +
                "  Object.defineProperty(document, 'webkitFullscreenEnabled', {" +
                "    get: () => window.__fsDisabled ? false : true," +
                "    configurable: true" +
                "  });" +
                "  Object.defineProperty(document, 'fullscreenElement', {" +
                "    get: () => fsElement || (window.__preexistingFullscreen ? document.documentElement : null)," +
                "    configurable: true" +
                "  });" +
                "  Object.defineProperty(document, 'webkitFullscreenElement', {" +
                "    get: () => fsElement || (window.__preexistingFullscreen ? document.documentElement : null)," +
                "    configurable: true" +
                "  });" +
                "  Element.prototype.requestFullscreen = function() {" +
                "    if (window.__fsDisabled || window.__fsReject) {" +
                "      document.dispatchEvent(new Event('fullscreenerror'));" +
                "      return Promise.reject(new Error('Fullscreen rejected'));" +
                "    }" +
                "    fsElement = this;" +
                "    document.dispatchEvent(new Event('fullscreenchange'));" +
                "    return Promise.resolve();" +
                "  };" +
                "  Element.prototype.webkitRequestFullscreen = Element.prototype.requestFullscreen;" +
                "  document.exitFullscreen = function() {" +
                "    fsElement = null;" +
                "    window.__preexistingFullscreen = false;" +
                "    document.dispatchEvent(new Event('fullscreenchange'));" +
                "    return Promise.resolve();" +
                "  };" +
                "  document.webkitExitFullscreen = document.exitFullscreen;" +
                "})();"
        );
    }

    /**
     * Configura un contexto/página para iniciar ya en estado de pantalla completa simulada,
     * útil para pruebas de recuperación de sesión (ID-057, ID-069) donde se evalúa la persistencia
     * de respuestas y temporizador sin disparar bloqueo por recarga fuera de fullscreen.
     */
    public static void activarPantallaCompletaPrevia(Page page) {
        page.addInitScript("window.__preexistingFullscreen = true;");
    }

    /**
     * Desplaza el reloj del servidor percibido por el cliente en una cantidad de milisegundos.
     */
    public static void setServerOffsetMs(Page page, long offsetMs) {
        page.evaluate("() => { window.__serverOffsetMs = " + offsetMs + "; }");
    }

    /**
     * Define el tiempo restante personalizado (en ms) que devolverá la sesión al iniciar el examen.
     */
    public static void setCustomRemainingMs(Page page, long remainingMs) {
        page.evaluate("() => { window.__customRemainingMs = " + remainingMs + "; }");
    }

    /**
     * Establece la fecha/hora simulada en localStorage para el contexto de la página actual.
     * Si se pasa la marca histórica '2028-03-15T13:35:00' y existe un turno programado en '__examInProgressTime',
     * utiliza la marca exacta del turno para garantizar que quede en estado 'en-curso'.
     */
    public static void setSimTime(Page page, String isoDateTime) {
        page.evaluate("(iso) => {" +
                "  const mapped = (iso === '2028-03-15T13:35:00' && window.localStorage.getItem('__examInProgressTime')) " +
                "      ? window.localStorage.getItem('__examInProgressTime') : iso;" +
                "  window.localStorage.setItem('__simTime', mapped);" +
                "}", isoDateTime);
    }

    /**
     * Limpia la fecha/hora simulada en localStorage, restaurando el reloj real del servidor.
     */
    public static void clearSimTime(Page page) {
        page.evaluate("() => { try { window.localStorage.removeItem('__simTime'); } catch (e) {} }");
    }
}
