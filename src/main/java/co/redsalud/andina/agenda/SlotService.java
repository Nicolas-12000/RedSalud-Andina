package co.redsalud.andina.agenda;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.redsalud.andina.agenda.internal.Slot;
import co.redsalud.andina.agenda.internal.SlotRepository;
import co.redsalud.andina.identidad.CurrentUser;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SlotService {

	static final int MAX_PAGE_SIZE = 100;

	private final SlotRepository slotRepository;

	/** A specialist publishes availability under their own identity and specialty. */
	@PreAuthorize("hasRole('SPECIALIST')")
	public SlotView createSlot(CurrentUser specialist, Instant startAt, Instant endAt) {
		if (!endAt.isAfter(startAt)) {
			throw new IllegalArgumentException("endAt must be after startAt");
		}
		try {
			return SlotView.of(slotRepository.saveAndFlush(new Slot(specialist.subject(), specialist.specialty(), startAt, endAt)));
		} catch (DataIntegrityViolationException e) {
			throw new AgendaConflictException("Specialist already has a slot starting at " + startAt);
		}
	}

	@Transactional(readOnly = true)
	public List<SlotView> findAvailable(String specialty, Instant from, int size) {
		return slotRepository.findAvailable(specialty, from, PageRequest.of(0, Math.min(size, MAX_PAGE_SIZE))).stream()
				.map(SlotView::of).toList();
	}
}
