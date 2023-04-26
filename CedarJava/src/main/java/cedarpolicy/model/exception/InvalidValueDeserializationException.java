package cedarpolicy.model.exception;

/** Error deserializing a value. */
public class InvalidValueDeserializationException extends RuntimeException {
    /**
     * Construct InvalidValueDeserializationException.
     *
     * @param errorMessage Error message.
     */
    public InvalidValueDeserializationException(String errorMessage) {
        super(errorMessage);
    }
}
