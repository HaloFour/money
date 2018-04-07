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
import com.comcast.money.core.internal.{ MDCSupport, SpanLocal }
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.{ Around, Aspect, Pointcut }
import org.slf4j.MDC

import scala.concurrent.ExecutionContext

@Aspect
class FutureAspect {
  @Pointcut("call(* scala.concurrent.Future$.apply(.., scala.concurrent.ExecutionContext)) && args(.., executionContext)")
  def futureApplyExecutionContext(executionContext: ExecutionContext): Unit = {}

  @Pointcut("call(* scala.concurrent.Future+.*(.., scala.concurrent.ExecutionContext)) && args(.., executionContext)")
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
      span =>
        new SpanRestoringExecutionContext(executionContext, span, Option(MDC.getCopyOfContextMap))
    }.getOrElse(executionContext)

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
