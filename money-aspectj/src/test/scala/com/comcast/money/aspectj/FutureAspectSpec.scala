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

package com.comcast.money.core.concurrent

import com.comcast.money.annotations.Traced
import com.comcast.money.api.Span
import com.comcast.money.core.internal.SpanLocal
import com.comcast.money.core.{ LogRecord, SpecHelpers, Tracers }
import org.scalatest._
import com.comcast.money.core.Money.Environment.tracer
import org.scalatest.mock.MockitoSugar

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class FutureAspectSpec extends FeatureSpecLike
    with Matchers
    with MockitoSugar
    with OneInstancePerTest
    with ConcurrentSupport
    with SpecHelpers
    with GivenWhenThen
    with BeforeAndAfterEach {

  override def beforeEach(): Unit = {
    LogRecord.clear()
    SpanLocal.clear()
  }

  feature("Traced futures") {
    scenario("Nested traces that propagate futures") {
      Given("a traced future that has a nested traced future in it")
      And("each future uses flatMap and map")

      @Traced(value = "nested", async = true)
      def nested() = {
        Future {
          tracer.record("begin", "nested")
          println(s"\r\n; begin nested ${SpanLocal.current}")
          Some(456)
        }.flatMap {
          case Some(v) =>
            tracer.record("flatMap", "nested")
            println(s"\r\n; flatMap nested ${SpanLocal.current}")
            Future {
              v
            }
        }.map {
          _ =>
            tracer.record("map", "nested")
            println(s"\r\n; map nested ${SpanLocal.current}")
            SpanLocal.current.get
        }
      }

      @Traced(value = "root", async = true)
      def root() = {
        Future {
          tracer.record("begin", "root")
          println(s"\r\n; begin root ${SpanLocal.current}")

          nested()
        }.flatMap { child =>
          tracer.record("flatMap", "root")
          println(s"\r\n; flatMap root ${SpanLocal.current}")
          child
        }.map {
          s =>
            tracer.record("map", "root")
            println(s"\r\n; map root ${SpanLocal.current}")
            (SpanLocal.current.get, s)
        }
      }

      val fut = root()

      When("the future completes execution")
      val (rootSpan, nestedSpan) = Await.result(fut, 2 seconds)

      Then("the parent of the nested trace is the root trace")
      nestedSpan.info().id().parentId() shouldEqual rootSpan.info().id().selfId()

      LogRecord.log("log").foreach(System.out.println)

      And("the root trace has notes for begin, flatMap, and map")
      expectLogMessageContainingStrings(Seq("[ begin=root ]", "[ flatMap=root ]", "[ map=root ]"))

      And("the nested trace has notes for begin, flatMap, and map")
      expectLogMessageContainingStrings(Seq("[ begin=nested ]", "[ flatMap=nested ]", "[ map=nested ]"))
    }
    scenario("foreach") {
      Given("a traced future that returns a string value")
      And("a foreach exists on the traced future that records a note and converts the string to a double")

      @Traced(value = "root", async = true)
      def root() = {
        val fut = Future {
          println("beginning root")
          tracer.record("begin", "root")
          "100"
        }
        fut.foreach { v =>
          println("foreach")
          tracer.record("gater", v.toDouble)
          v.toDouble
        }
        fut
      }

      val fut = root()

      When("the future is executed")
      Await.result(fut, 2 seconds)

      Then("the trace contains notes from both the main future method and the foreach method")
      expectLogMessageContainingStrings(Seq("[ begin=root ]", "[ gater=100.0 ]"))
    }

    scenario("filter") {
      Given("a traced future that runs a filter")

      @Traced(value = "root", async = true)
      def root() =
        Future {
          tracer.record("begin", "root")
          println(s"\r\n; begin root ${SpanLocal.current}")
          100
        }.map { v =>
          tracer.record("map", "root")
          println(s"\r\n; map root ${SpanLocal.current}")
          v
        }.filter(_ <= 100).map { v =>
          tracer.record("map2", "root")
          println(s"\r\n; map2 root ${SpanLocal.current}")
          200
        }

      val fut = root()

      When("the future is executed")
      Await.result(fut, 2 seconds)

      Then(
        "the trace contains the items recorded from the main future, map, and the note recorded after the filter is " +
          "applied"
      )
      expectLogMessageContainingStrings(Seq("[ begin=root ]", "[ map=root ]", "[ map2=root ]"))
    }

    scenario("filter does not match") {
      Given("a traced future that runs a filter whose filter does not match")

      @Traced(value = "root", async = true)
      def root() = Future {
        tracer.record("begin", "root")
        println(s"\r\n; begin root ${SpanLocal.current}")
        100
      }.map { v =>
        tracer.record("map", "root")
        println(s"\r\n; map root ${SpanLocal.current}")
        v
      }.filter(_ > 1000).map { v =>
        tracer.record("map2", "root")
        println(s"\r\n; map2 root ${SpanLocal.current}")
        200
      }

      val fut = root()

      When("the future completes")

      Then("a NoSuchElementException is thrown")
      intercept[NoSuchElementException] {
        Await.result(fut, 2 seconds)
      }

      And("the trace span does not contain the notes recorded after the filter")
      And("the result of the trace span is a failure")
      expectLogMessageContainingStrings(Seq("begin=root", "map=root", "span-success=false"))
    }

    scenario("collect") {
      Given("a traced future that runs a collect and the collect matches")

      @Traced(value = "root", async = true)
      def root() = Future {
        tracer.record("begin", "root")
        println(s"\r\n; begin root ${SpanLocal.current}")
        100
      }.map { v =>
        tracer.record("map", "root")
        println(s"\r\n; map root ${SpanLocal.current}")
        v
      }.collect {
        case v =>
          tracer.record("collect", "root")
          v
      }.map { v =>
        tracer.record("map2", "root")
        println(s"\r\n; map2 root ${SpanLocal.current}")
        200
      }

      val fut = root()

      When("the future completes")
      Await.result(fut, 2 seconds)

      Then("the trace span contains notes recorded in all functions attached to the future")
      expectLogMessageContainingStrings(Seq("begin=root", "collect=root", "map=root", "map2=root", "span-success=true"))
    }

    scenario("collect does not match") {
      Given("a traced future that has a collect that does not match")

      @Traced(value = "root", async = true)
      def root() = Future {
        tracer.record("begin", "root")
        println(s"\r\n; begin root ${SpanLocal.current}")
        Thread.sleep(500)
        100
      }.map { v =>
        tracer.record("map", "root")
        println(s"\r\n; map root ${SpanLocal.current}")
        v
      }.collect {
        case v if v > 1000 =>
          tracer.record("collect", "root")
          v
      }.map { v =>
        tracer.record("map2", "root")
        println(s"\r\n; map2 root ${SpanLocal.current}")
        200
      }

      val fut = root()

      When("the future completes")
      Then("a NoSuchElementException is expected")
      intercept[NoSuchElementException] {
        Await.result(fut, 2 seconds)
      }

      And("the trace span contains all notes that were recorded before the collect")
      And("does not contain notes recorded in the collect or after")
      And("the result of the trace span is failure")
      expectLogMessageContainingStrings(Seq("begin=root", "map=root", "span-success=false"))
    }

    scenario("recover") {
      Given("a traced future that throws an expected exception has a recover attached")

      @Traced(value = "root", async = true)
      def root() = Future {
        tracer.record("begin", "root")
        println(s"\r\n; begin root ${SpanLocal.current}")
        100
      }.map { v =>
        tracer.record("map", "root")
        println(s"\r\n; map root ${SpanLocal.current}")
        throw new IllegalArgumentException("fail")
      }.recover {
        case _ =>
          tracer.record("recover", "root")
          0
      }

      val fut = root()

      When("the future completes")
      Await.result(fut, 2 seconds)

      Then("no exception is thrown")
      And("the trace span contains notes from all of the functions, including the recover")
      And("the result of the trace span is success")
      expectLogMessageContainingStrings(Seq("begin=root", "map=root", "recover=root", "span-success=true"))
    }

    scenario("recover that never is called") {
      Given("a traced future that executes normally")
      And("the traced future has a recover")

      @Traced(value = "root", async = true)
      def root() = Future {
        tracer.record("begin", "root")
        println(s"\r\n; begin root ${SpanLocal.current}")
        100
      }.map { v =>
        tracer.record("map", "root")
        println(s"\r\n; map root ${SpanLocal.current}")
      }.recover {
        case _ =>
          tracer.record("recover", "root")
          0
      }

      val fut = root()

      When("the future completes")
      Await.result(fut, 2 seconds)

      Then("the trace span does not record any notes captured inside of the recover")
      expectLogMessageContainingStrings(Seq("begin=root", "map=root", "span-success=true"))
    }

    scenario("nested recover") {
      Given("a traced future that contains a nested trace future")
      And("the nested trace future throws an exception")
      And("the nested trace future has a recover")

      @Traced(value = "nested", async = true)
      def nested(v: Int) = Future {
        tracer.record("begin", "nested")
        println(s"\r\n; ${Thread.currentThread().getId()} - ${System.nanoTime()} begin nested ${SpanLocal.current}")
        300
      }.map { v =>
        println(
          s"\r\n; ${v.getClass().getName()}; ${Thread.currentThread().getId()} - ${
            System.nanoTime()
          } map nested ${SpanLocal.current}"
        )
        tracer.record("map", "nested")
        throw new IllegalArgumentException("fail")
      }.recover {
        case _ =>
          tracer.record("recover", "nested")
          println(
            s"\r\n; ${v.getClass().getName()}; ${Thread.currentThread().getId()} - ${
              System.nanoTime()
            } map nested ${SpanLocal.current}"
          )
          200
      }

      @Traced(value = "root", async = true)
      def root() = Future {
        tracer.record("begin", "root")
        println(s"\r\n; ${System.nanoTime()} begin root ${SpanLocal.current}")
        Thread.sleep(10)
        100
      }.map { v =>
        tracer.record("map", "root")
        println(s"\r\n; ${Thread.currentThread().getId()} - ${System.nanoTime()} map root ${SpanLocal.current}")

        nested(v)
      }.map {
        case _ =>
          0
      }

      val fut = root()

      When("the traced future completes")
      Await.result(fut, 2 seconds)

      Then("the trace span contains all notes captured by the root future")
      And("the root trace span completes with success")
      expectLogMessageContainingStrings(Seq("begin=root", "map=root", "span-success=true"))

      And("the nested trace span contains all notes captured by the root future")
      And("the nested trace span completes with success")
      expectLogMessageContainingStrings(Seq("begin=nested", "map=nested", "recover=nested", "span-success=true"))
    }

    scenario("failure results in a failed result in the span") {
      Given("a traced future throws an exception")

      @Traced(value = "root", async = true)
      def root() = Future {
        tracer.record("begin", "root")
        println(s"\r\n; begin root ${SpanLocal.current}")
        Thread.sleep(10)
        100
      }.map { v =>
        tracer.record("map", "root")
        println(s"\r\n; map root ${SpanLocal.current}")
        throw new IllegalArgumentException("fail")
      }

      val fut = root()

      When("the future completes")
      Then("the exception is thrown")
      intercept[IllegalArgumentException] {
        Await.result(fut, 2 seconds)
      }

      And("the trace span contains all notes recorded")
      And("the trace span completes with a failure")
      expectLogMessageContainingStrings(Seq("begin=root", "map=root", "span-success=false"))
    }

    scenario("transformed on failure") {
      Given("a traced future that contains a transform")
      And("the transform records a note on failure")
      And("the future throws an exception")

      @Traced(value = "root", async = true)
      def root() = Future {
        tracer.record("begin", "root")
        println(s"\r\n; begin root ${SpanLocal.current}")
        Thread.sleep(10)
        100
      }.map { v =>
        tracer.record("map", "root")
        println(s"\r\n; map root ${SpanLocal.current}")
        throw new IllegalArgumentException("fail")
      }.transform(
        { s =>
          tracer.record("success", "transform")
          200
        }, { t: Throwable =>
          tracer.record("failed", "transform")
          new IllegalStateException("fail on transform")
        }
      )

      val fut = root()

      When("the future completes")
      Then("the exception is thrown")
      intercept[IllegalStateException] {
        Await.result(fut, 2 seconds)
      }

      And("the trace span contains a note recorded in the failure function on the transform")
      And("the trace span results in a failure")
      expectLogMessageContainingStrings(Seq("begin=root", "failed=transform", "map=root", "span-success=false"))
    }

    scenario("transformed on success") {
      Given("a traced future that has a transform")
      And("the transform records a note on success")
      And("the future runs normally")

      @Traced(value = "root", async = true)
      def root() = Future {
        tracer.record("begin", "root")
        println(s"\r\n; begin root ${SpanLocal.current}")
        Thread.sleep(10)
        100
      }.map { v =>
        tracer.record("map", "root")
        println(s"\r\n; map root ${SpanLocal.current}")
      }.transform(
        { s =>
          tracer.record("success", "transform")
          200
        }, { t: Throwable =>
          tracer.record("failed", "transform")
          new IllegalStateException("fail on transform")
        }
      )

      val fut = root()

      When("the future completes")
      Await.result(fut, 2 seconds)

      Then("all notes are captured including the note recorded on success in the transform")
      expectLogMessageContainingStrings(Seq("begin=root", "map=root", "success=transform"))
    }

    scenario("onFailure") {
      Given("a traced future that has an onFailure")
      And("the traced future throws an exception")

      @Traced(value = "root", async = true)
      def root() = {
        val fut = Future {
          tracer.record("begin", "root")
          println(s"\r\n; onFailure begin root ${SpanLocal.current}")
          // Adding a sleep to ensure that we do not complete the future before adding the onFailure
          // if a future is complete already, onFailure will never fire
          Thread.sleep(500)
          100
        }.map { v =>
          tracer.record("map", "root")
          println(s"\r\n; onFailure map root ${SpanLocal.current}")
          try {
            600
          } finally {
            throw new IllegalArgumentException("fail")
          }
        }
        fut.onFailure {
          case _ =>
            println(s"\r\n; onFailure root ${SpanLocal.current}")
            tracer.record("onFailure", "root")
            200
        }
        fut
      }

      val fut = root()

      When("the future completes")
      Then("the exception is thrown")
      And("the result of the onFailure is returned")
      intercept[IllegalArgumentException] {
        val result = Await.result(fut, 2 seconds)
        result shouldEqual 200
      }

      And("the trace span contains all notes recorded, including the note recorded onFailure")
      And("the result of the trace is failure")
      expectLogMessageContainingStrings(Seq("begin=root", "map=root", "onFailure=root", "span-success=false"))
    }

    scenario("onSuccess") {
      Given("a traced future that has an onSuccess")
      And("the onSuccess records a note")

      @Traced(value = "root", async = true)
      def root() = {
        val fut = Future {
          tracer.record("begin", "root")
          println(s"\r\n; onSuccess begin root ${SpanLocal.current}")
          // Adding a sleep to ensure that we do not complete the future before adding the onSuccess
          // if a future is complete already, onFailure will never fire
          Thread.sleep(500)
          100
        }.map { v =>
          tracer.record("map", "root")
          println(s"\r\n; onSuccess map root ${SpanLocal.current}")
          600
        }

        fut.onSuccess {
          case _ =>
            println(s"\r\n; onSuccess root ${SpanLocal.current}")
            tracer.record("onSuccess", "root")
        }
        fut
      }

      val fut = root()

      When("the future completes")
      val result = Await.result(fut, 2 seconds)

      Then("the result of the main future is returned")
      result shouldEqual 600

      And("the trace span contains all notes, including the note recorded onSuccess")
      expectLogMessageContainingStrings(Seq("begin=root", "map=root", "onSuccess=root", "span-success=true"))
    }

    scenario("recoverWith") {
      Given("a traced future that has a recoverWith")
      And("the future throws an exception")
      And("the recover with records a note")

      @Traced(value = "root", async = true)
      def root() = Future {
        tracer.record("begin", "root")
        println(s"\r\n; begin root ${SpanLocal.current}")
        100
      }.map { v =>
        tracer.record("map", "root")
        println(s"\r\n; map root ${SpanLocal.current}")
        try {
          600
        } finally {
          throw new IllegalArgumentException("fail")
        }
      }.recoverWith {
        case e: IllegalArgumentException =>
          tracer.record("recoverWith", "root")
          Future {
            0
          }
      }

      val fut = root()

      When("the future completes")
      Await.result(fut, 2 seconds)

      Then("the trace span contains all notes, including the note recorded in recoverWith")
      And("the result of the trace span is success")
      expectLogMessageContainingStrings(Seq("begin=root", "map=root", "recoverWith=root", "span-success=true"))
    }
    scenario("simple for comprehension") {
      Given("two traced futures")

      @Traced(value = "one", async = true)
      def one() = Future {
        tracer.record("in", "one")
        Thread.sleep(50)
        SpanLocal.current.get
      }

      @Traced(value = "two", async = true)
      def two() = Future {
        tracer.record("in", "two")
        Thread.sleep(50)
        SpanLocal.current.get
      }

      val fut1 = one()
      val fut2 = two()

      And("a for comprehension that operates on each of the futures")
      val simpleMath = for {
        one <- fut1
        two <- fut2
      } yield (one, two)

      When("the for comprehension completes")
      val (firstSpan, secondSpan) = Await.result(simpleMath, 2 seconds)

      Then("the two futures are not linked through a parent id")
      secondSpan.info().id().parentId() shouldNot be(firstSpan.info().id().selfId())

      And("the first span contains the note it recorded")
      expectLogMessageContaining("[ in=one ]")

      And("the second span contains the note it recorded")
      expectLogMessageContaining("[ in=two ]")
    }

    scenario("zip") {
      Given("two separately traced futures")

      @Traced(value = "one", async = true)
      def one() = Future {
        tracer.record("in", "one")
        Thread.sleep(50)
        SpanLocal.current.get
      }

      @Traced(value = "two", async = true)
      def two() = Future {
        tracer.record("in", "two")
        Thread.sleep(50)
        SpanLocal.current.get
      }

      val fut1 = one()
      val fut2 = two()

      And("the futures are zipped")
      val combined = fut1 zip fut2

      When("the zipped future completes")
      val (firstSpan, secondSpan) = Await.result(combined, 2 seconds)

      Then("the two futures are not lined through a parent")
      secondSpan.info().id().parentId() shouldNot be(firstSpan.info().id().selfId())

      And("the first span contains the message it recorded")
      expectLogMessageContaining("[ in=one ]")

      And("the second span contains the message it recorded")
      expectLogMessageContaining("[ in=two ]")
    }

    scenario("fallbackTo on failure") {
      Given("a traced future that throws an exception")

      @Traced(value = "one", async = true)
      def one() = Future {
        tracer.record("in", "one")
        Thread.sleep(50)
        try {
          100
        } finally {
          throw new IllegalStateException("fail me")
        }
      }

      And("another traced future that runs normally")

      @Traced(value = "fall", async = true)
      def fall() = Future {
        println(s"\r\nfall back guy")
        tracer.record("fall", "guy")
        SpanLocal.current.get
      }

      val fut = one()
      val fut2 = fall()

      And("the failing future falls back to the successful future")
      val fut3 = fut fallbackTo fut2

      When("the fallback completes")
      Await.result(fut3, 2 seconds)

      Then("the result of the failing future contains notes it recorded")
      And("completes with a result of failed")
      expectLogMessageContainingStrings(Seq("in=one", "span-success=false"))

      And("the result of the fallback future contains notes it recorded")
      And("completes with a result of success")
      expectLogMessageContainingStrings(Seq("fall=guy", "span-success=true"))
    }
  }
}