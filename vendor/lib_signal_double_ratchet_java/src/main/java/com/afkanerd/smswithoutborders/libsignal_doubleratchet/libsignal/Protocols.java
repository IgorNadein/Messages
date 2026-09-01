package com.afkanerd.smswithoutborders.libsignal_doubleratchet.libsignal;

import static com.afkanerd.smswithoutborders.libsignal_doubleratchet.CryptoHelpers.buildVerificationHash;
import static com.afkanerd.smswithoutborders.libsignal_doubleratchet.CryptoHelpers.getCipherMacParameters;
import static com.afkanerd.smswithoutborders.libsignal_doubleratchet.CryptoHelpers.verifyCipherText;

import android.util.Pair;

import com.afkanerd.smswithoutborders.libsignal_doubleratchet.CryptoHelpers;
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.SecurityAES;
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.SecurityCurve25519;
import com.google.common.primitives.Bytes;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.crypto.Mac;

/**
 * This implementations are based on the signal protocols specifications.
 *
 * This are based on the recommended algorithms and parameters for the encryption
 * and decryption.
 *
 * The goal for this would be to transform it into library which can be used across
 * other SMS projects.
 *
 * <a href="https://signal.org/docs/specifications/doubleratchet/">...</a>
 */
public class Protocols {
    final static int HKDF_LEN = 32;
    final static int HKDF_NUM_KEYS = 2;
    final static String ALGO = "HMACSHA512";

    public static Pair<byte[], byte[]> GENERATE_DH() {
        SecurityCurve25519 securityCurve25519 = new SecurityCurve25519();
        return new Pair<>(securityCurve25519.getPrivateKey(), securityCurve25519.generateKey());
    }

    /**
     *
     * @param dhPair This private key (keypair required in Android if supported)
     * @param peerPublicKey
     * @return
     * @throws GeneralSecurityException
     * @throws IOException
     * @throws InterruptedException
     */
    public static byte[] DH(Pair<byte[], byte[]> dhPair, byte[] peerPublicKey) {
        SecurityCurve25519 securityCurve25519 = new SecurityCurve25519(dhPair.first);
        return securityCurve25519.calculateSharedSecret(peerPublicKey);
    }

    public static Pair<byte[], byte[]> KDF_RK(byte[] rk, byte[] dhOut) throws GeneralSecurityException {
        byte[] info = "KDF_RK".getBytes();
        byte[][] hkdfOutput = CryptoHelpers.HKDF(ALGO, dhOut, rk, info, HKDF_LEN, HKDF_NUM_KEYS);
        return new Pair<>(hkdfOutput[0], hkdfOutput[1]);
    }

    public static Pair<byte[], byte[]> KDF_CK(byte[] ck) throws GeneralSecurityException {
//        Mac mac = CryptoHelpers.HMAC512(ck);
        Mac mac = CryptoHelpers.HMAC256(ck);
        byte[] _ck = mac.doFinal(new byte[]{0x01});
        byte[] mk = mac.doFinal(new byte[]{0x02});
        return new Pair<>(_ck, mk);
    }

    public static byte[] ENCRYPT(byte[] mk, byte[] plainText, byte[] associated_data) throws Throwable {
        byte[] hkdfOutput = null;
        byte[] key = new byte[32];
        byte[] authenticationKey = new byte[32];
        byte[] iv = new byte[16];
        byte[] cipherText = null;
        byte[] mac = null;

        try {
            hkdfOutput = getCipherMacParameters(ALGO, mk);
            System.arraycopy(hkdfOutput, 0, key, 0, 32);
            System.arraycopy(hkdfOutput, 32, authenticationKey, 0, 32);
            System.arraycopy(hkdfOutput, 64, iv, 0, 16);
            cipherText = SecurityAES.encryptAES256CBC(plainText, key, iv);
            mac = buildVerificationHash(authenticationKey, associated_data, cipherText).doFinal();
            return Bytes.concat(cipherText, mac);
        } finally {
            if(hkdfOutput != null) Arrays.fill(hkdfOutput, (byte) 0);
            if(cipherText != null) Arrays.fill(cipherText, (byte) 0);
            if(mac != null) Arrays.fill(mac, (byte) 0);
            Arrays.fill(key, (byte) 0);
            Arrays.fill(authenticationKey, (byte) 0);
            Arrays.fill(iv, (byte) 0);
            Arrays.fill(mk, (byte) 0);
        }
    }

    public static byte[] DECRYPT(byte[] mk, byte[] cipherText, byte[] associated_data) throws Throwable {
        byte[] authenticatedCipherText = null;
        byte[] hkdfOutput = null;
        byte[] key = new byte[32];
        byte[] iv = new byte[16];
        try {
            authenticatedCipherText = verifyCipherText(ALGO, mk, cipherText, associated_data);
            hkdfOutput = getCipherMacParameters(ALGO, mk);
            System.arraycopy(hkdfOutput, 0, key, 0, 32);
            System.arraycopy(hkdfOutput, 64, iv, 0, 16);
            return SecurityAES.decryptAES256CBC(authenticatedCipherText, key, iv);
        } finally {
            if(authenticatedCipherText != null) Arrays.fill(authenticatedCipherText, (byte) 0);
            if(hkdfOutput != null) Arrays.fill(hkdfOutput, (byte) 0);
            Arrays.fill(key, (byte) 0);
            Arrays.fill(iv, (byte) 0);
            Arrays.fill(mk, (byte) 0);
        }
    }

    public static byte[] CONCAT(byte[] AD, Headers headers) throws IOException {
        return Bytes.concat(AD, headers.getSerialized());
    }

}
