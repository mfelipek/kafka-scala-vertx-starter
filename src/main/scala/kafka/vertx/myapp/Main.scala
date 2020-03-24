package kafka.vertx.myapp

import com.typesafe.scalalogging.StrictLogging
import io.vertx.lang.scala.{ScalaVerticle, VertxExecutionContext}
import io.vertx.scala.core.{Vertx, VertxOptions}
import kafka.vertx.myapp.websocket.WebsocketVerticle

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object Main extends App with StrictLogging {

  val options = VertxOptions()
  private val vertx = Vertx.currentContext.map(_.owner()).getOrElse(Vertx.vertx(options))
  implicit val ec: ExecutionContext = VertxExecutionContext(vertx.getOrCreateContext())

  vertx.deployVerticleFuture(ScalaVerticle.nameForVerticle[WebsocketVerticle])
    .onComplete{
      case Success(name) => logger.info("Websocket verticle deployed; %s", name)
      case Failure(t) => logger.error("Failed deploying websocket verticle", t)
    }
}
