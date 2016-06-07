//
//  Author: Hari Sekhon
//  Date: 2016-06-06 22:51:45 +0100 (Mon, 06 Jun 2016)
//
//  vim:ts=4:sts=4:sw=4:et
//
//  https://github.com/harisekhon/spark-apps
//
//  License: see accompanying Hari Sekhon LICENSE file
//
//  If you're using my code you're welcome to connect with me on LinkedIn and optionally send me feedback to help improve or steer this or other code I publish
//
//  http://www.linkedin.com/in/harisekhon
//

name := "check_kafka"

version := "0.1.0"

scalaVersion := "2.10.4"

//unmanagedBase := baseDirectory.value / "lib/target"

libraryDependencies ++= Seq (
    "org.apache.kafka" % "kafka_2.10" % "0.8.2.2",
    "commons-cli" % "commons-cli" % "1.3",
    "org.scalatest" %% "scalatest" % "2.2.4" % "test"
    //"net.sf.jopt-simple" % "jopt-simple" % "4.9"
)
