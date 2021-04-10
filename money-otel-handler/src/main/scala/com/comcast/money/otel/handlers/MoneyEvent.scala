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

package com.comcast.money.otel.handlers

import com.comcast.money.api.{ SpanEvent, SpanInfo }
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.trace.data.EventData

private[otel] case class MoneyEvent(event: SpanEvent) extends EventData {
  override def getName: String = event.name
  override def getAttributes: Attributes = event.attributes
  override def getEpochNanos: Long = event.timestamp
  override def getTotalAttributeCount: Int = event.attributes.size
}
