package my.chat.chatapp.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;

import java.util.Locale;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorView> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorView("not_found", ex.getMessage()));
    }

    @ExceptionHandler({AccessDeniedException.class, org.springframework.security.access.AccessDeniedException.class})
    public ResponseEntity<ErrorView> handleAccessDenied(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorView("forbidden", ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorView> handleBadRequest(BadRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorView("bad_request", ex.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorView> handleResponseStatus(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String message = ex.getReason() != null ? ex.getReason() : status.toString();
        return ResponseEntity.status(status)
                .body(new ErrorView(codeOf(status), message));
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ErrorView> handleInvalidInput(ServerWebInputException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorView("bad_request", "invalid request"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorView> handleUnexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorView("internal_error", "unexpected error"));
    }

    private static String codeOf(HttpStatusCode status) {
        if (status instanceof HttpStatus httpStatus) {
            return switch (httpStatus) {
                case BAD_REQUEST -> "bad_request";
                case UNAUTHORIZED -> "unauthorized";
                case FORBIDDEN -> "forbidden";
                case NOT_FOUND -> "not_found";
                case CONFLICT -> "conflict";
                default -> httpStatus.name().toLowerCase(Locale.ROOT);
            };
        }
        return "error";
    }
}
