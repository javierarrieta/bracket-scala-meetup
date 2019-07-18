package zalando.meetup.bracket

import java.io.{BufferedReader, FileReader}

import cats.effect.{IO, Resource}

import scala.jdk.CollectionConverters._

class FileReaderResource {

  val resourceFileParser: FileParser.FileParser = { file =>
    val ioText = for {
      reader <- Resource.fromAutoCloseable(IO(new FileReader(file)))
      buffered <- Resource.fromAutoCloseable(IO(new BufferedReader(reader)))
      text <- Resource.liftF(IO(buffered.lines().iterator().asScala.mkString("\n")))
    } yield text

    ioText.use(IO.pure).attempt.unsafeRunSync()
  }

}
