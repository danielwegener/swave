/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.reactivestreams

/**
 * A [[Subscription]] represents a one-to-one lifecycle of a [[Subscriber]] subscribing to a [[Publisher]].
 * <p>
 * It can only be used once by a single [[Subscriber]].
 * <p>
 * It is used to both signal desire for data and cancel demand (and allow resource cleanup).
 *
 */
trait Subscription {
  /**
   * No events will be sent by a [[Publisher]] until demand is signaled via this method.
   * <p>
   * It can be called however often and whenever needed—but the outstanding cumulative demand must never exceed Long.MAX_VALUE.
   * An outstanding cumulative demand of Long.MAX_VALUE may be treated by the [[Publisher]] as "effectively unbounded".
   * <p>
   * Whatever has been requested can be sent by the [[Publisher]] so only signal demand for what can be safely handled.
   * <p>
   * A [[Publisher]] can send less than is requested if the stream ends but
   * then must emit either [[Subscriber#onError(Throwable)]] or [[Subscriber#onComplete()]].
   *
   * @param n the strictly positive number of elements to requests to the upstream { @link Publisher}
   */
  def request(n: Long): Unit

  /**
   * Request the [[Publisher]] to stop sending data and clean up resources.
   * <p>
   * Data may still be sent to meet previously signalled demand after calling cancel as this request is asynchronous.
   */
  def cancel(): Unit
}
