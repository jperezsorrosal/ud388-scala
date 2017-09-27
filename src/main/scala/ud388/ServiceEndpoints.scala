package ud388

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.{Credentials, DebuggingDirectives}
import akka.http.scaladsl.model.StatusCodes._
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.config.{Config, ConfigFactory}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._
import io.circe.syntax._

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.StdIn
import slick.jdbc.H2Profile.api._

import scala.util.{Failure, Success, Try}

trait DBService {
  import slick.lifted.Tag

  lazy val db = Database.forConfig("h2mem1")

  val puppies = TableQuery[PuppyTable]
  println(puppies.schema.createStatements.mkString)

  val users = TableQuery[UserTable]
  println(users.schema.createStatements.mkString)

  val schema = puppies.schema ++ users.schema // ++ other.schema

  val createAction: DBIO[Unit] = schema.create

  //  val insertAction: DBIO[Option[Int]] = puppies ++= Seq(
  //    Puppy("Juanito", "El perro bonito.")
  //    ,Puppy("Tocino", "El perro gorrino.")
  //    ,Puppy("Marciano", "El perro marrano."))

  val initActions = createAction //>> insertAction

  db.run(initActions)

  /* Puppy operations */

  def getAllPuppies: Future[Seq[Puppy]] = {
    println("Getting All the puppies!")
    db.run(puppies.result)
  }

  def makeANewPuppy(name: String, description: String): Future[Int] = {
    println(s"Creating A New Puppy!")

    val puppy = Puppy(name, description)
    db.run(puppies += puppy)
  }

  def getPuppy(id : Int): Future[Option[Puppy]] = {
    println(s"Getting Puppy with id $id")

    db.run(puppies.filter(_.id === id).result.headOption)
  }

  def updatePuppy(id: Int, name: Option[String], description: Option[String]): Future[Option[Puppy]] ={
    println(s"Updating a Puppy with id $id")

    db.run(puppies.filter(_.id === id).result.headOption).flatMap {
      case Some(p) =>
        val updatedPuppy = Puppy(name.getOrElse(p.name), description.getOrElse(p.description), id)
        db.run(puppies.filter(_.id === id).update(updatedPuppy)).map(_ => Some(updatedPuppy))

      case None => Future.successful(None)
    }
  }

  def deletePuppy(id: Int): Future[Int] = {
    println(s"Removing Puppy with id $id")
    db.run(puppies.filter(_.id === id).delete)
  }

  /* User operatiions */

  def getAllUsers: Future[Seq[User]] = {
    println("Getting all the users")
    db.run(users.result)
  }

  def makeNewUser(username: String, password: String): Future[Option[UserAnswer]] = {
    println(s"Creating a new User")

    val user = User(username, UserUtils.hashPassword(password))

    db.run((users += user ).asTry) map {
      case Success(i) if i == 1 => Some(UserAnswer(username))
      case _ => None
    }
  }

  // authenticate the user:
  def myUserPassAuthenticator(credentials: Credentials): Future[Option[User]] =
    credentials match {
      case c @ Credentials.Provided(id) => db.run(users.filter(_.username === id).result.headOption).map{ ou =>
        ou match {
          case Some(u) => if (c.verify(u.passwordHash, UserUtils.hashPassword)) Some(u) else None
          case _ => None
        }
      }
      case _                        => Future(None)
    }

  def hasAdminPermissions(user: User): Boolean = {
    true

  }
}

trait ServiceEndpoints extends DBService with FailFastCirceSupport {

  implicit val system: ActorSystem
  implicit def executor: ExecutionContextExecutor
  implicit val materializer: Materializer

  def config: Config
  val logger: LoggingAdapter

  val puppiesRoutes: Route = path("puppies") {
    get {
      complete(getAllPuppies.map(_.asJson))
    } ~ post {
      parameters('name, 'description) { (name, description) =>

        complete(makeANewPuppy(name, description).map(_.asJson))
      }

    }
  } ~ path("puppies" / IntNumber) { id =>
    get {
      complete(getPuppy(id).map(_.asJson))
    }~ put {
      parameters('name.?, 'description.?) { (name, description) =>

        complete(updatePuppy(id, name, description).map(_.asJson))
      }
    } ~ delete {
      complete(deletePuppy(id).map(_.asJson))
    }
  }


  val usersRoutes: Route = path("users") {

    get {
      complete(getAllUsers)
    } ~ post {

      entity(as[UserForm]) { user =>
        onComplete(makeNewUser(user.username, user.password)) {
          case Success(Some(u)) => complete(Created, u.asJson)
          case _ => complete(Conflict, "Resource exists")
        }
      }
    }
  }

  val protectedRoutes =
    path("protected_resource") {
      authenticateBasicAsync[User](realm = "Secured resources", myUserPassAuthenticator) { user =>
        authorize(hasAdminPermissions(user)) {
          complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, s"User '${user.username}' visited 'protected_resource'."))
        }
      }
    }

  val routes = puppiesRoutes ~ usersRoutes ~ protectedRoutes
}


object Endpoints extends App with ServiceEndpoints {
  override implicit val system = ActorSystem()
  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorMaterializer()

  override val config = ConfigFactory.load()
  override val logger = Logging(system, getClass)


  DebuggingDirectives.logRequestResult("get-user")

  val bindingFuture = Http().bindAndHandle(routes, config.getString("http.interface"), config.getInt("http.port"))

  StdIn.readLine()
  // Unbind from the port and shut down when done
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => {
      db.close()
      system.terminate()
    })
}
