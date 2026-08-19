package com.proxychecker.infrastructure.output;

import com.proxychecker.domain.CheckResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Output writer strategy.
 */
public interface OutputFormatter {

    enum OutputFormat {
        json, csv
    }

    void write(List<CheckResult> results, Path output) throws IOException;
}