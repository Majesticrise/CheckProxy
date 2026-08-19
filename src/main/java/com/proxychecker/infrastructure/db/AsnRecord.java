package com.proxychecker.infrastructure.db;

/**
 * One row from the ASN CSV.
 */
public class AsnRecord {

    private final long startIp;
    private final long endIp;
    private final Long asn;
    private final String asnName;

    public AsnRecord(long startIp, long endIp, Long asn, String asnName) {
        this.startIp = startIp;
        this.endIp = endIp;
        this.asn = asn;
        this.asnName = asnName;
    }

    public long getStartIp() {
        return startIp;
    }

    public long getEndIp() {
        return endIp;
    }

    public Long getAsn() {
        return asn;
    }

    public String getAsnName() {
        return asnName;
    }
}