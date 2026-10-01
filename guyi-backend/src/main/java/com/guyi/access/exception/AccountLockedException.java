package com.guyi.access.exception;

/**
 * Raised when an account is temporarily locked after too many failed logins.
 *
 * <p>Extends {@link BusinessException} so existing handling keeps working, while the login endpoint
 * can map it to HTTP 429 instead of the generic 401.
 */
public class AccountLockedException extends BusinessException {

    public AccountLockedException(String message) {
        super(message);
    }
}
