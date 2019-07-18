package zalando.meetup.bracket

import java.io.{BufferedReader, File, FileInputStream, FileReader}

import cats.effect.{IO, Resource}

import scala.io.Source
import scala.jdk.CollectionConverters._
import scala.util.Try
import scala.util.control.NonFatal

object FileParser {

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

  val withSource: File => Either[Throwable, String] = {file =>
    Try(Source.fromInputStream(new FileInputStream(file)).getLines().mkString("\n")).toEither
  }

  val resourceFileParser: FileParser.FileParser = { file =>
    val ioText = for {
      reader <- Resource.fromAutoCloseable(IO(new FileReader(file)))
      buffered <- Resource.fromAutoCloseable(IO(new BufferedReader(reader)))
      text <- Resource.liftF(IO(buffered.lines().iterator().asScala.mkString("\n")))
    } yield text

    ioText.use(IO.pure).attempt.unsafeRunSync()
  }
}
