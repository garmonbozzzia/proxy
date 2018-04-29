/* Created on 29.04.18 */
object Extensions {
  implicit class Traceable[A] (val obj: A) extends AnyVal {
    def traceWith[B](f: A => B ): A = { println(f(obj)); obj}
    def trace[U](u: => U): A = traceWith(_ => u)
    def trace: A = trace[A](obj)
  }
}
