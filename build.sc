import mill._
import mill.scalalib._

object proxy extends ScalaModule {
  def scalaVersion = "2.12.4"
  object test extends Tests{
    def ivyDeps = Agg(
      ivy"com.lihaoyi::utest:0.6.4",
      ivy"com.lihaoyi::ammonite-ops:1.1.0")

    def testFrameworks = Seq("utest.runner.Framework")
  }
  def ivyDeps = Agg(
    ivy"net.debasishg::redisclient:3.5",
    ivy"com.typesafe.akka::akka-http-caching:10.1.1",
    ivy"com.typesafe.akka::akka-http:10.1.1",
    ivy"com.typesafe.akka::akka-stream:2.5.11"
    )
}