package kafka.vertx.myapp.producer

import io.vertx.scala.kafka.client.producer.RecordMetadata

import scala.concurrent.Future

trait ProducerTrait {

  def produce(topic: String, message: String): Future[RecordMetadata]
}
