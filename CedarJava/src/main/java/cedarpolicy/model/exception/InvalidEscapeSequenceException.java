package cedarpolicy.model.exception;

/** Error due to invalid escape sequence (__expr). */
public class InvalidEscapeSequenceException extends RuntimeException {
    /**
     * Construct InvalidEscapeSequenceException.
     *
     * @param errorMessage Error message.
     */
    public InvalidEscapeSequenceException(String errorMessage) {
        super(errorMessage);
    }
}
