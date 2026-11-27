package co.redsalud.andina.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import co.redsalud.andina.auditoria.ReasonRequiredException;

/** The one place where module exceptions become HTTP responses. */
@RestControllerAdvice
class ApiExceptionHandler {

	@ExceptionHandler(ReasonRequiredException.class)
	ProblemDetail reasonRequired(ReasonRequiredException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}
}
