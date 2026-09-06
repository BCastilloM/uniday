package cl.grupo4.uniday.repository;

import cl.grupo4.uniday.model.Horario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface HorarioRepository extends JpaRepository<Horario, Long> {

    List<Horario> findByMateriaIdInOrderByDiaSemanaAscHoraInicioAsc(Collection<Long> materiaIds);
}