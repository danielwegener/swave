/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package swave.core.io.files.impl

import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.{ StandardOpenOption, Path }
import scala.collection.JavaConverters._
import scala.concurrent.Promise
import com.typesafe.scalalogging.Logger
import swave.core.PipeElem
import swave.core.impl.Inport
import swave.core.impl.stages.drain.DrainStage
import swave.core.io.Bytes
import swave.core.macros.StageImpl

// format: OFF
@StageImpl
private[core] final class FileDrainStage[T](path: Path, options: Set[StandardOpenOption], _chunkSize: Int,
                                            resultPromise: Promise[Long])(implicit bytes: Bytes[T])
  extends DrainStage with PipeElem.Drain.File {

  def pipeElemType: String = "Drain.file"
  def pipeElemParams: List[Any] = path :: options :: _chunkSize :: Nil

  private[this] val log = Logger(getClass)
  private implicit def decorator(value: T): Bytes.Decorator[T] = Bytes.decorator(value)

  require(options contains StandardOpenOption.WRITE, "`options` must contain `StandardOpenOption.WRITE`")

  connectInAndSealWith { (ctx, in) ⇒
    registerForRunnerAssignmentIfRequired(ctx)
    ctx.registerForXStart(this)
    val cSize = if (_chunkSize > 0) _chunkSize else ctx.env.settings.fileIOSettings.defaultFileWritingChunkSize
    running(in, cSize)
  }

  def running(in: Inport, chunkSize: Int) = {

    def awaitingXStart() = state(
      xStart = () => {
        in.request(Long.MaxValue)
        try writing(FileChannel.open(path, options.asJava), bytes.empty, 0L)
        catch {
          case e: IOException =>
            log.debug("Couldn't open `{}` for writing: {}", path, e.toString)
            stopCancel(in)
        }
      })

    def writing(channel: FileChannel, currentChunk: T, totalBytesWritten: Long): State = state(
      onNext = (elem, _) ⇒ {
        try {
          val chunk = currentChunk ++ elem.asInstanceOf[T]
          if (chunk.size >= chunkSize) {
            channel.write(chunk.toByteBuffer)
            writing(channel, bytes.empty, totalBytesWritten + chunk.size)
          } else writing(channel, chunk, totalBytesWritten)
        } catch {
          case e: IOException =>
            log.debug("Error writing to `{}`: {}", path, e)
            swave.core.io.files.quietClose(channel)
            in.cancel()
            stop(e)
        }
      },

      onComplete = _ ⇒ {
        close(channel)
        resultPromise.success(totalBytesWritten)
        stop()
      },

      onError = (e, _) ⇒ {
        close(channel)
        resultPromise.failure(e)
        stop(e)
      })

    awaitingXStart()
  }

  private def close(channel: FileChannel): Unit =
    try channel.close()
    catch {
      case e: IOException => log.debug("Error closing `{}`: {}", path, e)
    }
}
