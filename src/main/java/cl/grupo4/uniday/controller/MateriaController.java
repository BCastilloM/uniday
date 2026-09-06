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

import java.util.List;

@Controller
@RequestMapping("/materias")
public class MateriaController {

    private final MateriaRepository materiaRepository;
    private final SemestreRepository semestreRepository;

    public MateriaController(MateriaRepository materiaRepository, SemestreRepository semestreRepository) {
        this.materiaRepository = materiaRepository;
        this.semestreRepository = semestreRepository;
    }

    private Long usuarioLogueado(HttpSession session) {
        return (Long) session.getAttribute(LoginInterceptor.SESSION_USUARIO_ID);
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
    public String listar(@RequestParam(required = false) Long semestre,
                         HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Long usuarioId = usuarioLogueado(session);

        Semestre semestreObj;
        if (semestre != null) {
            semestreObj = semestreDelUsuario(semestre, usuarioId);
        } else {
            semestreObj = semestreRepository.findByUsuarioIdAndActivoTrue(usuarioId).orElse(null);
        }

        if (semestreObj == null) {
            redirectAttributes.addFlashAttribute("error", "No hay un semestre activo. Selecciona uno.");
            return "redirect:/semestres";
        }

        List<Materia> materias = materiaRepository.findBySemestreId(semestreObj.getId());
        model.addAttribute("semestre", semestreObj);
        model.addAttribute("materias", materias);
        return "materias";
    }

    @GetMapping("/nueva")
    public String nueva(@RequestParam Long semestreId, HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Long usuarioId = usuarioLogueado(session);
        Semestre semestreObj = semestreDelUsuario(semestreId, usuarioId);

        if (semestreObj == null) {
            redirectAttributes.addFlashAttribute("error", "Semestre no encontrado.");
            return "redirect:/semestres";
        }

        model.addAttribute("materia", new Materia());
        model.addAttribute("semestre", semestreObj);
        model.addAttribute("titulo", "Nueva materia");
        model.addAttribute("action", "/materias");
        return "materia-form";
    }

    @Transactional
    @PostMapping("/cambiar-semestre")
    public String cambiarSemestre(@RequestParam Long semestreId, HttpSession session, RedirectAttributes redirectAttributes) {
        Long usuarioId = usuarioLogueado(session);
        Semestre semestre = semestreRepository.findById(semestreId).orElse(null);

        if (semestre == null || !semestre.getUsuarioId().equals(usuarioId)) {
            redirectAttributes.addFlashAttribute("error", "Semestre no encontrado.");
            return "redirect:/semestres";
        }

        // Desactiva todos y activa el seleccionado, dentro de la misma transacción
        // para que nunca queden 2+ semestres activos.
        semestreRepository.desactivarTodos(usuarioId);
        semestre.setActivo(true);
        semestreRepository.save(semestre);

        return "redirect:/materias";
    }

    @PostMapping
    public String guardar(@RequestParam Long semestreId,
                          @RequestParam String nombre,
                          @RequestParam(required = false) String codigo,
                          @RequestParam(required = false) Integer creditos,
                          @RequestParam(required = false) String descripcion,
                          @RequestParam(required = false) String profesor,
                          @RequestParam(required = false) Integer asistenciaExigida,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {

        if (nombre == null || nombre.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "El nombre de la materia no puede estar vacío.");
            return "redirect:/materias/nueva?semestreId=" + semestreId;
        }

        Long usuarioId = usuarioLogueado(session);
        Semestre semestreObj = semestreDelUsuario(semestreId, usuarioId);

        if (semestreObj == null) {
            redirectAttributes.addFlashAttribute("error", "Semestre no encontrado.");
            return "redirect:/semestres";
        }

        Materia materia = new Materia();
        materia.setSemestreId(semestreId);
        materia.setNombre(nombre.trim());
        materia.setCodigo(codigo != null && !codigo.isBlank() ? codigo.trim() : null);
        materia.setCreditos(creditos != null ? Math.max(0, creditos) : 0);
        materia.setDescripcion(descripcion != null && !descripcion.isBlank() ? descripcion.trim() : null);
        materia.setProfesor(profesor != null && !profesor.isBlank() ? profesor.trim() : null);
        materia.setAsistenciaExigida(asistenciaExigida != null ? asistenciaExigida : 75);
        materiaRepository.save(materia);

        redirectAttributes.addFlashAttribute("success", "Materia creada correctamente.");
        return "redirect:/materias?semestre=" + semestreId;
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Long usuarioId = usuarioLogueado(session);
        Materia materia = materiaRepository.findById(id).orElse(null);

        if (materia == null) {
            redirectAttributes.addFlashAttribute("error", "Materia no encontrada.");
            return "redirect:/semestres";
        }

        Semestre semestreObj = semestreDelUsuario(materia.getSemestreId(), usuarioId);
        if (semestreObj == null) {
            redirectAttributes.addFlashAttribute("error", "No tienes permiso para esta materia.");
            return "redirect:/semestres";
        }

        model.addAttribute("materia", materia);
        model.addAttribute("semestre", semestreObj);
        model.addAttribute("titulo", "Editar materia");
        model.addAttribute("action", "/materias/" + id + "/actualizar");
        return "materia-form";
    }

    @PostMapping("/{id}/actualizar")
    public String actualizar(@PathVariable Long id,
                             @RequestParam String nombre,
                             @RequestParam(required = false) String codigo,
                             @RequestParam(required = false) Integer creditos,
                             @RequestParam(required = false) String descripcion,
                             @RequestParam(required = false) String profesor,
                             @RequestParam(required = false) Integer asistenciaExigida,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {

        if (nombre == null || nombre.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "El nombre de la materia no puede estar vacío.");
            return "redirect:/materias/" + id + "/editar";
        }

        Long usuarioId = usuarioLogueado(session);
        Materia materia = materiaRepository.findById(id).orElse(null);

        if (materia == null) {
            redirectAttributes.addFlashAttribute("error", "Materia no encontrada.");
            return "redirect:/semestres";
        }

        Semestre semestreObj = semestreDelUsuario(materia.getSemestreId(), usuarioId);
        if (semestreObj == null) {
            redirectAttributes.addFlashAttribute("error", "No tienes permiso para esta materia.");
            return "redirect:/semestres";
        }

        materia.setNombre(nombre.trim());
        materia.setCodigo(codigo != null && !codigo.isBlank() ? codigo.trim() : null);
        materia.setCreditos(creditos != null ? Math.max(0, creditos) : 0);
        materia.setDescripcion(descripcion != null && !descripcion.isBlank() ? descripcion.trim() : null);
        materia.setProfesor(profesor != null && !profesor.isBlank() ? profesor.trim() : null);
        materia.setAsistenciaExigida(asistenciaExigida != null ? asistenciaExigida : 75);
        materiaRepository.save(materia);

        redirectAttributes.addFlashAttribute("success", "Materia actualizada correctamente.");
        return "redirect:/materias?semestre=" + materia.getSemestreId();
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        Long usuarioId = usuarioLogueado(session);
        Materia materia = materiaRepository.findById(id).orElse(null);

        if (materia == null) {
            redirectAttributes.addFlashAttribute("error", "Materia no encontrada.");
            return "redirect:/semestres";
        }

        Semestre semestreObj = semestreDelUsuario(materia.getSemestreId(), usuarioId);
        if (semestreObj == null) {
            redirectAttributes.addFlashAttribute("error", "No tienes permiso para esta materia.");
            return "redirect:/semestres";
        }

        materiaRepository.delete(materia);
        redirectAttributes.addFlashAttribute("success", "Materia eliminada correctamente.");
        return "redirect:/materias?semestre=" + materia.getSemestreId();
    }
}
