package co.redsalud.andina.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.redsalud.andina.auditoria.ReadReport;
import co.redsalud.andina.auditoria.ReadReportService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
class AuditController {

	private final ReadReportService reportService;

	@GetMapping("/resources/{resourceType}/{resourceId}/reads")
	ReadReport whoRead(@PathVariable String resourceType, @PathVariable String resourceId) {
		return reportService.whoRead(resourceType, resourceId);
	}
}
