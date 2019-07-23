package zalando.meetup.bracket

import java.sql.{Connection, PreparedStatement, ResultSet}

import cats.effect.{Resource, Sync}
import cats.syntax.functor._
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import javax.sql.DataSource
import scala.concurrent.duration.FiniteDuration


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

  def connectionResource[F[_]: Sync](ds: DataSource): Resource[F, Connection] = Resource.fromAutoCloseable(Sync[F].catchNonFatal(ds.getConnection()))

  private def findCustomerStmt[F[_]: Sync](connection: Connection, id: Long): F[PreparedStatement] = { 
    Sync[F].delay { 
      val stmt = connection.prepareStatement("SELECT name from CUSTOMERS WHERE customer_id = ?") 
      stmt.setLong(1, id)
      stmt
    }
  }

  def findCustomer[F[_] : Sync]: Connection => Long => F[Option[String]] = { connection => id =>
    val resultSet = for {
      stmt <- Resource.fromAutoCloseable[F, PreparedStatement](findCustomerStmt[F](connection, id))
      rs <- Resource.fromAutoCloseable(Sync[F].delay(stmt.executeQuery()))
    } yield rs
    resultSet.use(rsToOption[F])
  }

  private def rsToOption[F[_]: Sync](rs: ResultSet): F[Option[String]] = Sync[F].delay(Option(rs.next()).filter(identity).map(_ => rs.getString(1)))

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
