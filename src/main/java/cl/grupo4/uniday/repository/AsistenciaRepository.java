package cl.grupo4.uniday.repository;

import cl.grupo4.uniday.model.Asistencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AsistenciaRepository extends JpaRepository<Asistencia, Long> {

    List<Asistencia> findByMateriaIdInOrderByFechaDesc(List<Long> materiaIds);

    Optional<Asistencia> findByMateriaIdAndFecha(Long materiaId, LocalDate fecha);
}
