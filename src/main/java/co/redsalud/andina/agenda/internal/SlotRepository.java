package co.redsalud.andina.agenda.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SlotRepository extends JpaRepository<Slot, UUID> {

	@Query("""
			select s from Slot s
			where s.specialty = :specialty and s.startAt >= :from
			  and not exists (select 1 from Booking b where b.slotId = s.id)
			order by s.startAt
			""")
	List<Slot> findAvailable(String specialty, Instant from, Pageable pageable);
}
