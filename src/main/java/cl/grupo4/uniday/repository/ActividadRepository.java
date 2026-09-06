package cl.grupo4.uniday.repository;

import cl.grupo4.uniday.model.Actividad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ActividadRepository extends JpaRepository<Actividad, Long> {

    List<Actividad> findByMateriaIdInOrderByFechaAsc(Collection<Long> materiaIds);
}