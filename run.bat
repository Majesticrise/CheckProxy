java -Xmx1g -jar target/proxy-checker-1.0.0.jar -f proxies.txt --geo-db ./.data/ipv4_ip_location.csv.gz --asn-db ./.data/asn_ipv4_prefixes.csv.gz -o result.json --format json -t 5 -c 1024
pause