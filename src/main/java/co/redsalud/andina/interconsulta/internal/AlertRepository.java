package co.redsalud.andina.interconsulta.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

	@Query("""
			select a from Alert a
			where a.recipientSubject = :subject
			   or (:specialty is not null and a.recipientSpecialty = :specialty)
			order by a.createdAt desc
			""")
	List<Alert> findFor(UUID subject, String specialty, Pageable pageable);
}
