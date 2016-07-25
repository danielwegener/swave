/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package swave.core.internal.agrona

import java.util

/**
 * Timer Wheel (NOT thread safe)
 * <p>
 * Assumes single-writer principle and timers firing on processing thread.
 * Low (or NO) garbage.
 * <h3>Implementation Details</h3>
 * <p>
 * Based on netty's HashedTimerWheel, which is based on
 * <a href="http://cseweb.ucsd.edu/users/varghese/">George Varghese</a> and
 * Tony Lauck's paper,
 * <a href="http://cseweb.ucsd.edu/users/varghese/PAPERS/twheel.ps.Z">'Hashed
 * and Hierarchical Timing Wheels: data structures to efficiently implement a
 * timer facility'</a>.  More comprehensive slides are located
 * <a href="http://www.cse.wustl.edu/~cdgill/courses/cs6874/TimingWheels.ppt">here</a>.
 * <p>
 * Wheel is backed by arrays. Timer cancellation is O(1). Timer scheduling might be slightly
 * longer if a lot of timers are in the same tick. The underlying tick contains an array. That
 * array grows when needed, but does not currently shrink.
 * <p>
 * Timer objects may be reused if desired, but all reuse must be done with timer cancellation, expiration,
 * and timeouts in consideration.
 * <p>
 * Caveats
 * <p>
 * Timers that expire in the same tick will not be ordered with one another. As ticks are
 * fairly large normally, this means that some timers may expire out of order.
 */
object TimerWheel {
  val INITIAL_TICK_DEPTH: Int = 16

  private def checkTicksPerWheel(ticksPerWheel: Int): Unit = {
    if (ticksPerWheel < 2 || 1 != Integer.bitCount(ticksPerWheel)) {
      val msg: String = "ticksPerWheel must be a positive power of 2: ticksPerWheel=" + ticksPerWheel
      throw new IllegalArgumentException(msg)
    }
  }

  object TimerState extends Enumeration {
    type TimerState = Value
    val ACTIVE, CANCELLED, EXPIRED = Value
  }

}

/**
 * @constructor Construct a timer wheel for use in scheduling timers.
 * @param tickDurationInNs of each tick of the wheel
 * @param ticksPerWheel    of the wheel. Must be a power of 2.
 */
final class TimerWheel(val tickDurationInNs: Long, val ticksPerWheel: Int) {

  final private var mask: Long = 0L
  final private var startTime: Long = 0L
  final private var wheel: Array[Array[Timer]] = _
  private var currentTick: Long = 0L

  TimerWheel.checkTicksPerWheel(ticksPerWheel)
  this.mask = ticksPerWheel.toLong - 1L
  this.startTime = System.nanoTime
  if (tickDurationInNs >= (Long.MaxValue / ticksPerWheel))
    throw new IllegalArgumentException(s"tickDurationNanos: $tickDurationInNs (expected: 0 < tickDurationInNs < ${Long.MaxValue / ticksPerWheel.toLong}")
  wheel = new Array[Array[Timer]](ticksPerWheel)

  var i: Int = 0
  while (i < ticksPerWheel) {
    wheel(i) = new Array[Timer](TimerWheel.INITIAL_TICK_DEPTH)
    i += 1
    i - 1

  }

  /**
   * Return the current time as number of nanoseconds since start of the wheel.
   *
   * @return number of nanoseconds since start of the wheel
   */
  def ticks(): Long = System.nanoTime - startTime

  /**
   * Return a blank {@link Timer} suitable for rescheduling.
   * <p>
   * NOTE: Appears to be a cancelled timer
   *
   * @return new blank timer
   */
  def newBlankTimer(): Timer = new Timer()

  /**
   * Schedule a new timer that runs {@code task} when it expires.
   *
   * @param task to execute when timer expires
   * @return { @link Timer} for timer
   */
  def newTimeout(deadline: Long, task: Runnable): Timer = {
    val timeout: Timer = new Timer(deadline, task)
    wheel(timeout.wheelIndex) = addTimeoutToArray(wheel(timeout.wheelIndex), timeout)
    timeout
  }

  /**
   * Reschedule an expired timer, reusing the {@link Timer} object.
   *
   * @param delayNanos until timer should expire
   * @param timer      to reschedule
   * @throws IllegalArgumentException if timer is active
   */
  def rescheduleTimeout(delayNanos: Long, timer: Timer): Unit = {
    rescheduleTimeout(delayNanos, timer, timer.task)
  }

  /**
   * Reschedule an expired timer, reusing the {@link Timer} object.
   *
   * @param delayNanos until timer should expire
   * @param timer      to reschedule
   * @param task       to execute when timer expires
   * @throws IllegalArgumentException if timer is active
   */
  def rescheduleTimeout(delayNanos: Long, timer: Timer, task: Runnable): Unit = {
    rescheduleTimeoutDeadline(ticks + delayNanos, timer, task)
  }

  /**
   * Reschedule an expired timer, reusing the {@link Timer} object.
   *
   * @param timer to reschedule
   * @param task  to execute when timer expires
   * @throws IllegalArgumentException if timer is active
   */
  def rescheduleTimeoutDeadline(deadline: Long, timer: Timer, task: Runnable): Unit = {
    if (timer.isActive) throw new IllegalArgumentException("timer is active")
    timer.reset(deadline, task)
    wheel(timer.wheelIndex) = addTimeoutToArray(wheel(timer.wheelIndex), timer)
  }

  /**
   * Compute delay in milliseconds until next tick.
   *
   * @return number of milliseconds to next tick of the wheel.
   */
  def computeDelayInMs(): Long = {
    val deadline: Long = tickDurationInNs * (currentTick + 1)
    ((deadline - ticks) + 999999) / 1000000
  }

  /**
   * Process timers and execute any expired timers.
   *
   * @return number of timers expired.
   */
  def expireTimers(): Int = {
    var timersExpired: Int = 0
    val now: Long = ticks
    for (timer ← wheel((currentTick & mask).toInt)) {
      if (null != timer) {
        if (0 >= timer.remainingRounds) {
          timer.remove()
          timer.state = TimerWheel.TimerState.EXPIRED
          if (now >= timer.deadline) {
            timersExpired += 1
            timer.task.run()
          }
        } else timer.remainingRounds -= 1
      }
    }
    currentTick += 1
    timersExpired
  }

  private def addTimeoutToArray(oldArray: Array[Timer], timeout: Timer): Array[Timer] = {
    var i: Int = 0
    while (i < oldArray.length) {
      if (null == oldArray(i)) {
        oldArray(i) = timeout
        timeout.tickIndex = i
        return oldArray
      }
      i += 1
      i - 1
    }
    val newArray: Array[Timer] = util.Arrays.copyOf(oldArray, oldArray.length + 1)
    newArray(oldArray.length) = timeout
    timeout.tickIndex = oldArray.length
    newArray
  }

  final class Timer {
    var wheelIndex: Int = 0
    var deadline: Long = 0L
    var task: Runnable = _
    var tickIndex: Int = 0
    var remainingRounds: Long = 0L
    var state: TimerWheel.TimerState.Value = TimerWheel.TimerState.CANCELLED

    def this(deadline: Long, task: Runnable) {
      this()
      reset(deadline, task)
    }

    def reset(deadline: Long, task: Runnable): Unit = {
      this.deadline = deadline
      this.task = task
      val calculatedIndex: Long = deadline / TimerWheel.this.tickDurationInNs
      val ticks: Long = Math.max(calculatedIndex, TimerWheel.this.currentTick)
      this.wheelIndex = (ticks & TimerWheel.this.mask).toInt
      this.remainingRounds = (calculatedIndex - TimerWheel.this.currentTick) / TimerWheel.this.wheel.length
      this.state = TimerWheel.TimerState.ACTIVE
    }

    /**
     * Cancel pending timer. Idempotent.
     */
    def cancel(): Unit = {
      if (isActive) {
        remove()
        state = TimerWheel.TimerState.CANCELLED
      }
    }

    /**
     * Is timer active or not
     *
     * @return boolean indicating if timer is active or not
     */
    def isActive: Boolean = TimerWheel.TimerState.ACTIVE eq state

    /**
     * Was timer cancelled or not
     *
     * @return boolean indicating if timer was cancelled or not
     */
    def isCancelled: Boolean = TimerWheel.TimerState.CANCELLED eq state

    /**
     * Has timer expired or not
     *
     * @return boolean indicating if timer has expired or not
     */
    def isExpired: Boolean = TimerWheel.TimerState.EXPIRED eq state

    def remove(): Unit = {
      TimerWheel.this.wheel(this.wheelIndex)(this.tickIndex) = null
    }

    override def toString: String = "Timer{" + "wheelIndex=\'" + wheelIndex + "\'" + ", tickIndex=\'" + tickIndex + "\'" + ", deadline=\'" + deadline + "\'" + ", remainingRounds=\'" + remainingRounds + "\'" + "}"
  }

}
