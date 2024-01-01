#!/bin/bash

set -e

getCurrentTime() {
    date +"%s.%3N"
}

runHttpServer() {
    ./httpsrvimg/bin/java -Dsun.net.httpserver.nodelay=true -m httpsrv/httpsrv.Hello
}

profileHttpServer() {
    read line # wait for "ready" line emitted by server
    local javaEndTime=$(getCurrentTime)
    local statusCode=$(makeCurlRequest)
    local curlEndTime=$(getCurrentTime)

    echo "$1 $javaEndTime $curlEndTime $statusCode"
    pkill java
}

makeCurlRequest() {
    curl "http://localhost:8080/" -o /dev/null -s -w "%{http_code}"
}

measureExecutionTimes() {
    for i in {1..10}; do
        local startTime=$(getCurrentTime)
        runHttpServer | profileHttpServer $startTime
    done | processTimings
}

processTimings() {
    awk '{
        d1=$2-$1
        d2=$3-$1
        s1+=d1
        s2+=d2
        printf "%f, %f, %d\n", d1, d2, $4
    }
    END {
        printf "---\n%f, %f\n", s1/NR, s2/NR
    }'
}

measureExecutionTimes