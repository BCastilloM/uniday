package cl.grupo4.uniday.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "asistencias", uniqueConstraints = @UniqueConstraint(name = "uk_asistencias_materia_fecha", columnNames = {"materia_id", "fecha"}))
public class Asistencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "materia_id", nullable = false)
    private Long materiaId;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private boolean presente;

    public Asistencia() {
    }

    public Long getId() {
        return id;
    }

    public Long getMateriaId() {
        return materiaId;
    }

    public void setMateriaId(Long materiaId) {
        this.materiaId = materiaId;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public boolean isPresente() {
        return presente;
    }

    public void setPresente(boolean presente) {
        this.presente = presente;
    }
}
