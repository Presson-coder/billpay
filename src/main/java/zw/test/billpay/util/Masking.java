package zw.test.billpay.util;

/**
 * Masks customer identifiers before they are logged (data protection).
 */
public final class Masking {

    private static final int VISIBLE_DIGITS = 4;

    private Masking() {
    }

    /** "04123456780" becomes "*******6780". */
    public static String mask(String value) {
        if (value == null || value.length() <= VISIBLE_DIGITS) {
            return "****";
        }
        int hidden = value.length() - VISIBLE_DIGITS;
        return "*".repeat(hidden) + value.substring(hidden);
    }
}
