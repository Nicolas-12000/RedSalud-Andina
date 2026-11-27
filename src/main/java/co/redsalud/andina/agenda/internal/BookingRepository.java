package co.redsalud.andina.agenda.internal;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

	boolean existsBySlotId(UUID slotId);
}
