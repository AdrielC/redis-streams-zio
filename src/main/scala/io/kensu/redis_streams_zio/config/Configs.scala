package io.kensu.redis_streams_zio.config

import com.typesafe.config.Config
import zio.config.ConfigDescriptor._
import zio.config._
import zio.config.magnolia.{descriptor, Descriptor}
import zio.config.typesafe.TypesafeConfig
import zio.duration.Duration
import zio.{Has, Layer, UIO}

final case class RootConfig(
  kensu: AppConfig
)

final case class AppConfig(
  redis: RedisConfig,
  redisStreams: RedisStreamsConfig
)

final case class RedisStreamsConfig(
  consumers: ConsumersConfig,
  producers: ProducersConfig
)

final case class ConsumersConfig(
  notifications: NotificationsStreamConsumerConfig
)

final case class ProducersConfig(
  notifications: NotificationsStreamProducerConfig
)

final case class RedisConfig(
  url: String,
  password: String
)

final case class StreamName(value: String) {

  override def toString: String = value
}

object StreamName {

  implicit val descriptorStreamName: Descriptor[StreamName] = Descriptor(string.to[StreamName])
}


final case class StreamGroupName(value: String) {

  override def toString: String = value
}

object StreamGroupName {

  implicit val descriptor: Descriptor[StreamGroupName] = Descriptor(string.to[StreamGroupName])
}


final case class StreamConsumerName(value: String) {
  override def toString: String = value
}

object StreamConsumerName {

  implicit val descriptor: Descriptor[StreamConsumerName] = Descriptor(string.to[StreamConsumerName])
}


final case class StreamKey(value: String) {

  override def toString: String = value
}

object StreamKey {

  implicit val descriptor: Descriptor[StreamKey] = Descriptor(string.to[StreamKey])
}


final case class ClaimingConfig(
  initialDelay: Duration,
  repeatEvery: Duration,
  maxNoOfDeliveries: Long,
  maxIdleTime: Duration
)

final case class RetryConfig(
  min: Duration,
  max: Duration,
  factor: Double
)

trait StreamConsumerConfig {

  val claiming: ClaimingConfig
  val retry: RetryConfig
  val readTimeout: Duration
  val checkPendingEvery: Duration
  val streamName: StreamName
  val groupName: StreamGroupName
  val consumerName: StreamConsumerName
}

trait StreamProducerConfig {

  val streamName: StreamName
}

final case class NotificationsStreamConsumerConfig(
  claiming: ClaimingConfig,
  retry: RetryConfig,
  readTimeout: Duration,
  checkPendingEvery: Duration,
  streamName: StreamName,
  addKey: StreamKey,
  groupName: StreamGroupName,
  consumerName: StreamConsumerName
) extends StreamConsumerConfig

final case class NotificationsStreamProducerConfig(
  streamName: StreamName,
  addKey: StreamKey
) extends StreamProducerConfig

object Configs {

  import zio.config.syntax._
  import zio.config.typesafe._

  val appConfig: Layer[ReadError[String], Has[AppConfig]] =
    read(descriptor[RootConfig].from(TypesafeConfigSource.fromResourcePath)).map(_.kensu).toLayer
}
