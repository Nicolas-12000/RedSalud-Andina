package co.redsalud.andina.api;

import org.springframework.stereotype.Component;

import co.redsalud.andina.auditoria.AuditTrail;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/** The single place that records a refused request: the audit record and the metric. */
@Component
@RequiredArgsConstructor
class DeniedRequests {

	private final AuditTrail auditTrail;
	private final MeterRegistry meters;

	void record(HttpServletRequest request) {
		auditTrail.recordAccessDenied(request.getMethod() + " " + request.getRequestURI(),
				request.getHeader(ClinicalController.REASON_HEADER));
		meters.counter("redsalud.access.denied").increment();
	}
}
