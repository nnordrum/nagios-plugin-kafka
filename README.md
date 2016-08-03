Kafka Scala API Tester - Advanced Nagios Plugin / CLI Tool with Kerberos support
==============================
[![Build Status](https://travis-ci.org/HariSekhon/nagios-plugin-kafka.svg?branch=master)](https://travis-ci.org/HariSekhon/nagios-plugin-kafka)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/de500bf4f90d401ba5c98ed903c8a612)](https://www.codacy.com/app/harisekhon/nagios-plugin-kafka)
[![Dependency Status](https://www.versioneye.com/user/projects/57616d340a82b200276f6669/badge.svg)](https://www.versioneye.com/user/projects/57616d340a82b200276f6669)
[![Platform](https://img.shields.io/badge/platform-Linux%20%7C%20OS%20X-blue.svg)](https://github.com/harisekhon/nagios-plugin-kafka)
[![DockerHub](https://img.shields.io/badge/docker-available-blue.svg)](https://hub.docker.com/r/harisekhon/nagios-plugins/)
[![](https://images.microbadger.com/badges/version/harisekhon/nagios-plugin-kafka.svg)](http://microbadger.com/#/images/harisekhon/nagios-plugin-kafka)

Kafka 0.9+ API CLI Tester & Advanced Nagios Plugin with Kerberos support, written in Scala.

I'm currently holding back the Kafka library to 0.9.0.1 as there are issues with the 0.10.0.0 library, hence dependencies are reported as behind latest in the badge above.

Tested on Hortonworks HDP 2.4.0 with Kerberos + Ranger ACLs and Apache Kafka 0.8.x / 0.9.0.1 [docker images](https://hub.docker.com/r/harisekhon/kafka) with regular ACLs. Testing shows it does take an extra second to negotiate the Kerberos authentication so make sure not to set ```--timeout``` to less than 2 secs.

See [The Advanced Nagios Plugins Collection](https://github.com/harisekhon/nagios-plugins#advanced-nagios-plugins-collection) for many more related enterprise monitoring programs.

Hari Sekhon

Big Data Contractor, United Kingdom

https://www.linkedin.com/in/harisekhon

## Intro

This project builds a single self-contained Java jar file with all dependencies included and can simply be run on the command line with full switch option support:
```
java -jar check_kafka.jar --help
```

and there is an optional convenience shell wrapper script at the top level to make commands shorter:
```
./check_kafka --help
```

Run against one or more Kafka brokers, comma separated:
```
./check_kafka --brokers localhost:9092 --topic test
OK: Kafka broker successfully returned unique message, write time = 0.185s, read time = 0.045s, total time = 1.729s | write_time=0.185s read_time=0.045s total_time=1.729s
```

##### Kafka 0.9+ API Caveats

This program only supports Kafka 0.9+ as the API changed (again) and Kerberos security was only added in the 0.9 API. For Kafka versions before 0.9 you can find Python and Perl versions of this program in the [Advanced Nagios Plugins Collection](https://github.com/harisekhon/nagios-plugins#advanced-nagios-plugins-collection) that support 0.8 onwards (they dosn't support Kafka <= 0.7 as the API changed in 0.8 too and the underlying libraries in those languages don't support Kakfa <= 0.7).

It appears that several errors are caught too early in the new Kafka Java API and result in embedded looping retry behaviour on encountering errors (visible in debug level logging of the base library).

I haven't found a great way of handle that behaviour as it's not exposed to the client code so it ends up being handled via my generic default self timeout mechanism that I apply to all my tools. Hence if you specify an incorrect ```--brokers <host>:<port>``` or the Kafka brokers are down you or fail to negotiate the protocol due to security settings you will only receive a generic ```UNKNOWN: self timed out after 10 secs``` message as the code self terminates.

Otherwise the Kafka API would just hang there indefintely as it keeps retrying deeper in the library. I've tried various settings to get it to time out but nothing worked and I even posted to the Kafka users mailing list without answer. If you know of a setting that will make the Kafka Client library time out and return the more specific error then please let me know and I'll update this code accordingly.

#### Keberos Support

See the ```conf/``` directory for JAAS kerberos configurations.

### Build

#### Quick Start - Docker

A Dockerized pre-built version is available on [DockerHub](https://hub.docker.com/r/harisekhon/nagios-plugin-kafka).

If you have docker installed this one command will download and run it:
```
docker run harisekhon/nagios-plugin-kafka check_kafka --help
```

#### Automated Build from Source

Maven, Gradle and SBT automated builds are all provided.

A self-contained jar file with all dependencies will be created and symlinked to ```check_kafka.jar``` at the top level.

The Maven and Gradle builds are best as they will auto bootstap and run with no prior installed dependencies other than Java and ```make``` to kick it off.

The default ```make``` build will trigger a Gradle bootstrap from scratch with has an embedded checksum for security:

```
make
```

You can call any one of the 3 major build systems explicitly instead, which will recurse to build the library submodule using the same mechanism:

Maven:

```
make mvn
```

Gradle:

```
make gradle
```

SBT:

```
make sbt
```

#### Tests

There are tests in the ```tests/``` directory which use Kafka Docker containers I've set up on [DockerHub](https://hub.docker.com/r/harisekhon/kafka).

To trigger all tests run:
```
make test
```

### Kafka 0.8 support - Alternative Perl & Python Kafka API Nagios Plugins

The [Advanced Nagios Plugins Collection](https://github.com/harisekhon/nagios-plugins#advanced-nagios-plugins-collection) has both Perl and Python predecessors to this program which work with Kafka 0.8+. The main differenitator with this Scala version is that it uses the native Java API and has full Kerberos support, which the dynamic language versions do not.

### See Also

- [Python Tools](https://github.com/harisekhon/pytools) - 30+ Hadoop, Spark, Pig, Ambari Blueprints, AWS CloudFormation, Linux, Data Converters & Validators (Avro/Parquet/JSON/CSV/XML/YAML), Elasticsearch, Solr, IPython - CLI tools
- [Perl Tools](https://github.com/harisekhon/tools) - 25+ Hadoop, Hive, Solr, Linux, SQL, Ambari, Datameer, Web and various Linux CLI Tools
- [Spotify Tools](https://github.com/harisekhon/spotify-tools) - Backup & Play Automation: Spotify Lookup - converts Spotify URIs to 'Artist - Track' form by querying the Spotify Metadata API. Spotify Cmd - command line control of Spotify on Mac via AppleScript for automation, auto timed track flick through etc.
- [Spark Apps](https://github.com/harisekhon/spark-apps) - Spark Scala Apps (Spark => Elasticsearch indexer etc)
