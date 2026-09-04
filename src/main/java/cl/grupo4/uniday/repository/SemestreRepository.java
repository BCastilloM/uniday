package cl.grupo4.uniday.repository;

import cl.grupo4.uniday.model.Semestre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SemestreRepository extends JpaRepository<Semestre, Long> {

    List<Semestre> findByUsuarioId(Long usuarioId);

    Optional<Semestre> findByUsuarioIdAndActivoTrue(Long usuarioId);

    /** Pone en false el flag activo de todos los semestres del usuario (modificación masiva). */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("UPDATE Semestre s SET s.activo = false WHERE s.usuarioId = :usuarioId")
    void desactivarTodos(@org.springframework.data.repository.query.Param("usuarioId") Long usuarioId);
}
