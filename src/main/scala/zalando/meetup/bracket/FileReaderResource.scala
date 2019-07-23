package zalando.meetup.bracket

import java.io.{BufferedReader, FileReader}

import cats.effect.{IO, Resource}

import scala.jdk.CollectionConverters._

class FileReaderResource {

  val resourceFileParser: FileParser.FileParser = { file =>
    val ioHandle = for {
      reader <- Resource.fromAutoCloseable(IO(new FileReader(file)))
      buffered <- Resource.fromAutoCloseable(IO(new BufferedReader(reader)))
    } yield buffered

    ioHandle.use(handle => 
      IO(handle.lines().iterator().asScala.mkString("\n"))
    ).attempt.unsafeRunSync()
  }

}
