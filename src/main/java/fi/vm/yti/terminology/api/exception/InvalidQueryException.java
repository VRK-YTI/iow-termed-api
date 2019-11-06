package fi.vm.yti.terminology.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidQueryException extends RuntimeException {
    public InvalidQueryException() {
        super("Invalid query");
    }

    public InvalidQueryException(String message) {
        super(message);
    }
}
