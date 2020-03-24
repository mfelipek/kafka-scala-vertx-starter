package kafka.vertx.myapp.consumer

case class DemoConsumedRecord(message: String, topic: String, partition: Int, offset: Long, value: String, timestamp: Long)
