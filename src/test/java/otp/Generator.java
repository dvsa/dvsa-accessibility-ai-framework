package otp;

public class Generator {
    private static final String CRYPTO_TYPE = "HmacSHA1";
    private static final int TIME_BLOCK_IN_SECONDS = 30;
    private static final int MILLIS_IN_A_SECOND = 1000;
    private static final int NUMBER_OF_DIGITS = 6;

    public static void main(String[] args) {

        if(args.length == 0) {
            System.out.println("./2fa-generator <card_secret>");
            System.exit(1);
        }
        System.out.println(generatePin(args[0]));

    }

    public static String generatePin(String seed) {
        return generatePin(seed, System.currentTimeMillis());
    }

    public static String generatePin(String seed, long timeInMilliseconds) {
        if (timeInMilliseconds == 0) {
            timeInMilliseconds = System.currentTimeMillis();
        }
        long currentTimeSeconds = timeInMilliseconds / MILLIS_IN_A_SECOND;
        String step = Long.toHexString(currentTimeSeconds / TIME_BLOCK_IN_SECONDS).toUpperCase();
        return TimeBasedOneTimePasswordGenerator.generateTOTP(seed, step, NUMBER_OF_DIGITS, CRYPTO_TYPE);
    }
}