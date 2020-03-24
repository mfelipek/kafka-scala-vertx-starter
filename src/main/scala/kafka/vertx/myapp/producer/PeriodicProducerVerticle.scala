package kafka.vertx.myapp.producer

import com.typesafe.scalalogging.StrictLogging
import io.vertx.core.json.JsonObject
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.scala.kafka.client.producer.{KafkaProducer, RecordMetadata}

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

class PeriodicProducerVerticle(config: mutable.Map[String, String], topicName: String, message: String, eventBusId: String) extends ScalaVerticle with StrictLogging {

  def sendRecordResultToEventBus(record: RecordMetadata): Unit = {
    val recordData = RecordData(record.getTopic, record.getPartition, record.getOffset, record.getTimestamp, Delivered);
    vertx.eventBus().send(eventBusId, JsonObject.mapFrom(recordData).encode());
    logger.info("Produces message to kafka successfully")
  }

  def handleFailureInProducer(cause: Throwable): Unit = {
   logger.error("Failure in producing record: %s%n", cause)
  }

  override def startFuture(): Future[_] = Future {

    val producer: KafkaProducer[String, String] = KafkaProducer.create[String, String](vertx, config)

    val producerService = new ProducerService(producer);
    val promise = Promise[Unit]()

    vertx.setPeriodic(2000, _ => {
      producerService.produce(topicName, message)
        .onComplete{
          case Success(record) => sendRecordResultToEventBus(record)
          case Failure(cause) => handleFailureInProducer(cause)
        }
    })

    promise.success(Unit)
  }
}
