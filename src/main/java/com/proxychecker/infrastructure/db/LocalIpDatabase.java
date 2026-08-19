package com.proxychecker.infrastructure.db;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import com.proxychecker.domain.IpLocationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * In-memory IP range database using sorted arrays and binary search.
 */
public class LocalIpDatabase {

    private static final Logger log = LoggerFactory.getLogger(LocalIpDatabase.class);

    private final List<GeoRecord> geoRecords = new ArrayList<>();
    private final List<AsnRecord> asnRecords = new ArrayList<>();

    public void loadGeoDb(Path csvGzPath) {
        if (!Files.exists(csvGzPath)) {
            log.warn("Geo DB file not found: {}", csvGzPath);
            return;
        }

        List<GeoRecord> loaded = new ArrayList<>();
        try (Reader reader = openReader(csvGzPath);
             CSVReader csvReader = new CSVReaderBuilder(reader).withSkipLines(1).build()) {

            String[] line;
            while ((line = csvReader.readNext()) != null) {
                try {
                    if (line.length < 7) {
                        continue;
                    }
                    long start = IpLongConverter.ipToLong(line[0]);
                    long end = IpLongConverter.ipToLong(line[1]);
                    String countryCode = line[2];
                    String region = line[3];
                    String city = line[4];
                    Double latitude = parseDouble(line[5]);
                    Double longitude = parseDouble(line[6]);
                    loaded.add(new GeoRecord(start, end, countryCode, region, city, latitude, longitude));
                } catch (Exception e) {
                    log.debug("Skipping invalid geo record: {}", (Object) line, e);
                }
            }

            loaded.sort(Comparator.comparingLong(GeoRecord::getStartIp));
            synchronized (this) {
                geoRecords.clear();
                geoRecords.addAll(loaded);
            }
            log.info("Loaded {} geo records from {}", loaded.size(), csvGzPath);
        } catch (IOException | CsvValidationException e) {
            log.warn("Failed to load geo DB from {}: {}", csvGzPath, e.getMessage());
        }
    }

    public void loadAsnDb(Path csvGzPath) {
        if (!Files.exists(csvGzPath)) {
            log.warn("ASN DB file not found: {}", csvGzPath);
            return;
        }

        List<AsnRecord> loaded = new ArrayList<>();
        try (Reader reader = openReader(csvGzPath);
             CSVReader csvReader = new CSVReaderBuilder(reader).withSkipLines(1).build()) {

            String[] line;
            while ((line = csvReader.readNext()) != null) {
                try {
                    if (line.length < 4) {
                        continue;
                    }
                    long start = IpLongConverter.ipToLong(line[0]);
                    long end = IpLongConverter.ipToLong(line[1]);
                    Long asn = parseAsn(line[2]);
                    String asnName = line[3];
                    loaded.add(new AsnRecord(start, end, asn, asnName));
                } catch (Exception e) {
                    log.debug("Skipping invalid ASN record: {}", (Object) line, e);
                }
            }

            loaded.sort(Comparator.comparingLong(AsnRecord::getStartIp));
            synchronized (this) {
                asnRecords.clear();
                asnRecords.addAll(loaded);
            }
            log.info("Loaded {} ASN records from {}", loaded.size(), csvGzPath);
        } catch (IOException | CsvValidationException e) {
            log.warn("Failed to load ASN DB from {}: {}", csvGzPath, e.getMessage());
        }
    }

    public IpLocationInfo locate(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }

        final long ipLong;
        try {
            ipLong = IpLongConverter.ipToLong(ip);
        } catch (IllegalArgumentException e) {
            return null;
        }

        GeoRecord geo = findGeo(ipLong);
        AsnRecord asn = findAsn(ipLong);

        if (geo == null && asn == null) {
            return null;
        }

        IpLocationInfo info = new IpLocationInfo();
        if (geo != null) {
            info.setCountryCode(geo.getCountryCode());
            info.setRegion(geo.getRegion());
            info.setCity(geo.getCity());
            info.setLatitude(geo.getLatitude());
            info.setLongitude(geo.getLongitude());
        }
        if (asn != null) {
            info.setAsn(asn.getAsn());
            info.setAsnName(asn.getAsnName());
        }
        return info;
    }

    private GeoRecord findGeo(long ip) {
        if (geoRecords.isEmpty()) {
            return null;
        }

        // Use Collections.binarySearch with a dummy key
        int idx = Collections.binarySearch(
                geoRecords,
                new GeoRecord(ip, 0, null, null, null, null, null),
                Comparator.comparingLong(GeoRecord::getStartIp));

        if (idx >= 0) {
            GeoRecord rec = geoRecords.get(idx);
            return rec.getEndIp() >= ip ? rec : null;
        }

        int insertion = -idx - 1;
        if (insertion == 0) {
            return null;
        }

        GeoRecord previous = geoRecords.get(insertion - 1);
        if (previous.getStartIp() <= ip && previous.getEndIp() >= ip) {
            return previous;
        }
        return null;
    }

    private AsnRecord findAsn(long ip) {
        if (asnRecords.isEmpty()) {
            return null;
        }

        int idx = Collections.binarySearch(
                asnRecords,
                new AsnRecord(ip, 0, null, null),
                Comparator.comparingLong(AsnRecord::getStartIp));

        if (idx >= 0) {
            AsnRecord rec = asnRecords.get(idx);
            return rec.getEndIp() >= ip ? rec : null;
        }

        int insertion = -idx - 1;
        if (insertion == 0) {
            return null;
        }

        AsnRecord previous = asnRecords.get(insertion - 1);
        if (previous.getStartIp() <= ip && previous.getEndIp() >= ip) {
            return previous;
        }
        return null;
    }

    private Reader openReader(Path path) throws IOException {
        InputStream inputStream = Files.newInputStream(path);
        if (path.toString().endsWith(".gz")) {
            inputStream = new GZIPInputStream(inputStream);
        }
        return new InputStreamReader(inputStream, StandardCharsets.UTF_8);
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseAsn(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cleaned = value;
        if (cleaned.toUpperCase().startsWith("AS")) {
            cleaned = cleaned.substring(2);
        }
        try {
            return Long.parseLong(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}