package zuwagon.zutracklib;

/**
 * This interface must implemented by status listeners.
 */
public interface ZWStatusCallback {
    /**
     * Fires when new status received.
     *
     * @param code {@link ZWStatus ZWStatus} code.
     */
    void onStatus(int code);

    String BASE_URL = "https://api.zuwagon.com";
    String CALL_API = "CALL_API";
}
