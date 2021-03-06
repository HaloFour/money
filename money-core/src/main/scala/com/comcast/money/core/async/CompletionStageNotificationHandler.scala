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

package com.comcast.money.core.async

import java.util.concurrent.CompletionStage

import scala.util.{ Failure, Success, Try }

class CompletionStageNotificationHandler extends AbstractAsyncNotificationHandler[CompletionStage[_]] {
  /**
   * Implemented by derived type to register the callback function to be invoked when the future
   * has completed
   *
   * @param future the future instance for which the callback is to be registered
   * @param f      the callback function
   * @return the future instance with the completion callback registered
   */
  override def whenComplete(future: CompletionStage[_], f: Try[_] => Unit): CompletionStage[_] =
    future.whenComplete((result, exception) =>
      f(
        Option(exception)
          .map(Failure(_)).
          getOrElse(Success(result))))
}
