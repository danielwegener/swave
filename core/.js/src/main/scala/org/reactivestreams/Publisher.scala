/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.reactivestreams

/**
 * A [[Publisher]] is a provider of a potentially unbounded number of sequenced elements, publishing them according to
 * the demand received from its [[Subscriber]](s).
 * <p>
 * A {@link Publisher} can serve multiple {@link Subscriber}s subscribed {@link #subscribe(Subscriber)} dynamically
 * at various points in time.
 *
 * @tparam T the type of element signaled.
 */
trait Publisher[T] {
  /**
   * Request {@link Publisher} to start streaming data.
   * <p>
   * This is a "factory method" and can be called multiple times, each time starting a new {@link Subscription}.
   * <p>
   * Each {@link Subscription} will work for only a single {@link Subscriber}.
   * <p>
   * A {@link Subscriber} should only subscribe once to a single {@link Publisher}.
   * <p>
   * If the {@link Publisher} rejects the subscription attempt or otherwise fails it will
   * signal the error via {@link Subscriber#onError}.
   *
   * @param s the { @link Subscriber} that will consume signals from this { @link Publisher}
   */
  def subscribe(s: Subscriber[_ >: T]): Unit
}
