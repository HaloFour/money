/*
 * Copyright 2012-2015 Comcast Cable Communications Management, LLC
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

package com.comcast.money.core

import com.comcast.money.api.SpanId
import org.scalatest.{ Matchers, WordSpec }
import Formatters._

class FormattersSpec extends WordSpec with Matchers {

  private val expectedB3TraceIdHeaderVal = "61616161616161616161616161616161"
  private val expectedTraceIdGuid = "61616161-6161-6161-6161-616161616161"
  private val expectedParentSpanId = 1
  private val expectedSpanId = 2
  "Http Formatting" should {
    "convert from a money  http header" in {
      val spanId = new SpanId()
      Formatters.toMoneyHeader(spanId, (_, test) =>
        Formatters.fromMoneyHeader(_ => test).get shouldBe spanId)
    }

    def getTestHeader(header: String): String = header match {
      case B3TraceIdHeader => expectedB3TraceIdHeaderVal
      case B3ParentSpanIdHeader => expectedParentSpanId.toHexString
      case B3SpanIdHeader => expectedSpanId.toHexString
    }

    "convert from all X-B3 http headers" in {
      val expectedMaxParentSpanIdVal = Long.MaxValue
      val expectedMinSpanIdVal = Long.MinValue
      def getMaxMinHeader(header: String): String = header match {
        case B3TraceIdHeader => expectedB3TraceIdHeaderVal
        case B3ParentSpanIdHeader => expectedMaxParentSpanIdVal.toHexString
        case B3SpanIdHeader => expectedMinSpanIdVal.toHexString
      }

      val actualSpanId = Formatters.fromB3HttpHeaders(getMaxMinHeader, s => { println(s) }).get
      actualSpanId.traceId shouldBe expectedTraceIdGuid
      actualSpanId.parentId shouldBe expectedMaxParentSpanIdVal
      actualSpanId.selfId() shouldBe expectedMinSpanIdVal
    }

    "convert from 2 X-B3 http headers" in {
      val actualSpanId = Formatters.fromB3HttpHeaders(getTestHeader, s => {}).get
      actualSpanId.traceId shouldBe expectedTraceIdGuid
      actualSpanId.parentId shouldBe expectedParentSpanId.toLong
    }

    "convert from 1 X-B3 http header" in {
      val actualSpanId = Formatters.fromB3HttpHeaders(getTestHeader, s => {}).get
      actualSpanId.traceId shouldBe expectedTraceIdGuid
    }

    "convert to x-b3 headers 16 character traceId" in {
      val spanId = new SpanId(expectedTraceIdGuid, expectedParentSpanId, expectedSpanId)
      Formatters.toB3Headers(spanId, (k, v) => k match {
        case B3TraceIdHeader => v shouldBe expectedB3TraceIdHeaderVal
        case B3ParentSpanIdHeader => v shouldBe expectedParentSpanId.toHexString
        case B3SpanIdHeader => v shouldBe expectedSpanId.toHexString
      })
    }

    "convert to x-b3 headers 8 character traceId" in {
      val expectedShortB3TraceIdHeaderVal = "6161616161616161"
      val expectedShortTraceIdGuid = "61616161-6161-6161-0000-000000000000"
      val spanId = new SpanId(expectedShortTraceIdGuid, expectedParentSpanId, expectedSpanId)
      Formatters.toB3Headers(spanId, (k, v) => k match {
        case Formatters.B3TraceIdHeader => v shouldBe expectedShortB3TraceIdHeaderVal
        case Formatters.B3ParentSpanIdHeader => v shouldBe expectedParentSpanId.toHexString
        case Formatters.B3SpanIdHeader => v shouldBe expectedSpanId.toHexString
      })
    }

    "convert a string from hexadecimal to long" in {
      "61".fromHexStringToLong shouldBe 97
      "6162".fromHexStringToLong shouldBe 24930
    }

    "fail to convert a non-hex string from hexadecimal to long" in {
      intercept[NumberFormatException] { "".fromHexStringToLong }
      intercept[NumberFormatException] { "z".fromHexStringToLong }
    }

    "convert an empty string to guid format" in {
      "".toGuid shouldBe "00000000-0000-0000-0000-000000000000"
    }

    "convert a single character string to guid format" in {
      "a".toGuid shouldBe "a0000000-0000-0000-0000-000000000000"
    }

    "convert a 32 character string to guid format" in {
      "abcdefghijklmnopqrstuvwxyzabcdefg".toGuid shouldBe "abcdefgh-ijkl-mnop-qrst-uvwxyzabcdef"
    }

    "convert a 33 character string to guid format" in {
      "abcdefghijklmnopqrstuvwxyzabcdefgh".toGuid shouldBe "abcdefgh-ijkl-mnop-qrst-uvwxyzabcdef"
    }

    "convert an empty string from guid format" in {
      "".fromGuid shouldBe ""
    }

    "convert a single character string from guid format" in {
      "a".fromGuid shouldBe "a"
    }

    "convert a 32 character string from guid format" in {
      "abcdefgh-ijkl-mnop-qrst-uvwxyzabcdef".fromGuid shouldBe "abcdefghijklmnopqrstuvwxyzabcdef"
    }
  }
}
