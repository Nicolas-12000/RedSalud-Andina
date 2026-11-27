package co.redsalud.andina.analitica.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** Read side of the analytics schema, as plain values so the public service does not see entities. */
@Component
@RequiredArgsConstructor
public class IndicatorsQueries {

	public record EpisodeTotals(String centerId, long teleconsults, long uniquePatients) {
	}

	public record ConsultTotals(String centerId, long requested, long closed, Double averageWaitSeconds) {
	}

	private final EpisodeFactRepository episodeFacts;
	private final ConsultFactRepository consultFacts;

	public List<EpisodeTotals> episodeTotals() {
		return episodeFacts.totalsByCenter().stream()
				.map(t -> new EpisodeTotals(t.centerId(), t.teleconsults(), t.uniquePatients())).toList();
	}

	public List<ConsultTotals> consultTotals() {
		return consultFacts.totalsByCenter().stream()
				.map(t -> new ConsultTotals(t.centerId(), t.requested(), t.closed(), t.averageWaitSeconds())).toList();
	}
}
