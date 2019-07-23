package zalando.meetup.bracket

import cats.effect.Resource
import cats.syntax.either._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import pureconfig.error.ConfigReaderException
import zio.console.putStrLn
import zio.interop.catz._
import zio.{Task, ZIO}
import javax.sql.DataSource

object DatabaseReadsApp extends CatsApp {

  import DatabaseOperations._
  import HttpOperations.httpServer
  import zio.interop.catz.implicits._
  import pureconfig.generic.auto._

  private val appLifecycleLogger: Resource[Task, Unit] =
    Resource.make[Task, Unit](Task(println("Starting application")))(_ => Task(println("Resources released, shutting down app")))

  override def run(args: List[String]): ZIO[DatabaseReadsApp.Environment, Nothing, Int] = {

    val readDbConfig = Task.fromTry(pureconfig.loadConfig[DatabaseConfig].leftMap(ConfigReaderException.apply).toTry)
    val httpServerRun = (ds: DataSource) => 
      BlazeServerBuilder[Task]
        .bindHttp(8080, "localhost")
        .withHttpApp(Router("/" -> httpServer(ds, findCustomer[Task])).orNotFound)

    val program: Resource[Task, Unit] = for {
      _ <- appLifecycleLogger
      dbConfig <- Resource.liftF(readDbConfig)
      ds <- createDataSource[Task](dbConfig)
      _ <- httpServerRun(ds).resource
    } yield ()
    program.use(_ => Task.never).catchAll(e => putStrLn(s"Error: ${e.getMessage}") *> Task.succeed(1))
  }
}
