package co.redsalud.andina.interconsulta.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultRepository extends JpaRepository<Consult, UUID> {

	@Query("select c from Consult c where c.status <> co.redsalud.andina.interconsulta.ConsultStatus.CLOSED and c.updatedAt < :cutoff order by c.updatedAt")
	List<Consult> findOpenNotUpdatedSince(Instant cutoff, Pageable pageable);
}
