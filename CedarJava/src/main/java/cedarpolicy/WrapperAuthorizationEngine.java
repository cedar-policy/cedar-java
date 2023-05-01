package cedarpolicy;

import static cedarpolicy.CedarJson.objectReader;
import static cedarpolicy.CedarJson.objectWriter;

import java.io.IOException;
import cedarpolicy.model.AuthorizationQuery;
import cedarpolicy.model.AuthorizationResult;
import cedarpolicy.model.ValidationQuery;
import cedarpolicy.model.ValidationResult;
import cedarpolicy.model.exception.AuthException;
import cedarpolicy.model.exception.BadRequestException;
import cedarpolicy.model.exception.InternalException;
import cedarpolicy.model.slice.Slice;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** An authorization engine that is compiled in process. Communicated with via JNI. */
public final class WrapperAuthorizationEngine implements AuthorizationEngine {
    private static final Logger LOG = LoggerFactory.getLogger(WrapperAuthorizationEngine.class);

    static {
        System.loadLibrary("cedar_java_ffi");
    }

    /** Construct a wrapper authorization engine. */
    public WrapperAuthorizationEngine() {}

    @Override
    public AuthorizationResult isAuthorized(AuthorizationQuery q, Slice slice)
            throws AuthException {
        LOG.trace("Making an isAuthorized query:\n{}\nwith slice\n{}", q, slice);
        final AuthorizationRequest request = new AuthorizationRequest(q, slice);
        return call("AuthorizationOperation", AuthorizationResult.class, request);
    }

    @Override
    public ValidationResult validate(ValidationQuery q) throws AuthException {
        LOG.trace("Making a validate query:\n{}", q);
        return call("ValidateOperation", ValidationResult.class, q);
    }

    private static <REQ, RESP> RESP call(String operation, Class<RESP> responseClass, REQ request)
            throws AuthException {
        try {
            final String cedarJNIVersion = getCedarJNIVersion();
            if (!cedarJNIVersion.equals(AuthorizationEngine.getCedarLangVersion())) {
                throw new AuthException(
                        "Error, Java Cedar Language version is "
                                + AuthorizationEngine.getCedarLangVersion()
                                + " but JNI Cedar Language version is "
                                + cedarJNIVersion);
            }
            final String fullRequest = objectWriter().writeValueAsString(request);

            LOG.debug(
                    "Making a request ({}, {}) of length {} through the JNI interface:",
                    operation,
                    fullRequest.length());
            LOG.trace("The request:\n{}", fullRequest);

            final String response = callCedarJNI(operation, fullRequest);
            LOG.trace("Received response of length {}:\n{}", response.length(), response);

            final JsonNode responseNode = objectReader().readTree(response);
            boolean wasSuccessful = responseNode.path("success").asBoolean(false);
            if (wasSuccessful) {
                final String resultJson = responseNode.path("result").textValue();
                return objectReader().readValue(resultJson, responseClass);
            } else {
                final ErrorResponse error = objectReader().forType(ErrorResponse.class).readValue(responseNode);
                if (error.isInternal) {
                    throw new InternalException(error.errors);
                } else {
                    throw new BadRequestException(error.errors);
                }
            }
        } catch (JsonProcessingException e) {
            throw new AuthException("JSON Serialization Error", e);
        } catch (IllegalArgumentException e) {
            throw new AuthException("Authorization error caused by illegal argument exception.", e);
        } catch (IOException e) {
            throw new AuthException("JSON Deserialization Error", e);
        }
    }

    private static final class AuthorizationRequest extends AuthorizationQuery {
        @JsonProperty public final Slice slice;

        AuthorizationRequest(AuthorizationQuery query, Slice slice) {
            super(
                    query.principalEUID,
                    query.actionEUID,
                    query.resourceEUID,
                    query.context,
                    query.schema);
            this.slice = slice;
        }
    }

    private static final class ErrorResponse {
        public final boolean success, isInternal;
        public final String[] errors;

        @JsonCreator
        ErrorResponse(
                @JsonProperty("success") boolean success,
                @JsonProperty("isInternal") boolean isInternal,
                @JsonProperty("errors") String[] errors) {
            this.success = success;
            this.isInternal = isInternal;
            this.errors = errors;
        }
    }

    /**
     * Call out to the Rust implementation.
     *
     * @param call Call type ("AuthorizationOperation" or "ValidateOperation").
     * @param input Query input in JSON format as a String
     * @return The query result (permit / deny for authorization, valid / invalid for validation)
     */
    private static native String callCedarJNI(String call, String input);

    /**
     * Get the Cedar language major version supported by the JNI (e.g., "1.2")
     *
     * @return The Cedar language version supported by the JNI
     */
    private static native String getCedarJNIVersion();
}
