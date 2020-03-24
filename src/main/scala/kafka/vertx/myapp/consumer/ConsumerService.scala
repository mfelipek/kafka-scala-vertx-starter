package kafka.vertx.myapp.consumer

import com.typesafe.scalalogging.StrictLogging
import io.vertx.core.json.JsonObject
import io.vertx.lang.scala.VertxExecutionContext
import io.vertx.scala.core.Vertx
import io.vertx.scala.kafka.client.common.TopicPartition
import io.vertx.scala.kafka.client.consumer.{KafkaConsumer, KafkaConsumerRecord}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

class ConsumerService(consumer: KafkaConsumer[String, String]) extends ConsumerTrait with StrictLogging {

  private val vertx = Vertx.currentContext.map(_.owner()).getOrElse(Vertx.vertx())
  implicit val ec: ExecutionContext = VertxExecutionContext(vertx.getOrCreateContext())

  val ADDRESS = "kafka-consumer-service-address"

  var paused: Boolean = false

  override def subscribe(topicName: String, eventBusId: String): Future[_] = {
    val promise = Promise[Unit]()

    if (paused) {
      val topicPartition = TopicPartition().setTopic(topicName)
      consumer.resumeFuture(topicPartition)
        .onComplete {
          case Success(_) => handleSuccessInResumedConsumer(promise)
          case Failure(cause) => handleFailureInResumedConsumer(cause, promise)
        }
    } else {
      consumer.handler(handleConsumedKafkaMessage(topicName, eventBusId, _))
      consumer.subscribeFuture(topicName).onComplete{
        case Success(_) => {
          logger.info("Consumer subscribed successfully to kafka topic")
        }
        case Failure(cause) => {
          logger.error(s"$cause")
        }
      }
    }

    promise.future
  }

  private def handleConsumedKafkaMessage(topicName: String, eventBusId: String, record: KafkaConsumerRecord[String, String]): Unit = {
    val consumedRecord = DemoConsumedRecord(record.value(), record.topic, record.partition, record.offset, record.value, record.timestamp)
    val jsonObject = JsonObject.mapFrom(consumedRecord)
    vertx.eventBus().send(eventBusId, jsonObject.encode())
  }

  private def handleSuccessInResumedConsumer(promise: Promise[Unit]): Unit = {
    System.out.printf("Success in resumed consumer")
    promise.success(Unit)
  }

  private def handleFailureInResumedConsumer(cause: Throwable, promise: Promise[Unit]): Unit = {
    System.out.printf("Failure in resumed consumer: %s%n", cause)
    promise.failure(cause)
  }

  override def pause(topicName: String): Future[_] = {
    val topicPartition = TopicPartition().setTopic(topicName)

    consumer.pauseFuture(topicPartition)
  }
}
