package com.proxychecker;

import com.proxychecker.cli.CliOptions;
import picocli.CommandLine;

/**
 * Application entry point.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CliOptions()).execute(args);
        System.exit(exitCode);
    }
}