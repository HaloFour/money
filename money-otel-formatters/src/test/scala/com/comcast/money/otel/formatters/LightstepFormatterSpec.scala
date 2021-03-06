/*
 * Copyright 2012 Comcast Cable Communications Management, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.comcast.money.otel.formatters

import com.comcast.money.core.TraceGenerators
import com.comcast.money.core.formatters.FormatterUtils.randomRemoteSpanId
import com.comcast.money.otel.formatters.LightstepFormatter.{ TracerSampledHeader, TracerSpanIdHeader, TracerTraceIdHeader }
import org.mockito.Mockito.{ verify, verifyNoMoreInteractions }
import org.scalatest.Inside.inside
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.collection.mutable

class LightstepFormatterSpec extends AnyWordSpec with MockitoSugar with Matchers with ScalaCheckDrivenPropertyChecks with TraceGenerators {
  val underTest = LightstepFormatter
  val nullString = null.asInstanceOf[String]

  "LightstepFormatter" should {
    "can roundtrip a span id" in {
      val spanId = randomRemoteSpanId()

      val map = mutable.Map[String, String]()

      underTest.toHttpHeaders(spanId, map.put)

      val result = underTest.fromHttpHeaders(Seq(), k => map.getOrElse(k, nullString))

      inside(result) {
        case Some(s) =>
          s.selfId shouldBe spanId.selfId
          // The Lightstep formatter only supports 64-bit trace IDs so
          // the first 16 characters are zeroed out on round-trip
          s.traceIdAsHex shouldBe ("0" * 16) + spanId.traceIdAsHex.substring(16, 32)
          s.isSampled shouldBe spanId.isSampled
      }
    }

    "lists the headers" in {
      underTest.fields shouldBe Seq(TracerTraceIdHeader, TracerSpanIdHeader, TracerSampledHeader)
    }

    "copy the request headers to the response" in {
      val setHeader = mock[(String, String) => Unit]

      underTest.setResponseHeaders({
        case TracerTraceIdHeader => TracerTraceIdHeader
        case TracerSpanIdHeader => TracerSpanIdHeader
        case TracerSampledHeader => TracerSampledHeader
      }, setHeader)

      verify(setHeader).apply(TracerTraceIdHeader, TracerTraceIdHeader)
      verify(setHeader).apply(TracerSpanIdHeader, TracerSpanIdHeader)
      verify(setHeader).apply(TracerSampledHeader, TracerSampledHeader)
      verifyNoMoreInteractions(setHeader)
    }
  }
}
