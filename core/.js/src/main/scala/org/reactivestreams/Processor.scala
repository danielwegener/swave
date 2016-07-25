/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.reactivestreams

/**
 * A Processor represents a processing stage—which is both a {@link Subscriber}
 * and a [[Publisher]] and obeys the contracts of both.
 *
 * @tparam T the type of element signaled to the { @link Subscriber}
 * @tparam R the type of element signaled by the { @link Publisher}
 */
trait Processor[T, R] extends Subscriber[T] with Publisher[R] {}
