package io.kensu.redis_streams_zio.services.producers

import java.util.concurrent.TimeUnit

import io.kensu.redis_streams_zio.config.{ StreamKey, StreamName }
import io.kensu.redis_streams_zio.redis.streams.RedisStream.RedisStream
import io.kensu.redis_streams_zio.redis.streams.StreamInstance
import io.kensu.redis_streams_zio.redis.streams.StreamInstance.Notifications
import io.kensu.redis_streams_zio.specs.mocks.RedisStreamMock
import io.kensu.redis_streams_zio.specs.zio.CustomAssertions
import org.redisson.api.StreamMessageId
import zio.clock._
import zio.duration._
import zio.logging.Logging
import zio.test.Assertion._
import zio.test.Eql.eqlReflexive
import zio.test._
import zio.test.environment.{ TestClock, TestEnvironment }
import zio.test.mock.Expectation._
import zio.{ Chunk, ULayer, ZLayer }

object EventProducerSpec extends DefaultRunnableSpec with CustomAssertions {

  import TestData._

  private def testEnv(redisStreamMock: ULayer[RedisStream[StreamInstance.Notifications]]) =
    redisStreamMock ++ ZLayer.identity[Clock] ++ Logging.ignore >>> EventProducer.redisFor(
      Notifications(testStreamName)
    )

  override def spec: ZSpec[TestEnvironment, Failure] =
    suite("EventProducer.redis")(
      suite("publish")(
        testM("fail if cannot send an event") {
          val redisStreamMock =
            RedisStreamMock.Add(equalTo(testStreamKey, testEventBytes), failure(new RuntimeException("BOOM"))) ++
              RedisStreamMock.Add(equalTo(testStreamKey, testEventBytes), failure(new RuntimeException("BOOM"))) ++
              RedisStreamMock.Add(equalTo(testStreamKey, testEventBytes), failure(new RuntimeException("BOOM"))) ++
              RedisStreamMock.Add(equalTo(testStreamKey, testEventBytes), failure(new RuntimeException("BOOM")))

          (for {
            timeBefore <- currentTime(TimeUnit.SECONDS)
            forked     <- EventProducer.publish[StreamInstance.Notifications, TestEvent](testStreamKey, testEvent).run.fork
            _          <- TestClock.adjust(21.seconds) //3 retries for 3 sec exponential * 2
            msg        <- forked.join
            timeAfter  <- currentTime(TimeUnit.SECONDS)
          } yield {
            assert(msg)(fails(isSubtype[RuntimeException](anything))) &&
            assert(timeAfter - timeBefore)(isGreaterThanEqualTo(21L))
          }).provideSomeLayer[TestEnvironment](testEnv(redisStreamMock))
        },
        testM("succeed if can send an event") {
          val redisStreamMock =
            RedisStreamMock.Add(equalTo(testStreamKey, testEventBytes), value(new StreamMessageId(123L, 456L)))

          EventProducer
            .publish[StreamInstance.Notifications, TestEvent](testStreamKey, testEvent)
            .map(createdMsgId => assert(createdMsgId)(equalTo(PublishedEventId("123-456"))))
            .provideCustomLayer(testEnv(redisStreamMock))
        }
      )
    )

  private object TestData {

    val testStreamName = StreamName("test-stream")
    val testStreamKey  = StreamKey("create")
    val testEvent      = TestEvent("Important delivery!")
    val testEventBytes = Chunk.fromArray(testEvent.asBytes)
  }

  final case class TestEvent(msg: String) {
    lazy val asBytes = msg.getBytes("UTF-8")
  }
  object TestEvent {
    implicit val eventSerializable: EventSerializable[TestEvent] = new EventSerializable[TestEvent] {
      override def serialize(e: TestEvent): Array[Byte] = e.asBytes
    }
  }
}
