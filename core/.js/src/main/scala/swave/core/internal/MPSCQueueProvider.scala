/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package swave.core.internal

import swave.core.MPSCQueue

import scala.scalajs.js

object MPSCQueueProvider {

  def apply[E]: MPSCQueue[E] = new MPSCQueue[E] {
    val delegate = new js.Array[E]()
    override def add(e: E): Boolean = {
      delegate.push(e)
      true
    }
    override def poll(): E = if (delegate.length != 0) delegate.pop() else null.asInstanceOf[E]
    override def isEmpty: Boolean = delegate.isEmpty
    override def offer(e: E): Boolean = add(e)
  }
}
