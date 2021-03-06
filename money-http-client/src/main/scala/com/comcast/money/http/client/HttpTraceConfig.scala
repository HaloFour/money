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

package com.comcast.money.http.client

object HttpTraceConfig {

  lazy val HttpResponseTimeTraceKey: String = "http-call-duration"
  lazy val HttpFullResponseTimeTraceKey: String = "http-call-with-body-duration"
  lazy val ProcessResponseTimeTraceKey: String = "http-process-response-duration"
  lazy val HttpResponseCodeTraceKey: String = "http-response-code"
}
