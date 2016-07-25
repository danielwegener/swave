/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.reactivestreams

/**
 * Will receive call to [[Subscriber#onSubscribe(Subscription)]] once after passing an instance of [[Subscriber]] to [[Publisher#subscribe(Subscriber)]].
 * <p>
 * No further notifications will be received until [[Subscription#request(long)}]] is called.
 * <p>
 * After signaling demand:
 * <ul>
 * <li>One or more invocations of {@link #onNext(Object)} up to the maximum number defined by {@link Subscription#request(long)}</li>
 * <li>Single invocation of {@link #onError(Throwable)} or {@link Subscriber#onComplete()} which signals a terminal state after which no further events will be sent.
 * </ul>
 * <p>
 * Demand can be signaled via {@link Subscription#request(long)} whenever the {@link Subscriber} instance is capable of handling more.
 *
 * @tparam T the type of element signaled.
 */
trait Subscriber[T] {
  /**
   * Invoked after calling {@link Publisher#subscribe(Subscriber)}.
   * <p>
   * No data will start flowing until {@link Subscription#request(long)} is invoked.
   * <p>
   * It is the responsibility of this {@link Subscriber} instance to call {@link Subscription#request(long)} whenever more data is wanted.
   * <p>
   * The {@link Publisher} will send notifications only in response to {@link Subscription#request(long)}.
   *
   * @param s
   * { @link Subscription} that allows requesting data via { @link Subscription#request(long)}
   */
  def onSubscribe(s: Subscription): Unit

  /**
   * Data notification sent by the [[Publisher]] in response to requests to {@link Subscription#request(long)}.
   *
   * @param t the element signaled
   */
  def onNext(t: T): Unit

  /**
   * Failed terminal state.
   * <p>
   * No further events will be sent even if {@link Subscription#request(long)} is invoked again.
   *
   * @param t the throwable signaled
   */
  def onError(t: Throwable): Unit

  /**
   * Successful terminal state.
   * <p>
   * No further events will be sent even if {@link Subscription#request(long)} is invoked again.
   */
  def onComplete(): Unit
}
