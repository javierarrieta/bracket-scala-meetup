package zalando.meetup.bracket

import java.sql.Connection

import cats.effect.{Resource, Sync}
import cats.syntax.flatMap._
import javax.sql.DataSource
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

class HttpOperations[F[_] : Sync] {

  private object zioDsl extends Http4sDsl[F]

  import zioDsl._

  def httpServer(ds: DataSource, customerFn: Connection => Long => Resource[F, Option[String]]): HttpRoutes[F] = {
    val customerNameR = Resource.fromAutoCloseable(Sync[F].delay(ds.getConnection)).map(customerFn)
    val customerF: Long => F[Option[String]] = { id => customerNameR.flatMap(_.apply(id)).use(Sync[F].delay(_)) }

    HttpRoutes.of[F] { case GET -> Root / "customers" / LongVar(id) => customerF(id).flatMap(_.fold(NotFound("Customer not found"))(s => Ok(s))) }
  }
}
