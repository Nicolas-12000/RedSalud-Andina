package co.redsalud.andina.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import co.redsalud.andina.agenda.AgendaConflictException;
import co.redsalud.andina.agenda.SlotNotFoundException;
import co.redsalud.andina.auditoria.ReasonRequiredException;
import co.redsalud.andina.episodio.EpisodeConflictException;
import co.redsalud.andina.episodio.EpisodeNotFoundException;
import co.redsalud.andina.receta.PrescriptionImmutableException;
import co.redsalud.andina.receta.PrescriptionNotFoundException;

/** The one place where module exceptions become HTTP responses. */
@RestControllerAdvice
class ApiExceptionHandler {

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
}
