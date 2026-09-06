package cl.grupo4.uniday.controller;

import cl.grupo4.uniday.config.LoginInterceptor;
import cl.grupo4.uniday.model.Materia;
import cl.grupo4.uniday.model.Nota;
import cl.grupo4.uniday.model.Semestre;
import cl.grupo4.uniday.repository.MateriaRepository;
import cl.grupo4.uniday.repository.NotaRepository;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/notas")
public class NotaController {

    private final NotaRepository notaRepository;
    private final MateriaRepository materiaRepository;
    private final SemestreRepository semestreRepository;

    public NotaController(NotaRepository notaRepository,
                          MateriaRepository materiaRepository,
                          SemestreRepository semestreRepository) {
        this.notaRepository = notaRepository;
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

    private static final double MAXIMO_PONDERACION = 100;
    private static final double EPSILON = 0.01;

    private double[] estadisticas(List<Nota> notas) {
        Map<Long, List<Nota>> hijasPorPadre = new HashMap<>();
        for (Nota nota : notas) {
            if (nota.getNotaPadreId() != null) {
                hijasPorPadre.computeIfAbsent(nota.getNotaPadreId(), k -> new ArrayList<>()).add(nota);
            }
        }

        double sumaPonderada = 0;
        double sumaPonderaciones = 0;
        for (Nota nota : notas) {
            if (nota.getNotaPadreId() != null) {
                continue;
            }
            List<Nota> hijas = hijasPorPadre.get(nota.getId());
            double valorEfectivo = (hijas != null && !hijas.isEmpty()) ? promedioHijas(hijas) : nota.getValor();
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
        if (sumaPonderaciones <= 0) {
            return 0;
        }
        return sumaValorPonderado / sumaPonderaciones;
    }

    private double sumaPonderaciones(List<Nota> notas) {
        double suma = 0;
        for (Nota nota : notas) {
            suma += nota.getPonderacion();
        }
        return suma;
    }

    private boolean supera100(double sumaExistente, double nuevaPonderacion) {
        return sumaExistente + nuevaPonderacion > MAXIMO_PONDERACION + EPSILON;
    }

    private String validar(String nombre, Double valor, Long materiaId, Long usuarioId) {
        if (nombre == null || nombre.trim().isEmpty()) {
            return "El nombre de la nota no puede estar vacío.";
        }
        if (valor == null || valor < 1.0 || valor > 7.0) {
            return "La nota debe estar entre 1.0 y 7.0.";
        }
        Materia materia = materiaRepository.findById(materiaId).orElse(null);
        if (materia == null || semestreDelUsuario(materia.getSemestreId(), usuarioId) == null) {
            return "Materia no encontrada.";
        }
        return null;
    }

    private String validarConPonderacion(String nombre, Double valor, Double ponderacion, Long materiaId, Long usuarioId) {
        String error = validar(nombre, valor, materiaId, usuarioId);
        if (error != null) {
            return error;
        }
        if (ponderacion == null || ponderacion <= 0 || ponderacion > MAXIMO_PONDERACION + EPSILON) {
            return "La ponderación debe estar entre 1 y 100.";
        }
        return null;
    }

    private List<Map<String, Object>> filasPadres(List<Nota> notas) {
        Map<Long, List<Nota>> hijasPorPadre = new HashMap<>();
        for (Nota nota : notas) {
            if (nota.getNotaPadreId() != null) {
                hijasPorPadre.computeIfAbsent(nota.getNotaPadreId(), k -> new ArrayList<>()).add(nota);
            }
        }

        List<Map<String, Object>> padres = new ArrayList<>();
        for (Nota nota : notas) {
            if (nota.getNotaPadreId() != null) {
                continue;
            }
            List<Nota> hijas = hijasPorPadre.getOrDefault(nota.getId(), new ArrayList<>());
            Double valorEfectivo = hijas.isEmpty() ? null : promedioHijas(hijas);
            Map<String, Object> fila = new HashMap<>();
            fila.put("nota", nota);
            fila.put("hijas", hijas);
            fila.put("valorEfectivo", valorEfectivo);
            fila.put("promedioHijas", valorEfectivo);
            padres.add(fila);
        }
        return padres;
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
        if (materias.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Primero agrega materias al semestre.");
            return "redirect:/materias";
        }

        List<Map<String, Object>> materiasConPromedio = new ArrayList<>();
        for (Materia materia : materias) {
            List<Nota> notas = notaRepository.findByMateriaId(materia.getId());
            double[] stats = estadisticas(notas);
            double sumaPonderada = stats[0];
            double sumaPonderaciones = stats[1];

            Double promedio = sumaPonderaciones > 0 ? sumaPonderada / sumaPonderaciones : null;

            Map<String, Object> datos = new HashMap<>();
            datos.put("id", materia.getId());
            datos.put("nombre", materia.getNombre());
            datos.put("codigo", materia.getCodigo());
            datos.put("cantidadNotas", notas.size());
            datos.put("promedio", promedio);
            datos.put("sumaPonderaciones", sumaPonderaciones);
            datos.put("faltaPonderacion", sumaPonderaciones < 100);
            datos.put("porcentajePonderado", sumaPonderaciones);
            materiasConPromedio.add(datos);
        }

        model.addAttribute("semestre", semestre);
        model.addAttribute("materias", materiasConPromedio);
        return "notas";
    }

    @GetMapping("/materia/{id}")
    public String detalle(@PathVariable Long id,
                          @RequestParam(required = false) Long editando,
                          @RequestParam(required = false) Long nuevahija,
                          HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Long usuarioId = usuarioLogueado(session);
        Materia materia = materiaRepository.findById(id).orElse(null);

        if (materia == null) {
            redirectAttributes.addFlashAttribute("error", "Materia no encontrada.");
            return "redirect:/notas";
        }

        if (semestreDelUsuario(materia.getSemestreId(), usuarioId) == null) {
            redirectAttributes.addFlashAttribute("error", "No tienes permiso para esta materia.");
            return "redirect:/notas";
        }

        List<Nota> notas = notaRepository.findByMateriaId(materia.getId());
        double[] stats = estadisticas(notas);
        double sumaPonderada = stats[0];
        double sumaPonderaciones = stats[1];
        Double promedio = sumaPonderaciones > 0 ? sumaPonderada / sumaPonderaciones : null;

        Nota notaEditando = null;
        String formModo = "nueva";
        Long formTargetId = null;
        String padreNombre = null;
        String nombreSugerido = null;
        Double ponderacionSugerida = null;
        String formAction = "/notas/materia/" + materia.getId() + "/nueva";
        String tituloForm = "Agregar nota";

        if (nuevahija != null) {
            Nota padre = notaRepository.findById(nuevahija).orElse(null);
            if (padre == null || !padre.getMateriaId().equals(materia.getId()) || padre.getNotaPadreId() != null) {
                redirectAttributes.addFlashAttribute("error", "Nota no encontrada.");
                return "redirect:/notas/materia/" + materia.getId();
            }
            formModo = "hija";
            formTargetId = padre.getId();
            padreNombre = padre.getNombre();
            int hijasDelPadre = notaRepository.findByNotaPadreId(padre.getId()).size();
            nombreSugerido = "Parte " + (hijasDelPadre + 1);
            ponderacionSugerida = Math.round((MAXIMO_PONDERACION / (hijasDelPadre + 1)) * 100.0) / 100.0;
            formAction = "/notas/" + nuevahija + "/hija";
            tituloForm = "Agregar parte a " + padre.getNombre();
        } else if (editando != null) {
            Nota nota = notaRepository.findById(editando).orElse(null);
            if (nota == null || !nota.getMateriaId().equals(materia.getId())) {
                redirectAttributes.addFlashAttribute("error", "Nota no encontrada.");
                return "redirect:/notas/materia/" + materia.getId();
            }
            notaEditando = nota;
            formModo = "editar";
            formTargetId = nota.getId();
            formAction = "/notas/" + editando + "/actualizar";
            tituloForm = "Editar nota";
        }

        model.addAttribute("materia", materia);
        model.addAttribute("padres", filasPadres(notas));
        model.addAttribute("promedio", promedio);
        model.addAttribute("sumaPonderada", sumaPonderada);
        model.addAttribute("sumaPonderaciones", sumaPonderaciones);
        model.addAttribute("faltaPonderacion", sumaPonderaciones < 100);
        model.addAttribute("notaEditando", notaEditando);
        model.addAttribute("formAction", formAction);
        model.addAttribute("tituloForm", tituloForm);
        model.addAttribute("formModo", formModo);
        model.addAttribute("formTargetId", formTargetId);
        model.addAttribute("padreNombre", padreNombre);
        model.addAttribute("nombreSugerido", nombreSugerido);
        model.addAttribute("ponderacionSugerida", ponderacionSugerida);
        return "nota-detalle";
    }

    @PostMapping("/materia/{id}/nueva")
    public String crear(@PathVariable Long id,
                        @RequestParam String nombre,
                        @RequestParam(required = false) Double valor,
                        @RequestParam(required = false) Double ponderacion,
                        HttpSession session,
                        RedirectAttributes redirectAttributes) {
        Long usuarioId = usuarioLogueado(session);
        String error = validarConPonderacion(nombre, valor, ponderacion, id, usuarioId);
        if (error != null) {
            redirectAttributes.addFlashAttribute("error", error);
            return "redirect:/notas/materia/" + id;
        }

        double sumaPadresExistentes = sumaPonderaciones(notaRepository.findByMateriaId(id).stream()
                .filter(n -> n.getNotaPadreId() == null)
                .toList());
        if (supera100(sumaPadresExistentes, ponderacion)) {
            redirectAttributes.addFlashAttribute("error", "La suma de ponderaciones no puede superar 100%.");
            return "redirect:/notas/materia/" + id;
        }

        Nota nota = new Nota();
        nota.setMateriaId(id);
        nota.setNombre(nombre.trim());
        nota.setValor(valor);
        nota.setPonderacion(ponderacion);
        notaRepository.save(nota);

        redirectAttributes.addFlashAttribute("success", "Nota agregada correctamente.");
        return "redirect:/notas/materia/" + id;
    }

    @PostMapping("/{id}/actualizar")
    public String actualizar(@PathVariable Long id,
                             @RequestParam String nombre,
                             @RequestParam(required = false) Double valor,
                             @RequestParam(required = false) Double ponderacion,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {
        Long usuarioId = usuarioLogueado(session);
        Nota nota = notaRepository.findById(id).orElse(null);
        if (nota == null) {
            redirectAttributes.addFlashAttribute("error", "Nota no encontrada.");
            return "redirect:/notas";
        }

        Materia materia = materiaRepository.findById(nota.getMateriaId()).orElse(null);
        if (semestreDelUsuario(materia != null ? materia.getSemestreId() : null, usuarioId) == null) {
            redirectAttributes.addFlashAttribute("error", "No tienes permiso para esta nota.");
            return "redirect:/notas";
        }

        String error = validarConPonderacion(nombre, valor, ponderacion, nota.getMateriaId(), usuarioId);
        if (error != null) {
            redirectAttributes.addFlashAttribute("error", error);
            return "redirect:/notas/materia/" + nota.getMateriaId();
        }

        double sumaDelResto;
        if (nota.getNotaPadreId() == null) {
            List<Nota> otrosPadres = notaRepository.findByMateriaId(nota.getMateriaId()).stream()
                    .filter(n -> n.getNotaPadreId() == null && !n.getId().equals(id))
                    .toList();
            sumaDelResto = sumaPonderaciones(otrosPadres);
        } else {
            List<Nota> otrasHermanas = notaRepository.findByNotaPadreId(nota.getNotaPadreId()).stream()
                    .filter(n -> !n.getId().equals(id))
                    .toList();
            sumaDelResto = sumaPonderaciones(otrasHermanas);
        }
        if (supera100(sumaDelResto, ponderacion)) {
            redirectAttributes.addFlashAttribute("error", "La suma de ponderaciones no puede superar 100%.");
            return "redirect:/notas/materia/" + nota.getMateriaId();
        }

        nota.setNombre(nombre.trim());
        nota.setValor(valor);
        nota.setPonderacion(ponderacion);
        notaRepository.save(nota);

        redirectAttributes.addFlashAttribute("success", "Nota actualizada correctamente.");
        return "redirect:/notas/materia/" + nota.getMateriaId();
    }

    @PostMapping("/{id}/hija")
    public String agregarHija(@PathVariable Long id,
                              @RequestParam String nombre,
                              @RequestParam(required = false) Double valor,
                              @RequestParam(required = false) Double ponderacion,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        Long usuarioId = usuarioLogueado(session);
        Nota padre = notaRepository.findById(id).orElse(null);
        if (padre == null) {
            redirectAttributes.addFlashAttribute("error", "Nota no encontrada.");
            return "redirect:/notas";
        }

        Materia materia = materiaRepository.findById(padre.getMateriaId()).orElse(null);
        if (semestreDelUsuario(materia != null ? materia.getSemestreId() : null, usuarioId) == null) {
            redirectAttributes.addFlashAttribute("error", "No tienes permiso para esta nota.");
            return "redirect:/notas";
        }

        String error = validar(nombre, valor, padre.getMateriaId(), usuarioId);
        if (error != null) {
            redirectAttributes.addFlashAttribute("error", error);
            return "redirect:/notas/materia/" + padre.getMateriaId();
        }

        boolean pondManual = ponderacion != null;
        List<Nota> hijas = notaRepository.findByNotaPadreId(padre.getId());

        if (pondManual) {
            if (ponderacion <= 0 || ponderacion > MAXIMO_PONDERACION + EPSILON) {
                redirectAttributes.addFlashAttribute("error", "La ponderación debe ser un valor entre 1 y 100.");
                return "redirect:/notas/materia/" + padre.getMateriaId();
            }
            if (supera100(ponderacion, 0)) {
                redirectAttributes.addFlashAttribute("error", "La suma de ponderaciones no puede superar 100%.");
                return "redirect:/notas/materia/" + padre.getMateriaId();
            }
        }
        double nuevaPonderacion = pondManual ? ponderacion : MAXIMO_PONDERACION / (hijas.size() + 1);
        if (!hijas.isEmpty()) {
            double pondHermana = pondManual ? (MAXIMO_PONDERACION - nuevaPonderacion) / hijas.size() : nuevaPonderacion;
            for (Nota hija : hijas) {
                hija.setPonderacion(pondHermana);
                notaRepository.save(hija);
            }
        }

        Nota nota = new Nota();
        nota.setMateriaId(padre.getMateriaId());
        nota.setNotaPadreId(padre.getId());
        nota.setNombre(nombre.trim());
        nota.setValor(valor);
        nota.setPonderacion(nuevaPonderacion);
        notaRepository.save(nota);

        redirectAttributes.addFlashAttribute("success", "Parte agregada correctamente.");
        return "redirect:/notas/materia/" + padre.getMateriaId();
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long id,
                           HttpSession session,
                           RedirectAttributes redirectAttributes) {
        Long usuarioId = usuarioLogueado(session);
        Nota nota = notaRepository.findById(id).orElse(null);
        if (nota == null) {
            redirectAttributes.addFlashAttribute("error", "Nota no encontrada.");
            return "redirect:/notas";
        }

        Materia materia = materiaRepository.findById(nota.getMateriaId()).orElse(null);
        if (semestreDelUsuario(materia != null ? materia.getSemestreId() : null, usuarioId) == null) {
            redirectAttributes.addFlashAttribute("error", "No tienes permiso para esta nota.");
            return "redirect:/notas";
        }

        if (nota.getNotaPadreId() != null) {
            List<Nota> hermanasRestantes = notaRepository.findByNotaPadreId(nota.getNotaPadreId())
                    .stream()
                    .filter(hermana -> !hermana.getId().equals(id))
                    .toList();
            if (!hermanasRestantes.isEmpty()) {
                double nuevaPonderacion = MAXIMO_PONDERACION / hermanasRestantes.size();
                for (Nota hermana : hermanasRestantes) {
                    hermana.setPonderacion(nuevaPonderacion);
                    notaRepository.save(hermana);
                }
            }
        }

        notaRepository.delete(nota);
        redirectAttributes.addFlashAttribute("success", "Nota eliminada correctamente.");
        return "redirect:/notas/materia/" + nota.getMateriaId();
    }
}