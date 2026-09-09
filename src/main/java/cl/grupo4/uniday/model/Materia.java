package cl.grupo4.uniday.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "materias")
public class Materia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "semestre_id", nullable = false)
    private Long semestreId;

    @Column(nullable = false)
    private String nombre;

    private String profesor;

    @Column(length = 50)
    private String codigo;

    @Column(nullable = false)
    private int creditos = 0;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "asistencia_exigida", nullable = false)
    private int asistenciaExigida = 75;

    @Column(name = "fecha_inicio_clases")
    private LocalDate fechaInicioClases;

    @Column(name = "fecha_fin_clases")
    private LocalDate fechaFinClases;

    public Materia() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSemestreId() {
        return semestreId;
    }

    public void setSemestreId(Long semestreId) {
        this.semestreId = semestreId;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public int getCreditos() {
        return creditos;
    }

    public void setCreditos(int creditos) {
        this.creditos = creditos;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getProfesor() {
        return profesor;
    }

    public void setProfesor(String profesor) {
        this.profesor = profesor;
    }

    public int getAsistenciaExigida() {
        return asistenciaExigida;
    }

    public void setAsistenciaExigida(int asistenciaExigida) {
        this.asistenciaExigida = asistenciaExigida;
    }

    public LocalDate getFechaInicioClases() {
        return fechaInicioClases;
    }

    public void setFechaInicioClases(LocalDate fechaInicioClases) {
        this.fechaInicioClases = fechaInicioClases;
    }

    public LocalDate getFechaFinClases() {
        return fechaFinClases;
    }

    public void setFechaFinClases(LocalDate fechaFinClases) {
        this.fechaFinClases = fechaFinClases;
    }
}
