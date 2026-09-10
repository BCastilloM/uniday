package cl.grupo4.uniday.controller;

import cl.grupo4.uniday.config.LoginInterceptor;
import cl.grupo4.uniday.model.Horario;
import cl.grupo4.uniday.model.Materia;
import cl.grupo4.uniday.model.Semestre;
import cl.grupo4.uniday.repository.HorarioRepository;
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

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/horarios")
public class HorarioController {

    // 1=Lunes ... 7=Domingo (LinkedHashMap para que el selector muestre el orden de la semana)
    private static final Map<Integer, String> DIAS;

    static {
        DIAS = new LinkedHashMap<>();
        DIAS.put(1, "Lunes");
        DIAS.put(2, "Martes");
        DIAS.put(3, "Miércoles");
        DIAS.put(4, "Jueves");
        DIAS.put(5, "Viernes");
        DIAS.put(6, "Sábado");
        DIAS.put(7, "Domingo");
    }

    private final HorarioRepository horarioRepository;
    private final MateriaRepository materiaRepository;
    private final SemestreRepository semestreRepository;

    public HorarioController(HorarioRepository horarioRepository,
                             MateriaRepository materiaRepository,
                             SemestreRepository semestreRepository) {
        this.horarioRepository = horarioRepository;
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

    @GetMapping
    public String listar(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Long usuarioId = usuarioLogueado(session);
        Semestre semestre = semestreActivoDelUsuario(usuarioId);

        if (semestre == null) {
            redirectAttributes.addFlashAttribute("error", "No hay un semestre activo. Selecciona uno.");
            return "redirect:/semestres";
        }

        List<Materia> materias = materiaRepository.findBySemestreId(semestre.getId());
        List<Horario> horarios;
        Map<Long, String> materiaNombres = new HashMap<>();
        Map<Long, String> materiaColores = new HashMap<>();
        Map<Long, String> materiaIconos = new HashMap<>();
        Map<Long, String> materiaProfesores = new HashMap<>();
        for (Materia materia : materias) {
            materiaNombres.put(materia.getId(), materia.getNombre());
            materiaColores.put(materia.getId(), materia.getColor());
            materiaIconos.put(materia.getId(), materia.getIcono());
            materiaProfesores.put(materia.getId(), materia.getProfesor());
        }

        if (materias.isEmpty()) {
            horarios = List.of();
        } else {
            List<Long> materiaIds = materias.stream().map(Materia::getId).toList();
            horarios = horarioRepository.findByMateriaIdInOrderByDiaSemanaAscHoraInicioAsc(materiaIds);
        }

        Map<Integer, List<Horario>> bloquesPorDia = new LinkedHashMap<>();
        Map<Long, Integer> topPx = new HashMap<>();
        Map<Long, Integer> heightPx = new HashMap<>();
        Map<Long, String> leftPct = new HashMap<>();
        Map<Long, String> widthPct = new HashMap<>();
        List<String> horas = new ArrayList<>();
        int totalPx = 0;
        int rowH = 72;   // alto de una celda de hora (con aire)
        int inset = 4;   // aire interno del bloque (2px arriba y 2px abajo)

        if (!horarios.isEmpty()) {
            // Rango de horas visible (desde la primera clase hasta la última)
            int horaMin = 23, horaMax = 0;
            for (Horario h : horarios) {
                int ini = h.getHoraInicio().getHour();
                int fin = h.getHoraFin().getHour();
                if (h.getHoraFin().getMinute() > 0) fin++; // 10:30 ocupa hasta las 11
                horaMin = Math.min(horaMin, ini);
                horaMax = Math.max(horaMax, fin);
                bloquesPorDia.computeIfAbsent(h.getDiaSemana(), k -> new ArrayList<>()).add(h);
            }
            for (List<Horario> lista : bloquesPorDia.values()) {
                lista.sort(Comparator.comparing(Horario::getHoraInicio));
            }

            for (int h = horaMin; h < horaMax; h++) {
                horas.add(String.format("%02d:00", h));
            }
            totalPx = (horaMax - horaMin) * rowH;

            int base = horaMin * 60;
            for (Horario h : horarios) {
                int ini = aMinutos(h.getHoraInicio());
                int fin = aMinutos(h.getHoraFin());
                int dur = Math.max(fin - ini, 30);
                topPx.put(h.getId(), (ini - base) * rowH / 60 + inset / 2);
                heightPx.put(h.getId(), Math.max(dur * rowH / 60 - inset, 24));
            }
        }

        // Si dos o más horarios se solapan en el mismo día, se reparten lado a lado
        asignarColumnasSolapadas(bloquesPorDia, leftPct, widthPct);

        model.addAttribute("semestre", semestre);
        model.addAttribute("bloquesPorDia", bloquesPorDia);
        model.addAttribute("materiaNombres", materiaNombres);
        model.addAttribute("materiaColores", materiaColores);
        model.addAttribute("materiaIconos", materiaIconos);
        model.addAttribute("materiaProfesores", materiaProfesores);
        model.addAttribute("horas", horas);
        model.addAttribute("totalPx", totalPx);
        model.addAttribute("rowAltura", rowH);
        model.addAttribute("topPx", topPx);
        model.addAttribute("heightPx", heightPx);
        model.addAttribute("leftPct", leftPct);
        model.addAttribute("widthPct", widthPct);
        model.addAttribute("diasNombres", List.of("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"));
        return "horarios";
    }

    private int aMinutos(LocalTime t) {
        return t.getHour() * 60 + t.getMinute();
    }

    private void asignarColumnasSolapadas(Map<Integer, List<Horario>> bloquesPorDia,
                                          Map<Long, String> leftPct, Map<Long, String> widthPct) {
        for (Map.Entry<Integer, List<Horario>> entry : bloquesPorDia.entrySet()) {
            List<Horario> lista = entry.getValue();

            // Clusters: bloques conectados por solapamiento (cadena)
            List<Horario> cluster = new ArrayList<>();
            int clusterMaxFin = -1;
            for (Horario h : lista) {
                int ini = aMinutos(h.getHoraInicio());
                if (!cluster.isEmpty() && ini >= clusterMaxFin) {
                    asignarLanes(cluster, leftPct, widthPct);
                    cluster = new ArrayList<>();
                    clusterMaxFin = -1;
                }
                cluster.add(h);
                clusterMaxFin = Math.max(clusterMaxFin, aMinutos(h.getHoraFin()));
            }
            if (!cluster.isEmpty()) {
                asignarLanes(cluster, leftPct, widthPct);
            }
        }
    }

    private void asignarLanes(List<Horario> cluster, Map<Long, String> leftPct, Map<Long, String> widthPct) {
        int n = cluster.size();
        int[] finCarriles = new int[n];
        int[] carril = new int[n];
        int carriles = 0;

        for (int i = 0; i < n; i++) {
            Horario h = cluster.get(i);
            int ini = aMinutos(h.getHoraInicio());
            int fin = aMinutos(h.getHoraFin());
            int lane = -1; // primer carril libre para este bloque (first-fit)
            for (int l = 0; l < carriles; l++) {
                if (finCarriles[l] <= ini) {
                    lane = l;
                    break;
                }
            }
            if (lane == -1) {
                lane = carriles++;
            }
            finCarriles[lane] = Math.max(finCarriles[lane], fin);
            carril[i] = lane;
        }

        double ancho = 100.0 / carriles;
        for (int i = 0; i < n; i++) {
            leftPct.put(cluster.get(i).getId(), String.format(Locale.ROOT, "%.4f", carril[i] * ancho));
            widthPct.put(cluster.get(i).getId(), String.format(Locale.ROOT, "%.4f", ancho));
        }
    }

    @GetMapping("/nuevo")
    public String nuevo(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Long usuarioId = usuarioLogueado(session);
        Semestre semestre = semestreActivoDelUsuario(usuarioId);

        if (semestre == null) {
            redirectAttributes.addFlashAttribute("error", "No hay un semestre activo. Selecciona uno.");
            return "redirect:/semestres";
        }

        List<Materia> materias = materiaRepository.findBySemestreId(semestre.getId());
        if (materias.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Primero agrega materias al semestre.");
            return "redirect:/horarios";
        }

        model.addAttribute("horario", new Horario());
        model.addAttribute("materias", materias);
        model.addAttribute("dias", DIAS);
        model.addAttribute("titulo", "Nuevo horario");
        model.addAttribute("action", "/horarios");
        return "horario-form";
    }

    @PostMapping
    public String guardar(@RequestParam Long materiaId,
                          @RequestParam int diaSemana,
                          @RequestParam(required = false) LocalTime horaInicio,
                          @RequestParam(required = false) LocalTime horaFin,
                          @RequestParam(required = false) String sala,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {

        Long usuarioId = usuarioLogueado(session);
        return persistirHorario(new Horario(), materiaId, diaSemana, horaInicio, horaFin, sala,
                usuarioId, "/horarios/nuevo", redirectAttributes, "Horario creado correctamente.");
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Long usuarioId = usuarioLogueado(session);
        Horario horario = horarioRepository.findById(id).orElse(null);

        if (horario == null) {
            redirectAttributes.addFlashAttribute("error", "Horario no encontrado.");
            return "redirect:/semestres";
        }

        Materia materia = materiaRepository.findById(horario.getMateriaId()).orElse(null);
        Semestre semestreObj = semestreDelUsuario(materia != null ? materia.getSemestreId() : null, usuarioId);
        if (semestreObj == null) {
            redirectAttributes.addFlashAttribute("error", "No tienes permiso para este horario.");
            return "redirect:/semestres";
        }

        model.addAttribute("horario", horario);
        model.addAttribute("materias", materiaRepository.findBySemestreId(semestreObj.getId()));
        model.addAttribute("dias", DIAS);
        model.addAttribute("titulo", "Editar horario");
        model.addAttribute("action", "/horarios/" + id + "/actualizar");
        return "horario-form";
    }

    @PostMapping("/{id}/actualizar")
    public String actualizar(@PathVariable Long id,
                             @RequestParam Long materiaId,
                             @RequestParam int diaSemana,
                             @RequestParam(required = false) LocalTime horaInicio,
                             @RequestParam(required = false) LocalTime horaFin,
                             @RequestParam(required = false) String sala,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {

        Long usuarioId = usuarioLogueado(session);
        Horario horario = horarioRepository.findById(id).orElse(null);

        if (horario == null) {
            redirectAttributes.addFlashAttribute("error", "Horario no encontrado.");
            return "redirect:/semestres";
        }

        Materia materiaActual = materiaRepository.findById(horario.getMateriaId()).orElse(null);
        if (semestreDelUsuario(materiaActual != null ? materiaActual.getSemestreId() : null, usuarioId) == null) {
            redirectAttributes.addFlashAttribute("error", "No tienes permiso para este horario.");
            return "redirect:/semestres";
        }

        return persistirHorario(horario, materiaId, diaSemana, horaInicio, horaFin, sala,
                usuarioId, "/horarios/" + id + "/editar", redirectAttributes,
                "Horario actualizado correctamente.");
    }

    private String persistirHorario(Horario horario, Long materiaId, int diaSemana,
                                    LocalTime horaInicio, LocalTime horaFin, String sala,
                                    Long usuarioId, String paginaError,
                                    RedirectAttributes redirectAttributes, String mensajeOk) {
        Materia materia = materiaRepository.findById(materiaId).orElse(null);
        if (materia == null || semestreDelUsuario(materia.getSemestreId(), usuarioId) == null) {
            redirectAttributes.addFlashAttribute("error", "Materia no encontrada.");
            return "redirect:" + paginaError;
        }

        if (diaSemana < 1 || diaSemana > 7) {
            redirectAttributes.addFlashAttribute("error", "Día inválido.");
            return "redirect:" + paginaError;
        }

        if (horaInicio == null || horaFin == null || !horaFin.isAfter(horaInicio)) {
            redirectAttributes.addFlashAttribute("error", "La hora de fin debe ser después de la hora de inicio.");
            return "redirect:" + paginaError;
        }

        horario.setMateriaId(materiaId);
        horario.setDiaSemana(diaSemana);
        horario.setHoraInicio(horaInicio);
        horario.setHoraFin(horaFin);
        horario.setSala(sala != null && !sala.isBlank() ? sala.trim() : null);
        horarioRepository.save(horario);

        redirectAttributes.addFlashAttribute("success", mensajeOk);
        return "redirect:/horarios";
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        Long usuarioId = usuarioLogueado(session);
        Horario horario = horarioRepository.findById(id).orElse(null);

        if (horario == null) {
            redirectAttributes.addFlashAttribute("error", "Horario no encontrado.");
            return "redirect:/semestres";
        }

        Materia materia = materiaRepository.findById(horario.getMateriaId()).orElse(null);
        if (semestreDelUsuario(materia != null ? materia.getSemestreId() : null, usuarioId) == null) {
            redirectAttributes.addFlashAttribute("error", "No tienes permiso para este horario.");
            return "redirect:/semestres";
        }

        horarioRepository.delete(horario);
        redirectAttributes.addFlashAttribute("success", "Horario eliminado correctamente.");
        return "redirect:/horarios";
    }
}