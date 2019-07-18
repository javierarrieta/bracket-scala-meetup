package zalando.meetup.bracket

import java.sql.{Connection, PreparedStatement}

import cats.effect.{Resource, Sync}
import cats.syntax.applicativeError._
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
import zio.console.putStrLn
import zio.interop.catz._
import zio.{Task, ZIO}

import scala.concurrent.duration._

object DatabaseReadsApp extends CatsApp {

  import DatabaseOperations._
  import zio.interop.catz.implicits._

  private val databaseConfig: DatabaseConfig = DatabaseConfig("org.postgresql.Driver", "jdbc:postgresql://localhost:5432/customers",
    "admin", "admin", 2, 1, 1.second, 24.hours)
  private val dbResource: Resource[Task, HikariDataSource] = createDataSource[Task](databaseConfig)
  private val appLifecycleLogger: Resource[Task, Unit] =
    Resource.make[Task, Unit](Task(println("Starting application")))(_ => Task(println("Resources released, shutting down app")))

  override def run(args: List[String]): ZIO[DatabaseReadsApp.Environment, Nothing, Int] = {
    val httpOperations = new HttpOperations[Task]
    val program: Resource[Task, Unit] = for {
      _ <- appLifecycleLogger
      ds <- dbResource
      _ <- BlazeServerBuilder[Task].bindHttp(8080, "localhost").withHttpApp(Router("/" -> httpOperations.httpServer(ds, findCustomer[Task])).orNotFound).resource
    } yield ()
    program.use(_ => Task.succeed(0)).catchAll(e => putStrLn(s"Error: ${e.getMessage}") *> Task.succeed(1))
  }
}

object DatabaseOperations {

  final case class DatabaseConfig(
                                   driver: String,
                                   url: String,
                                   user: String,
                                   pass: String,
                                   maxPoolSize: Int,
                                   minIdle: Int,
                                   connectionTimeout: FiniteDuration,
                                   maxLifetime: FiniteDuration)

  def createDataSource[F[_] : Sync](conf: DatabaseConfig): Resource[F, HikariDataSource] =
    Resource.fromAutoCloseable(createDSConfig[F](conf).map(new HikariDataSource(_)))

  def findCustomer[F[_] : Sync]: Connection => Long => Resource[F, Option[String]] = { connection =>
    id =>
      for {
        stmt <- Resource.fromAutoCloseable[F, PreparedStatement](Sync[F].delay(connection.prepareStatement("SELECT name from CUSTOMERS WHERE customer_id = ?")))
        rs <- Resource.fromAutoCloseable(Sync[F].delay(stmt.setLong(1, id)) *> Sync[F].delay(stmt.executeQuery()))
        name <- Resource.liftF(Sync[F].delay(rs.getString(1)).map(Option.apply).recover { case _: IllegalArgumentException => None })
      } yield name
  }

  private def createDSConfig[F[_] : Sync](dBConf: DatabaseConfig) = Sync[F].delay {
    val dataSourceConfig = new HikariConfig()
    dataSourceConfig.setDriverClassName(dBConf.driver)
    dataSourceConfig.setJdbcUrl(dBConf.url)
    dataSourceConfig.setUsername(dBConf.user)
    dataSourceConfig.setPassword(dBConf.pass)
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

    HttpRoutes.of[F] { case GET -> Root / "customer" / LongVar(id) => customerF(id).flatMap(_.fold(NotFound("Customer not found"))(s => Ok(s))) }
  }
}
