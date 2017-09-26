package ud388

import akka.event.NoLogging
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenge}
import akka.http.scaladsl.model.{ContentTypes, Uri}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.model.headers._
import io.circe.generic.auto._
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import slick.jdbc.H2Profile.api._

import scala.concurrent.Await
import scala.concurrent.duration._


class ServiceEndpointsUsersTests extends FlatSpec with Matchers with ScalatestRouteTest with ServiceEndpoints with BeforeAndAfterAll {
  override def testConfigSource = "akka.loglevel = DEBUG"
  override def config = testConfig
  override val logger = NoLogging

  override lazy val db = Database.forConfig("h2testdbUsers") // use test DB

  val password = "Tocino"
  val hashedPassword = UserUtils.hashPassword(password)

  val usersList = Seq(
    User("Juanito", hashedPassword, 1)
    ,User("Tocino", hashedPassword, 2)
    ,User("Marciano", hashedPassword, 3)
  )

  val insertAction: DBIO[Option[Int]] = users ++= usersList

  override def beforeAll = {

    Await.result(db.run(schema.drop >> schema.create >> insertAction), 2.seconds)
  }

  "The Users service" should "Get the list of al the susers" in {
    Get("/users") ~> routes ~> check {
      status shouldBe OK
      contentType shouldBe ContentTypes.`application/json`
      responseAs[Seq[User]].sortBy(_.id) shouldBe usersList.sortBy(_.id)
    }
  }

  it should "create a new user using POST request" in {
    //db.run(insertAction)

    val username = "Pingu"

    val user = User(username, hashedPassword)

    Post[User](s"/users", user) ~> routes ~> check {
      status shouldBe Created
      contentType shouldBe ContentTypes.`application/json`
      responseAs[UserAnswer] shouldBe UserAnswer(username)
    }
  }

  it should "Return Conflict Status when we tray to create existing User" in {
    val username = "Duplicated"
    val user = User(username, hashedPassword)

    Post[User](s"/users", user) ~> routes ~> check {
      status shouldBe Created
      contentType shouldBe ContentTypes.`application/json`
      responseAs[UserAnswer] shouldBe UserAnswer(username)
    }
    Post[User](s"/users", user) ~> routes ~>check {
      status shouldBe Conflict
    }

  }

  it should "Not allow access to protected_resources without credentials" in {
    Get("/protected_resource") ~> routes ~> check {
      status shouldEqual Unauthorized
      responseAs[String] shouldEqual "The resource requires authentication, which was not supplied with the request"
      header[`WWW-Authenticate`].get.challenges.head shouldEqual HttpChallenge("Basic", Some("Secured resources"), Map("charset" → "UTF-8"))
    }
  }

  it should "Not allow access to protected_resource with the incorrect creentials" in {
    val username = "Juan"
    val user = User("Othername", hashedPassword)

    Await.result(db.run(users += user).map( _ shouldBe 1), 2.seconds)

    val juansCredentials = BasicHttpCredentials(username, password)

    Get(s"/protected_resource") ~> addCredentials(juansCredentials) ~> routes ~> check {
      status shouldEqual Unauthorized
      responseAs[String] shouldBe s"User '$username' visited 'protected_resource'."
    }
  }

  it should "Allow to access protected_resource with the correct credentials" in {
    val username = "Juan"
    val user = User(username, hashedPassword)

    Await.result(db.run(users += user).map( _ shouldBe 1), 2.seconds)

    val juansCredentials = BasicHttpCredentials(username, password)

    Get(s"/protected_resource") ~> addCredentials(juansCredentials) ~> routes ~> check {
      status shouldBe OK
      responseAs[String] shouldBe s"User '$username' visited 'protected_resource'."
    }
  }
}

