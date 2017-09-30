package ud388

import akka.event.NoLogging
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenge}
import akka.http.scaladsl.model.{ContentTypes, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshaller._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Route
import io.circe.generic.auto._
import org.scalatest.{Assertion, BeforeAndAfterAll, FlatSpec, Matchers}
import slick.jdbc.H2Profile.api._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class ServiceEndpointsUsersTests extends FlatSpec with Matchers with ScalatestRouteTest with ServiceEndpoints with BeforeAndAfterAll {
  override def testConfigSource = "akka.loglevel = DEBUG"
  override def config = testConfig
  override val logger = NoLogging

  val sealedRoutes = Route.seal(routes)

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

  "The Users service" should "Get the list of all the users" in {
    Get("/users") ~> routes ~> check {
      status shouldBe OK
      contentType shouldBe ContentTypes.`application/json`
      responseAs[Seq[User]].sortBy(_.id) shouldBe usersList.sortBy(_.id)
    }
  }

  it should "create a new user using POST request" in {
    //db.run(insertAction)

    val username = "Pingu"

    val user = UserForm(username, password)

    Post[UserForm](s"/users", user) ~> routes ~> check {
      status shouldBe Created
      contentType shouldBe ContentTypes.`application/json`
      responseAs[UserAnswer] shouldBe UserAnswer(username)
    }
  }

  it should "Return Conflict Status when we tray to create existing User" in {
    val username = "Duplicated"
    val user = UserForm(username, password)

    Post[UserForm](s"/users", user) ~> routes ~> check {
      status shouldBe Created
      contentType shouldBe ContentTypes.`application/json`
      responseAs[UserAnswer] shouldBe UserAnswer(username)
    }
    Post[UserForm](s"/users", user) ~> routes ~>check {
      status shouldBe Conflict
    }

  }

  it should "Not allow access to protected_resources without credentials" in {
    Get("/protected_resource") ~> sealedRoutes ~> check {
      status shouldBe Unauthorized
      contentType shouldBe ContentTypes.`text/plain(UTF-8)`
      responseAs[String] shouldEqual "The resource requires authentication, which was not supplied with the request"
      header[`WWW-Authenticate`].get.challenges.head shouldEqual HttpChallenge("Basic", Some("Secured resources"), Map("charset" → "UTF-8"))
    }
  }

  it should "Not allow access to protected_resource with the incorrect creentials (username)" in {
    val username = "Juan"
    val user = User("Othername", hashedPassword)

    Await.result(db.run(users += user).map( _ shouldBe 1), 2.seconds)

    val juansCredentials = BasicHttpCredentials(username, password)

    Get(s"/protected_resource") ~> addCredentials(juansCredentials) ~> sealedRoutes ~> check {
      status shouldEqual Unauthorized
      contentType shouldBe ContentTypes.`text/plain(UTF-8)`
      responseAs[String] shouldBe s"The supplied authentication is invalid"
    }
  }

  it should "Not allow access to protected_resource with the incorrect creentials (password)" in {
    val username = "Juan"
    val user = User(username, hashedPassword)

    Await.result(db.run(users += user).map( _ shouldBe 1), 2.seconds)

    val juansCredentials = BasicHttpCredentials(username, "otherPassword")

    Get(s"/protected_resource") ~> addCredentials(juansCredentials) ~> sealedRoutes ~> check {
      status shouldEqual Unauthorized
      contentType shouldBe ContentTypes.`text/plain(UTF-8)`
      responseAs[String] shouldBe s"The supplied authentication is invalid"
    }
  }

  it should "Not Allow to access 'protected_resource' without the correct credentials and authorization to this resource." in {
    val username = "Juano"
    val user = User(username, hashedPassword)

    Await.result(db.run(users += user).map( _ shouldBe 1), 2.seconds)

    val juansCredentials = BasicHttpCredentials(username, password)

    Get(s"/protected_resource") ~> addCredentials(juansCredentials) ~> sealedRoutes ~> check {
      status shouldBe Forbidden
      contentType shouldBe ContentTypes.`text/plain(UTF-8)`
      responseAs[String] shouldBe "The supplied authentication is not authorized to access this resource"
    }
  }

  it should "Allow access 'protected_resource' with the correct credentials and authorization to this resource." in {

    val username = "JuanoAuthorized"
    val user = User(username, hashedPassword)
    val resource = Resource("/protected_resource")

    val insertionsAction: DBIO[Int] = for {
      _ <- users += user
      _ <- resources += resource
      Some(userId) <- findUserId(user.username)
      Some(resourceId) <- findResourceId(resource.urlPath)
      rowsAdded <- authorizations += Authorization(resourceId, userId)
    } yield rowsAdded

    Await.result(db.run(insertionsAction), 3.seconds)

    val juansCredentials = BasicHttpCredentials(username, password)

    Get(s"/protected_resource") ~> addCredentials(juansCredentials) ~> sealedRoutes ~> check {
      status shouldBe OK
      contentType shouldBe ContentTypes.`text/plain(UTF-8)`
      responseAs[String] shouldBe s"User '$username' visited 'protected_resource'."
    }
  }


  it should "Allow access 'protected_token' with the correct token and authorization to this resource." in {


    val username = "JuanoTokenAuthorized"
    val user = User(username, hashedPassword)
    val resource = Resource("/protected_token")

    val insertionsAction: DBIO[Int] = for {
      _ <- users += user
      _ <- resources += resource
      Some(userId) <- findUserId(user.username)
      Some(resourceId) <- findResourceId(resource.urlPath)
      rowsAdded <- authorizations += Authorization(resourceId, userId)
    } yield rowsAdded

    Await.result(db.run(insertionsAction), 3.seconds)



    val futureAssertion = db.run(findUserId(username)) map {
      case Some(userId) =>
        val token = createJWT(issuer, userId, ttl)

        verifyJWT(token.token, issuer) shouldBe Some(userId)

        val tokenCredentials = OAuth2BearerToken(token.token)

        Get(s"/protected_token") ~> addCredentials(tokenCredentials) ~> sealedRoutes ~> check {
          status shouldBe OK
          contentType shouldBe ContentTypes.`text/plain(UTF-8)`
          responseAs[String] shouldBe s"User '$username' visited 'protected_token'."
        }
      case _ => fail(s"user $username is not in DB")
    }

    Await.result(futureAssertion, 3.seconds)

  }
}

