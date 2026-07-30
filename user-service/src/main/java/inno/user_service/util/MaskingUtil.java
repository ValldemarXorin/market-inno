package inno.user_service.util;

public final class MaskingUtil {

    private MaskingUtil() {
    }

    public static String maskCardNumber(String number) {
        if (number == null || number.length() < 13) {
            return "****";
        }
        return "**** **** **** " + number.substring(number.length() - 4);
    }
}
