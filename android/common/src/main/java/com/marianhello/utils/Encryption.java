package com.marianhello.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import java.security.NoSuchAlgorithmException;
import java.security.spec.KeySpec;
import java.security.spec.InvalidKeySpecException;

import java.util.Base64;

/**
 * AES-CBC inputs - 16 bytes IV, need the same IV and secret keys for encryption and decryption.
 * <p>
 * The output consist of IV, password's SALT, encrypted content and auth tag in the following format:
 * output = byte[] {i i i s s s c c c c c c ...}
 * <p>
 * i = IV bytes
 * s = Salt bytes
 * c = content bytes (encrypted content)
 */
public class Encryption {

    private static final String ENCRYPT_ALGO = "AES/GCM/NoPadding";

    private static final int TAG_LENGTH_BIT = 128; // must be one of {128, 120, 112, 104, 96}
    private static final int IV_LENGTH_BYTE = 12;
    private static final int SALT_LENGTH_BYTE = 16;
    private static final Charset UTF_8 = StandardCharsets.UTF_8;
    private static final byte[] SALT = new byte[SALT_LENGTH_BYTE]; // 16 bytes SALT
    private static final byte[] IV = new byte[IV_LENGTH_BYTE]; // GCM recommended 12 bytes IV?

    // return a base64 encoded AES encrypted text
    public static String encrypt(String message, String password) throws Exception {
        byte[] messageBytes = message.getBytes(UTF_8);

        // secret key from password
        SecretKey aesKeyFromPassword = getAESKeyFromPassword(password.toCharArray(), SALT);

        Cipher cipher = Cipher.getInstance(ENCRYPT_ALGO);

        // ASE-GCM needs GCMParameterSpec
        cipher.init(Cipher.ENCRYPT_MODE, aesKeyFromPassword, new GCMParameterSpec(TAG_LENGTH_BIT, IV));

        byte[] cipherText = cipher.doFinal(messageBytes);

        // string representation, base64, send this string to other for decryption.
        return fromByteArrayToHexString(cipherText);
    }

    // we need the same password, SALT and IV to decrypt it
    public static String decrypt(String message, String password) throws Exception {
        byte[] cipherText = fromHexStringToByteArray(message);

        // get back the aes key from the same password and SALT
        SecretKey aesKeyFromPassword = getAESKeyFromPassword(password.toCharArray(), SALT);

        Cipher cipher = Cipher.getInstance(ENCRYPT_ALGO);

        cipher.init(Cipher.DECRYPT_MODE, aesKeyFromPassword, new GCMParameterSpec(TAG_LENGTH_BIT, IV));

        byte[] plainText = cipher.doFinal(cipherText);

        return new String(plainText, UTF_8);
    }

    // AES 256 bits secret key derived from a password
    public static SecretKey getAESKeyFromPassword(char[] password, byte[] SALT)
            throws NoSuchAlgorithmException, InvalidKeySpecException {

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password, SALT, 65536, 256);
        SecretKey secret = new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
        return secret;

    }

    public static String fromByteArrayToHexString(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    public static byte[] fromHexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len/2];
        for (int i = 0; i < len; i += 2) {
            data[i/2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

}