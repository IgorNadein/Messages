package com.afkanerd.smswithoutborders.libsignal_doubleratchet;

import android.util.Base64;

import com.google.common.primitives.Bytes;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import at.favre.lib.hkdf.HKDF;

public class CryptoHelpers {

    public final static String pemStartPrefix = "-----BEGIN PUBLIC KEY-----\n";
    public final static String pemEndPrefix = "\n-----END PUBLIC KEY-----";

    public static byte[] getCipherMacParameters(String ALGO, byte[] mk) throws GeneralSecurityException {
        int hashLen = 80;
        byte[] info = "ENCRYPT".getBytes();
        byte[] salt = new byte[hashLen];
        Arrays.fill(salt, (byte) 0);

        return HKDF(ALGO, mk, salt, info, hashLen, 1)[0];
    }

    public static Mac buildVerificationHash(byte[] authKey, byte[] AD, byte[] cipherText) throws GeneralSecurityException {
        Mac mac = CryptoHelpers.HMAC256(authKey);
        byte[] updatedParams = Bytes.concat(AD, cipherText);
        mac.update(updatedParams);
        return mac;
    }

    public static byte[] verifyCipherText(String ALGO, byte[] mk, byte[] cipherText, byte[] AD) throws Exception {
        final int SHA256_DIGEST_LEN = 32;
        if(cipherText == null || cipherText.length <= SHA256_DIGEST_LEN)
            throw new GeneralSecurityException("Ciphertext is truncated");

        byte[] hkdfOutput = getCipherMacParameters(ALGO, mk);
        byte[] authenticationKey = new byte[32];
        byte[] macValue = new byte[SHA256_DIGEST_LEN];
        byte[] reconstructedMac = null;
        try {
            System.arraycopy(hkdfOutput, 32, authenticationKey, 0, 32);

            System.arraycopy(cipherText, cipherText.length - SHA256_DIGEST_LEN,
                    macValue, 0, SHA256_DIGEST_LEN);

            byte[] extractedCipherText = new byte[cipherText.length - SHA256_DIGEST_LEN];
            System.arraycopy(cipherText, 0, extractedCipherText,
                    0, extractedCipherText.length);

            reconstructedMac = buildVerificationHash(
                    authenticationKey, AD, extractedCipherText).doFinal();
            if(MessageDigest.isEqual(macValue, reconstructedMac)) {
                return extractedCipherText;
            }
            Arrays.fill(extractedCipherText, (byte) 0);
            throw new GeneralSecurityException("Cipher signature verification failed");
        } finally {
            Arrays.fill(hkdfOutput, (byte) 0);
            Arrays.fill(authenticationKey, (byte) 0);
            Arrays.fill(macValue, (byte) 0);
            if(reconstructedMac != null) Arrays.fill(reconstructedMac, (byte) 0);
        }
    }

    public static byte[][] HKDF(String algo, byte[] ikm, byte[] salt, byte[] info, int len, int num) throws GeneralSecurityException {
        if (num < 1)
            num = 1;

        HKDF hkdf = algo.equals("HMACSHA512") ? HKDF.fromHmacSha512() : HKDF.fromHmacSha256();
        byte[] output = hkdf.extractAndExpand(salt, ikm, info, len * num);
        byte[][] outputs = new byte[num][len];
        for (int i = 0; i < num; ++i) {
            System.arraycopy(output, i * len, outputs[i], 0, len);
        }
        Arrays.fill(output, (byte) 0);
        return outputs;
    }

    public static Mac HMAC256(byte[] data) throws GeneralSecurityException {
        String algorithm = "HmacSHA256";
        Mac hmacOutput = Mac.getInstance(algorithm);
        SecretKey key = new SecretKeySpec(data, algorithm);
        hmacOutput.init(key);
        return hmacOutput;
    }

    public static byte[] generateRandomBytes(int length) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new

                byte[length];
        random.nextBytes(bytes);
        return bytes;
    }
}
