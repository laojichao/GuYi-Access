package com.guyi.access.util;

import java.security.SecureRandom;

public class CardCodeGenerator {

    private static final String KEYSPACE = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generate(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(KEYSPACE.charAt(RANDOM.nextInt(KEYSPACE.length())));
        }
        return sb.toString();
    }

    public static String generateWithPrefix(String prefix, int length) {
        return prefix + generate(length);
    }
}
