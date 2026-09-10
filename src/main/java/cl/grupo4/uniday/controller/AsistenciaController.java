package cl.grupo4.uniday.controller;

import cl.grupo4.uniday.config.LoginInterceptor;
import cl.grupo4.uniday.model.Asistencia;
import cl.grupo4.uniday.model.Horario;
import cl.grupo4.uniday.model.Materia;
import cl.grupo4.uniday.model.Semestre;
import cl.grupo4.uniday.repository.AsistenciaRepository;
import cl.grupo4.uniday.repository.HorarioRepository;
import cl.grupo4.uniday.repository.MateriaRepository;
import cl.grupo4.uniday.repository.SemestreRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/asistencia")
public class AsistenciaController {

    // 1=Lunes ... 7=Domingo (misma convención que HorarioController)
    private static final String[] DIAS_SEMANA = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};

    private final AsistenciaRepository asistenciaRepository;
    private final MateriaRepository materiaRepository;
    private final HorarioRepository horarioRepository;
    private final SemestreRepository semestreRepository;

    public AsistenciaController(AsistenciaRepository asistenciaRepository,
                                MateriaRepository materiaRepository,
                                HorarioRepository horarioRepository,
                                SemestreRepository semestreRepository) {
        this.asistenciaRepository = asistenciaRepository;
        this.materiaRepository = materiaRepository;
        this.horarioRepository = horarioRepository;
        this.semestreRepository = semestreRepository;
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
        LocalDate hoy = LocalDate.now();

        Map<Long, List<Asistencia>> registrosMateria = new LinkedHashMap<>();
        for (Materia materia : materias) {
            registrosMateria.put(materia.getId(), new ArrayList<>());
        }
        for (Asistencia asistencia : asistencias) {
            registrosMateria.get(asistencia.getMateriaId()).add(asistencia);
        }

        Map<Long, Double> porcentajes = new LinkedHashMap<>();
        Map<Long, Map<String, Object>> resumenes = new LinkedHashMap<>();
        Map<Long, List<Map<String, Object>>> clasesPorMateria = new LinkedHashMap<>();
        for (Materia materia : materias) {
            Map<LocalDate, Asistencia> registrosPorFecha = new HashMap<>();
            for (Asistencia registro : registrosMateria.get(materia.getId())) {
                registrosPorFecha.put(registro.getFecha(), registro);
            }

            Map<String, Object> calculo = calcularMateria(materia, semestre, horarios, registrosPorFecha, hoy);
            porcentajes.put(materia.getId(), (Double) calculo.get("porcentaje"));
            resumenes.put(materia.getId(), calculo);
            clasesPorMateria.put(materia.getId(), clasesDe(calculo));
        }

        model.addAttribute("semestre", semestre);
        model.addAttribute("materias", materias);
        model.addAttribute("porcentajes", porcentajes);
        model.addAttribute("resumenes", resumenes);
        model.addAttribute("clasesPorMateria", clasesPorMateria);
        model.addAttribute("hoy", hoy);
        return "asistencia";
    }

    @PostMapping
    public String guardar(@RequestParam Long materiaId,
                          @RequestParam LocalDate fecha,
                          @RequestParam(defaultValue = "presente") String accion,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {
        Map<String, Object> resultado = marcar(materiaId, fecha, accion, session);
        if (resultado.containsKey("error")) {
            redirectAttributes.addFlashAttribute("error", resultado.get("error"));
        } else {
            redirectAttributes.addFlashAttribute("success", "Asistencia guardada correctamente.");
        }
        return "redirect:/asistencia";
    }

    @PostMapping("/marcar")
    @ResponseBody
    public Map<String, Object> marcar(@RequestParam Long materiaId,
                                      @RequestParam LocalDate fecha,
                                      @RequestParam(defaultValue = "presente") String accion,
                                      HttpSession session) {
        Map<String, Object> respuesta = new LinkedHashMap<>();
        Long usuarioId = (Long) session.getAttribute(LoginInterceptor.SESSION_USUARIO_ID);
        Materia materia = materiaRepository.findById(materiaId).orElse(null);
        Semestre semestre = materia == null ? null : semestreRepository.findById(materia.getSemestreId()).orElse(null);
        if (materia == null || semestre == null || !semestre.getUsuarioId().equals(usuarioId)) {
            respuesta.put("error", "No tienes permiso para esta materia.");
            return respuesta;
        }
        if (semestre.getFechaInicio() == null || semestre.getFechaFin() == null) {
            respuesta.put("error", "Define el período del semestre antes de registrar asistencia.");
            return respuesta;
        }
        if (!"presente".equals(accion) && !"ausente".equals(accion) && !"no_hubo".equals(accion) && !"limpiar".equals(accion)) {
            respuesta.put("error", "Acción inválida.");
            return respuesta;
        }

        if ("presente".equals(accion) || "ausente".equals(accion) || "no_hubo".equals(accion)) {
            List<Horario> horariosMateria = horarioRepository.findByMateriaIdInOrderByDiaSemanaAscHoraInicioAsc(List.of(materiaId));
            Set<Integer> dias = horariosMateria.stream().map(Horario::getDiaSemana).collect(Collectors.toSet());
            boolean enRangoDelSemestre = !fecha.isBefore(semestre.getFechaInicio()) && !fecha.isAfter(semestre.getFechaFin());
            boolean enDiaDeClase = dias.contains(fecha.getDayOfWeek().getValue());
            if (!enRangoDelSemestre || !enDiaDeClase) {
                respuesta.put("error", "Esa fecha no es una clase según el horario.");
                return respuesta;
            }
        }

        if ("limpiar".equals(accion)) {
            asistenciaRepository.findByMateriaIdAndFecha(materiaId, fecha)
                    .ifPresent(asistenciaRepository::delete);
        } else {
            Asistencia asistencia = asistenciaRepository.findByMateriaIdAndFecha(materiaId, fecha)
                    .orElseGet(Asistencia::new);
            asistencia.setMateriaId(materiaId);
            asistencia.setFecha(fecha);
            if ("no_hubo".equals(accion)) {
                asistencia.setNoHubo(true);
                asistencia.setPresente(false);
            } else {
                asistencia.setNoHubo(false);
                asistencia.setPresente("presente".equals(accion));
            }
            asistenciaRepository.save(asistencia);
        }

        List<Horario> horarios = horarioRepository.findByMateriaIdInOrderByDiaSemanaAscHoraInicioAsc(List.of(materiaId));
        Map<LocalDate, Asistencia> registrosPorFecha = new HashMap<>();
        for (Asistencia registro : asistenciaRepository.findByMateriaIdInOrderByFechaDesc(List.of(materiaId))) {
            registrosPorFecha.put(registro.getFecha(), registro);
        }
        Map<String, Object> calculo = calcularMateria(materia, semestre, horarios, registrosPorFecha, LocalDate.now());
        respuesta.put("ok", true);
        respuesta.put("resumen", calculo);
        return respuesta;
    }

    private List<LocalDate> clasesBrutas(Semestre semestre, Materia materia, List<Horario> horarios) {
        if (semestre == null || semestre.getFechaInicio() == null || semestre.getFechaFin() == null) {
            return List.of();
        }
        Set<Integer> dias = horarios.stream()
                .filter(horario -> horario.getMateriaId().equals(materia.getId()))
                .map(Horario::getDiaSemana)
                .collect(Collectors.toSet());
        if (dias.isEmpty()) {
            return List.of();
        }
        List<LocalDate> fechas = new ArrayList<>();
        for (LocalDate fecha = semestre.getFechaInicio();
             !fecha.isAfter(semestre.getFechaFin()); fecha = fecha.plusDays(1)) {
            if (dias.contains(fecha.getDayOfWeek().getValue())) {
                fechas.add(fecha);
            }
        }
        return fechas;
    }

    private Map<String, Object> calcularMateria(Materia materia, Semestre semestre,
                                                List<Horario> horarios,
                                                Map<LocalDate, Asistencia> registrosPorFecha,
                                                LocalDate hoy) {
        List<LocalDate> brutas = clasesBrutas(semestre, materia, horarios);
        Set<LocalDate> descontadasFechas = registrosPorFecha.entrySet().stream()
                .filter(entrada -> Boolean.TRUE.equals(entrada.getValue().getNoHubo()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        List<LocalDate> planificadas = brutas.stream()
                .filter(fecha -> !descontadasFechas.contains(fecha))
                .toList();

        long totalClases = planificadas.size();
        long planificadasBrutas = brutas.size();
        long descontadas = descontadasFechas.size();
        long asistidas = planificadas.stream()
                .filter(fecha -> {
                    Asistencia registro = registrosPorFecha.get(fecha);
                    return registro != null && registro.isPresente();
                }).count();
        long ausentes = planificadas.stream()
                .filter(fecha -> {
                    Asistencia registro = registrosPorFecha.get(fecha);
                    return registro != null && !registro.isPresente() && !Boolean.TRUE.equals(registro.getNoHubo());
                }).count();
        long pendientes = totalClases - asistidas - ausentes;
        long pasadas = planificadas.stream().filter(fecha -> !fecha.isAfter(hoy)).count();
        double porcentaje = totalClases == 0 ? 0.0 : asistidas * 100.0 / totalClases;

        long faltasMaximas = Math.max(0,
                (long) Math.floor(totalClases * (100.0 - materia.getAsistenciaExigida()) / 100.0));
        long faltasRestantes = Math.max(0, faltasMaximas - ausentes);

        List<LocalDate> proximasClases = new ArrayList<>();
        for (LocalDate fecha : planificadas) {
            if (fecha.isAfter(hoy) && !registrosPorFecha.containsKey(fecha)) {
                proximasClases.add(fecha);
                if (proximasClases.size() == 3) {
                    break;
                }
            }
        }

        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("totalClases", totalClases);
        resumen.put("planificadas", planificadasBrutas);
        resumen.put("descontadas", descontadas);
        resumen.put("asistidas", asistidas);
        resumen.put("ausentes", ausentes);
        resumen.put("pendientes", pendientes);
        resumen.put("pasadas", pasadas);
        resumen.put("porcentaje", porcentaje);
        resumen.put("faltasMaximas", faltasMaximas);
        resumen.put("faltasRegistradas", ausentes);
        resumen.put("faltasRestantes", faltasRestantes);
        resumen.put("exigida", materia.getAsistenciaExigida());
        resumen.put("ultimaFechaParaFaltar", ultimaFechaPermitida(planificadas, registrosPorFecha, faltasRestantes, hoy));
        resumen.put("proximasClases", proximasClases);

        List<Map<String, Object>> listaClases = new ArrayList<>();
        int numeroClase = 1;
        for (LocalDate fecha : brutas) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("claseNumero", numeroClase++);
            item.put("fecha", fecha);
            item.put("diaNombre", DIAS_SEMANA[fecha.getDayOfWeek().getValue() - 1]);
            Asistencia registro = registrosPorFecha.get(fecha);
            String estado = null;
            if (registro != null) {
                estado = Boolean.TRUE.equals(registro.getNoHubo()) ? "no_hubo"
                        : (registro.isPresente() ? "presente" : "ausente");
            }
            item.put("estado", estado);
            item.put("pasada", !fecha.isAfter(hoy));
            listaClases.add(item);
        }
        resumen.put("clases", listaClases);
        return resumen;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> clasesDe(Map<String, Object> calculo) {
        return (List<Map<String, Object>>) calculo.get("clases");
    }

    private LocalDate ultimaFechaPermitida(List<LocalDate> clasesPlanificadas,
                                           Map<LocalDate, Asistencia> registrosPorFecha,
                                           long faltasRestantes,
                                           LocalDate hoy) {
        if (faltasRestantes == 0) {
            return null;
        }
        long disponibles = faltasRestantes;
        LocalDate ultima = null;
        for (LocalDate fecha : clasesPlanificadas) {
            if (!fecha.isBefore(hoy) && !registrosPorFecha.containsKey(fecha)) {
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