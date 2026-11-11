package com.integration.framework.tracking;

import java.security.SecureRandom;

/** Generates request ids: {@code <APP>-<12 random alphanumeric characters>}. */
public class RequestIdGenerator {

    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private static final int RANDOM_LENGTH = 12;

    private final String appShortName;
    private final SecureRandom random = new SecureRandom();

    public RequestIdGenerator(String appShortName) {
        this.appShortName = appShortName;
    }

    public String next() {
        StringBuilder id = new StringBuilder(appShortName).append('-');
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            id.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return id.toString();
    }
}
