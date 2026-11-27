package co.redsalud.andina.api;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Denials decided by the filter chain (for example the actuator rule) never reach a controller, so they would
 * escape ApiExceptionHandler. The security configuration picks this handler up so they are audited too.
 */
@Component
@RequiredArgsConstructor
class AuditingAccessDeniedHandler implements AccessDeniedHandler {

	private final DeniedRequests deniedRequests;

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) throws IOException {
		deniedRequests.record(request);
		response.setStatus(HttpStatus.FORBIDDEN.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.getWriter().write("{\"title\":\"Forbidden\",\"status\":403,\"detail\":\"Access denied\"}");
	}
}
