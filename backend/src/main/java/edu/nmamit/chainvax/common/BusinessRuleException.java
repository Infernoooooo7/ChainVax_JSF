package edu.nmamit.chainvax.common;

/** A domain rule was violated (HTTP 409 with a machine-readable code). */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
