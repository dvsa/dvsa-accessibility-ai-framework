package otp;


import java.lang.reflect.UndeclaredThrowableException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class TimeBasedOneTimePasswordGenerator {
    private static final int[] DIGITS_POWER = {1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000};
    private static final String SECRET_KEY_SPEC_ALGORITHM = "RAW";
    private static final String SINGLE_BYTE_FOR_CONVERSION = "10";
    private static final String ONE_TIME_PASSWORD_PAD = "0";
    private static final int HEX_RADIX = 16;
    private static final int MASK_255 = 0xff;
    private static final int MASK_127 = 0x7f;
    private static final int MASK_15 = 0xf;
    private static final int SHIFT_24 = 24;
    private static final int SHIFT_16 = 16;
    private static final int SHIFT_8 = 8;

    private TimeBasedOneTimePasswordGenerator() {
        //private constructor to force static

    }

    /**
     * This method generates a TOTP value for the given set of parameters.
     *
     * @param key        : the shared secret, HEX encoded
     * @param time       : a value that reflects a time
     * @param codeDigits : number of digits to return
     * @param crypto     : the crypto function to use
     * @return: a numeric String in base 10 that includes truncationDigits digits
     */
    public static String generateTOTP(String key, String time, int codeDigits, String crypto) {
        // Using the counter
        // First 8 bytes are for the movingFactor
        // Compliant with base RFC 4226 (HOTP)
        String paddedTime = time;
        while (paddedTime.length() < HEX_RADIX) {
            paddedTime = ONE_TIME_PASSWORD_PAD + paddedTime;
        }

        // Get the HEX in a Byte[]
        byte[] hash = hmacSha(crypto, hexStr2Bytes(key), hexStr2Bytes(paddedTime));

        // put selected bytes into result int
        int offset = hash[hash.length - 1] & MASK_15;
        int binary = ((hash[offset] & MASK_127) << SHIFT_24) | ((hash[offset + 1] & MASK_255) << SHIFT_16)
                | ((hash[offset + 2] & MASK_255) << SHIFT_8) | (hash[offset + 3] & MASK_255);
        int otp = binary % DIGITS_POWER[codeDigits];

        String oneTimePassword = Integer.toString(otp);
        while (oneTimePassword.length() < codeDigits) {
            oneTimePassword = ONE_TIME_PASSWORD_PAD + oneTimePassword;
        }
        return oneTimePassword;
    }

    /**
     * This method converts a HEX string to Byte[]
     *
     * @param hex : the HEX string
     * @return: a byte array
     */
    private static byte[] hexStr2Bytes(String hex) {
        // Adding one byte to get the right conversion
        // Values starting with "0" can be converted
        byte[] hexAsByteArray = new BigInteger(SINGLE_BYTE_FOR_CONVERSION + hex, HEX_RADIX).toByteArray();

        // Copy all the REAL bytes, not the "first"
        byte[] tailBytes = new byte[hexAsByteArray.length - 1];
        for (int i = 0; i < tailBytes.length; i++) {
            tailBytes[i] = hexAsByteArray[i + 1];
        }
        return tailBytes;
    }

    /**
     * This method uses the JCE to provide the crypto algorithm. HMAC computes a Hashed Message Authentication Code with
     * the crypto hash algorithm as a parameter.
     *
     * @param crypto   : the crypto algorithm (HmacSHA1, HmacSHA256, HmacSHA512)
     * @param keyBytes : the bytes to use for the HMAC key
     * @param text     : the message or text to be authenticated
     */
    private static byte[] hmacSha(String crypto, byte[] keyBytes, byte[] text) {
        try {
            Mac hashedMessageAuthenticationCode;
            hashedMessageAuthenticationCode = Mac.getInstance(crypto);
            SecretKeySpec macKey = new SecretKeySpec(keyBytes, SECRET_KEY_SPEC_ALGORITHM);
            hashedMessageAuthenticationCode.init(macKey);
            return hashedMessageAuthenticationCode.doFinal(text);
        } catch (GeneralSecurityException gse) {
            throw new UndeclaredThrowableException(gse);
        }
    }
}