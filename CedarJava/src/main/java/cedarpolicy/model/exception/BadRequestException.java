package cedarpolicy.model.exception;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * An exception which is thrown when Cedar encounters an error in a supplied query which caused it
 * to stop processing; for example, a syntax error in a policy string.
 */
public class BadRequestException extends AuthException {
    private final List<String> errors;

    /**
     * Failure due to bad request.
     *
     * @param errors List of Errors.
     */
    public BadRequestException(String[] errors) {
        super("Bad request: " + String.join("\n", errors));
        this.errors = new ArrayList<>(Arrays.asList(errors));
    }

    /**
     * Get the errors.
     *
     * @return the error messages returned by Cedar
     */
    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }
}
