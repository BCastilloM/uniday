package cl.grupo4.uniday.config;

import cl.grupo4.uniday.model.Materia;
import cl.grupo4.uniday.model.Semestre;
import cl.grupo4.uniday.model.Usuario;
import cl.grupo4.uniday.repository.MateriaRepository;
import cl.grupo4.uniday.repository.SemestreRepository;
import cl.grupo4.uniday.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = false)
public class DataSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final SemestreRepository semestreRepository;
    private final MateriaRepository materiaRepository;

    public DataSeeder(UsuarioRepository usuarioRepository,
                      SemestreRepository semestreRepository,
                      MateriaRepository materiaRepository) {
        this.usuarioRepository = usuarioRepository;
        this.semestreRepository = semestreRepository;
        this.materiaRepository = materiaRepository;
    }

    @Override
    public void run(String... args) {
        if (usuarioRepository.count() > 0) {
            return; // ya hay datos, no tocar nada
        }

        Usuario demo = new Usuario();
        demo.setNombre("María González");
        demo.setEmail("demo@uniday.cl");
        demo.setPassword(PasswordUtils.hashPassword("1234"));
        demo.setFechaCreacion(LocalDateTime.now());
        demo = usuarioRepository.save(demo);

        Semestre semestre2026_1 = new Semestre();
        semestre2026_1.setUsuarioId(demo.getId());
        semestre2026_1.setNombre("1er Semestre 2026");
        semestre2026_1.setActivo(true);
        semestre2026_1.setFechaInicio(LocalDate.of(2026, 3, 2));
        semestre2026_1.setFechaFin(LocalDate.of(2026, 7, 10));
        semestre2026_1 = semestreRepository.save(semestre2026_1);

        List<Materia> materias2026 = List.of(
            crearMateria(semestre2026_1.getId(), "Programación Orientada a Objetos", "INF-220", 5, "Paradigma de orientación a objetos, herencia, polimorfismo y patrones de diseño en Java.", "Ing. Carlos Muñoz", 80, "#3B82F6", "bi-cpu"),
            crearMateria(semestre2026_1.getId(), "Base de Datos", "INF-230", 5, "Diseño relacional, normalización, SQL avanzado y fundamentos de transacciones ACID.", "Ing. Ana Reyes", 75, "#22C55E", "bi-database"),
            crearMateria(semestre2026_1.getId(), "Ingeniería de Software I", "INF-240", 4, "Ciclos de vida del software, metodologías ágiles Scrum y modelado UML.", "Ing. Pedro Soto", 70, "#A855F7", "bi-lightbulb"),
            crearMateria(semestre2026_1.getId(), "Redes de Computadores", "TEL-210", 4, "Modelo OSI, TCP/IP, direccionamiento IPv4/IPv6, protocolos de capa de transporte y enlace.", "Ing. Luis Fuentes", 85, "#F97316", "bi-globe"),
            crearMateria(semestre2026_1.getId(), "Estadística y Probabilidades", "MAT-215", 4, "Variables aleatorias, distribuciones de probabilidad, intervalos de confianza y pruebas de hipótesis.", "Prof. Claudia Vera", 75, "#06B6D4", "bi-calculator")
        );
        materiaRepository.saveAll(materias2026);

        Semestre semestre2025_2 = new Semestre();
        semestre2025_2.setUsuarioId(demo.getId());
        semestre2025_2.setNombre("2do Semestre 2025");
        semestre2025_2.setActivo(false);
        semestre2025_2.setFechaInicio(LocalDate.of(2025, 8, 4));
        semestre2025_2.setFechaFin(LocalDate.of(2025, 12, 12));
        semestre2025_2 = semestreRepository.save(semestre2025_2);

        List<Materia> materias2025 = List.of(
            crearMateria(semestre2025_2.getId(), "Estructuras de Datos", "INF-120", 5, "Listas enlazadas, árboles binarios, grafos y algoritmos de ordenamiento y búsqueda.", "Ing. Carlos Muñoz", 80, "#EF4444", "bi-book"),
            crearMateria(semestre2025_2.getId(), "Sistemas Operativos", "INF-130", 5, "Gestión de procesos, memoria virtual, concurrencia, sincronización y sistemas de archivos.", "Ing. Roberto Díaz", 75, "#F59E0B", "bi-gear"),
            crearMateria(semestre2025_2.getId(), "Inglés Técnico", "IDI-101", 3, "Lectura comprensiva de documentación técnica y redacción de especificaciones en inglés.", "Prof. Sarah Mitchell", 70, "#EC4899", "bi-translate")
        );
        materiaRepository.saveAll(materias2025);

        System.out.println("=== DataSeeder ===");
        System.out.println("Datos demo cargados: demo@uniday.cl / 1234");
        System.out.println("2 semestres, 8 materias con codigos y creditos");
    }

    private Materia crearMateria(Long semestreId, String nombre, String codigo, int creditos, String descripcion, String profesor, int asistencia,
                                 String color, String icono) {
        Materia m = new Materia();
        m.setSemestreId(semestreId);
        m.setNombre(nombre);
        m.setCodigo(codigo);
        m.setCreditos(creditos);
        m.setDescripcion(descripcion);
        m.setProfesor(profesor);
        m.setAsistenciaExigida(asistencia);
        m.setColor(color);
        m.setIcono(icono);
        return m;
    }
}
