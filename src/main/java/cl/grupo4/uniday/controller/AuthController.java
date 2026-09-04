package cl.grupo4.uniday.controller;

import cl.grupo4.uniday.config.LoginInterceptor;
import cl.grupo4.uniday.config.PasswordUtils;
import cl.grupo4.uniday.model.Usuario;
import cl.grupo4.uniday.repository.UsuarioRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

@Controller
public class AuthController {

    private final UsuarioRepository usuarioRepository;

    public AuthController(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/")
    public String raiz(HttpSession session) {
        if (session.getAttribute(LoginInterceptor.SESSION_USUARIO_ID) != null) {
            return "redirect:/materias";
        }
        return "redirect:/login";
    }

    @GetMapping("/registro")
    public String mostrarRegistro(Model model) {
        model.addAttribute("usuario", new Usuario());
        return "registro";
    }

    @PostMapping("/registro")
    public String registrar(@RequestParam String nombre,
                            @RequestParam String email,
                            @RequestParam String password,
                            @RequestParam String confirmPassword,
                            RedirectAttributes redirectAttributes) {

        if (nombre == null || nombre.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "El nombre no puede estar vacío.");
            return "redirect:/registro";
        }

        if (email == null || !email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            redirectAttributes.addFlashAttribute("error", "Ingresa un formato de email válido.");
            return "redirect:/registro";
        }

        if (password == null || password.length() < 4) {
            redirectAttributes.addFlashAttribute("error", "La contraseña debe tener al menos 4 caracteres.");
            return "redirect:/registro";
        }

        if (!password.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "Las contraseñas no coinciden.");
            return "redirect:/registro";
        }

        if (usuarioRepository.existsByEmail(email.trim().toLowerCase())) {
            redirectAttributes.addFlashAttribute("error", "Ya existe una cuenta con ese email.");
            return "redirect:/registro";
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(nombre.trim());
        usuario.setEmail(email.trim().toLowerCase());
        usuario.setPassword(PasswordUtils.hashPassword(password));
        usuario.setFechaCreacion(LocalDateTime.now());
        usuarioRepository.save(usuario);

        redirectAttributes.addFlashAttribute("success", "Cuenta creada correctamente. Inicia sesión.");
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String mostrarLogin() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email,
                        @RequestParam String password,
                        HttpSession session,
                        RedirectAttributes redirectAttributes) {

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Debes ingresar tu email y contraseña.");
            return "redirect:/login";
        }

        Usuario usuario = usuarioRepository.findByEmail(email.trim().toLowerCase()).orElse(null);

        if (usuario == null || !PasswordUtils.verifyPassword(password, usuario.getPassword())) {
            redirectAttributes.addFlashAttribute("error", "Email o contraseña incorrectos.");
            return "redirect:/login";
        }

        session.setAttribute(LoginInterceptor.SESSION_USUARIO_ID, usuario.getId());
        return "redirect:/semestres";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    @PostMapping("/logout")
    public String logoutPost(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
