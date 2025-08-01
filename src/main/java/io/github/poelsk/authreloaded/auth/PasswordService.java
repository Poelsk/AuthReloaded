package io.github.poelsk.authreloaded.auth;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordService {

    private static final int WORKLOAD = 12;

    public String hashPassword(String plaintextPassword) {
        String salt = BCrypt.gensalt(WORKLOAD);
        return BCrypt.hashpw(plaintextPassword, salt);
    }

    public boolean checkPassword(String plaintextPassword, String hashedPassword) {
        if (hashedPassword == null ||!hashedPassword.startsWith("$2a$")) {
            return false;
        }
        try {
            return BCrypt.checkpw(plaintextPassword, hashedPassword);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}