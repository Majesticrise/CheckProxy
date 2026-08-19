package com.proxychecker.infrastructure.output;

import com.opencsv.CSVWriter;
import com.proxychecker.domain.CheckResult;
import com.proxychecker.domain.IpLocationInfo;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * CSV output writer. Flattens location object into separate columns.
 */
public class CsvOutputWriter implements OutputFormatter {

    @Override
    public void write(List<CheckResult> results, Path output) throws IOException {
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }

        try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8);
             CSVWriter csvWriter = new CSVWriter(
                     writer,
                     CSVWriter.DEFAULT_SEPARATOR,
                     CSVWriter.DEFAULT_QUOTE_CHARACTER,
                     CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                     CSVWriter.DEFAULT_LINE_END)) {

            csvWriter.writeNext(new String[]{
                    "proxy", "working", "responseTimeMs", "exitIp",
                    "countryCode", "region", "city", "latitude", "longitude", "asn", "asnName"
            });

            for (CheckResult r : results) {
                IpLocationInfo loc = r.getLocation();
                String[] row = new String[]{
                        nvl(r.getProxy()),
                        String.valueOf(r.isWorking()),
                        String.valueOf(r.getResponseTimeMs()),
                        nvl(r.getExitIp()),
                        loc != null ? nvl(loc.getCountryCode()) : "",
                        loc != null ? nvl(loc.getRegion()) : "",
                        loc != null ? nvl(loc.getCity()) : "",
                        loc != null && loc.getLatitude() != null ? String.valueOf(loc.getLatitude()) : "",
                        loc != null && loc.getLongitude() != null ? String.valueOf(loc.getLongitude()) : "",
                        loc != null && loc.getAsn() != null ? String.valueOf(loc.getAsn()) : "",
                        loc != null ? nvl(loc.getAsnName()) : ""
                };
                csvWriter.writeNext(row);
            }
        }
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }
}