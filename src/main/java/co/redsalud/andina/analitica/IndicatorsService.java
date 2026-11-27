package co.redsalud.andina.analitica;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.redsalud.andina.analitica.internal.IndicatorsQueries;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class IndicatorsService {

	private final IndicatorsQueries queries;

	@PreAuthorize("hasRole('NETWORK_MANAGER')")
	@Transactional(readOnly = true)
	public List<CenterIndicators> byCenter() {
		Map<String, IndicatorsQueries.EpisodeTotals> episodes = new TreeMap<>();
		queries.episodeTotals().forEach(t -> episodes.put(t.centerId(), t));
		Map<String, IndicatorsQueries.ConsultTotals> consults = new TreeMap<>();
		queries.consultTotals().forEach(t -> consults.put(t.centerId(), t));

		Map<String, Boolean> centers = new TreeMap<>();
		episodes.keySet().forEach(c -> centers.put(c, true));
		consults.keySet().forEach(c -> centers.put(c, true));
		return centers.keySet().stream().map(center -> {
			var e = episodes.get(center);
			var c = consults.get(center);
			long requested = c == null ? 0 : c.requested();
			long closed = c == null ? 0 : c.closed();
			return new CenterIndicators(center, e == null ? 0 : e.teleconsults(), e == null ? 0 : e.uniquePatients(),
					requested, closed, requested == 0 ? null : (double) closed / requested,
					c == null ? null : c.averageWaitSeconds());
		}).toList();
	}
}
