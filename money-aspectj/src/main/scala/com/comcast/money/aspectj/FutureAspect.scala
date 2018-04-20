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

package com.comcast.money.aspectj

import java.util

import com.comcast.money.api.Span
import com.comcast.money.core.async.AsyncSpan
import com.comcast.money.core.internal.{ MDCSupport, SpanLocal }
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.{ Around, Aspect, Pointcut }
import org.slf4j.MDC

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

@Aspect
class FutureAspect {
  @Pointcut("adviceexecution() && within(FutureAspect)") def myAdvice(): Unit = {}

  @Pointcut("execution(* scala.concurrent.Future+.onComplete(scala.Function1, scala.concurrent.ExecutionContext)) && target(self) && args(f, executionContext)")
  def futureOnComplete[T, U](self: Future[T], f: Try[T] => U, executionContext: ExecutionContext): Unit = {}

  @Pointcut("execution(* scala.concurrent.Future+.foreach(scala.Function1, scala.concurrent.ExecutionContext)) && target(self) && args(f, executionContext)")
  def futureForEach[T, U](self: Future[T], f: T => U, executionContext: ExecutionContext): Unit = {}

  @Pointcut("execution(* scala.concurrent.Future+.onSuccess(scala.PartialFunction, scala.concurrent.ExecutionContext)) && target(self) && args(f, executionContext)")
  def futureOnSuccess[T, U](self: Future[T], f: PartialFunction[T, U], executionContext: ExecutionContext): Unit = {}

  @Pointcut("execution(* scala.concurrent.Future+.onFailure(scala.PartialFunction, scala.concurrent.ExecutionContext)) && target(self) && args(f, executionContext)")
  def futureOnFailure[T, U](self: Future[T], f: PartialFunction[Throwable, U], executionContext: ExecutionContext): Unit = {}

  @Around("futureOnComplete(self, f, executionContext) && !cflow(myAdvice())")
  def instrumentFutureOnComplete[T, U](joinpoint: ProceedingJoinPoint, self: Future[T], f: Try[T] => U, executionContext: ExecutionContext): AnyRef = {
    SpanLocal.current match {
      case Some(span: AsyncSpan) =>
        span.start()
        val replaced: Try[T] => Unit = {
          result =>
            Try { f(result) }.recover {
              case e => executionContext.reportFailure(e)
            }
            span.stop(result.isSuccess)
        }
        joinpoint.proceed(Array(replaced, wrapExecutionContext(span, executionContext)))
        null
      case _ =>
        joinpoint.proceed()
    }
  }

  @Around("futureForEach(self, f, executionContext) && !cflow(myAdvice())")
  def instrumentFutureForEach[T, U](joinpoint: ProceedingJoinPoint, self: Future[T], f: T => U, executionContext: ExecutionContext): AnyRef = {
    instrumentFutureTerminal[T, Unit](joinpoint, self, {
      case Success(v) => f(v)
      case Failure(_) =>
    }, executionContext)
  }

  @Around("futureOnSuccess(self, f, executionContext) && !cflow(myAdvice())")
  def instrumentFutureOnSuccess[T, U](joinpoint: ProceedingJoinPoint, self: Future[T], f: PartialFunction[T, U], executionContext: ExecutionContext): AnyRef = {
    instrumentFutureTerminal[T, Unit](joinpoint, self, {
      case Success(v) => f(v)
      case Failure(_) =>
    }, executionContext)
  }

  @Around("futureOnFailure(self, f, executionContext) && !cflow(myAdvice())")
  def instrumentFutureOnFailure[T, U](joinpoint: ProceedingJoinPoint, self: Future[T], f: PartialFunction[Throwable, U], executionContext: ExecutionContext): AnyRef = {
    instrumentFutureTerminal[T, Unit](joinpoint, self, {
      case Success(_) =>
      case Failure(e) => f(e)
    }, executionContext)
  }

  private def instrumentFutureTerminal[T, U](joinPoint: ProceedingJoinPoint, self: Future[T], f: Try[T] => U, executionContext: ExecutionContext): AnyRef = {
    SpanLocal.current match {
      case Some(span: AsyncSpan) =>
        span.start()
        self.onComplete {
          result =>
            Try { f(result) }.recover {
              case e => executionContext.reportFailure(e)
            }
            span.stop(result.isSuccess)
        }(wrapExecutionContext(span, executionContext))
        null
      case _ =>
        joinPoint.proceed()
    }
  }

  @Pointcut("execution(* scala.concurrent.Future$.apply(.., scala.concurrent.ExecutionContext)) && args(.., executionContext)")
  def futureApplyExecutionContext(executionContext: ExecutionContext): Unit = {}

  @Pointcut("execution(* scala.concurrent.Future+.*(.., scala.concurrent.ExecutionContext)) && args(.., executionContext)")
  def futureMethodExecutionContext(executionContext: ExecutionContext): Unit = {}

  @Around("futureApplyExecutionContext(executionContext) || futureMethodExecutionContext(executionContext)")
  def instrumentFutureExecutionContext(joinpoint: ProceedingJoinPoint, executionContext: ExecutionContext): AnyRef =
    joinpoint.proceed(replaceExecutionContext(executionContext, joinpoint.getArgs))

  def replaceExecutionContext(executionContext: ExecutionContext, args: Array[AnyRef]): Array[AnyRef] = {
    args.update(args.length - 1, wrapExecutionContext(executionContext))
    args
  }

  private lazy val mdcSupport = new MDCSupport()

  private def wrapExecutionContext(executionContext: ExecutionContext): ExecutionContext =
    SpanLocal.current.map {
      span => wrapExecutionContext(span, executionContext)
    }.getOrElse(executionContext)

  private def wrapExecutionContext(span: Span, executionContext: ExecutionContext): ExecutionContext =
    new SpanRestoringExecutionContext(executionContext, span, Option(MDC.getCopyOfContextMap))

  private class SpanRestoringExecutionContext(wrappee: ExecutionContext, span: Span, mdc: Option[util.Map[_, _]]) extends ExecutionContext {
    override def execute(runnable: Runnable): Unit =
      wrappee.execute(new Runnable {
        override def run(): Unit =
          withTracingContext { runnable.run() }
      })

    override def reportFailure(cause: Throwable): Unit =
      withTracingContext { wrappee.reportFailure(cause) }

    private def withTracingContext[T](f: => T): T = {
      val previousMdc = Option(MDC.getCopyOfContextMap)

      SpanLocal.push(span)
      mdcSupport.propogateMDC(mdc)
      try {
        f
      } finally {
        SpanLocal.pop()
        mdcSupport.propogateMDC(previousMdc)
      }
    }
  }
}
