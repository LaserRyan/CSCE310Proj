package bookstore.client.api;

/** A request the server refused (status >= 400) or could not be reached (status 0). */
public class ApiException extends RuntimeException {
    private final int status;
    private final boolean sentToken;

    public ApiException(int status, String message) {
        this(status, message, false);
    }

    public ApiException(int status, String message, boolean sentToken) {
        super(message);
        this.status = status;
        this.sentToken = sentToken;
    }

    public int status() {
        return status;
    }

    /** A logged-in call was refused because the session ended (not a wrong password at login). */
    public boolean isSessionExpired() {
        return status == 401 && sentToken;
    }
}
