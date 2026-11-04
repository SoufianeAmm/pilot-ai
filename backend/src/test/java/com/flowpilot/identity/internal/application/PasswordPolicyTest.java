package com.flowpilot.identity.internal.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.flowpilot.identity.internal.application.AuthExceptions.WeakPasswordException;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @ParameterizedTest
    @ValueSource(strings = {"exactly12chr", "correct horse battery staple", "ünïcödé-pässwörd"})
    void validate_acceptablePasswords_pass(String password) {
        assertThatCode(() -> policy.validate(password)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "            ", "elevenchars"})
    void validate_missingBlankOrShort_isRejected(String password) {
        assertThatThrownBy(() -> policy.validate(password)).isInstanceOf(WeakPasswordException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {129, 1_000})
    void validate_tooLong_isRejected(int length) {
        assertThatThrownBy(() -> policy.validate("a".repeat(length))).isInstanceOf(WeakPasswordException.class);
    }
}
