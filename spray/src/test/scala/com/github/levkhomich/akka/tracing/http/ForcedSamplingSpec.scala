package com.github.levkhomich.akka.tracing.http

import java.util.concurrent.TimeoutException
import scala.concurrent.Await
import scala.concurrent.duration._

import com.github.levkhomich.akka.tracing._
import org.specs2.mutable.Specification
import spray.http._
import spray.httpx.unmarshalling.{ Deserialized, FromRequestUnmarshaller }
import spray.routing.HttpService
import spray.testkit.Specs2RouteTest

class ForcedSamplingSpec extends Specification with TracingTestCommons
    with BaseTracingDirectives with MockCollector with Specs2RouteTest with HttpService {

  sequential

  override implicit val system = testActorSystem(sampleRate = Int.MaxValue)
  override val actorRefFactory = system

  override protected def trace: TracingExtensionImpl =
    TracingExtension(system)

  val tracedHandleWithRoute =
    get {
      tracedHandleWith("testService") { r: TestMessage =>
        HttpResponse(StatusCodes.OK)
      }
    }

  val tracedCompleteRoute =
    get {
      tracedComplete("testService", "testRpc")(HttpResponse(StatusCodes.OK))
    }

  "tracedHandleWith directive" should {
    val SpanCount = 100

    "force sampling of requests with X-B3-Sampled = true" in {
      for (_ <- 0 until SpanCount) {
        Get().withHeaders(
          HttpHeaders.RawHeader(TracingHeaders.Sampled, true.toString)
        ) ~> tracedHandleWithRoute ~> check {
            response.status mustEqual StatusCodes.OK
          }
      }
      expectSpans(SpanCount)
    }

    "force sampling of requests with X-B3-Flags containing Debug flag" in {
      for (i <- 0 until SpanCount) {
        Get().withHeaders(
          HttpHeaders.RawHeader(TracingHeaders.Flags, (i | TracingHeaders.DebugFlag).toString)
        ) ~> tracedHandleWithRoute ~> check {
            response.status mustEqual StatusCodes.OK
          }
      }
      expectSpans(SpanCount)
    }

  }

  "tracedComplete directive" should {
    val SpanCount = 100

    "force sampling of requests with X-B3-Sampled = true" in {
      for (_ <- 0 until SpanCount) {
        Get().withHeaders(
          HttpHeaders.RawHeader(TracingHeaders.Sampled, true.toString)
        ) ~> tracedCompleteRoute ~> check {
            response.status mustEqual StatusCodes.OK
          }
      }
      expectSpans(SpanCount)
    }

    "force sampling of requests with X-B3-Flags containing Debug flag" in {
      for (i <- 0 until SpanCount) {
        Get().withHeaders(
          HttpHeaders.RawHeader(TracingHeaders.Flags, (i | TracingHeaders.DebugFlag).toString)
        ) ~> tracedCompleteRoute ~> check {
            response.status mustEqual StatusCodes.OK
          }
      }
      expectSpans(SpanCount)
    }

  }

  step {
    collector.stop()
    terminateActorSystem(system)
  }

  implicit def um: FromRequestUnmarshaller[TestMessage] =
    new FromRequestUnmarshaller[TestMessage] {
      override def apply(request: HttpRequest): Deserialized[TestMessage] =
        Right(TestMessage(request.entity.asString))
    }

}
