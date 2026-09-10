package cl.grupo4.uniday.controller;

import cl.grupo4.uniday.config.LoginInterceptor;
import cl.grupo4.uniday.model.Actividad;
import cl.grupo4.uniday.model.Materia;
import cl.grupo4.uniday.model.Semestre;
import cl.grupo4.uniday.repository.ActividadRepository;
import cl.grupo4.uniday.repository.MateriaRepository;
import cl.grupo4.uniday.repository.SemestreRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/calendario")
public class ActividadController {

    private static final Map<String, String> TIPOS = new LinkedHashMap<>();
    private static final Map<String, String> TIPOS_PLURAL = new LinkedHashMap<>();
    private static final Map<String, String> ESTADOS = new LinkedHashMap<>();
    private static final Map<Integer, String> MESES = new LinkedHashMap<>();
    private static final Map<Integer, String> DIAS_CORTOS = new LinkedHashMap<>();

    static {
        TIPOS.put("tarea", "Tarea");
        TIPOS.put("certamen", "Certamen");
        TIPOS.put("test", "Test");
        TIPOS.put("presentación", "Presentación");

        TIPOS_PLURAL.put("tarea", "Tareas");
        TIPOS_PLURAL.put("certamen", "Certámenes");
        TIPOS_PLURAL.put("test", "Tests");
        TIPOS_PLURAL.put("presentación", "Presentaciones");

        ESTADOS.put("pendiente", "Pendiente");
        ESTADOS.put("progreso", "En progreso");
        ESTADOS.put("completada", "Completada");

        MESES.put(1, "Enero");
        MESES.put(2, "Febrero");
        MESES.put(3, "Marzo");
        MESES.put(4, "Abril");
        MESES.put(5, "Mayo");
        MESES.put(6, "Junio");
        MESES.put(7, "Julio");
        MESES.put(8, "Agosto");
        MESES.put(9, "Septiembre");
        MESES.put(10, "Octubre");
        MESES.put(11, "Noviembre");
        MESES.put(12, "Diciembre");

        DIAS_CORTOS.put(1, "lun");
        DIAS_CORTOS.put(2, "mar");
        DIAS_CORTOS.put(3, "mié");
        DIAS_CORTOS.put(4, "jue");
        DIAS_CORTOS.put(5, "vie");
        DIAS_CORTOS.put(6, "sáb");
        DIAS_CORTOS.put(7, "dom");
    }

    private final ActividadRepository actividadRepository;
    private final MateriaRepository materiaRepository;
    private final SemestreRepository semestreRepository;

    public ActividadController(ActividadRepository actividadRepository,
                                MateriaRepository materiaRepository,
                                SemestreRepository semestreRepository) {
        this.actividadRepository = actividadRepository;
        this.materiaRepository = materiaRepository;
        this.semestreRepository = semestreRepository;
    }

    private Long usuarioLogueado(HttpSession session) {
        return (Long) session.getAttribute(LoginInterceptor.SESSION_USUARIO_ID);
    }

    private Semestre semestreActivoDelUsuario(Long usuarioId) {
        return semestreRepository.findByUsuarioIdAndActivoTrue(usuarioId).orElse(null);
    }

    private Semestre semestreDelUsuario(Long semestreId, Long usuarioId) {
        if (semestreId == null) {
            return null;
        }
        Semestre semestre = semestreRepository.findById(semestreId).orElse(null);
        if (semestre == null || !semestre.getUsuarioId().equals(usuarioId)) {
            return null;
        }
        return semestre;
    }

    private String urlCalendario(int anio, int mes, LocalDate dia) {
        StringBuilder url = new StringBuilder("/calendario?anio=").append(anio).append("&mes=").append(mes);
        if (dia != null) {
            url.append("&dia=").append(dia);
        }
        return url.toString();
    }

    private String fechaRelativa(LocalDate fecha, LocalDate hoy) {
        if (fecha.equals(hoy)) {
            return "Hoy";
        }
        if (fecha.equals(hoy.plusDays(1))) {
            return "Mañana";
        }
        if (fecha.equals(hoy.minusDays(1))) {
            return "Ayer";
        }
        if (fecha.isBefore(hoy)) {
            long dias = ChronoUnit.DAYS.between(fecha, hoy);
            return "Vence: hace " + dias + (dias == 1 ? " día" : " días");
        }
        return DIAS_CORTOS.get(fecha.getDayOfWeek().getValue()) + " " +
                String.format("%02d/%02d", fecha.getDayOfMonth(), fecha.getMonthValue());
    }

    private Map<String, Object> mapaActividad(Actividad e, Map<Long, String> materiaNombres,
                                              Map<Long, String> materiaColores,
                                              Map<Long, String> materiaIconos, LocalDate hoy) {
        Map<String, Object> actividad = new HashMap<>();
        actividad.put("id", e.getId());
        actividad.put("titulo", e.getTitulo());
        actividad.put("materiaNombre", materiaNombres.getOrDefault(e.getMateriaId(), ""));
        actividad.put("materiaColor", materiaColores.getOrDefault(e.getMateriaId(), "#192584"));
        actividad.put("materiaIcono", materiaIconos.getOrDefault(e.getMateriaId(), "bi-book"));
        actividad.put("tipoCapitalizado", TIPOS.getOrDefault(e.getTipo(), e.getTipo()));
        actividad.put("fechaRelativa", fechaRelativa(e.getFecha(), hoy));
        actividad.put("estado", e.getEstado());
        actividad.put("vencida", e.getFecha().isBefore(hoy) && !"completada".equals(e.getEstado()));
        actividad.put("esHoy", e.getFecha().equals(hoy));
        return actividad;
    }

    @GetMapping
    public String listar(@RequestParam(required = false) Integer anio,
                         @RequestParam(required = false) Integer mes,
                         @RequestParam(required = false) LocalDate dia,
                         HttpSession session, Model model, RedirectAttributes redirectAttributes) {

        Long usuarioId = usuarioLogueado(session);
        Semestre semestre = semestreActivoDelUsuario(usuarioId);

        if (semestre == null) {
            redirectAttributes.addFlashAttribute("error", "No hay un semestre activo. Selecciona uno.");
            return "redirect:/semestres";
        }

        LocalDate hoy = LocalDate.now();
        int anioVista = anio != null && anio >= 1900 && anio <= 9999 ? anio : hoy.getYear();
        int mesVista = mes != null && mes >= 1 && mes <= 12 ? mes : hoy.getMonthValue();
        YearMonth yearMonth = YearMonth.of(anioVista, mesVista);

        // Entrada directa (sin parámetros): preseleccionar hoy para mostrar sus actividades al tiro
        LocalDate diaVista = dia;
        if (anio == null && mes == null && dia == null) {
            diaVista = hoy;
        }

        List<Materia> materias = materiaRepository.findBySemestreId(semestre.getId());
        Map<Long, String> materiaNombres = new HashMap<>();
        Map<Long, String> materiaColores = new HashMap<>();
        Map<Long, String> materiaIconos = new HashMap<>();
        for (Materia materia : materias) {
            materiaNombres.put(materia.getId(), materia.getNombre());
            materiaColores.put(materia.getId(), materia.getColor());
            materiaIconos.put(materia.getId(), materia.getIcono());
        }

        // Conteo de actividades por día para los puntos de la grilla (todas las del mes, sin filtro)
        Map<LocalDate, Integer> cantidadPorDia = new HashMap<>();
        List<Actividad> actividadesMes = List.of();
        if (!materias.isEmpty()) {
            List<Long> materiaIds = materias.stream().map(Materia::getId).toList();
            actividadesMes = actividadRepository.findByMateriaIdInOrderByFechaAsc(materiaIds);
            for (Actividad e : actividadesMes) {
                cantidadPorDia.merge(e.getFecha(), 1, Integer::sum);
            }
        }

        // Celdas del mes (null = celda vacía para alinear la grilla)
        List<Map<String, Object>> dias = new ArrayList<>();
        for (int i = 0; i < yearMonth.atDay(1).getDayOfWeek().getValue() - 1; i++) {
            dias.add(null);
        }
        for (int d = 1; d <= yearMonth.lengthOfMonth(); d++) {
            LocalDate fecha = yearMonth.atDay(d);
            int cantidad = cantidadPorDia.getOrDefault(fecha, 0);
            boolean selected = fecha.equals(diaVista);

            // Chips por materia para la grilla: una cápsula por asignatura (color + ícono) con su conteo
            Map<Long, Integer> actividadesPorMateria = new LinkedHashMap<>();
            for (Actividad e : actividadesMes) {
                if (e.getFecha().equals(fecha)) {
                    actividadesPorMateria.merge(e.getMateriaId(), 1, Integer::sum);
                }
            }
            List<Map<String, Object>> chips = new ArrayList<>();
            for (Map.Entry<Long, Integer> entry : actividadesPorMateria.entrySet()) {
                Map<String, Object> chip = new HashMap<>();
                chip.put("color", materiaColores.getOrDefault(entry.getKey(), "#192584"));
                chip.put("icono", materiaIconos.getOrDefault(entry.getKey(), "bi-book"));
                chip.put("nombre", materiaNombres.getOrDefault(entry.getKey(), ""));
                chip.put("cantidad", entry.getValue());
                chips.add(chip);
            }

            Map<String, Object> celda = new HashMap<>();
            celda.put("numero", d);
            celda.put("cantidad", cantidad);
            celda.put("chips", chips);
            celda.put("masChips", chips.size() > 3);
            celda.put("chipRestantes", chips.size() - 3);
            celda.put("esHoy", fecha.equals(hoy));
            celda.put("selected", selected);
            // Todos los días son seleccionables (con o sin actividades); volver a hacer clic limpia el día
            celda.put("href", urlCalendario(anioVista, mesVista, selected ? null : fecha));
            dias.add(celda);
        }
        while (dias.size() % 7 != 0) {
            dias.add(null);
        }

        Map<String, List<Map<String, Object>>> actividadesPorTipo = new LinkedHashMap<>();
        for (String tipo : TIPOS.keySet()) {
            actividadesPorTipo.put(TIPOS_PLURAL.get(tipo), new ArrayList<>());
        }
        if (diaVista != null) {
            List<Actividad> delDia = new ArrayList<>();
            for (Actividad e : actividadesMes) {
                if (e.getFecha().equals(diaVista)) {
                    delDia.add(e);
                }
            }
            // Dos pasadas por grupo: primero las no completadas (fecha asc), luego las completadas (fecha asc)
            for (String tipo : TIPOS.keySet()) {
                List<Map<String, Object>> grupo = actividadesPorTipo.get(TIPOS_PLURAL.get(tipo));
                for (Actividad e : delDia) {
                    if (tipo.equals(e.getTipo()) && !"completada".equals(e.getEstado())) {
                        grupo.add(mapaActividad(e, materiaNombres, materiaColores, materiaIconos, hoy));
                    }
                }
                for (Actividad e : delDia) {
                    if (tipo.equals(e.getTipo()) && "completada".equals(e.getEstado())) {
                        grupo.add(mapaActividad(e, materiaNombres, materiaColores, materiaIconos, hoy));
                    }
                }
            }
        }

        boolean mostrarLista = diaVista != null;
        boolean hayActividades = actividadesPorTipo.values().stream().anyMatch(lista -> !lista.isEmpty());

        YearMonth anterior = yearMonth.minusMonths(1);
        YearMonth siguiente = yearMonth.plusMonths(1);

        model.addAttribute("semestre", semestre);
        model.addAttribute("dias", dias);
        model.addAttribute("mostrarLista", mostrarLista);
        model.addAttribute("diaSeleccionado", diaVista);
        model.addAttribute("hayActividades", hayActividades);
        model.addAttribute("actividadesPorTipo", actividadesPorTipo);
        model.addAttribute("mesNombre", MESES.get(mesVista) + " " + anioVista);
        model.addAttribute("anio", anioVista);
        model.addAttribute("mes", mesVista);
        model.addAttribute("hrefAnterior", urlCalendario(anterior.getYear(), anterior.getMonthValue(), null));
        model.addAttribute("hrefSiguiente", urlCalendario(siguiente.getYear(), siguiente.getMonthValue(), null));
        model.addAttribute("hrefNuevo", diaVista != null && !diaVista.isBefore(hoy) ? "/calendario/nuevo?dia=" + diaVista : "/calendario/nuevo");
        return "calendario";
    }

    @GetMapping("/nuevo")
    public String nuevo(@RequestParam(required = false) LocalDate dia,
                        HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Long usuarioId = usuarioLogueado(session);
        Semestre semestre = semestreActivoDelUsuario(usuarioId);

        if (semestre == null) {
            redirectAttributes.addFlashAttribute("error", "No hay un semestre activo. Selecciona uno.");
            return "redirect:/semestres";
        }

        List<Materia> materias = materiaRepository.findBySemestreId(semestre.getId());
        if (materias.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Primero agrega materias al semestre.");
            return "redirect:/calendario";
        }

        Actividad actividad = new Actividad();
        actividad.setTipo("tarea");
        if (dia != null && !dia.isBefore(LocalDate.now())) {
            actividad.setFecha(dia);
        }

        model.addAttribute("actividad", actividad);
        model.addAttribute("materias", materias);
        model.addAttribute("tipos", TIPOS);
        model.addAttribute("fechaMin", LocalDate.now());
        model.addAttribute("titulo", "Nueva actividad");
        model.addAttribute("action", "/calendario");
        return "actividad-form";
    }

    private String validar(String titulo, String tipo, Long materiaId, LocalDate fecha, Long usuarioId) {
        if (titulo == null || titulo.trim().isEmpty()) {
            return "El título de la actividad no puede estar vacío.";
        }
        if (tipo == null || !TIPOS.containsKey(tipo)) {
            return "Tipo de actividad inválido.";
        }
        if (fecha == null) {
            return "La fecha es obligatoria.";
        }
        if (fecha.isBefore(LocalDate.now())) {
            return "La fecha no puede ser anterior a hoy.";
        }
        Materia materia = materiaRepository.findById(materiaId).orElse(null);
        if (materia == null || semestreDelUsuario(materia.getSemestreId(), usuarioId) == null) {
            return "Materia no encontrada.";
        }
        return null;
    }

    private String estadoNormalizado(String estado) {
        String valor = estado == null || estado.isBlank() ? "pendiente" : estado;
        return ESTADOS.containsKey(valor) ? valor : null;
    }

    @PostMapping
    public String guardar(@RequestParam String titulo,
                          @RequestParam String tipo,
                          @RequestParam Long materiaId,
                          @RequestParam(required = false) LocalDate fecha,
                          @RequestParam(required = false) String estado,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {

        Long usuarioId = usuarioLogueado(session);
        String error = validar(titulo, tipo, materiaId, fecha, usuarioId);
        if (error != null) {
            redirectAttributes.addFlashAttribute("error", error);
            return "redirect:/calendario/nuevo";
        }
        String estadoFinal = estadoNormalizado(estado);
        if (estadoFinal == null) {
            redirectAttributes.addFlashAttribute("error", "Estado inválido.");
            return "redirect:/calendario/nuevo";
        }

        Actividad actividad = new Actividad();
        actividad.setTitulo(titulo.trim());
        actividad.setTipo(tipo);
        actividad.setMateriaId(materiaId);
        actividad.setFecha(fecha);
        actividad.setEstado(estadoFinal);
        actividadRepository.save(actividad);

        redirectAttributes.addFlashAttribute("success", "Actividad creada correctamente.");
        return "redirect:" + urlCalendario(fecha.getYear(), fecha.getMonthValue(), fecha);
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id,
                         HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Long usuarioId = usuarioLogueado(session);
        Actividad actividad = actividadRepository.findById(id).orElse(null);

        if (actividad == null) {
            redirectAttributes.addFlashAttribute("error", "Actividad no encontrada.");
            return "redirect:/calendario";
        }

        Materia materia = materiaRepository.findById(actividad.getMateriaId()).orElse(null);
        Semestre semestre = semestreDelUsuario(materia != null ? materia.getSemestreId() : null, usuarioId);
        if (semestre == null) {
            redirectAttributes.addFlashAttribute("error", "No tienes permiso para esta actividad.");
            return "redirect:/calendario";
        }

        model.addAttribute("actividad", actividad);
        model.addAttribute("materias", materiaRepository.findBySemestreId(semestre.getId()));
        model.addAttribute("tipos", TIPOS);
        model.addAttribute("fechaMin", LocalDate.now());
        model.addAttribute("titulo", "Editar actividad");
        model.addAttribute("action", "/calendario/" + id + "/actualizar");
        return "actividad-form";
    }

    @PostMapping("/{id}/actualizar")
    public String actualizar(@PathVariable Long id,
                             @RequestParam String titulo,
                             @RequestParam String tipo,
                             @RequestParam Long materiaId,
                             @RequestParam(required = false) LocalDate fecha,
                             @RequestParam(required = false) String estado,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {

        Long usuarioId = usuarioLogueado(session);
        Actividad actividad = actividadRepository.findById(id).orElse(null);

        if (actividad == null) {
            redirectAttributes.addFlashAttribute("error", "Actividad no encontrada.");
            return "redirect:/calendario";
        }

        Materia materiaActual = materiaRepository.findById(actividad.getMateriaId()).orElse(null);
        if (semestreDelUsuario(materiaActual != null ? materiaActual.getSemestreId() : null, usuarioId) == null) {
            redirectAttributes.addFlashAttribute("error", "No tienes permiso para esta actividad.");
            return "redirect:/calendario";
        }

        String error = validar(titulo, tipo, materiaId, fecha, usuarioId);
        if (error != null) {
            redirectAttributes.addFlashAttribute("error", error);
            return "redirect:/calendario/" + id + "/editar";
        }
        String estadoFinal = estadoNormalizado(estado);
        if (estadoFinal == null) {
            redirectAttributes.addFlashAttribute("error", "Estado inválido.");
            return "redirect:/calendario/" + id + "/editar";
        }

        actividad.setTitulo(titulo.trim());
        actividad.setTipo(tipo);
        actividad.setMateriaId(materiaId);
        actividad.setFecha(fecha);
        actividad.setEstado(estadoFinal);
        actividadRepository.save(actividad);

        redirectAttributes.addFlashAttribute("success", "Actividad actualizada correctamente.");
        return "redirect:" + urlCalendario(fecha.getYear(), fecha.getMonthValue(), fecha);
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        Long usuarioId = usuarioLogueado(session);
        Actividad actividad = actividadRepository.findById(id).orElse(null);

        if (actividad == null) {
            redirectAttributes.addFlashAttribute("error", "Actividad no encontrada.");
            return "redirect:/calendario";
        }

        Materia materia = materiaRepository.findById(actividad.getMateriaId()).orElse(null);
        if (semestreDelUsuario(materia != null ? materia.getSemestreId() : null, usuarioId) == null) {
            redirectAttributes.addFlashAttribute("error", "No tienes permiso para esta actividad.");
            return "redirect:/calendario";
        }

        actividadRepository.delete(actividad);
        redirectAttributes.addFlashAttribute("success", "Actividad eliminada correctamente.");
        return "redirect:/calendario";
    }

    @PostMapping("/{id}/estado")
    public String cambiarEstado(@PathVariable Long id,
                                @RequestParam String estado,
                                @RequestParam(required = false) Integer anio,
                                @RequestParam(required = false) Integer mes,
                                @RequestParam(required = false) LocalDate dia,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {

        Long usuarioId = usuarioLogueado(session);
        Actividad actividad = actividadRepository.findById(id).orElse(null);

        if (actividad == null) {
            redirectAttributes.addFlashAttribute("error", "Actividad no encontrada.");
            return "redirect:/calendario";
        }

        Materia materia = materiaRepository.findById(actividad.getMateriaId()).orElse(null);
        if (semestreDelUsuario(materia != null ? materia.getSemestreId() : null, usuarioId) == null) {
            redirectAttributes.addFlashAttribute("error", "No tienes permiso para esta actividad.");
            return "redirect:/calendario";
        }

        if (!ESTADOS.containsKey(estado)) {
            redirectAttributes.addFlashAttribute("error", "Estado inválido.");
            return "redirect:/calendario";
        }

        actividad.setEstado(estado);
        actividadRepository.save(actividad);

        int anioRedir = anio != null && anio >= 1900 && anio <= 9999 ? anio : LocalDate.now().getYear();
        int mesRedir = mes != null && mes >= 1 && mes <= 12 ? mes : LocalDate.now().getMonthValue();
        return "redirect:" + urlCalendario(anioRedir, mesRedir, dia);
    }
}