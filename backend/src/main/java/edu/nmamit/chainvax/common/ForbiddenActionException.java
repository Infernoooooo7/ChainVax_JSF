package edu.nmamit.chainvax.common;

/** The user's role allows the endpoint, but not on this particular site/batch (HTTP 403). */
public class ForbiddenActionException extends RuntimeException {

    public ForbiddenActionException(String message) {
        super(message);
    }
}
