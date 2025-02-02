/**
 * Copyright 2019 Confluent Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.confluent.examples.clients.cloud;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.Producer;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;

import io.confluent.examples.clients.cloud.model.DataRecord;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.common.errors.TopicExistsException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Collections;
import java.util.Map;

public class ProducerExample {

  // True - Create the given topic (default)
  // False - Don't create the topic
  public static boolean CREATE_TOPIC = true;

  // Create topic in Confluent Cloud
  public static void createTopic(final String topic,
                          final int partitions,
                          final int replication,
                          final Properties cloudConfig) {
      final NewTopic newTopic = new NewTopic(topic, partitions, (short) replication);
      try (final AdminClient adminClient = AdminClient.create(cloudConfig)) {
          adminClient.createTopics(Collections.singletonList(newTopic)).all().get();
      } catch (final InterruptedException | ExecutionException e) {
          // Ignore if TopicExistsException, which may be valid if topic exists
          if (!(e.getCause() instanceof TopicExistsException)) {
              throw new RuntimeException(e);
          }
      }
  }

  public static void main(final String[] args) throws IOException {
    if (args.length != 3) {
      System.out.println("Please provide command line arguments: configPath topic create [y|n:y]");
      System.exit(1);
    }
    
    // By default, it will create the Topic, but if 'n' or 'N' the topic creation will be skipped
    if ((args[2] != null) && (args[2].toUpperCase().startsWith("N"))) {
    	CREATE_TOPIC = false;
    }

    // Load properties from a configuration file
    // The configuration properties defined in the configuration file are assumed to include:
    //   ssl.endpoint.identification.algorithm=https
    //   sasl.mechanism=PLAIN
    //   request.timeout.ms=20000
    //   bootstrap.servers=<CLUSTER_BOOTSTRAP_SERVER>
    //   retry.backoff.ms=500
    //   sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="<CLUSTER_API_KEY>" password="<CLUSTER_API_SECRET>";
    //   security.protocol=SASL_SSL
    final Properties props = loadConfig(args[0]);

    // Create topic if needed
    final String topic = args[1];
    
    if (CREATE_TOPIC) {
    	System.out.println("About to create topic '" + topic + "'");
  		createTopic(topic, 1, 1, props);
    } else {
    	System.out.println("Skipping topic creation for topic '" + topic + "'");
    }

    // Add additional properties.
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "io.confluent.kafka.serializers.KafkaJsonSerializer");

    Producer<String, DataRecord> producer = new KafkaProducer<String, DataRecord>(props);

    // Produce sample data
    final Long numMessages = 10L;
    for (Long i = 0L; i < numMessages; i++) {
      String key = "alice";
      DataRecord record = new DataRecord(i);

      System.out.printf("Producing record: %s\t%s%n", key, record);
      producer.send(new ProducerRecord<String, DataRecord>(topic, key, record), new Callback() {
          @Override
          public void onCompletion(RecordMetadata m, Exception e) {
            if (e != null) {
              e.printStackTrace();
            } else {
              System.out.printf("Produced record to topic %s partition [%d] @ offset %d%n", m.topic(), m.partition(), m.offset());
            }
          }
      });
    }

    producer.flush();

    System.out.printf("10 messages were produced to topic %s%n", topic);

    producer.close();
  }

  public static Properties loadConfig(final String configFile) throws IOException {
    if (!Files.exists(Paths.get(configFile))) {
      throw new IOException(configFile + " not found.");
    }
    final Properties cfg = new Properties();
    try (InputStream inputStream = new FileInputStream(configFile)) {
      cfg.load(inputStream);
    }
    return cfg;
  }

}
