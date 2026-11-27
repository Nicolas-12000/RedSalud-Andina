package co.redsalud.andina.analitica.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ConsultFactRepository extends JpaRepository<ConsultFact, UUID> {

	record Totals(String centerId, long requested, long closed, Double averageWaitSeconds) {
	}

	@Query("select new co.redsalud.andina.analitica.internal.ConsultFactRepository$Totals("
			+ "c.centerId, count(c), sum(case when c.closedAt is not null then 1L else 0L end), avg(c.waitSeconds)) "
			+ "from ConsultFact c group by c.centerId")
	List<Totals> totalsByCenter();
}
