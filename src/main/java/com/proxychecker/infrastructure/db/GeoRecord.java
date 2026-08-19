package com.proxychecker.infrastructure.db;

/**
 * One row from the GeoLite2 CSV.
 */
public class GeoRecord {

    private final long startIp;
    private final long endIp;
    private final String countryCode;
    private final String region;
    private final String city;
    private final Double latitude;
    private final Double longitude;

    public GeoRecord(long startIp, long endIp, String countryCode, String region,
                     String city, Double latitude, Double longitude) {
        this.startIp = startIp;
        this.endIp = endIp;
        this.countryCode = countryCode;
        this.region = region;
        this.city = city;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public long getStartIp() {
        return startIp;
    }

    public long getEndIp() {
        return endIp;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getRegion() {
        return region;
    }

    public String getCity() {
        return city;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }
}