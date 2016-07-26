//
//  Author: Hari Sekhon
//  Date: 2016-06-06 22:43:57 +0100 (Mon, 06 Jun 2016)
//
//  vim:ts=4:sts=4:sw=4:et
//
//  https://github.com/harisekhon/nagios-plugin-kafka
//
//  License: see accompanying Hari Sekhon LICENSE file
//
//  If you're using my code you're welcome to connect with me on LinkedIn and optionally send me feedback to help steer this or other code I publish
//
//  https://www.linkedin.com/in/harisekhon
//

package com.linkedin.harisekhon.kafka

import com.linkedin.harisekhon.CLI
import com.linkedin.harisekhon.Utils._

import java.io.{File, InputStream, PipedInputStream, PipedOutputStream}
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.{Arrays, Properties}

import org.apache.kafka.common.KafkaException
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.common.TopicPartition

//import org.apache.log4j.Logger

import scala.collection.JavaConversions._

object CheckKafka extends App {
    new CheckKafka().main2(args)
}

class CheckKafka extends CLI {
    // using utils logger for uniformly increasing logging level for all logging via --verbose
//    val log = Logger.getLogger("CheckKafka")
    println("CheckKafka constructor")
    // TODO: replace scalaz.ValidationNel / cats.Validated and combine with |@|
    var brokers: String = ""
    var topic: String = ""
    var partition: Int = 0
    var last_offset: Long = 0
    var jaas_config: Option[String] = None
    val consumer_props = new Properties
    val producer_props = new Properties
    if (consumer_props eq producer_props) {
        throw new IllegalArgumentException("Consumer + Producer props should not be the same object")
    }
    val producer_properties: InputStream = getClass.getResourceAsStream("/producer.properties")
    if (producer_properties == null) {
        log.error("could not find producer.properties file")
        System.exit(2)
    }
    val consumer_properties: InputStream = getClass.getResourceAsStream("/consumer.properties")
    if (consumer_properties == null) {
        log.error("could not find consumer.properties file")
        System.exit(2)
    }
    val uuid = java.util.UUID.randomUUID.toString
    val epoch = System.currentTimeMillis()
    val date = new SimpleDateFormat("yyyy-dd-MM HH:MM:ss.SSS Z").format(epoch)
    val id: String = s"Hari Sekhon check_kafka (scala) - random token=$uuid, $date"

    val msg = s"test message generated by $id"
    log.info(s"test message => '$msg'")

    override def addOptions(): Unit = {
        println("addOptions")
        options.addOption("B", "brokers", true, "Kafka broker list in the format host1:port1,host2:port2 ...")
        options.addOption("T", "topic", true, "Kafka topic to test")
        // TODO: consider round robin partitions for each run
        options.addOption("P", "partition", true, "Kafka partition to test (default: 0)")
        options.addOption("l", "list-topics", true, "List Kafka topics and exit")
        options.addOption("p", "list-partitions", true, "List Kafka partitions for the given topic and exit (requires --topic)")
    }

    override def processArgs(): Unit = {
        if (cmd.hasOption("brokers")) {
            brokers = cmd.getOptionValue("brokers", "")
        }
        println(s"brokers are $brokers")
        validateNodePortList(brokers, "kafka")
        if (cmd.hasOption("topic")) {
            topic = cmd.getOptionValue("topic", "")
        }
        if(topic.isEmpty){
            usage("topic not defined")
        }
        val partitionStr = cmd.getOptionValue("partition", "0")
        // if you have more than 10000 partitions please contact me to explain and get this limit increased!
        partition = Integer.parseInt(partitionStr)
        validateInt(partition, "partition", 0, 10000)
//            try {
//                Integer.parseInt(options.getOption("partition").getValue("0"))
//            } catch {
//                 case e: NumberFormatException => {
//                     quit("UNKNOWN", "Invalid argument for partition, must be an integer")
//                 }
//                 0
//            }
        load_props()
        setup_jaas()
    }

    def load_props(): Unit = {
        consumer_props.put("bootstrap.servers", brokers)
        producer_props.put("bootstrap.servers", brokers)

        val consumer_props_args = consumer_props.clone().asInstanceOf[Properties]
        consumer_props.load(consumer_properties)
        if (log.isDebugEnabled) {
            log.debug("Loaded Consumer Properties from resource file:")
            consumer_props.foreach({ case (k, v) => log.debug(s"  $k = $v") })
            log.debug("Loading Consumer Property args:")
            consumer_props_args.foreach({ case (k, v) => log.debug(s"  $k = $v") })
        }
        val consumer_in = new PipedInputStream
        val consumer_out = new PipedOutputStream(consumer_in)
        new Thread(
            new Runnable() {
                def run(): Unit = {
                    consumer_props_args.store(consumer_out, "")
                    consumer_out.close()
                }
            }
        ).start()
        consumer_props.load(consumer_in)
        // enforce unique group to make sure we are guaranteed to received our unique message back
        val group_id: String = s"$uuid, $date"
        log.info(s"group id='$group_id'")
        consumer_props.put("group.id", group_id)

        val producer_props_args = producer_props.clone().asInstanceOf[Properties]
        producer_props.load(producer_properties)
        if (log.isDebugEnabled) {
            log.debug("Loaded Producer Properties from resource file:")
            producer_props.foreach({ case (k, v) => log.debug(s"  $k = $v") })
            log.debug("Loading Producer Property args:")
            producer_props_args.foreach({ case (k, v) => log.debug(s"  $k = $v") })
        }
        val producer_in = new PipedInputStream()
        val producer_out = new PipedOutputStream(producer_in)
        new Thread(
            new Runnable() {
                def run(): Unit = {
                    producer_props_args.store(producer_out, "")
                    producer_out.close()
                }
            }
        ).start()
        producer_props.load(producer_in)
    }

    def setup_jaas(): Unit = {
        log.debug("setting up JAAS for Kerberos security")
        val DEFAULT_JAAS_FILE = "kafka_cli_jaas.conf"
        val HDP_JAAS_PATH = "/usr/hdp/current/kafka-broker/config/kafka_client_jaas.conf"

//        val srcpath = new File(classOf[CheckKafka].getProtectionDomain.getCodeSource.getLocation.toURI.getPath)
        val srcpath = new File(getClass.getProtectionDomain.getCodeSource.getLocation.toURI.getPath)
        val jar = if (srcpath.toString.contains("/target/")) {
            srcpath.getParentFile.getParentFile
        } else {
            srcpath
        }
        val jaas_default_config = Paths.get(jar.getParentFile.getAbsolutePath, "conf", DEFAULT_JAAS_FILE).toString
        val jaas_prop = System.getProperty("java.security.auth.login.config")
        if (jaas_config.nonEmpty && jaas_config.get.toString.nonEmpty) {
            log.info(s"using JAAS config file arg '$jaas_config'")
        } else if (jaas_prop != null) {
            val jaas_file = new File(jaas_prop)
            if (jaas_file.exists() && jaas_file.isFile()) {
                jaas_config = Option(jaas_prop)
                log.info(s"using JAAS config file from System property java.security.auth.login.config = '$jaas_config'")
            } else {
                log.warn(s"JAAS path specified in System property java.security.auth.login.config = '$jaas_prop' does not exist!")
            }
        }
        if (jaas_config.isEmpty || jaas_config.get.toString.isEmpty) {
            val hdp_jaas_file = new File(HDP_JAAS_PATH)
            if (hdp_jaas_file.exists() && hdp_jaas_file.isFile()) {
                log.info(s"found HDP Kafka kerberos config '$HDP_JAAS_PATH'")
                jaas_config = Option(HDP_JAAS_PATH)
            }
        }
        if (jaas_config.isEmpty || jaas_config.get.toString.isEmpty) {
            val jaas_default_file = new File(jaas_default_config)
            if (jaas_default_file.exists() && jaas_default_file.isFile()) {
                log.info(s"using default JaaS config file '$jaas_default_config'")
                jaas_config = Option(jaas_default_config)
            } else {
                log.warn("cannot find default JAAS file and none supplied")
            }
        }
        if (jaas_config.nonEmpty && jaas_config.get.toString.nonEmpty) {
            System.setProperty("java.security.auth.login.config", jaas_config.get)
        } else {
            log.warn("no JAAS config defined")
        }
    }

    override def run(): Unit = {
        try {
            // without port suffix raises the following exception, which we intend to catch and print nicely
            // Exception in thread "main" org.apache.kafka.common.KafkaException: Failed to construct kafka consumer
            // ...
            // org.apache.kafka.common.config.ConfigException: Invalid url in bootstrap.servers: 192.168.99.100
            run_test()
        } catch {
            case e: KafkaException => {
                println("Caught Kafka Exception: ")
                e.printStackTrace()
                System.exit(2)
            }
            case e: Throwable => {
                println("Caught unexpected Exception: ")
                e.printStackTrace()
                System.exit(2)
            }
        }
    }

    def run_test(): Unit = {
        log.debug("run_test()")
        val start_time = System.currentTimeMillis()
        // Cannot use 0.8 consumers as only new 0.9 API supports Kerberos
        log.info("creating Kafka consumer")
        val consumer = new KafkaConsumer[String, String](consumer_props)
        log.info("creating Kafka producer")
        val producer = new KafkaProducer[String, String](producer_props)
        subscribe(consumer=consumer, topic=topic, partition=partition)
        val start_write = System.currentTimeMillis()
        produce(producer=producer, topic=topic, partition=partition, msg=msg)
        val write_time = (System.currentTimeMillis() - start_write) / 1000.0
        val read_start_time = System.currentTimeMillis()
        consume(consumer=consumer, topic=topic, partition=partition)
        val end_time = System.currentTimeMillis()
        val read_time = (end_time - read_start_time) / 1000.0
        val total_time = (end_time - start_time) / 1000.0
        val plural =
            if (consumer_props.get("bootstrap.servers").isInstanceOf[String] &&
                consumer_props.get("bootstrap.servers").asInstanceOf[String].split("\\s+,\\s+").length > 1)
            {
                "s"
            } else {
                ""
            }
        val output = s"OK: Kafka broker$plural successfully returned unique message, write_time=${write_time}s, read_time=${read_time}s, total_time=${total_time}s | write_time=${write_time}s read_time=${read_time}s total_time=${total_time}s"
//        log.info(output)
        println(output)
    }

    def subscribe(consumer: KafkaConsumer[String, String], topic: String = topic, partition: Int = partition): Unit = {
        log.debug(s"subscribe(consumer, $topic, $partition)")
        val topic_partition = new TopicPartition(topic, partition)
        // conflicts with partition assignment
        // log.debug(s"subscribing to topic $topic")
        // consumer.subscribe(Arrays.asList(topic))
        log.info(s"consumer assigning topic '$topic' partition '$partition'")
        consumer.assign(Arrays.asList(topic_partition))
        // consumer.assign(Arrays.asList(partition))
        // not connected to port so no conn refused at this point
        last_offset = consumer.position(topic_partition)
    }

    def produce(producer: KafkaProducer[String, String], topic: String = topic, partition: Int = partition, msg: String = msg): Unit = {
        log.debug(s"produce(producer, $topic, $partition, $msg")
        log.info(s"sending message to topic $topic partition $partition")
        producer.send(new ProducerRecord[String, String](topic, partition, id, msg)) // key and partition optional
        log.info("producer.flush()")
        producer.flush()
        log.info("producer.close()")
        producer.close() // blocks until msgs are sent
    }

    def consume(consumer: KafkaConsumer[String, String], topic: String = topic, partition: Int = partition): Unit = {
        log.debug(s"consumer(consumer, $topic, $partition")
        val topic_partition = new TopicPartition(topic, partition)
        log.info(s"seeking to last known offset $last_offset")
        consumer.seek(topic_partition, last_offset)
        log.info(s"consuming from offset $last_offset")
        val records: ConsumerRecords[String, String] = consumer.poll(200) // ms
        log.info("closing consumer")
        consumer.close()
        val consumed_record_count: Int = records.count()
        log.info(s"consumed record count = $consumed_record_count")
        assert(consumed_record_count != 0)
        var msg2: String = null
        for (record: ConsumerRecord[String, String] <- records) {
            val record_topic = record.topic()
            val value = record.value()
            log.info(s"found message, topic '$record_topic', value = '$value'")
            assert(topic.equals(record_topic))
            if (msg.equals(value)) {
                msg2 = value
            }
        }
        log.info(s"message returned: $msg2")
        log.info(s"message expected: $msg")
        if (msg2 == null) {
            println("CRITICAL: message not returned by Kafka")
            System.exit(2)
        } else if (!msg.equals(msg2)) {
            println("CRITICAL: message returned does not equal message sent!")
            System.exit(2)
        }
    }

}
