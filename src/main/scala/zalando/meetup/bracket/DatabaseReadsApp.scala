package zalando.meetup.bracket

import java.sql.{Connection, PreparedStatement, ResultSet}

import cats.effect.{Resource, Sync}
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import javax.sql.DataSource
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import pureconfig.error.ConfigReaderException
import zio.console.putStrLn
import zio.interop.catz._
import zio.{Task, ZIO}

import scala.concurrent.duration._

object DatabaseReadsApp extends CatsApp {

  import DatabaseOperations._
  import zio.interop.catz.implicits._
  import pureconfig.generic.auto._

  private val appLifecycleLogger: Resource[Task, Unit] =
    Resource.make[Task, Unit](Task(println("Starting application")))(_ => Task(println("Resources released, shutting down app")))

  override def run(args: List[String]): ZIO[DatabaseReadsApp.Environment, Nothing, Int] = {
    val httpOperations = new HttpOperations[Task]
    val program: Resource[Task, Unit] = for {
      _ <- appLifecycleLogger
      dbConfig <- Resource.liftF(Task.fromTry(pureconfig.loadConfig[DatabaseConfig].left.map(ConfigReaderException.apply).toTry))
      ds <- createDataSource[Task](dbConfig)
      _ <- BlazeServerBuilder[Task].bindHttp(8080, "localhost").withHttpApp(Router("/" -> httpOperations.httpServer(ds, findCustomer[Task])).orNotFound).resource
    } yield ()
    program.use(_ => Task.never).catchAll(e => putStrLn(s"Error: ${e.getMessage}") *> Task.succeed(1))
  }
}

object DatabaseOperations {

  final case class DatabaseConfig(
                                   driver: String,
                                   url: String,
                                   username: String,
                                   password: String,
                                   maxPoolSize: Int,
                                   minIdle: Int,
                                   connectionTimeout: FiniteDuration,
                                   maxLifetime: FiniteDuration)

  def createDataSource[F[_] : Sync](conf: DatabaseConfig): Resource[F, HikariDataSource] =
    Resource.fromAutoCloseable(createDSConfig[F](conf).map(new HikariDataSource(_)))

  def findCustomer[F[_] : Sync]: Connection => Long => Resource[F, Option[String]] = { connection => id =>
    for {
      stmt <- Resource.fromAutoCloseable[F, PreparedStatement](Sync[F].delay(connection.prepareStatement("SELECT name from CUSTOMERS WHERE customer_id = ?")))
      rs <- Resource.fromAutoCloseable(Sync[F].delay(stmt.setLong(1, id)) *> Sync[F].delay(stmt.executeQuery()))
      name <- Resource.liftF(Sync[F].delay(rsToOption(rs)))
    } yield name
  }

  def rsToOption(rs: ResultSet): Option[String] = Option(rs.next()).filter(identity).map(_ => rs.getString(1))

  private def createDSConfig[F[_] : Sync](dBConf: DatabaseConfig) = Sync[F].delay {
    val dataSourceConfig = new HikariConfig()
    dataSourceConfig.setDriverClassName(dBConf.driver)
    dataSourceConfig.setJdbcUrl(dBConf.url)
    dataSourceConfig.setUsername(dBConf.username)
    dataSourceConfig.setPassword(dBConf.password)
    dataSourceConfig.setMaximumPoolSize(dBConf.maxPoolSize)
    dataSourceConfig.setMinimumIdle(dBConf.minIdle)
    dataSourceConfig.setMaxLifetime(dBConf.maxLifetime.toMillis)
    dataSourceConfig.setConnectionTimeout(dBConf.connectionTimeout.toMillis)

    dataSourceConfig.setAutoCommit(false)
    dataSourceConfig.setReadOnly(false)

    dataSourceConfig
  }
}

class HttpOperations[F[_] : Sync] {

  private object zioDsl extends Http4sDsl[F]

  import zioDsl._

  def httpServer(ds: DataSource, customerFn: Connection => Long => Resource[F, Option[String]]): HttpRoutes[F] = {
    val customerNameR = Resource.fromAutoCloseable(Sync[F].delay(ds.getConnection)).map(customerFn)
    val customerF: Long => F[Option[String]] = { id => customerNameR.flatMap(_.apply(id)).use(Sync[F].delay(_)) }

    HttpRoutes.of[F] { case GET -> Root / "customers" / LongVar(id) => customerF(id).flatMap(_.fold(NotFound("Customer not found"))(s => Ok(s))) }
  }
}
