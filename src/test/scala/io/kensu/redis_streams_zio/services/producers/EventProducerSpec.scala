package io.kensu.redis_streams_zio.services.producers

import java.util.concurrent.TimeUnit

import io.kensu.redis_streams_zio.config.{StreamKey, StreamName}
import io.kensu.redis_streams_zio.redis.streams.NotificationsRedisStream
import io.kensu.redis_streams_zio.redis.streams.{RedisStream, StreamInstance}
import io.kensu.redis_streams_zio.specs.mocks.NotificationsRedisStreamMock
import org.redisson.api.StreamMessageId
import zio.{Chunk, Has, ULayer, ZLayer}
import zio.clock.*
import zio.duration.*
import zio.logging.Logging
import zio.test.*
import zio.test.Assertion.*
import zio.test.environment.{TestClock, TestEnvironment}
import zio.test.mock.Expectation.*

object EventProducerSpec extends DefaultRunnableSpec:

  import TestData.*

  private def testEnv(redisStreamMock: ULayer[Has[RedisStream[StreamInstance.Notifications]]]) =
    (redisStreamMock ++ ZLayer.identity[Clock] ++ Logging.ignore) >>> NotificationsEventProducer.redis

  override def spec: ZSpec[TestEnvironment, Failure] =
    suite("EventProducer.redis")(
      suite("publish")(
        testM("fail if cannot send an event") {
          val redisStreamMock =
            NotificationsRedisStreamMock.StreamInstance(value(StreamInstance.Notifications(streamName))) ++
              NotificationsRedisStreamMock.Add(
                equalTo(testStreamKey, testEventBytes),
                failure(new RuntimeException("BOOM"))
              ) ++
              NotificationsRedisStreamMock.Add(
                equalTo(testStreamKey, testEventBytes),
                failure(new RuntimeException("BOOM"))
              ) ++
              NotificationsRedisStreamMock.Add(
                equalTo(testStreamKey, testEventBytes),
                failure(new RuntimeException("BOOM"))
              ) ++
              NotificationsRedisStreamMock.Add(
                equalTo(testStreamKey, testEventBytes),
                failure(new RuntimeException("BOOM"))
              )

          (for
            timeBefore <- currentTime(TimeUnit.SECONDS)
            forked     <- NotificationsEventProducer(_.publish(testStreamKey, testEvent)).run.fork
            _          <- TestClock.adjust(21.seconds) // 3 retries for 3 sec exponential * 2
            msg        <- forked.join
            timeAfter  <- currentTime(TimeUnit.SECONDS)
          yield {
            assert(msg)(fails(isSubtype[RuntimeException](anything))) &&
            assert(timeAfter - timeBefore)(isGreaterThanEqualTo(21L))
          }).provideSomeLayer[TestEnvironment](testEnv(redisStreamMock))
        },
        testM("succeed if can send an event") {
          val redisStreamMock =
            NotificationsRedisStreamMock.StreamInstance(value(StreamInstance.Notifications(streamName))) ++
              NotificationsRedisStreamMock.Add(
                equalTo(testStreamKey, testEventBytes),
                value(new StreamMessageId(123L, 456L))
              )

          NotificationsEventProducer(_.publish(testStreamKey, testEvent))
            .map(createdMsgId => assert(createdMsgId)(equalTo(PublishedEventId("123-456"))))
            .provideCustomLayer(testEnv(redisStreamMock))
        }
      )
    )

  private object TestData:

    val streamName: StreamName      = StreamName("test-stream")
    val testStreamKey: StreamKey    = StreamKey("create")
    val testEvent: TestEvent        = TestEvent("Important delivery!")
    val testEventBytes: Chunk[Byte] = Chunk.fromArray(testEvent.asBytes)

  final case class TestEvent(msg: String):
    lazy val asBytes: Array[Byte] = msg.getBytes("UTF-8")

  object TestEvent:

    given EventSerializable[TestEvent] =
      (e: TestEvent) => e.asBytes
