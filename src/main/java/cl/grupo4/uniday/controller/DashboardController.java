package cl.grupo4.uniday.controller;

import cl.grupo4.uniday.config.LoginInterceptor;
import cl.grupo4.uniday.model.Actividad;
import cl.grupo4.uniday.model.Horario;
import cl.grupo4.uniday.model.Materia;
import cl.grupo4.uniday.model.Nota;
import cl.grupo4.uniday.model.Semestre;
import cl.grupo4.uniday.repository.ActividadRepository;
import cl.grupo4.uniday.repository.HorarioRepository;
import cl.grupo4.uniday.repository.MateriaRepository;
import cl.grupo4.uniday.repository.NotaRepository;
import cl.grupo4.uniday.repository.SemestreRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
public class DashboardController {

    private final ActividadRepository actividadRepository;
    private final HorarioRepository horarioRepository;
    private final MateriaRepository materiaRepository;
    private final NotaRepository notaRepository;
    private final SemestreRepository semestreRepository;

    public DashboardController(ActividadRepository actividadRepository,
                               HorarioRepository horarioRepository,
                               MateriaRepository materiaRepository,
                               NotaRepository notaRepository,
                               SemestreRepository semestreRepository) {
        this.actividadRepository = actividadRepository;
        this.horarioRepository = horarioRepository;
        this.materiaRepository = materiaRepository;
        this.notaRepository = notaRepository;
        this.semestreRepository = semestreRepository;
    }

    @GetMapping("/")
    public String inicio(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Long usuarioId = (Long) session.getAttribute(LoginInterceptor.SESSION_USUARIO_ID);
        if (usuarioId == null) {
            return "redirect:/login";
        }

        Semestre semestre = semestreRepository.findByUsuarioIdAndActivoTrue(usuarioId).orElse(null);
        if (semestre == null) {
            redirectAttributes.addFlashAttribute("error", "Selecciona un semestre activo para ver tu dashboard.");
            return "redirect:/semestres";
        }

        List<Materia> materias = materiaRepository.findBySemestreId(semestre.getId());
        List<Long> materiaIds = materias.stream().map(Materia::getId).toList();
        List<Horario> horarios = materiaIds.isEmpty()
                ? List.of()
                : horarioRepository.findByMateriaIdInOrderByDiaSemanaAscHoraInicioAsc(materiaIds);

        Set<Long> materiasConHorario = new HashSet<>();
        for (Horario horario : horarios) {
            materiasConHorario.add(horario.getMateriaId());
        }

        List<Map<String, Object>> materiasConPromedio = new ArrayList<>();
        double sumaPromedios = 0;
        int materiasConNotas = 0;
        for (Materia materia : materias) {
            List<Nota> notas = notaRepository.findByMateriaId(materia.getId());
            double[] estadisticas = calcularEstadisticas(notas);
            Double promedio = estadisticas[1] > 0 ? estadisticas[0] / estadisticas[1] : null;

            Map<String, Object> datos = new HashMap<>();
            datos.put("id", materia.getId());
            datos.put("nombre", materia.getNombre());
            datos.put("codigo", materia.getCodigo());
            datos.put("promedio", promedio);
            datos.put("cantidadNotas", notas.size());
            materiasConPromedio.add(datos);
            if (promedio != null) {
                sumaPromedios += promedio;
                materiasConNotas++;
            }
        }

        Map<Long, String> materiaNombres = new HashMap<>();
        for (Materia materia : materias) {
            materiaNombres.put(materia.getId(), materia.getNombre());
        }

        List<Map<String, Object>> proximasActividades = new ArrayList<>();
        if (!materiaIds.isEmpty()) {
            LocalDate hoy = LocalDate.now();
            List<Actividad> actividades = actividadRepository.findByMateriaIdInOrderByFechaAsc(materiaIds);
            actividades.stream()
                    .filter(actividad -> !actividad.getFecha().isBefore(hoy))
                    .filter(actividad -> !"completada".equals(actividad.getEstado()))
                    .limit(5)
                    .forEach(actividad -> {
                        Map<String, Object> datos = new HashMap<>();
                        datos.put("titulo", actividad.getTitulo());
                        datos.put("tipo", actividad.getTipo());
                        datos.put("fecha", actividad.getFecha());
                        datos.put("materiaNombre", materiaNombres.get(actividad.getMateriaId()));
                        proximasActividades.add(datos);
                    });
        }

        double porcentajeHorario = materias.isEmpty()
                ? 0
                : materiasConHorario.size() * 100.0 / materias.size();
        Double promedioGeneral = materiasConNotas == 0 ? null : sumaPromedios / materiasConNotas;

        model.addAttribute("semestre", semestre);
        model.addAttribute("materias", materiasConPromedio);
        model.addAttribute("cantidadMaterias", materias.size());
        model.addAttribute("promedioGeneral", promedioGeneral);
        model.addAttribute("porcentajeHorario", porcentajeHorario);
        model.addAttribute("materiasConHorario", materiasConHorario.size());
        model.addAttribute("proximasActividades", proximasActividades);
        return "dashboard";
    }

    private double[] calcularEstadisticas(List<Nota> notas) {
        Map<Long, List<Nota>> hijasPorPadre = new HashMap<>();
        for (Nota nota : notas) {
            if (nota.getNotaPadreId() != null) {
                hijasPorPadre.computeIfAbsent(nota.getNotaPadreId(), clave -> new ArrayList<>()).add(nota);
            }
        }

        double sumaPonderada = 0;
        double sumaPonderaciones = 0;
        for (Nota nota : notas) {
            if (nota.getNotaPadreId() != null) {
                continue;
            }
            List<Nota> hijas = hijasPorPadre.get(nota.getId());
            double valorEfectivo = hijas == null || hijas.isEmpty()
                    ? nota.getValor()
                    : promedioHijas(hijas);
            sumaPonderada += valorEfectivo * nota.getPonderacion();
            sumaPonderaciones += nota.getPonderacion();
        }
        return new double[]{sumaPonderada, sumaPonderaciones};
    }

    private double promedioHijas(List<Nota> hijas) {
        double sumaValorPonderado = 0;
        double sumaPonderaciones = 0;
        for (Nota hija : hijas) {
            sumaValorPonderado += hija.getValor() * hija.getPonderacion();
            sumaPonderaciones += hija.getPonderacion();
        }
        return sumaPonderaciones == 0 ? 0 : sumaValorPonderado / sumaPonderaciones;
    }
}