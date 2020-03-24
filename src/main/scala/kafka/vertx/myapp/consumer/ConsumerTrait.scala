package kafka.vertx.myapp.consumer

import scala.concurrent.Future

trait ConsumerTrait {

  def subscribe(topicName: String, eventBusId: String) : Future[_]

  def pause(topicName: String) : Future[_]
}