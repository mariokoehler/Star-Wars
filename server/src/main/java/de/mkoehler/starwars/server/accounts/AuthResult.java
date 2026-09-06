package de.mkoehler.starwars.server.accounts;

/**
 * The outcome of {@link AccountStore#login(String, String, String)}: either
 * a successful login (new or existing account) with the resulting
 * {@link PlayerAccount}, or a rejection with a human-readable reason to show
 * on the Connect Dialog (design.md 5.1).
 *
 * @param success whether the login succeeded
 * @param message a human-readable message - a welcome on success, a
 *                rejection reason on failure
 * @param account the account logged into, or {@code null} on failure
 */
public record AuthResult(boolean success, String message, PlayerAccount account) {

    /**
     * Creates a successful result.
     *
     * @param account the account that was logged into (or just created)
     * @param message a human-readable welcome message
     * @return the successful result
     */
    public static AuthResult success(PlayerAccount account, String message) {
        return new AuthResult(true, message, account);
    }

    /**
     * Creates a rejected result.
     *
     * @param message a human-readable rejection reason
     * @return the failed result
     */
    public static AuthResult failure(String message) {
        return new AuthResult(false, message, null);
    }
}
