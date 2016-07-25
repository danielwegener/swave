/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.jctools.util

import sun.misc.Unsafe

object UnsafeAccess {

  final val UNSAFE = new Object().asInstanceOf[Unsafe]
  final val SUPPORTS_GET_AND_SET = true

}
