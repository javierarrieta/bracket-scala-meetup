package zalando.meetup.bracket

import java.io.{BufferedReader, File, FileReader}

import scala.jdk.CollectionConverters._
import scala.util.Try
import scala.util.control.NonFatal

object alternatives {

  type FileParser = File => Either[Throwable, String]

  def javaWay(file: File): Either[Throwable, String] = {
    try {
      val reader = new FileReader(file)
      try {
        val buffered = new BufferedReader(reader)
        Right(buffered.lines().iterator().asScala.mkString("\n"))
      } finally {
        reader.close()
      }
    } catch {
      case NonFatal(e) => Left(e)
    }
  }

  val scalaWay: File => Either[Throwable, String] = { file =>

    val tryText = for {
      reader <- Try(new FileReader(file))
      buffered <- Try(new BufferedReader(reader))
      text <- Try(buffered.lines().iterator().asScala.mkString("\n"))
    } yield text

    tryText.toEither
  }

}
