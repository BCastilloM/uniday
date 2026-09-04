package cl.grupo4.uniday.repository;

import cl.grupo4.uniday.model.Materia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MateriaRepository extends JpaRepository<Materia, Long> {

    List<Materia> findBySemestreId(Long semestreId);

    void deleteBySemestreId(Long semestreId);
}
