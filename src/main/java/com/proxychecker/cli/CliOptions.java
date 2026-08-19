package com.proxychecker.cli;

import com.proxychecker.domain.CheckResult;
import com.proxychecker.domain.ProxyInfo;
import com.proxychecker.infrastructure.db.LocalIpDatabase;
import com.proxychecker.infrastructure.output.CsvOutputWriter;
import com.proxychecker.infrastructure.output.JsonOutputWriter;
import com.proxychecker.infrastructure.output.OutputFormatter;
import com.proxychecker.parser.ProxyListParser;
import com.proxychecker.service.ProxyCheckingService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(
        name = "proxychecker",
        mixinStandardHelpOptions = true,
        version = "proxy-checker 1.0.0",
        description = "Batch proxy availability checker supporting HTTP, HTTPS, SOCKS4 and SOCKS5."
)
public class CliOptions implements Callable<Integer> {

    @Option(names = {"-f", "--file"}, required = true, description = "Proxy list file path")
    private Path file;

    @Option(names = {"-t", "--timeout"}, defaultValue = "5", description = "Timeout in seconds for connect/read")
    private int timeout;

    @Option(names = {"-c", "--concurrency"}, defaultValue = "200", description = "Maximum concurrent checks")
    private int concurrency;

    @Option(names = {"--geo-db"}, description = "GeoLite2 CSV.GZ path (ip_from, ip_to, country_code, region, city, latitude, longitude)")
    private Path geoDb;

    @Option(names = {"--asn-db"}, description = "ASN CSV.GZ path (start_ip, end_ip, as_number, as_name)")
    private Path asnDb;

    @Option(names = {"-o", "--output"}, defaultValue = "result.json", description = "Output file path")
    private Path output;

    @Option(names = {"--format"}, defaultValue = "json", description = "Output format: json or csv")
    private OutputFormatter.OutputFormat format;

    @Option(names = {"--test-url"}, defaultValue = "https://httpbin.org/ip",
            description = "Test URL that returns JSON with 'origin' field (default: https://httpbin.org/ip)")
    private String testUrl;

    @Override
    public Integer call() throws Exception {
        if (timeout <= 0) {
            System.err.println("Timeout must be a positive integer.");
            return 1;
        }
        if (concurrency <= 0) {
            System.err.println("Concurrency must be a positive integer.");
            return 1;
        }

        List<ProxyInfo> proxies = ProxyListParser.parse(file);
        if (proxies.isEmpty()) {
            System.err.println("No valid proxies found in file: " + file);
            return 1;
        }

        LocalIpDatabase ipDatabase = new LocalIpDatabase();
        if (geoDb != null) {
            ipDatabase.loadGeoDb(geoDb);
        }
        if (asnDb != null) {
            ipDatabase.loadAsnDb(asnDb);
        }

        long timeoutMillis = timeout * 1000L;
        ProxyCheckingService service = new ProxyCheckingService(timeoutMillis, concurrency, ipDatabase, testUrl);
        List<CheckResult> allResults = service.checkAll(proxies);

        // 过滤出可用的，并按响应时间升序排序（最快的在前面）
        List<CheckResult> workingResults = allResults.stream()
                .filter(CheckResult::isWorking)
                .sorted(Comparator.comparingLong(CheckResult::getResponseTimeMs))
                .collect(Collectors.toList());

        System.out.println("Total proxies: " + allResults.size() + ", Working: " + workingResults.size());

        OutputFormatter formatter = switch (format) {
            case json -> new JsonOutputWriter();
            case csv -> new CsvOutputWriter();
        };
        formatter.write(workingResults, output);

        System.out.println("Results written to " + output.toAbsolutePath());
        return 0;
    }
}