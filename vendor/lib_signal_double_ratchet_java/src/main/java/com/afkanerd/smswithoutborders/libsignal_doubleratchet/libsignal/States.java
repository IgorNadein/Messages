package com.afkanerd.smswithoutborders.libsignal_doubleratchet.libsignal;

import android.util.Pair;
import android.util.Base64;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class States {
    public Pair<byte[], byte[]> DHs;
    public byte[] DHr;
    public byte[] RK;
    public byte[] CKs;
    public byte[] CKr;

    public int Ns = 0;
    public int Nr = 0;
    public int PN = 0;

    public Map<Pair<byte[], Integer>, byte[]> MKSKIPPED = new HashMap<>();

    public States(String states) throws JSONException {
        if(states == null)
            throw new JSONException("Ratchet state is null");

        JSONObject jsonObject = new JSONObject(states);
        if(jsonObject.has(FIELD_SCHEMA) &&
                jsonObject.getInt(FIELD_SCHEMA) != STATE_SCHEMA_VERSION)
            throw new JSONException("Unsupported ratchet state schema");
        if(jsonObject.has(FIELD_DHS)) {
            String[] encodedValues = jsonObject.getString(FIELD_DHS).split(" ");
            if(encodedValues.length != 2)
                throw new JSONException("Invalid DHs key pair");
            this.DHs = new Pair<>(android.util.Base64.decode(encodedValues[0], Base64.NO_WRAP),
                    android.util.Base64.decode(encodedValues[1], Base64.NO_WRAP));
            requireLength(this.DHs.first, 32, FIELD_DHS);
            requireLength(this.DHs.second, 32, FIELD_DHS);
        }
        if(jsonObject.has(FIELD_DHR)) {
            this.DHr = Base64.decode(jsonObject.getString(FIELD_DHR), Base64.NO_WRAP);
            requireLength(this.DHr, 32, FIELD_DHR);
        }

        if(jsonObject.has(FIELD_RK)) {
            this.RK = Base64.decode(jsonObject.getString(FIELD_RK), Base64.NO_WRAP);
            requireLength(this.RK, 32, FIELD_RK);
        }
        if(jsonObject.has(FIELD_CKS)) {
            this.CKs = Base64.decode(jsonObject.getString(FIELD_CKS), Base64.NO_WRAP);
            requireLength(this.CKs, 32, FIELD_CKS);
        }
        if(jsonObject.has(FIELD_CKR)) {
            this.CKr = Base64.decode(jsonObject.getString(FIELD_CKR), Base64.NO_WRAP);
            requireLength(this.CKr, 32, FIELD_CKR);
        }
        this.Ns = requireCounter(jsonObject, FIELD_NS);
        this.Nr = requireCounter(jsonObject, FIELD_NR);
        this.PN = requireCounter(jsonObject, FIELD_PN);

        JSONArray mkskipped = jsonObject.optJSONArray(FIELD_SKIPPED);
        if(mkskipped == null) mkskipped = new JSONArray();
        for(int i=0;i<mkskipped.length();++i) {
            JSONObject pair = mkskipped.getJSONObject(i);
            byte[] pubkey = Base64.decode(pair.getString(StatesMKSKIPPED.PUBLIC_KEY), Base64.NO_WRAP);
            byte[] messageKey = Base64.decode(
                    pair.getString(StatesMKSKIPPED.MK), Base64.NO_WRAP);
            requireLength(pubkey, 32, StatesMKSKIPPED.PUBLIC_KEY);
            requireLength(messageKey, 32, StatesMKSKIPPED.MK);
            int count = pair.getInt(StatesMKSKIPPED.N);
            if(count < 0) throw new JSONException("Negative skipped-message counter");
            this.MKSKIPPED.put(new Pair<>(pubkey, count), messageKey);
        }
    }

    public static byte[] getADForHeaders(States states, Headers headers) {
        for(Map.Entry<Pair<byte[], Integer>, byte[]> entry : states.MKSKIPPED.entrySet()) {
            if(entry.getKey().second == (headers.PN + headers.N))
                return entry.getKey().first;
        }

        return null;
    }

    public States() {
    }

    public String getSerializedStates() {
        try {
            JSONObject output = new JSONObject();
            output.put(FIELD_SCHEMA, STATE_SCHEMA_VERSION);
            if(DHs != null) {
                output.put(FIELD_DHS,
                        Base64.encodeToString(DHs.first, Base64.NO_WRAP) + " " +
                        Base64.encodeToString(DHs.second, Base64.NO_WRAP));
            }
            putBytes(output, FIELD_DHR, DHr);
            putBytes(output, FIELD_RK, RK);
            putBytes(output, FIELD_CKS, CKs);
            putBytes(output, FIELD_CKR, CKr);
            output.put(FIELD_NS, Ns);
            output.put(FIELD_NR, Nr);
            output.put(FIELD_PN, PN);

            JSONArray skipped = new JSONArray();
            for(Map.Entry<Pair<byte[], Integer>, byte[]> entry : MKSKIPPED.entrySet()) {
                JSONObject item = new JSONObject();
                item.put(StatesMKSKIPPED.PUBLIC_KEY,
                        Base64.encodeToString(entry.getKey().first, Base64.NO_WRAP));
                item.put(StatesMKSKIPPED.N, entry.getKey().second);
                item.put(StatesMKSKIPPED.MK,
                        Base64.encodeToString(entry.getValue(), Base64.NO_WRAP));
                skipped.put(item);
            }
            output.put(FIELD_SKIPPED, skipped);
            return output.toString();
        } catch(JSONException error) {
            throw new IllegalStateException("Unable to serialize ratchet state", error);
        }
    }

    private static void putBytes(JSONObject output, String name, byte[] value)
            throws JSONException {
        if(value != null) output.put(name, Base64.encodeToString(value, Base64.NO_WRAP));
    }

    private static int requireCounter(JSONObject input, String name) throws JSONException {
        int value = input.getInt(name);
        if(value < 0) throw new JSONException("Negative ratchet counter: " + name);
        return value;
    }

    private static void requireLength(byte[] value, int expected, String name)
            throws JSONException {
        if(value == null || value.length != expected)
            throw new JSONException("Invalid length for " + name);
    }

    private static final int STATE_SCHEMA_VERSION = 2;
    private static final String FIELD_SCHEMA = "schema";
    private static final String FIELD_DHS = "DHs";
    private static final String FIELD_DHR = "DHr";
    private static final String FIELD_RK = "RK";
    private static final String FIELD_CKS = "CKs";
    private static final String FIELD_CKR = "CKr";
    private static final String FIELD_NS = "Ns";
    private static final String FIELD_NR = "Nr";
    private static final String FIELD_PN = "PN";
    private static final String FIELD_SKIPPED = "MKSKIPPED";

    @Override
    public boolean equals(@Nullable Object obj) {
        if(obj instanceof States state) {
            return state.getSerializedStates().equals(this.getSerializedStates());
        }
        return false;
    }

    public static class StatesMKSKIPPED {
        public final static String PUBLIC_KEY = "PUBLIC_KEY";
        public final static String N = "N";
        public final static String MK = "MK";
    }

}
