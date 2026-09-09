package cl.grupo4.uniday.controller;

import cl.grupo4.uniday.config.LoginInterceptor;
import cl.grupo4.uniday.model.Asistencia;
import cl.grupo4.uniday.model.DiaNoClase;
import cl.grupo4.uniday.model.Horario;
import cl.grupo4.uniday.model.Materia;
import cl.grupo4.uniday.model.Semestre;
import cl.grupo4.uniday.repository.AsistenciaRepository;
import cl.grupo4.uniday.repository.HorarioRepository;
import cl.grupo4.uniday.repository.DiaNoClaseRepository;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/asistencia")
public class AsistenciaController {

    private final AsistenciaRepository asistenciaRepository;
    private final MateriaRepository materiaRepository;
    private final HorarioRepository horarioRepository;
    private final SemestreRepository semestreRepository;
    private final DiaNoClaseRepository diaNoClaseRepository;

    public AsistenciaController(AsistenciaRepository asistenciaRepository,
                                MateriaRepository materiaRepository,
                                HorarioRepository horarioRepository,
                                SemestreRepository semestreRepository,
                                DiaNoClaseRepository diaNoClaseRepository) {
        this.asistenciaRepository = asistenciaRepository;
        this.materiaRepository = materiaRepository;
        this.horarioRepository = horarioRepository;
        this.semestreRepository = semestreRepository;
        this.diaNoClaseRepository = diaNoClaseRepository;
    }

    @GetMapping
    public String listar(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Long usuarioId = (Long) session.getAttribute(LoginInterceptor.SESSION_USUARIO_ID);
        Semestre semestre = semestreRepository.findByUsuarioIdAndActivoTrue(usuarioId).orElse(null);
        if (semestre == null) {
            redirectAttributes.addFlashAttribute("error", "No hay un semestre activo. Selecciona uno.");
            return "redirect:/semestres";
        }

        List<Materia> materias = materiaRepository.findBySemestreId(semestre.getId());
        List<Long> materiaIds = materias.stream().map(Materia::getId).toList();
        List<Asistencia> asistencias = materiaIds.isEmpty()
                ? List.of()
                : asistenciaRepository.findByMateriaIdInOrderByFechaDesc(materiaIds);
        List<Horario> horarios = materiaIds.isEmpty()
            ? List.of()
            : horarioRepository.findByMateriaIdInOrderByDiaSemanaAscHoraInicioAsc(materiaIds);
        List<DiaNoClase> diasNoClase = diaNoClaseRepository.findBySemestreIdOrderByFechaInicioAsc(semestre.getId());

        Map<Long, List<Asistencia>> asistenciasPorMateria = new LinkedHashMap<>();
        Map<Long, Double> porcentajes = new LinkedHashMap<>();
        Map<Long, Map<String, Object>> resumenes = new LinkedHashMap<>();
        for (Materia materia : materias) {
            asistenciasPorMateria.put(materia.getId(), new ArrayList<>());
            porcentajes.put(materia.getId(), 0.0);
            resumenes.put(materia.getId(), new LinkedHashMap<>());
        }
        for (Asistencia asistencia : asistencias) {
            asistenciasPorMateria.get(asistencia.getMateriaId()).add(asistencia);
        }
        for (Materia materia : materias) {
            List<Asistencia> registros = asistenciasPorMateria.get(materia.getId());
            long presentes = registros.stream().filter(Asistencia::isPresente).count();
            double porcentaje = registros.isEmpty() ? 0.0 : presentes * 100.0 / registros.size();
            porcentajes.put(materia.getId(), porcentaje);

                Map<String, Object> resumen = resumenes.get(materia.getId());
                List<Integer> dias = horarios.stream()
                    .filter(horario -> horario.getMateriaId().equals(materia.getId()))
                    .map(Horario::getDiaSemana)
                    .distinct()
                    .toList();
                    List<LocalDate> clasesPlanificadas = fechasDeClase(materia, dias, diasNoClase);
                int faltasMaximas = (int) Math.floor(clasesPlanificadas.size()
                    * (100 - materia.getAsistenciaExigida()) / 100.0);
                int faltasRegistradas = (int) registros.stream().filter(asistencia -> !asistencia.isPresente()).count();
                int faltasRestantes = Math.max(0, faltasMaximas - faltasRegistradas);
                resumen.put("totalClases", clasesPlanificadas.size());
                resumen.put("faltasMaximas", faltasMaximas);
                resumen.put("faltasRegistradas", faltasRegistradas);
                resumen.put("faltasRestantes", faltasRestantes);
                resumen.put("ultimaFechaParaFaltar", ultimaFechaPermitida(clasesPlanificadas, registros, faltasRestantes));
        }

        model.addAttribute("semestre", semestre);
        model.addAttribute("materias", materias);
        model.addAttribute("asistenciasPorMateria", asistenciasPorMateria);
        model.addAttribute("porcentajes", porcentajes);
        model.addAttribute("resumenes", resumenes);
        model.addAttribute("diasNoClase", diasNoClase);
        model.addAttribute("hoy", LocalDate.now());
        return "asistencia";
    }

    @PostMapping
    public String guardar(@RequestParam Long materiaId,
                          @RequestParam LocalDate fecha,
                          @RequestParam(defaultValue = "false") boolean presente,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {
        Long usuarioId = (Long) session.getAttribute(LoginInterceptor.SESSION_USUARIO_ID);
        Materia materia = materiaRepository.findById(materiaId).orElse(null);
        if (materia == null || semestreRepository.findById(materia.getSemestreId())
                .filter(semestre -> semestre.getUsuarioId().equals(usuarioId)).isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "No tienes permiso para esta materia.");
            return "redirect:/asistencia";
        }
        if ((materia.getFechaInicioClases() != null && fecha.isBefore(materia.getFechaInicioClases()))
                || (materia.getFechaFinClases() != null && fecha.isAfter(materia.getFechaFinClases()))) {
            redirectAttributes.addFlashAttribute("error", "La fecha debe estar dentro del período de clases de la materia.");
            return "redirect:/asistencia";
        }
        if (diaNoClaseRepository.findBySemestreIdOrderByFechaInicioAsc(materia.getSemestreId()).stream()
                .anyMatch(dia -> !fecha.isBefore(dia.getFechaInicio()) && !fecha.isAfter(dia.getFechaFin()))) {
            redirectAttributes.addFlashAttribute("error", "Esa fecha está marcada como feriado o receso.");
            return "redirect:/asistencia";
        }

        Asistencia asistencia = asistenciaRepository.findByMateriaIdAndFecha(materiaId, fecha)
                .orElseGet(Asistencia::new);
        asistencia.setMateriaId(materiaId);
        asistencia.setFecha(fecha);
        asistencia.setPresente(presente);
        asistenciaRepository.save(asistencia);

        redirectAttributes.addFlashAttribute("success", "Asistencia guardada correctamente.");
        return "redirect:/asistencia";
    }

    @PostMapping("/no-clases")
    public String guardarDiaNoClase(@RequestParam LocalDate fechaInicio,
                                    @RequestParam LocalDate fechaFin,
                                    @RequestParam(required = false) String motivo,
                                    HttpSession session,
                                    RedirectAttributes redirectAttributes) {
        Long usuarioId = (Long) session.getAttribute(LoginInterceptor.SESSION_USUARIO_ID);
        Semestre semestre = semestreRepository.findByUsuarioIdAndActivoTrue(usuarioId).orElse(null);
        if (semestre == null) {
            redirectAttributes.addFlashAttribute("error", "No hay un semestre activo.");
            return "redirect:/semestres";
        }
        if (fechaFin.isBefore(fechaInicio)) {
            redirectAttributes.addFlashAttribute("error", "La fecha final no puede ser anterior a la inicial.");
            return "redirect:/asistencia";
        }
        DiaNoClase dia = new DiaNoClase();
        dia.setSemestreId(semestre.getId());
        dia.setFechaInicio(fechaInicio);
        dia.setFechaFin(fechaFin);
        dia.setMotivo(motivo == null || motivo.isBlank() ? "Sin clases" : motivo.trim());
        diaNoClaseRepository.save(dia);
        redirectAttributes.addFlashAttribute("success", "Período sin clases guardado.");
        return "redirect:/asistencia";
    }

    @PostMapping("/no-clases/{id}/eliminar")
    public String eliminarDiaNoClase(@PathVariable Long id,
                                    HttpSession session,
                                    RedirectAttributes redirectAttributes) {
        Long usuarioId = (Long) session.getAttribute(LoginInterceptor.SESSION_USUARIO_ID);
        DiaNoClase dia = diaNoClaseRepository.findById(id).orElse(null);
        if (dia == null || semestreRepository.findById(dia.getSemestreId())
                .filter(semestre -> semestre.getUsuarioId().equals(usuarioId)).isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Período sin clases no encontrado.");
            return "redirect:/asistencia";
        }
        diaNoClaseRepository.delete(dia);
        redirectAttributes.addFlashAttribute("success", "Período sin clases eliminado.");
        return "redirect:/asistencia";
    }

    private List<LocalDate> fechasDeClase(Materia materia, List<Integer> dias, List<DiaNoClase> diasNoClase) {
        if (materia.getFechaInicioClases() == null || materia.getFechaFinClases() == null || dias.isEmpty()) {
            return List.of();
        }
        List<LocalDate> fechas = new ArrayList<>();
        for (LocalDate fecha = materia.getFechaInicioClases();
             !fecha.isAfter(materia.getFechaFinClases()); fecha = fecha.plusDays(1)) {
            LocalDate fechaActual = fecha;
                boolean esDiaSinClase = diasNoClase.stream()
                .anyMatch(dia -> !fechaActual.isBefore(dia.getFechaInicio()) && !fechaActual.isAfter(dia.getFechaFin()));
                if (dias.contains(fecha.getDayOfWeek().getValue()) && !esDiaSinClase) {
                fechas.add(fecha);
            }
        }
        return fechas;
    }

    private LocalDate ultimaFechaPermitida(List<LocalDate> clasesPlanificadas,
                                           List<Asistencia> registros,
                                           int faltasRestantes) {
        if (faltasRestantes == 0) {
            return null;
        }
        Map<LocalDate, Asistencia> registrosPorFecha = new HashMap<>();
        for (Asistencia registro : registros) {
            registrosPorFecha.put(registro.getFecha(), registro);
        }
        int disponibles = faltasRestantes;
        LocalDate ultima = null;
        for (LocalDate fecha : clasesPlanificadas) {
            if (!fecha.isBefore(LocalDate.now()) && !registrosPorFecha.containsKey(fecha)) {
                ultima = fecha;
                disponibles--;
                if (disponibles == 0) {
                    break;
                }
            }
        }
        return ultima;
    }
}
