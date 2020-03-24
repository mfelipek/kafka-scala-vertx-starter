package kafka.vertx.myapp.producer

sealed trait RecordDataStatus

case object Delivered extends RecordDataStatus
case object Error extends RecordDataStatus

case class RecordData(topic: String, partition: Int, offset: Long, timestamp: Long, status: RecordDataStatus)
