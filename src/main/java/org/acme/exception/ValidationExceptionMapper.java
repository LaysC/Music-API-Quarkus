package org.acme.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.ArrayList;
import java.util.List;

@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        List<String> erros = new ArrayList<>();

        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            String nomeCampo = lastFieldName(violation.getPropertyPath().toString());
            String mensagem = violation.getMessage();
            erros.add(nomeCampo + ": " + mensagem);
        }

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponseBody(400, "Erro de Validação", erros))
                .build();
    }

    private String lastFieldName(String path) {
        String[] parts = path.split("\\.");
        return parts[parts.length - 1];
    }

    public static class ErrorResponseBody {
        public int status;
        public String message;
        public List<String> errors;

        public ErrorResponseBody(int status, String message, List<String> errors) {
            this.status = status;
            this.message = message;
            this.errors = errors;
        }
    }
}