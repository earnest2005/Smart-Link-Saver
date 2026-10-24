package com.smartlinksaver.service;

/**
 * Checked exception for anything that stops a link operation:
 * invalid input, duplicates, missing links or storage failures.
 * The message is written to be shown directly to the user.
 */
public class LinkException extends Exception {

    public LinkException(String message) {
        super(message);
    }

    public LinkException(String message, Throwable cause) {
        super(message, cause);
    }
}
