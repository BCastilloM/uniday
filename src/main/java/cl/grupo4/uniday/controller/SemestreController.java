package cl.grupo4.uniday.controller;

import cl.grupo4.uniday.config.LoginInterceptor;
import cl.grupo4.uniday.model.Materia;
import cl.grupo4.uniday.model.Semestre;
import cl.grupo4.uniday.repository.MateriaRepository;
import cl.grupo4.uniday.repository.SemestreRepository;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/semestres")
public class SemestreController {

    private final SemestreRepository semestreRepository;
    private final MateriaRepository materiaRepository;

    public SemestreController(SemestreRepository semestreRepository, MateriaRepository materiaRepository) {
        this.semestreRepository = semestreRepository;
        this.materiaRepository = materiaRepository;
    }

    private Long usuarioLogueado(HttpSession session) {
        return (Long) session.getAttribute(LoginInterceptor.SESSION_USUARIO_ID);
    }

    @GetMapping
    public String listar(HttpSession session, Model model) {
        Long usuarioId = usuarioLogueado(session);
        List<Semestre> semestres = semestreRepository.findByUsuarioId(usuarioId);
        model.addAttribute("semestres", semestres);

        // Agrupa las materias por semestre y calcula total de créditos para el historial académico
        Map<Long, List<Materia>> materiasPorSemestre = new HashMap<>();
        Map<Long, Integer> creditosPorSemestre = new HashMap<>();
        for (Semestre semestre : semestres) {
            List<Materia> mats = materiaRepository.findBySemestreId(semestre.getId());
            materiasPorSemestre.put(semestre.getId(), mats);
            int totalCreditos = mats.stream().mapToInt(Materia::getCreditos).sum();
            creditosPorSemestre.put(semestre.getId(), totalCreditos);
        }
        model.addAttribute("materiasPorSemestre", materiasPorSemestre);
        model.addAttribute("creditosPorSemestre", creditosPorSemestre);

        Semestre activo = semestreRepository.findByUsuarioIdAndActivoTrue(usuarioId).orElse(null);
        model.addAttribute("semestreActivo", activo);

        return "semestres";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        model.addAttribute("semestre", new Semestre());
        model.addAttribute("titulo", "Nuevo semestre");
        model.addAttribute("action", "/semestres");
        return "semestre-form";
    }

    @PostMapping
    public String guardar(@RequestParam String nombre,
                          @RequestParam(required = false) String fechaInicio,
                          @RequestParam(required = false) String fechaFin,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {

        if (nombre == null || nombre.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "El nombre del semestre no puede estar vacío.");
            return "redirect:/semestres/nuevo";
        }

        Long usuarioId = usuarioLogueado(session);

        LocalDate start = null;
        LocalDate end = null;
        if (fechaInicio != null && !fechaInicio.isBlank()) {
            start = LocalDate.parse(fechaInicio);
        }
        if (fechaFin != null && !fechaFin.isBlank()) {
            end = LocalDate.parse(fechaFin);
        }

        if (start != null && end != null && start.isAfter(end)) {
            redirectAttributes.addFlashAttribute("error", "La fecha de inicio no puede ser posterior a la fecha de término.");
            return "redirect:/semestres/nuevo";
        }

        Semestre semestre = new Semestre();
        semestre.setNombre(nombre.trim());
        semestre.setUsuarioId(usuarioId);
        semestre.setActivo(false);
        semestre.setFechaInicio(start);
        semestre.setFechaFin(end);
        semestreRepository.save(semestre);

        redirectAttributes.addFlashAttribute("success", "Semestre creado correctamente.");
        return "redirect:/semestres";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Long usuarioId = usuarioLogueado(session);
        Semestre semestre = semestreRepository.findById(id).orElse(null);

        if (semestre == null || !semestre.getUsuarioId().equals(usuarioId)) {
            redirectAttributes.addFlashAttribute("error", "Semestre no encontrado.");
            return "redirect:/semestres";
        }

        model.addAttribute("semestre", semestre);
        model.addAttribute("titulo", "Editar semestre");
        model.addAttribute("action", "/semestres/" + id + "/actualizar");
        return "semestre-form";
    }

    @PostMapping("/{id}/actualizar")
    public String actualizar(@PathVariable Long id,
                             @RequestParam String nombre,
                             @RequestParam(required = false) String fechaInicio,
                             @RequestParam(required = false) String fechaFin,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {

        if (nombre == null || nombre.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "El nombre del semestre no puede estar vacío.");
            return "redirect:/semestres/" + id + "/editar";
        }

        Long usuarioId = usuarioLogueado(session);
        Semestre semestre = semestreRepository.findById(id).orElse(null);

        if (semestre == null || !semestre.getUsuarioId().equals(usuarioId)) {
            redirectAttributes.addFlashAttribute("error", "Semestre no encontrado.");
            return "redirect:/semestres";
        }

        LocalDate start = null;
        LocalDate end = null;
        if (fechaInicio != null && !fechaInicio.isBlank()) {
            start = LocalDate.parse(fechaInicio);
        }
        if (fechaFin != null && !fechaFin.isBlank()) {
            end = LocalDate.parse(fechaFin);
        }

        if (start != null && end != null && start.isAfter(end)) {
            redirectAttributes.addFlashAttribute("error", "La fecha de inicio no puede ser posterior a la fecha de término.");
            return "redirect:/semestres/" + id + "/editar";
        }

        semestre.setNombre(nombre.trim());
        semestre.setFechaInicio(start);
        semestre.setFechaFin(end);
        semestreRepository.save(semestre);

        redirectAttributes.addFlashAttribute("success", "Semestre actualizado correctamente.");
        return "redirect:/semestres";
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        Long usuarioId = usuarioLogueado(session);
        Semestre semestre = semestreRepository.findById(id).orElse(null);

        if (semestre == null || !semestre.getUsuarioId().equals(usuarioId)) {
            redirectAttributes.addFlashAttribute("error", "Semestre no encontrado.");
            return "redirect:/semestres";
        }

        // Borra primero sus materias (cascade manual), luego el semestre
        materiaRepository.deleteBySemestreId(id);
        semestreRepository.delete(semestre);

        redirectAttributes.addFlashAttribute("success", "Semestre eliminado correctamente.");
        return "redirect:/semestres";
    }

    @Transactional
    @PostMapping("/{id}/activar")
    public String activar(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        Long usuarioId = usuarioLogueado(session);
        Semestre semestre = semestreRepository.findById(id).orElse(null);

        if (semestre == null || !semestre.getUsuarioId().equals(usuarioId)) {
            redirectAttributes.addFlashAttribute("error", "Semestre no encontrado.");
            return "redirect:/semestres";
        }

        // Desactiva todos los semestres del usuario y activa el seleccionado.
        // Todo dentro de la misma transacción para que nunca queden 2+ activos.
        semestreRepository.desactivarTodos(usuarioId);
        semestre.setActivo(true);
        semestreRepository.save(semestre);

        redirectAttributes.addFlashAttribute("success", "Semestre activado: " + semestre.getNombre());
        return "redirect:/semestres";
    }
}
