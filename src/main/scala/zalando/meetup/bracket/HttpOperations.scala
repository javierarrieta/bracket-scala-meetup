package zalando.meetup.bracket

import cats.effect.Sync
import cats.syntax.flatMap._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

object HttpOperations {

  def httpServer[F[_] : Sync](customerFn: Long => F[Option[String]]): HttpRoutes[F] = {
    val zioDsl = Http4sDsl[F]

    import zioDsl._

    HttpRoutes.of[F] { case GET -> Root / "customers" / LongVar(id) => customerFn(id).flatMap(_.fold(NotFound("Customer not found"))(s => Ok(s))) }
  }
}
