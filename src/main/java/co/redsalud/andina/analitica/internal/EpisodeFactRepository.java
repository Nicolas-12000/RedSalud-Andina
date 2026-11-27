package co.redsalud.andina.analitica.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface EpisodeFactRepository extends JpaRepository<EpisodeFact, UUID> {

	record Totals(String centerId, long teleconsults, long uniquePatients) {
	}

	@Query("select new co.redsalud.andina.analitica.internal.EpisodeFactRepository$Totals("
			+ "e.centerId, count(e), count(distinct e.patientPseudonym)) from EpisodeFact e group by e.centerId")
	List<Totals> totalsByCenter();
}
