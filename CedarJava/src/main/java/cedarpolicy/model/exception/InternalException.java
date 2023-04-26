package cedarpolicy.model.exception;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** An exception which is thrown when Cedar encounters an internal error when processing a query. */
public class InternalException extends AuthException {
    private final List<String> errors;

    /**
     * Internal exception from Rust library.
     *
     * @param errors List of Errors.
     */
    public InternalException(String[] errors) {
        super("Internal error: " + String.join("\n", errors));
        this.errors = new ArrayList<>(Arrays.asList(errors));
    }

    /**
     * Get errors.
     *
     * @return the error messages returned by Cedar
     */
    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }
}
