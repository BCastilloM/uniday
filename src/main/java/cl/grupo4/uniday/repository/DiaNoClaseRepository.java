package cl.grupo4.uniday.repository;

import cl.grupo4.uniday.model.DiaNoClase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiaNoClaseRepository extends JpaRepository<DiaNoClase, Long> {

    List<DiaNoClase> findBySemestreIdOrderByFechaInicioAsc(Long semestreId);
}
