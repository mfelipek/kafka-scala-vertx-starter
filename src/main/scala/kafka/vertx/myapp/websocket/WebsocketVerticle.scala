package kafka.vertx.myapp.websocket

import com.typesafe.scalalogging.StrictLogging
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.core.json.Json
import io.vertx.scala.core.http.ServerWebSocket
import io.vertx.scala.ext.web.Router
import io.vertx.scala.ext.web.handler.StaticHandler
import io.vertx.scala.kafka.client.consumer.KafkaConsumer
import kafka.vertx.myapp.consumer.ConsumerService
import kafka.vertx.myapp.producer.PeriodicProducerVerticle
import org.apache.log4j.BasicConfigurator

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

class WebsocketVerticle extends WebsocketScalaVerticle

class WebsocketScalaVerticle extends ScalaVerticle with StrictLogging  {

  var consuming = false
  val topic = "test"
  var periodicProducerVerticleId = ""

  val PRODUCE_PATH = "/demoproduce"
  val CONSUME_PATH = "/democonsume"

  override def startFuture(): Future[_] = Future {

    Json.mapper.registerModule(com.fasterxml.jackson.module.scala.DefaultScalaModule)

    // so we don't need to configure log4j manually
    BasicConfigurator.configure()

    val promise = Promise[Unit]()
    val server = vertx.createHttpServer()

    //Create a router to answer GET-requests to "/hello" with "world"
    val router = Router.router(vertx)
    router.get().handler(StaticHandler.create())

    server.websocketHandler((websocket: ServerWebSocket) => {
      val path = websocket.path()
      if (PRODUCE_PATH.equals(path)) {
        handleProduceSocket(websocket)
      } else if (CONSUME_PATH.equals(path)) {
        handleConsumeSocket(websocket)
      } else {
        websocket.reject()
      }

    }).requestHandler(router).listenFuture(8080, "localhost")
      .onComplete{
        case Success(_) => {
          logger.info("WebSocket is now listening%n")
          promise.success(Unit)
        }
        case Failure(t) => {
          logger.error("WebSocket failed to listen: %s%n", t)
          promise.failure(t)
        }
      }
  }

  private def retrieveKafkaConfig() : mutable.Map[String, String] = {

    val config = mutable.Map(
      "bootstrap.servers" -> "localhost:9092",
      "key.deserializer" -> "org.apache.kafka.common.serialization.StringDeserializer",
      "value.deserializer" -> "org.apache.kafka.common.serialization.StringDeserializer",
      "group.id" -> "my_group",
      "auto.offset.reset" -> "earliest",
      "enable.auto.commit" -> "false",
      "acks" -> "1",
      "key.serializer" -> "org.apache.kafka.common.serialization.StringSerializer",
      "value.serializer" -> "org.apache.kafka.common.serialization.StringSerializer")

    config
  }

  private def handleProduceSocket(websocket: ServerWebSocket): Unit = {
    websocket.handler((buffer: Buffer) => {

      val websocketMessage = buffer.getString(0, buffer.length())
      val messageObject = new JsonObject(websocketMessage)
      val action = messageObject.getString("action")

      if (action.equals("start") && periodicProducerVerticleId.isEmpty) {
        val custom = messageObject.getString("custom")
        val message = if (custom == null) "Ping" else custom

        val periodicProducer = new PeriodicProducerVerticle(retrieveKafkaConfig(), topic, message, websocket.textHandlerID())

        vertx.deployVerticleFuture(periodicProducer)
          .onComplete{
            case Success(id) => {
              periodicProducerVerticleId = id
              logger.info("Producing records...")
            }
            case Failure(t) => {
              logger.error("Failed to deploy periodicProducer verticle: %s%n", t)
            }
          }
      }
      if (action.equals("stop")) {
        undeployPeriodicProducer()
      }
    })
  }

  private def undeployPeriodicProducer(): Unit = {
    logger.info("Current producer id: %s%n", periodicProducerVerticleId)
    if (!periodicProducerVerticleId.isEmpty()) {
      vertx.undeployFuture(periodicProducerVerticleId)
        .onComplete{
          case Success(_) => {
            periodicProducerVerticleId = ""
            logger.info("Periodic producer verticle undeployed.%n")
          }
          case Failure(t) => {
            logger.error("Failed to undeploy periodicProducer verticle: %s%n", t)
          }
        }
    }
  }

  private def handleConsumeSocket(websocket: ServerWebSocket): Unit = {
    val consumer: KafkaConsumer[String, String] = KafkaConsumer.create[String, String](vertx, retrieveKafkaConfig())

    val consumerService = new ConsumerService(consumer)
    websocket.handler((buffer: Buffer) => {
      val message = buffer.getString(0, buffer.length())
      val messageObject = new JsonObject(message)
      val action = messageObject.getString("action")
      if (action.equals("start") && !consuming) {
        consumerService.subscribe(topic, websocket.textHandlerID())
          .onComplete{
            case Success(_) => {
              consuming = true
              logger.info("Consuming records...%n")
            }
            case Failure(t) => {
              logger.error("Failed to start consuming: %s%n", t)
            }
          }
      }

      if (action.equals("stop") && consuming) {
        pauseConsumer(consumerService)
      }
    })
  }

  private def pauseConsumer(consumer: ConsumerService): Unit = {
    if (consuming) {
      consumer.pause(topic)
        .onComplete{
          case Success(_) => {
            consuming = false
            logger.info("Stopped consuming.%n")
          }
          case Failure(t) => {
            logger.error("Failed to pause the consumer: %s%n", t)
          }
        }
    }
  }
}
