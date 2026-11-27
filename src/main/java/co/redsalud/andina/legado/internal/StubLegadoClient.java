package co.redsalud.andina.legado.internal;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({ "dev", "test" })
class StubLegadoClient implements LegadoClient {

	@Override
	public LegacyHistory fetchHistory(String patientCode) {
		String now = LocalDateTime.now(ZoneId.of("America/Bogota")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
		return new LegacyHistory(patientCode, "Stub Patient", "Stub Street 1",
				List.of(new LegacyHistory.Encounter("E-1", "15/03/2021", "Routine check"),
						new LegacyHistory.Encounter("E-2", "02/11/2023", "Follow-up")),
				List.of(new LegacyHistory.Document("D-1", "Chest X-ray report", "RADIOLOGY")), now);
	}
}
