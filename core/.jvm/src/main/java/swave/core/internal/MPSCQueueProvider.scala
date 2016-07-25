/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package swave.core.internal

import org.jctools.queues.MpscLinkedQueue8
import swave.core.MPSCQueue

object MPSCQueueProvider {

  def apply[E]: MPSCQueue[E] = new MPSCQueue[E] {
    val delegate = new MpscLinkedQueue8[E]
    override def add(e: E): Boolean = delegate.add(e)
    override def poll(): E = delegate.poll()
    override def isEmpty: Boolean = delegate.isEmpty
    override def offer(e: E): Boolean = delegate.offer(e)
  }
}
