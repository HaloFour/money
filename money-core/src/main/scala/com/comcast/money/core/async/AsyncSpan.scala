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

package com.comcast.money.core.async

import java.lang
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger }

import com.comcast.money.api.{ Note, Span, SpanInfo }

class AsyncSpan(span: Span) extends Span {
  private val counter = new AtomicInteger(1)
  private var result = true

  /**
   * Signals the span that it has started
   */
  override def start(): Unit =
    System.out.printf("Incrementing async counter to %s.%n", counter.incrementAndGet().toString())

  /**
   * Stops the span asserts a successful result
   */
  override def stop(): Unit = stop(true)

  /**
   * Ends a span, moving it to a Stopped state
   *
   * @param result The result of the span (success or failure)
   */
  override def stop(result: lang.Boolean): Unit = {
    if (result == false) {
      this.result = false
    }
    val remaining = counter.decrementAndGet()
    System.out.printf("Decrementing async counter to %s.%n", remaining.toString())
    if (remaining == 0) {
      System.out.println("Stopping async span")
      span.stop(this.result)
    }
  }

  /**
   * Records a given note onto the span.  If the note was already present, it will be overwritten
   *
   * @param note The note to be recorded
   */
  override def record(note: Note[_]): Unit = span.record(note)

  /**
   * Starts a new timer on the span
   *
   * @param timerKey The name of the timer to start
   */
  override def startTimer(timerKey: String): Unit = span.startTimer(timerKey)

  /**
   * Stops an existing timer on the span
   *
   * @param timerKey The name of the timer
   */
  override def stopTimer(timerKey: String): Unit = span.startTimer(timerKey)

  /**
   * @return The current state of the Span
   */
  override def info(): SpanInfo = span.info()

  override def toString: String = s"Async(${counter.get}, ${span.toString})"
}
