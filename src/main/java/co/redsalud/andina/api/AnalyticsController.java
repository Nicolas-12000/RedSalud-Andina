package co.redsalud.andina.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.redsalud.andina.analitica.CenterIndicators;
import co.redsalud.andina.analitica.IndicatorsService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
class AnalyticsController {

	private final IndicatorsService indicatorsService;

	@GetMapping("/centers")
	List<CenterIndicators> byCenter() {
		return indicatorsService.byCenter();
	}
}
