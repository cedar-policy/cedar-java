package cedarpolicy.model.exception;

/** Error deserializing a value. */
public class InvalidValueSerializationException extends RuntimeException {
    /**
     * Construct InvalidValueSerializationException.
     *
     * @param errorMessage Error message.
     */
    public InvalidValueSerializationException(String errorMessage) {
        super(errorMessage);
    }
}
