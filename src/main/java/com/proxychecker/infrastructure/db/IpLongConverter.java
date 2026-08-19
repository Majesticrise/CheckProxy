package com.proxychecker.infrastructure.db;

/**
 * Converts dotted-decimal IPv4 to unsigned 32-bit long.
 */
public final class IpLongConverter {

    private IpLongConverter() {
    }

    public static long ipToLong(String ip) {
        String[] parts = ip.trim().split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid IPv4 address: " + ip);
        }

        long result = 0;
        for (String part : parts) {
            int octet;
            try {
                octet = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid IPv4 address: " + ip, e);
            }
            if (octet < 0 || octet > 255) {
                throw new IllegalArgumentException("Invalid IPv4 address: " + ip);
            }
            result = (result << 8) | octet;
        }
        return result;
    }

    public static String longToIp(long value) {
        return ((value >> 24) & 0xFF) + "." +
                ((value >> 16) & 0xFF) + "." +
                ((value >> 8) & 0xFF) + "." +
                (value & 0xFF);
    }
}