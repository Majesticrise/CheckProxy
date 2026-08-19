package com.proxychecker.parser;

import com.proxychecker.domain.ProxyInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Reads and parses proxy list file.
 */
public final class ProxyListParser {

    private static final Logger log = LoggerFactory.getLogger(ProxyListParser.class);

    private ProxyListParser() {
    }

    public static List<ProxyInfo> parse(Path file) throws IOException {
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return lines.map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(line -> {
                        try {
                            return ProxyInfo.parse(line);
                        } catch (Exception e) {
                            log.warn("Skipping invalid proxy line '{}': {}", line, e.getMessage());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();
        }
    }
}