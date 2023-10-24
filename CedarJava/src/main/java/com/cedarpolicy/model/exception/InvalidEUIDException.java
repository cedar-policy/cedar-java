package com.cedarpolicy.model.exception;

/**
 * Thrown when an invalid EUID is parsed
 */
public class InvalidEUIDException extends Exception {

    public InvalidEUIDException(String message) {
        super(message);
    }
    
}
