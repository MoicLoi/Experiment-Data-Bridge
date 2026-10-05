# EDB Third-Party Dependency Licenses

Generated from the dependency artifacts shipped in a release package; this file is
**not maintained by hand**. Regenerate it after any dependency change:

```powershell
pwsh -File packaging/demo-v1/collect-licenses.ps1 `
  -LibDir release/edb-demo-v1/cli/lib `
  -ArchivePath release/edb-demo-v1/server/EXPERIMENT_DATA_BRIDGE.jar `
  -OutputPath THIRD-PARTY-LICENSES.md
```

- Generated (UTC): 2026-10-05 01:55:15
- CLI distribution JARs inspected: 112 (unresolved: 57)
- Server fat JAR bundled POMs inspected: 74 (unresolved: 48)

## How each value was obtained

| ``Source`` | Meaning |
|---|---|
| ``bundled pom.xml`` / ``pom.xml`` | Vendor-declared license in Maven metadata - most authoritative. |
| ``MANIFEST.MF`` | ``Bundle-License`` from the OSGi manifest. |
| ``license text`` | Signature detected in the license text bundled in the JAR. |
| ``-`` | Nothing machine-readable found - open the artifact and verify by hand. |

Names are normalised to SPDX-style identifiers. A license name here is what an
artifact declares about itself: this document is an engineering inventory,
**not legal advice**.

## Server dependencies (from the fat JAR)

| Module | Version | Declared license | Source |
|---|---|---|---|
| `com.fasterxml.jackson.core:jackson-annotations` | 2.22 | Apache-2.0 | bundled pom.xml |
| `com.fasterxml.jackson.core:jackson-core` | 2.22.1 | Apache-2.0 | bundled pom.xml |
| `com.fasterxml.jackson.core:jackson-databind` | 2.22.1 | Apache-2.0 | bundled pom.xml |
| `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` | 2.22.1 | UNKNOWN | - |
| `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` | 2.22.1 | UNKNOWN | - |
| `com.fasterxml.jackson.module:jackson-module-kotlin` | 2.22.1 | Apache-2.0 | bundled pom.xml |
| `com.github.docker-java:docker-java-api` | 3.4.1 | UNKNOWN | - |
| `com.github.docker-java:docker-java-core` | 3.4.1 | UNKNOWN | - |
| `com.github.docker-java:docker-java-transport` | 3.4.1 | UNKNOWN | - |
| `com.github.docker-java:docker-java-transport-httpclient5` | 3.4.1 | UNKNOWN | - |
| `com.github.virtuald:curvesapi` | 1.08 | BSD License | bundled pom.xml |
| `com.google.code.findbugs:jsr305` | 7 | Apache-2.0 | bundled pom.xml |
| `com.google.errorprone:error_prone_annotations` | 2.18.0 | Apache-2.0 | bundled pom.xml |
| `com.google.guava:failureaccess` | 26.0-android | UNKNOWN | - |
| `com.google.guava:guava` | 32.1.2-jre | UNKNOWN | - |
| `com.google.guava:listenablefuture` | 26.0-android | UNKNOWN | - |
| `com.googlecode.javaewah:JavaEWAH` | 1.2.3 | Apache-2.0 | bundled pom.xml |
| `com.zaxxer:HikariCP` | 6.3.0 | Apache-2.0 | bundled pom.xml |
| `com.zaxxer:SparseBitSet` | 1.3 | Apache-2.0 | bundled pom.xml |
| `commons-codec:commons-codec` | 79 | UNKNOWN | - |
| `commons-io:commons-io` | 78 | UNKNOWN | - |
| `commons-logging:commons-logging` | 71 | UNKNOWN | - |
| `dev.langchain4j:langchain4j` | 1.18.1 | Apache-2.0 | bundled pom.xml |
| `dev.langchain4j:langchain4j-core` | 1.18.1 | Apache-2.0 | bundled pom.xml |
| `dev.langchain4j:langchain4j-http-client` | 1.18.1 | Apache-2.0 | bundled pom.xml |
| `dev.langchain4j:langchain4j-http-client-jdk` | 1.18.1 | Apache-2.0 | bundled pom.xml |
| `dev.langchain4j:langchain4j-open-ai` | 1.18.1 | Apache-2.0 | bundled pom.xml |
| `io.jsonwebtoken:jjwt-api` | 0.12.6 | UNKNOWN | - |
| `io.jsonwebtoken:jjwt-impl` | 0.12.6 | UNKNOWN | - |
| `io.jsonwebtoken:jjwt-jackson` | 0.12.6 | UNKNOWN | - |
| `io.lettuce:lettuce-core` | 6.7.1.RELEASE | MIT | bundled pom.xml |
| `io.netty:netty-buffer` | 4.2.2.Final | UNKNOWN | - |
| `io.netty:netty-codec` | 4.1.118.Final | UNKNOWN | - |
| `io.netty:netty-codec-base` | 4.2.2.Final | UNKNOWN | - |
| `io.netty:netty-codec-compression` | 4.2.2.Final | UNKNOWN | - |
| `io.netty:netty-codec-dns` | 4.1.118.Final | UNKNOWN | - |
| `io.netty:netty-codec-http` | 4.2.2.Final | UNKNOWN | - |
| `io.netty:netty-codec-http2` | 4.2.2.Final | UNKNOWN | - |
| `io.netty:netty-common` | 4.2.2.Final | UNKNOWN | - |
| `io.netty:netty-handler` | 4.2.2.Final | UNKNOWN | - |
| `io.netty:netty-resolver` | 4.2.2.Final | UNKNOWN | - |
| `io.netty:netty-resolver-dns` | 4.1.118.Final | UNKNOWN | - |
| `io.netty:netty-transport` | 4.2.2.Final | UNKNOWN | - |
| `io.netty:netty-transport-classes-epoll` | 4.2.2.Final | UNKNOWN | - |
| `io.netty:netty-transport-classes-kqueue` | 4.2.2.Final | UNKNOWN | - |
| `io.netty:netty-transport-native-epoll` | 4.2.2.Final | UNKNOWN | - |
| `io.netty:netty-transport-native-kqueue` | 4.2.2.Final | UNKNOWN | - |
| `io.netty:netty-transport-native-unix-common` | 4.2.2.Final | UNKNOWN | - |
| `org.apache.commons:commons-collections4` | 48 | UNKNOWN | - |
| `org.apache.commons:commons-compress` | 72 | UNKNOWN | - |
| `org.apache.commons:commons-lang3` | 72 | UNKNOWN | - |
| `org.apache.commons:commons-math3` | 39 | UNKNOWN | - |
| `org.apache.httpcomponents.client5:httpclient5` | 5.0.3 | UNKNOWN | - |
| `org.apache.httpcomponents.core5:httpcore5` | 5.0.2 | UNKNOWN | - |
| `org.apache.httpcomponents.core5:httpcore5-h2` | 5.0.2 | UNKNOWN | - |
| `org.apache.logging.log4j:log4j-api` | 2.24.3 | Apache-2.0 | bundled pom.xml |
| `org.apache.logging.log4j:log4j-core` | 2.24.3 | Apache-2.0 | bundled pom.xml |
| `org.apache.logging.log4j:log4j-slf4j2-impl` | 2.24.3 | Apache-2.0 | bundled pom.xml |
| `org.apache.opennlp:opennlp-tools` | 2.5.9 | UNKNOWN | - |
| `org.apache.pdfbox:fontbox` | 3.0.3 | UNKNOWN | - |
| `org.apache.pdfbox:pdfbox` | 3.0.3 | UNKNOWN | - |
| `org.apache.pdfbox:pdfbox-io` | 3.0.3 | UNKNOWN | - |
| `org.apache.xmlbeans:xmlbeans` | 5.3.0 | Apache-2.0 | bundled pom.xml |
| `org.eclipse.jetty.alpn:alpn-api` | 21 | UNKNOWN | - |
| `org.eclipse.jgit:org.eclipse.jgit` | 7.2.1.202505142326-r | UNKNOWN | - |
| `org.flywaydb:flyway-core` | 11.10.0 | UNKNOWN | - |
| `org.flywaydb:flyway-mysql` | 11.10.0 | UNKNOWN | - |
| `org.fusesource.jansi:jansi` | 1.12 | Apache-2.0 | bundled pom.xml |
| `org.jctools:jctools-core` | 4.0.5 | Apache-2.0 | bundled pom.xml |
| `org.mindrot:jbcrypt` | 0.4 | ISC | bundled pom.xml |
| `org.slf4j:slf4j-api` | 2.0.18 | UNKNOWN | - |
| `org.snakeyaml:snakeyaml-engine` | 2.9 | Apache-2.0 | bundled pom.xml |
| `org.yaml:snakeyaml` | 2.5 | Apache-2.0 | bundled pom.xml |
| `redis.clients.authentication:redis-authx-core` | 7 | MIT | bundled pom.xml |

## CLI distribution JARs

| Artifact | Maven coordinates | Declared license | Source |
|---|---|---|---|
| `annotations-23.0.0.jar` | - | UNKNOWN | - |
| `bcpkix-jdk18on-1.76.jar` | - | UNKNOWN | - |
| `bcprov-jdk18on-1.76.jar` | - | UNKNOWN | - |
| `bcutil-jdk18on-1.76.jar` | - | UNKNOWN | - |
| `cli.jar` | - | UNKNOWN | - |
| `clikt-jvm.jar` | - | UNKNOWN | - |
| `clikt-mordant-jvm.jar` | - | UNKNOWN | - |
| `colormath-jvm.jar` | - | UNKNOWN | - |
| `commons-codec-1.18.0.jar` | `org.apache.commons:commons-parent:79` | Apache-2.0 | MANIFEST.MF |
| `commons-collections4-4.4.jar` | `org.apache.commons:commons-parent:48` | Apache-2.0 | MANIFEST.MF |
| `commons-compress-1.27.1.jar` | `org.apache.commons:commons-parent:72` | Apache-2.0 | MANIFEST.MF |
| `commons-io-2.18.0.jar` | `org.apache.commons:commons-parent:78` | Apache-2.0 | MANIFEST.MF |
| `commons-lang3-3.16.0.jar` | `org.apache.commons:commons-parent:72` | Apache-2.0 | MANIFEST.MF |
| `commons-logging-1.3.3.jar` | `org.apache.commons:commons-parent:71` | Apache-2.0 | MANIFEST.MF |
| `commons-math3-3.6.1.jar` | `org.apache.commons:commons-parent:39` | Apache-2.0 | MANIFEST.MF |
| `config-1.4.3.jar` | - | Apache-2.0 | MANIFEST.MF |
| `core.jar` | - | UNKNOWN | - |
| `curvesapi-1.08.jar` | `com.github.virtuald:curvesapi:1.08` | BSD License | pom.xml |
| `docker-java-api-3.4.1.jar` | `com.github.docker-java:docker-java-parent:3.4.1` | UNKNOWN | - |
| `docker-java-core-3.4.1.jar` | `com.github.docker-java:docker-java-parent:3.4.1` | UNKNOWN | - |
| `docker-java-transport-3.4.1.jar` | `com.github.docker-java:docker-java-parent:3.4.1` | UNKNOWN | - |
| `docker-java-transport-httpclient5-3.4.1.jar` | `com.github.docker-java:docker-java-parent:3.4.1` | UNKNOWN | - |
| `exposed-core-0.61.0.jar` | - | UNKNOWN | - |
| `exposed-dao-0.61.0.jar` | - | UNKNOWN | - |
| `exposed-java-time-0.61.0.jar` | - | UNKNOWN | - |
| `exposed-jdbc-0.61.0.jar` | - | UNKNOWN | - |
| `flyway-core-11.10.0.jar` | `org.flywaydb:flyway-parent:11.10.0` | Apache-2.0 | license text |
| `flyway-mysql-11.10.0.jar` | `org.flywaydb:flyway-parent:11.10.0` | UNKNOWN | - |
| `fontbox-3.0.3.jar` | `org.apache.pdfbox:pdfbox-parent:3.0.3` | Apache-2.0 | MANIFEST.MF |
| `guava-19.0.jar` | `com.google.guava:guava-parent:19.0` | Apache-2.0 | MANIFEST.MF |
| `HikariCP-6.3.0.jar` | `com.zaxxer:HikariCP:6.3.0` | Apache-2.0 | pom.xml |
| `httpclient5-5.0.3.jar` | `org.apache.httpcomponents.client5:httpclient5-parent:5.0.3` | Apache-2.0 | license text |
| `httpcore5-5.0.2.jar` | `org.apache.httpcomponents.core5:httpcore5-parent:5.0.2` | Apache-2.0 | license text |
| `httpcore5-h2-5.0.2.jar` | `org.apache.httpcomponents.core5:httpcore5-parent:5.0.2` | Apache-2.0 | license text |
| `jackson-annotations-2.19.0.jar` | `com.fasterxml.jackson:jackson-parent:2.19` | Apache-2.0 | pom.xml |
| `jackson-core-2.19.0.jar` | `com.fasterxml.jackson:jackson-base:2.19.0` | Apache-2.0 | pom.xml |
| `jackson-databind-2.19.0.jar` | `com.fasterxml.jackson:jackson-base:2.19.0` | Apache-2.0 | pom.xml |
| `jackson-dataformat-yaml-2.19.0.jar` | `com.fasterxml.jackson.dataformat:jackson-dataformats-text:2.19.0` | Apache-2.0 | MANIFEST.MF |
| `jackson-module-kotlin-2.19.0.jar` | `com.fasterxml.jackson:jackson-base:2.19.0` | Apache-2.0 | pom.xml |
| `jansi-2.4.1.jar` | `org.fusesource:fusesource-pom:1.12` | Apache-2.0 | pom.xml |
| `JavaEWAH-1.2.3.jar` | `com.googlecode.javaewah:JavaEWAH:1.2.3` | Apache-2.0 | pom.xml |
| `java-jwt-4.4.0.jar` | - | UNKNOWN | - |
| `jbcrypt-0.4.jar` | `org.mindrot:jbcrypt:0.4` | ISC | pom.xml |
| `jjwt-api-0.12.6.jar` | `io.jsonwebtoken:jjwt-root:0.12.6` | Apache-2.0 | MANIFEST.MF |
| `jjwt-impl-0.12.6.jar` | `io.jsonwebtoken:jjwt-root:0.12.6` | Apache-2.0 | MANIFEST.MF |
| `jjwt-jackson-0.12.6.jar` | `io.jsonwebtoken:jjwt-root:0.12.6` | Apache-2.0 | MANIFEST.MF |
| `jna-5.14.0.jar` | - | Apache-2.0 | license text |
| `koin-core-jvm-4.1.0.jar` | - | UNKNOWN | - |
| `koin-ktor-jvm-4.1.0.jar` | - | UNKNOWN | - |
| `koin-logger-slf4j-4.1.0.jar` | - | UNKNOWN | - |
| `kotlin-reflect-2.1.20.jar` | - | UNKNOWN | - |
| `kotlin-stdlib-2.3.0.jar` | - | UNKNOWN | - |
| `kotlin-test-2.2.0.jar` | - | UNKNOWN | - |
| `kotlinx-coroutines-core-jvm-1.10.2.jar` | - | UNKNOWN | - |
| `kotlinx-coroutines-slf4j-1.10.2.jar` | - | UNKNOWN | - |
| `kotlinx-io-bytestring-jvm-0.7.0.jar` | - | UNKNOWN | - |
| `kotlinx-io-core-jvm-0.7.0.jar` | - | UNKNOWN | - |
| `kotlinx-serialization-core-jvm-1.8.1.jar` | - | UNKNOWN | - |
| `kotlinx-serialization-json-jvm-1.8.1.jar` | - | UNKNOWN | - |
| `ktor-client-cio-jvm-3.2.3.jar` | - | UNKNOWN | - |
| `ktor-client-content-negotiation-jvm-3.2.3.jar` | - | UNKNOWN | - |
| `ktor-client-core-jvm-3.2.3.jar` | - | UNKNOWN | - |
| `ktor-events-jvm-3.2.3.jar` | - | UNKNOWN | - |
| `ktor-http-cio-jvm-3.2.3.jar` | - | UNKNOWN | - |
| `ktor-http-jvm-3.2.3.jar` | - | UNKNOWN | - |
| `ktor-io-jvm-3.2.3.jar` | - | UNKNOWN | - |
| `ktor-network-jvm-3.2.3.jar` | - | UNKNOWN | - |
| `ktor-network-tls-jvm-3.2.3.jar` | - | UNKNOWN | - |
| `ktor-serialization-jvm-3.2.3.jar` | - | UNKNOWN | - |
| `ktor-server-core-jvm-3.1.3.jar` | - | UNKNOWN | - |
| `ktor-sse-jvm-3.2.3.jar` | - | UNKNOWN | - |
| `ktor-utils-jvm-3.2.3.jar` | - | UNKNOWN | - |
| `ktor-websocket-serialization-jvm-3.2.3.jar` | - | UNKNOWN | - |
| `ktor-websockets-jvm-3.2.3.jar` | - | UNKNOWN | - |
| `lettuce-core-6.7.1.RELEASE.jar` | `io.lettuce:lettuce-core:6.7.1.RELEASE` | MIT | pom.xml |
| `log4j-api-2.24.3.jar` | `org.apache.logging.log4j:log4j:2.24.3` | Apache-2.0 | pom.xml |
| `log4j-core-2.24.3.jar` | `org.apache.logging.log4j:log4j:2.24.3` | Apache-2.0 | pom.xml |
| `log4j-slf4j2-impl-2.24.3.jar` | `org.apache.logging.log4j:log4j:2.24.3` | Apache-2.0 | pom.xml |
| `mordant-jvm.jar` | - | UNKNOWN | - |
| `mordant-jvm-ffm-jvm.jar` | - | UNKNOWN | - |
| `mordant-jvm-graal-ffi-jvm.jar` | - | UNKNOWN | - |
| `mordant-jvm-jna-jvm.jar` | - | UNKNOWN | - |
| `mordant-omnibus-jvm.jar` | - | UNKNOWN | - |
| `mysql-connector-j-9.4.0.jar` | - | Multiple - see bundled license text (Apache-2.0, EPL-2.0, EPL-1.0, GPL-2.0, GPL-3.0, LGPL-2.1, MIT) | license text |
| `netty-buffer-4.1.118.Final.jar` | `io.netty:netty-parent:4.1.118.Final` | Apache-2.0 | MANIFEST.MF |
| `netty-codec-4.1.118.Final.jar` | `io.netty:netty-parent:4.1.118.Final` | Apache-2.0 | MANIFEST.MF |
| `netty-codec-dns-4.1.118.Final.jar` | `io.netty:netty-parent:4.1.118.Final` | Apache-2.0 | MANIFEST.MF |
| `netty-common-4.1.118.Final.jar` | `io.netty:netty-parent:4.1.118.Final` | Apache-2.0 | pom.xml |
| `netty-handler-4.1.118.Final.jar` | `io.netty:netty-parent:4.1.118.Final` | Apache-2.0 | MANIFEST.MF |
| `netty-resolver-4.1.118.Final.jar` | `io.netty:netty-parent:4.1.118.Final` | Apache-2.0 | MANIFEST.MF |
| `netty-resolver-dns-4.1.118.Final.jar` | `io.netty:netty-parent:4.1.118.Final` | Apache-2.0 | MANIFEST.MF |
| `netty-transport-4.1.118.Final.jar` | `io.netty:netty-parent:4.1.118.Final` | Apache-2.0 | MANIFEST.MF |
| `netty-transport-native-unix-common-4.1.118.Final.jar` | `io.netty:netty-parent:4.1.118.Final` | Apache-2.0 | MANIFEST.MF |
| `org.eclipse.jgit-7.2.1.202505142326-r.jar` | `org.eclipse.jgit:org.eclipse.jgit-parent:7.2.1.202505142326-r` | Multiple - see bundled license text (EDL-1.0, MIT) | license text |
| `pdfbox-3.0.3.jar` | `org.apache.pdfbox:pdfbox-parent:3.0.3` | Apache-2.0 | MANIFEST.MF |
| `pdfbox-io-3.0.3.jar` | `org.apache.pdfbox:pdfbox-parent:3.0.3` | Apache-2.0 | MANIFEST.MF |
| `plugin-api.jar` | - | UNKNOWN | - |
| `poi-5.4.1.jar` | - | Multiple - see bundled license text (Apache-2.0, MIT) | license text |
| `poi-ooxml-5.4.1.jar` | - | Multiple - see bundled license text (Apache-2.0, MIT) | license text |
| `poi-ooxml-lite-5.4.1.jar` | - | Multiple - see bundled license text (Apache-2.0, MIT) | license text |
| `protobuf-java-4.31.1.jar` | - | BSD-3-Clause | MANIFEST.MF |
| `reactive-streams-1.0.4.jar` | - | UNKNOWN | - |
| `reactor-core-3.6.6.jar` | - | UNKNOWN | - |
| `redis-authx-core-0.1.1-beta2.jar` | `org.sonatype.oss:oss-parent:7` | MIT | pom.xml |
| `slf4j-api-2.0.17.jar` | `org.slf4j:slf4j-parent:2.0.17` | MIT | MANIFEST.MF |
| `snakeyaml-2.4.jar` | `org.yaml:snakeyaml:2.4` | Apache-2.0 | pom.xml |
| `snakeyaml-engine-2.9.jar` | `org.snakeyaml:snakeyaml-engine:2.9` | Apache-2.0 | pom.xml |
| `SparseBitSet-1.3.jar` | `com.zaxxer:SparseBitSet:1.3` | Apache-2.0 | pom.xml |
| `stately-concurrency-jvm-2.1.0.jar` | - | UNKNOWN | - |
| `stately-concurrent-collections-jvm-2.1.0.jar` | - | UNKNOWN | - |
| `stately-strict-jvm-2.1.0.jar` | - | UNKNOWN | - |
| `xmlbeans-5.3.0.jar` | `org.apache.xmlbeans:xmlbeans:5.3.0` | Apache-2.0 | pom.xml |

## Unresolved before publishing binaries

These artifacts ship no machine-readable license metadata and must be verified by hand:

- `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml 2.22.1`
- `com.fasterxml.jackson.datatype:jackson-datatype-jsr310 2.22.1`
- `com.github.docker-java:docker-java-api 3.4.1`
- `com.github.docker-java:docker-java-core 3.4.1`
- `com.github.docker-java:docker-java-transport 3.4.1`
- `com.github.docker-java:docker-java-transport-httpclient5 3.4.1`
- `com.google.guava:failureaccess 26.0-android`
- `com.google.guava:guava 32.1.2-jre`
- `com.google.guava:listenablefuture 26.0-android`
- `commons-codec:commons-codec 79`
- `commons-io:commons-io 78`
- `commons-logging:commons-logging 71`
- `io.jsonwebtoken:jjwt-api 0.12.6`
- `io.jsonwebtoken:jjwt-impl 0.12.6`
- `io.jsonwebtoken:jjwt-jackson 0.12.6`
- `io.netty:netty-buffer 4.2.2.Final`
- `io.netty:netty-codec 4.1.118.Final`
- `io.netty:netty-codec-base 4.2.2.Final`
- `io.netty:netty-codec-compression 4.2.2.Final`
- `io.netty:netty-codec-dns 4.1.118.Final`
- `io.netty:netty-codec-http 4.2.2.Final`
- `io.netty:netty-codec-http2 4.2.2.Final`
- `io.netty:netty-common 4.2.2.Final`
- `io.netty:netty-handler 4.2.2.Final`
- `io.netty:netty-resolver 4.2.2.Final`
- `io.netty:netty-resolver-dns 4.1.118.Final`
- `io.netty:netty-transport 4.2.2.Final`
- `io.netty:netty-transport-classes-epoll 4.2.2.Final`
- `io.netty:netty-transport-classes-kqueue 4.2.2.Final`
- `io.netty:netty-transport-native-epoll 4.2.2.Final`
- `io.netty:netty-transport-native-kqueue 4.2.2.Final`
- `io.netty:netty-transport-native-unix-common 4.2.2.Final`
- `org.apache.commons:commons-collections4 48`
- `org.apache.commons:commons-compress 72`
- `org.apache.commons:commons-lang3 72`
- `org.apache.commons:commons-math3 39`
- `org.apache.httpcomponents.client5:httpclient5 5.0.3`
- `org.apache.httpcomponents.core5:httpcore5 5.0.2`
- `org.apache.httpcomponents.core5:httpcore5-h2 5.0.2`
- `org.apache.opennlp:opennlp-tools 2.5.9`
- `org.apache.pdfbox:fontbox 3.0.3`
- `org.apache.pdfbox:pdfbox 3.0.3`
- `org.apache.pdfbox:pdfbox-io 3.0.3`
- `org.eclipse.jetty.alpn:alpn-api 21`
- `org.eclipse.jgit:org.eclipse.jgit 7.2.1.202505142326-r`
- `org.flywaydb:flyway-core 11.10.0`
- `org.flywaydb:flyway-mysql 11.10.0`
- `org.slf4j:slf4j-api 2.0.18`
- `annotations-23.0.0.jar`
- `bcpkix-jdk18on-1.76.jar`
- `bcprov-jdk18on-1.76.jar`
- `bcutil-jdk18on-1.76.jar`
- `cli.jar`
- `clikt-jvm.jar`
- `clikt-mordant-jvm.jar`
- `colormath-jvm.jar`
- `core.jar`
- `docker-java-api-3.4.1.jar`
- `docker-java-core-3.4.1.jar`
- `docker-java-transport-3.4.1.jar`
- `docker-java-transport-httpclient5-3.4.1.jar`
- `exposed-core-0.61.0.jar`
- `exposed-dao-0.61.0.jar`
- `exposed-java-time-0.61.0.jar`
- `exposed-jdbc-0.61.0.jar`
- `flyway-mysql-11.10.0.jar`
- `java-jwt-4.4.0.jar`
- `koin-core-jvm-4.1.0.jar`
- `koin-ktor-jvm-4.1.0.jar`
- `koin-logger-slf4j-4.1.0.jar`
- `kotlin-reflect-2.1.20.jar`
- `kotlin-stdlib-2.3.0.jar`
- `kotlin-test-2.2.0.jar`
- `kotlinx-coroutines-core-jvm-1.10.2.jar`
- `kotlinx-coroutines-slf4j-1.10.2.jar`
- `kotlinx-io-bytestring-jvm-0.7.0.jar`
- `kotlinx-io-core-jvm-0.7.0.jar`
- `kotlinx-serialization-core-jvm-1.8.1.jar`
- `kotlinx-serialization-json-jvm-1.8.1.jar`
- `ktor-client-cio-jvm-3.2.3.jar`
- `ktor-client-content-negotiation-jvm-3.2.3.jar`
- `ktor-client-core-jvm-3.2.3.jar`
- `ktor-events-jvm-3.2.3.jar`
- `ktor-http-cio-jvm-3.2.3.jar`
- `ktor-http-jvm-3.2.3.jar`
- `ktor-io-jvm-3.2.3.jar`
- `ktor-network-jvm-3.2.3.jar`
- `ktor-network-tls-jvm-3.2.3.jar`
- `ktor-serialization-jvm-3.2.3.jar`
- `ktor-server-core-jvm-3.1.3.jar`
- `ktor-sse-jvm-3.2.3.jar`
- `ktor-utils-jvm-3.2.3.jar`
- `ktor-websocket-serialization-jvm-3.2.3.jar`
- `ktor-websockets-jvm-3.2.3.jar`
- `mordant-jvm.jar`
- `mordant-jvm-ffm-jvm.jar`
- `mordant-jvm-graal-ffi-jvm.jar`
- `mordant-jvm-jna-jvm.jar`
- `mordant-omnibus-jvm.jar`
- `plugin-api.jar`
- `reactive-streams-1.0.4.jar`
- `reactor-core-3.6.6.jar`
- `stately-concurrency-jvm-2.1.0.jar`
- `stately-concurrent-collections-jvm-2.1.0.jar`
- `stately-strict-jvm-2.1.0.jar`

