package zalando.meetup.bracket

import java.sql.Connection

import cats.effect.Sync
import cats.syntax.flatMap._
import javax.sql.DataSource
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import DatabaseOperations._

object HttpOperations {

  def httpServer[F[_] : Sync](ds: DataSource, customerFn: Connection => Long => F[Option[String]]): HttpRoutes[F] = {
    val dsl = Http4sDsl[F]

    import dsl._

    val customerF: Long => F[Option[String]] = { id => connectionResource(ds).use(c => customerFn(c)(id)) }

    HttpRoutes.of[F] { case GET -> Root / "customers" / LongVar(id) => customerF(id).flatMap(_.fold(NotFound("Customer not found"))(s => Ok(s))) }
  }
}
