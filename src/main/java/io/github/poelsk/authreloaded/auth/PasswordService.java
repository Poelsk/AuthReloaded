package io.github.poelsk.authreloaded.auth;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordService {

    private static final int WORKLOAD = 12;
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 128;

    public enum ValidationResult {
        VALID,
        TOO_SHORT,
        TOO_LONG,
        CONTAINS_SPACES,
        EMPTY_OR_NULL
    }

    public ValidationResult validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            return ValidationResult.EMPTY_OR_NULL;
        }

        if (password.length() < MIN_PASSWORD_LENGTH) {
            return ValidationResult.TOO_SHORT;
        }

        if (password.length() > MAX_PASSWORD_LENGTH) {
            return ValidationResult.TOO_LONG;
        }

        if (password.contains(" ")) {
            return ValidationResult.CONTAINS_SPACES;
        }

        return ValidationResult.VALID;
    }
    public String hashPassword(String plaintextPassword) {
        String salt = BCrypt.gensalt(WORKLOAD);
        return BCrypt.hashpw(plaintextPassword, salt);
    }
    public boolean checkPassword(String plaintextPassword, String hashedPassword) {
        if (hashedPassword == null || !hashedPassword.startsWith("$2a$")) {
            return false;
        }
        try {
            return BCrypt.checkpw(plaintextPassword, hashedPassword);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    public int getMinPasswordLength() {
        return MIN_PASSWORD_LENGTH;
    }

    public int getMaxPasswordLength() {
        return MAX_PASSWORD_LENGTH;
    }
}