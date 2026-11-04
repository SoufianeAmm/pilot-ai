package com.flowpilot.identity.internal.application;

import org.springframework.stereotype.Component;

import com.flowpilot.identity.internal.application.AuthExceptions.WeakPasswordException;

/**
 * Length-based policy following current NIST guidance (SP 800-63B): long passphrases are
 * allowed and no arbitrary composition rules are imposed. The upper bound limits hashing cost.
 */
@Component
public class PasswordPolicy {

    static final int MIN_LENGTH = 12;
    static final int MAX_LENGTH = 128;

    public void validate(String password) {
        if (password == null || password.isBlank()
                || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new WeakPasswordException(
                    "The password must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters long.");
        }
    }
}
