/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package swave.core.impl

import java.util.concurrent.{ ConcurrentHashMap, TimeoutException }
import scala.annotation.tailrec
import scala.util.Try
import scala.concurrent.{ Promise, Future }
import scala.concurrent.duration._
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory
import swave.core.macros._
import swave.core._

private[core] final class StreamEnvImpl(
    val name: String,
    val config: Config,
    val settings: StreamEnv.Settings,
    val classLoader: ClassLoader) extends StreamEnv {

  val startTime = System.currentTimeMillis()

  val log = Logger(LoggerFactory.getLogger(name))

  val dispatchers = DispatchersImpl(settings.dispatcherSettings)

  val scheduler = SchedulerImpl(settings.schedulerSettings)

  if (settings.logConfigOnStart) log.info(settings.toString) // TODO: improve rendering

  def defaultDispatcher = dispatchers.defaultDispatcher

  def shutdown(): StreamEnv.Termination =
    new StreamEnv.Termination {
      val schedulerTermination = scheduler.shutdown()
      val dispatchersTermination = dispatchers.shutdownAll()

      def isTerminated: Boolean = schedulerTermination.isCompleted && unterminatedDispatchers.isEmpty

      def unterminatedDispatchers: List[String] = dispatchersTermination()

      def awaitTermination(timeout: FiniteDuration): Unit = {
        requireArg(timeout >= Duration.Zero)
        var deadline = System.nanoTime() + timeout.toNanos
        if (deadline < 0) deadline = Long.MaxValue // overflow protection

        @tailrec def await(): Unit =
          if (!isTerminated) {
            if (System.nanoTime() < deadline) {
              Thread.sleep(1L)
              await()
            } else {
              val unterminated =
                if (schedulerTermination.isCompleted) unterminatedDispatchers
                else "scheduler" :: unterminatedDispatchers
              throw new TimeoutException(s"StreamEnv did not shut down within specified timeout of $timeout.\n" +
                s"Unterminated dispatchers: [${unterminated.mkString(", ")}]")
            }
          }

        await()
      }
    }

  private[this] val _extensions = new ConcurrentHashMap[ExtensionId[_], Future[_ <: Extension]]

  @tailrec def getOrLoadExtension[T <: Extension](id: ExtensionId[T]): Future[T] =
    _extensions.get(id) match {
      case null ⇒
        val promise = Promise[T]()
        _extensions.putIfAbsent(id, promise.future) match {
          case null ⇒
            val tryValue = Try(id.createExtension(this))
            promise.complete(tryValue)
            val future = Promise.fromTry(tryValue).future
            _extensions.put(id, future) // speed up future accesses somewhat
            future
          case _ ⇒ getOrLoadExtension(id)
        }
      case x ⇒ x.asInstanceOf[Future[T]]
    }
}
