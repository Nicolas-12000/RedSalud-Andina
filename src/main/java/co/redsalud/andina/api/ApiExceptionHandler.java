package co.redsalud.andina.api;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import co.redsalud.andina.agenda.AgendaConflictException;
import co.redsalud.andina.agenda.SlotNotFoundException;
import co.redsalud.andina.auditoria.ReasonRequiredException;
import co.redsalud.andina.episodio.EpisodeConflictException;
import co.redsalud.andina.episodio.EpisodeNotFoundException;
import co.redsalud.andina.interconsulta.ConsultConflictException;
import co.redsalud.andina.interconsulta.ConsultNotFoundException;
import co.redsalud.andina.legado.LegacyTranslationException;
import co.redsalud.andina.legado.LegacyUnavailableException;
import co.redsalud.andina.legado.PatientNotInLegacyException;
import co.redsalud.andina.receta.PrescriptionImmutableException;
import co.redsalud.andina.receta.PrescriptionNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/** The one place where module exceptions become HTTP responses. */
@RestControllerAdvice
@RequiredArgsConstructor
class ApiExceptionHandler {

	private final DeniedRequests deniedRequests;

	/** Every denial, from a role check, a patient-access check or an ownership check, passes here and is audited. */
	@ExceptionHandler(AccessDeniedException.class)
	ProblemDetail denied(AccessDeniedException ex, HttpServletRequest request) {
		deniedRequests.record(request);
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
	}

	@ExceptionHandler(ReasonRequiredException.class)
	ProblemDetail reasonRequired(ReasonRequiredException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	@ExceptionHandler(AgendaConflictException.class)
	ProblemDetail conflict(AgendaConflictException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
	}

	@ExceptionHandler(SlotNotFoundException.class)
	ProblemDetail notFound(SlotNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(EpisodeConflictException.class)
	ProblemDetail episodeConflict(EpisodeConflictException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
	}

	@ExceptionHandler(EpisodeNotFoundException.class)
	ProblemDetail episodeNotFound(EpisodeNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(PrescriptionImmutableException.class)
	ProblemDetail prescriptionImmutable(PrescriptionImmutableException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
	}

	@ExceptionHandler(PrescriptionNotFoundException.class)
	ProblemDetail prescriptionNotFound(PrescriptionNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler({ ConsultConflictException.class, OptimisticLockingFailureException.class })
	ProblemDetail consultConflict(RuntimeException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
	}

	@ExceptionHandler(ConsultNotFoundException.class)
	ProblemDetail consultNotFound(ConsultNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(LegacyUnavailableException.class)
	ProblemDetail legacyUnavailable(LegacyUnavailableException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
	}

	@ExceptionHandler(PatientNotInLegacyException.class)
	ProblemDetail patientNotInLegacy(PatientNotInLegacyException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(LegacyTranslationException.class)
	ProblemDetail legacyTranslation(LegacyTranslationException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
	}
}
