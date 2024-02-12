# Overview

The code and experiments shown in this repository are intended to 
demonstrate the capabilities of a simple, minimal Java 
web server application built atop the following JDK primitives:

* [Java 21](https://openjdk.org/projects/jdk/21/)
* [Virtual threads](https://openjdk.org/jeps/444)
* [JDK HTTP server](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpServer.html)
* [JDK HTTP client](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html)

Experiments were conducted in AWS cloud on modest `c5.2xlarge`
EC2 instances with 8 vCPU and 16 GB RAM. 

* 117,000+ req/sec [hello-world server](#hello-server) (0.8 ms per req)
* 53,000+ req/sec [gateway/forwarding server](#gateway-server) (1.8 ms per req) 
* 101 millisecond startup
* 179 millisecond time-to-response
* 57 MB modular run-time image

# Environment

* `c5.2xlarge` EC2 instances
* Amazon Linux AMI 2.0.20231219 x86_64 ECS HVM GP2 `ami-0d63309a12899f79d` 
* OpenJDK 21.0.1
* [wrk](https://github.com/wg/wrk) HTTP benchmarking tool

Launch instance:
```
aws ec2 run-instances --image-id ami-0d63309a12899f79d ...
```

Download OpenJDK:
```
curl -s "https://download.java.net/java/GA/jdk21.0.1/415e3f918a1f4062a0074a2794853d0d/12/GPL/openjdk-21.0.1_linux-x64_bin.tar.gz" --output openjdk-21.0.1_linux-x64_bin.tar.gz
tar xvf openjdk-21.0.1_linux-x64_bin.tar.gz
```

Download and build `wrk`
```
sudo yum install git
sudo yum install "Development Tools"
git clone https://github.com/wg/wrk.git
cd wrk
make
```

# Compile and Build

## As jlink Image

Compile source code with a `javac` command in the project directory.

```
javac -d mods/ src/httpsrv/*.java src/module-info.java
```

Create a custom, modular run-time image with a `jlink` command.
`JAVA_HOME` refers to the OpenJDK installation directory.

```
jlink --module-path $JAVA_HOME/jmods:mods --add-modules httpsrv --output httpsrvimg
```

The contents of the resulting `httpsrvimg` are 57 MB.

```
$ du -h httpsrvimg/
364K	httpsrvimg/lib/security
25M	httpsrvimg/lib/server
56M	httpsrvimg/lib
4.0K	httpsrvimg/conf/sdp
12K	httpsrvimg/conf/security/policy/limited
8.0K	httpsrvimg/conf/security/policy/unlimited
24K	httpsrvimg/conf/security/policy
92K	httpsrvimg/conf/security
104K	httpsrvimg/conf
4.0K	httpsrvimg/include/linux
196K	httpsrvimg/include
48K	httpsrvimg/bin
116K	httpsrvimg/legal/java.base
0	httpsrvimg/legal/java.net.http
0	httpsrvimg/legal/jdk.httpserver
116K	httpsrvimg/legal
57M	httpsrvimg/
```

## As Native Binary

It's also possible to build a native binary image with minimal configuration.
Provided you have a GraalVM distribution and Gradle configured in your PATH, follow this steps.

Create file with following contents in root.
```
plugins {
    id 'application'
    id 'java'
    id 'org.graalvm.buildtools.native' version '0.10.0'
}

repositories {
    mavenCentral()
}

sourceSets {
    main { java { srcDirs = ['src'] } }
}

application {
    mainClass = 'httpsrv.Hello'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

Build the image.
```
gradle nativeCompile
```

Find the generated image in `./build/native/nativeCompile/java-httpserver-vthreads`.

# Start-up Time

The [Hello Server](src/httpsrv/Hello.java) writes a "ready" message to standard output
after launch. This serves as a timing signal that indicates the conclusion of the JVM
and application start-up process.

During each timing cycle, the [profile.sh](profile.sh) shell script measures

1. System time prior to start
2. System time after "ready" message received in stdin
3. System time after `GET /` 200 OK response received

The difference between [1] and [2] is the start-up time and
the difference between [2] and [3] is the time-to-response.

The [profile.sh](profile.sh) shell script measures these differences
across 10 launches and prints the averages.

Start-up is `0.101` seconds on average.

Time-to-response is `0.179` seconds on average.

```
$ ./profile.sh 
0.116000, 0.191000, 200
0.097000, 0.185000, 200
0.096000, 0.175000, 200
0.108000, 0.189000, 200
0.097000, 0.170000, 200
0.098000, 0.178000, 200
0.097000, 0.170000, 200
0.108000, 0.183000, 200
0.102000, 0.178000, 200
0.098000, 0.172000, 200
---
0.101700, 0.179100
```

# Hello Server

[Hello](src/httpsrv/Hello.java) is a simple application that
starts a [JDK HTTP server](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpServer.html).

It responds to every HTTP request with a "hello world" plain-text response. 

It uses a virtual-thread-per-task executor.

```
./httpsrvimg/bin/java -Dsun.net.httpserver.nodelay=true -m httpsrv/httpsrv.Hello
```

In the benchmark setting, two EC2 instances were used.
One instance ran the Hello server and the other instance ran `wrk`.

```
+---------+                +---------+
|         |                |         |
|   wrk   +--------------->|  Hello  |
|         |                |         |
+---------+                +---------+
```

The following parameters were used for the `wrk` benchmark below:

* 100 concurrent connections (`-c`)
* 60 second duration (`-d`)
* 8 threads (`-t`)

The resulting request rate for the execution below was
`117,448` requests per second with an average latency of 844 microseconds.  

```
$ ./wrk --latency -d 60s -c 100 -t 8 http://10.39.197.177:8080/
Running 1m test @ http://10.39.197.177:8080/
  8 threads and 100 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   844.44us    1.64ms 203.40ms   99.91%
    Req/Sec    14.78k     6.84k   24.85k    51.03%
  Latency Distribution
     50%  627.00us
     75%    0.94ms
     90%    1.71ms
     99%    2.04ms
  7058584 requests in 1.00m, 585.65MB read
Requests/sec: 117448.07
Transfer/sec:      9.74MB
```

# Gateway Server

[Gateway](src/httpsrv/Gateway.java) is a simple application that
starts a [JDK HTTP server](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpServer.html).

It forwards every HTTP request to a separately deployed [Hello](src/httpsrv/Hello.java) server
via the [JDK HTTP client](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html).
Similarly, the Hello server response is forwarded back to the Gateway client. 

It uses a virtual-thread-per-task executor.


```
./httpsrvimg/bin/java -Dsun.net.httpserver.nodelay=true -m httpsrv/httpsrv.Gateway http://10.39.197.199:8080/ 
```

In the benchmark setting, three EC2 instances were used.
One instance ran the Gateway server, another ran the Hello server, and the last instance ran `wrk`.

```
+---------+                +-----------+                 +---------+
|         |                |           |                 |         |
|   wrk   +--------------->|  Gateway  +---------------->|  Hello  |
|         |                |           |                 |         |
+---------+                +-----------+                 +---------+
```

* 100 concurrent connections (`-c`)
* 60 second duration (`-d`)
* 8 threads (`-t`)

The resulting request rate for the execution below was
`53,485` requests per second with an average latency of 1.82 milliseconds.

```
$ ./wrk --latency -d 60s -c 100 -t 8 http://10.39.197.177:8080/
Running 1m test @ http://10.39.197.177:8080/
  8 threads and 100 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.82ms    1.99ms 203.11ms   99.03%
    Req/Sec     6.72k   397.07     8.32k    81.67%
  Latency Distribution
     50%    1.68ms
     75%    2.04ms
     90%    2.50ms
     99%    3.80ms
  3209334 requests in 1.00m, 266.28MB read
Requests/sec:  53485.25
Transfer/sec:      4.44MB
```
