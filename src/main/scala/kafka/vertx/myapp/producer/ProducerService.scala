package kafka.vertx.myapp.producer

import com.typesafe.scalalogging.StrictLogging
import io.vertx.scala.kafka.client.producer.{KafkaProducer, KafkaProducerRecord, RecordMetadata}

import scala.concurrent.Future

class ProducerService (producer: KafkaProducer[String, String]) extends ProducerTrait with StrictLogging {

  override def produce(topic: String, message: String): Future[RecordMetadata] = {
    val record = KafkaProducerRecord.create[String, String](topic, message)
    producer.sendFuture(record)
  }
}