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

import com.comcast.money.annotations.{ Timed, Traced }
import com.comcast.money.core.internal.{ MDCSupport, SpanLocal }
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.{ Around, Aspect, Pointcut }
import org.slf4j.MDC

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

@Aspect
class FutureAspect {
  @Pointcut("execution(@com.comcast.money.annotations.Traced * *(..)) && @annotation(traceAnnotation)")
  def traced(traceAnnotation: Traced): Unit = {}

  @Pointcut("execution(@com.comcast.money.annotations.Timed * *(..)) && @annotation(timedAnnotation)")
  def timed(timedAnnotation: Timed): Unit = {}

  /*
  @Around("call(scala.concurrent.Future *(..)) && cflow(traced(traceAnnotation))")
  def instrumentFuture(joinPoint: ProceedingJoinPoint, traceAnnotation: Traced): AnyRef = {
    if (traceAnnotation.async()) {
      joinPoint.proceed() match {
        case future: Future[_] =>
          future
        case other => other
      }
    } else {
      joinPoint.proceed()
    }
  }
  */

  @Pointcut("call(* scala.concurrent.Future$.apply(..)) && args(body, executor)")
  def futureApply[T](body: () => T, executor: ExecutionContext): Unit = {}

  @Pointcut("call(* scala.concurrent.Future.andThen(..)) && args(pf, executor)")
  def futureAndThen[T, U](pf: PartialFunction[Try[T], U], executor: ExecutionContext): Unit = {}

  @Pointcut("call(* scala.concurrent.Future.collect(..)) && args(pf, executor)")
  def futureCollect[T, S](pf: PartialFunction[T, S], executor: ExecutionContext): Unit = {}

  @Pointcut("call(* scala.concurrent.Future.filter(..)) && args(pred, executor)")
  def futureFilter[T](pred: T => Boolean, executor: ExecutionContext): Unit = {}

  @Pointcut("call(* scala.concurrent.Future.flatMap(..)) && args(f, executor)")
  def futureFlatMap[T, U](f: T => Future[U], executor: ExecutionContext): Unit = {}

  @Pointcut("call(* scala.concurrent.Future.foreach(..)) && args(f, executor)")
  def futureForEach[T, U](f: T => U, executor: ExecutionContext): Unit = {}

  @Pointcut("call(* scala.concurrent.Future.map(..)) && args(f, executor)")
  def futureMap[T, U](f: T => U, executor: ExecutionContext): Unit = {}

  @Pointcut("call(* scala.concurrent.Future.onComplete(..)) && args(f, executor)")
  def futureOnComplete[T, U](f: Try[T] => U, executor: ExecutionContext): Unit = {}

  @Pointcut("call(* scala.concurrent.Future.onFailure(..)) && args(pf, executor)")
  def futureOnFailure[T, U](pf: PartialFunction[Throwable, U], executor: ExecutionContext): Unit = {}

  @Pointcut("call(* scala.concurrent.Future.onSuccess(..)) && args(pf, executor)")
  def futureOnSuccess[T, U](pf: PartialFunction[T, U], executor: ExecutionContext): Unit = {}

  @Pointcut("call(* scala.concurrent.Future.recover(..)) && args(pf, executor)")
  def futureRecover[T, U](pf: PartialFunction[Throwable, U], executor: ExecutionContext): Unit = {}

  @Pointcut("call(* scala.concurrent.Future.recoverWith(..)) && args(pf, executor)")
  def futureRecoverWith[T, U](pf: PartialFunction[Throwable, Future[U]], executor: ExecutionContext): Unit = {}

  @Pointcut("call(* scala.concurrent.Future.transform(..)) && args(s, f, executor)")
  def futureTransform[T, S](s: T => S, f: Throwable => Throwable, executor: ExecutionContext): Unit = {}

  @Pointcut("call(* scala.concurrent.Future.withFilter(..)) && args(p, executor)")
  def futureWithFilter[T](p: T => Boolean, executor: ExecutionContext): Unit = {}

  @Around("futureApply(body, executor) && cflow(traced(traceAnnotation))")
  def instrumentFutureApply[T](joinpoint: ProceedingJoinPoint, body: () => T, executor: ExecutionContext, traceAnnotation: Traced): AnyRef =
    instrumentAsync(joinpoint, traceAnnotation) { Array(body, wrapExecutionContext(executor)) }

  @Around("futureAndThen(pf, executor) && cflow(traced(traceAnnotation))")
  def instrumentFutureAndThen[T, U](joinpoint: ProceedingJoinPoint, pf: PartialFunction[Try[T], U], executor: ExecutionContext, traceAnnotation: Traced): AnyRef =
    instrumentAsync(joinpoint, traceAnnotation) { Array(pf, wrapExecutionContext(executor)) }

  @Around("futureCollect(pf, executor) && cflow(traced(traceAnnotation))")
  def instrumentFutureCollect[T, S](joinpoint: ProceedingJoinPoint, pf: PartialFunction[T, S], executor: ExecutionContext, traceAnnotation: Traced): AnyRef =
    instrumentAsync(joinpoint, traceAnnotation) { Array(pf, wrapExecutionContext(executor)) }

  @Around("futureFilter(pred, executor) && cflow(traced(traceAnnotation))")
  def instrumentFutureFilter[T](joinpoint: ProceedingJoinPoint, pred: T => Boolean, executor: ExecutionContext, traceAnnotation: Traced): AnyRef =
    instrumentAsync(joinpoint, traceAnnotation) { Array(pred, wrapExecutionContext(executor)) }

  @Around("futureFlatMap(f, executor) && cflow(traced(traceAnnotation))")
  def instrumentFutureFlatMap[T, U](joinpoint: ProceedingJoinPoint, f: T => Future[U], executor: ExecutionContext, traceAnnotation: Traced): AnyRef =
    instrumentAsync(joinpoint, traceAnnotation) { Array(f, wrapExecutionContext(executor)) }

  @Around("futureForEach(f, executor) && cflow(traced(traceAnnotation))")
  def instrumentFutureForEach[T, U](joinpoint: ProceedingJoinPoint, f: T => U, executor: ExecutionContext, traceAnnotation: Traced): AnyRef =
    instrumentAsync(joinpoint, traceAnnotation) { Array(f, wrapExecutionContext(executor)) }

  @Around("futureMap(f, executor) && cflow(traced(traceAnnotation))")
  def instrumentFutureMap[T, U](joinpoint: ProceedingJoinPoint, f: T => U, executor: ExecutionContext, traceAnnotation: Traced): AnyRef =
    instrumentAsync(joinpoint, traceAnnotation) { Array(f, wrapExecutionContext(executor)) }

  @Around("futureOnComplete(f, executor) && cflow(traced(traceAnnotation))")
  def instrumentFutureOnComplete[T, U](joinpoint: ProceedingJoinPoint, f: Try[T] => U, executor: ExecutionContext, traceAnnotation: Traced): AnyRef =
    instrumentAsync(joinpoint, traceAnnotation) { Array(f, wrapExecutionContext(executor)) }

  @Around("futureOnFailure(pf, executor) && cflow(traced(traceAnnotation))")
  def instrumentFutureOnFailure[T, U](joinpoint: ProceedingJoinPoint, pf: PartialFunction[Throwable, U], executor: ExecutionContext, traceAnnotation: Traced): AnyRef =
    instrumentAsync(joinpoint, traceAnnotation) { Array(pf, wrapExecutionContext(executor)) }

  @Around("futureOnSuccess(pf, executor) && cflow(traced(traceAnnotation))")
  def instrumentFutureOnSuccess[T, U](joinpoint: ProceedingJoinPoint, pf: PartialFunction[T, U], executor: ExecutionContext, traceAnnotation: Traced): AnyRef =
    instrumentAsync(joinpoint, traceAnnotation) { Array(pf, wrapExecutionContext(executor)) }

  @Around("futureRecover(pf, executor) && cflow(traced(traceAnnotation))")
  def instrumentFutureRecover[T, U](joinpoint: ProceedingJoinPoint, pf: PartialFunction[Throwable, U], executor: ExecutionContext, traceAnnotation: Traced): AnyRef =
    instrumentAsync(joinpoint, traceAnnotation) { Array(pf, wrapExecutionContext(executor)) }

  @Around("futureRecoverWith(pf, executor) && cflow(traced(traceAnnotation))")
  def instrumentFutureRecoverWith[T, U](joinpoint: ProceedingJoinPoint, pf: PartialFunction[Throwable, Future[U]], executor: ExecutionContext, traceAnnotation: Traced): AnyRef =
    instrumentAsync(joinpoint, traceAnnotation) { Array(pf, wrapExecutionContext(executor)) }

  @Around("futureTransform(s, f, executor) && cflow(traced(traceAnnotation))")
  def instrumentFutureTransform[T, S](joinpoint: ProceedingJoinPoint, s: T => S, f: Throwable => Throwable, executor: ExecutionContext, traceAnnotation: Traced): AnyRef =
    instrumentAsync(joinpoint, traceAnnotation) { Array(s, f, wrapExecutionContext(executor)) }

  @Around("futureWithFilter(p, executor) && cflow(traced(traceAnnotation))")
  def instrumentFutureWithFilter[T](joinpoint: ProceedingJoinPoint, p: T => Boolean, executor: ExecutionContext, traceAnnotation: Traced): AnyRef =
    instrumentAsync(joinpoint, traceAnnotation) { Array(p, wrapExecutionContext(executor)) }

  private def instrumentAsync(joinpoint: ProceedingJoinPoint, traceAnnotation: Traced)(f: => Array[AnyRef]) = {
    if (traceAnnotation.async()) {
      joinpoint.proceed(f)
    } else {
      joinpoint.proceed()
    }
  }

  private lazy val mdcSupport = new MDCSupport()

  private def wrapExecutionContext(executionContext: ExecutionContext): ExecutionContext =
    SpanLocal.current.map {
      span =>
        val mdc = Option(MDC.getCopyOfContextMap)
        new ExecutionContext {
          override def execute(runnable: Runnable): Unit = {
            val previous = Option(MDC.getCopyOfContextMap)
            try {
              SpanLocal.push(span)
              mdcSupport.propogateMDC(mdc)
              runnable.run()
            } finally {
              SpanLocal.pop()
              mdcSupport.propogateMDC(previous)
            }
          }

          override def reportFailure(cause: Throwable): Unit = executionContext.reportFailure(cause)
        }
    }.getOrElse(executionContext)
}
