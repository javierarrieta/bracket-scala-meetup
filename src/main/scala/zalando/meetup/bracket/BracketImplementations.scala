package zalando.meetup.bracket

import cats.MonadError
import cats.effect.{Bracket, ExitCase}

object BracketImplementations {

  implicit def myBracketImpl[F[_], E](implicit monadError: MonadError[F, E]): Bracket[F, E] = new Bracket[F, E] {
    override def bracketCase[A, B](acquire: F[A])(use: A => F[B])(release: (A, ExitCase[E]) => F[Unit]): F[B] = ???

    override def raiseError[A](e: E): F[A] = monadError.raiseError(e)

    override def handleErrorWith[A](fa: F[A])(f: E => F[A]): F[A] = monadError.handleErrorWith(fa)(f)

    override def pure[A](x: A): F[A] = monadError.pure(x)

    override def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B] = monadError.flatMap(fa)(f)

    override def tailRecM[A, B](a: A)(f: A => F[Either[A, B]]): F[B] = monadError.tailRecM(a)(f)
  }
}
