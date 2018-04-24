import mill._
import mill.scalalib._

object proxy extends ScalaModule {
  def scalaVersion = "2.12.4"
  def ivyDeps = Agg(
    ivy"com.typesafe.akka::akka-http:10.1.1",
    ivy"com.typesafe.akka::akka-stream:2.5.11"
    )
}