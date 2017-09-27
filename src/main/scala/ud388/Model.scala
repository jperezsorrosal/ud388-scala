package ud388

import org.apache.commons.codec.digest.DigestUtils

import slick.jdbc.H2Profile.api._
import slick.lifted.Tag


trait DBSchema {

  /*** PUPPY ***/

  final case class Puppy(name: String, description: String, id: Int = 0)

  final class PuppyTable(tag: Tag) extends Table[Puppy](tag, "puppy") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def description =column[String]("description")

    def * = (name, description, id).mapTo[Puppy]
  }

  val puppies = TableQuery[PuppyTable]
  println(puppies.schema.createStatements.mkString)


  /*** USER ***/

  final case class User(username: String, passwordHash: String, id: Int = 0)

  // need this 'cause decoding json from the json request to create a user without an explicit 'id',
  // circe doesn't know how to create a User object.
  final case class UserForm(username: String, password: String)
  final case class UserAnswer(username: String)

  object UserUtils {

    final def hashPassword(password: String): String = DigestUtils.sha256Hex(password)

    final def verifyPassword(user: User, password: String): Boolean = user.passwordHash == DigestUtils.sha256Hex(password)
  }

  final class UserTable(tag: Tag) extends Table[User](tag, "user") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def username = column[String]("username", O.Unique)
    def passwordHash = column[String]("passwordHash")

    def * = (username, passwordHash, id).mapTo[User]
  }

  val users = TableQuery[UserTable]
  println(users.schema.createStatements.mkString)

  def findUserId(name: String): DBIO[Option[Int]] = users.filter(_.username === name).map(_.id).result.headOption


  /*** RESOURCE ****/

  final case class Resource(urlPath: String, id: Int = 0)

  final class ResourceTable(tag: Tag) extends Table[Resource](tag, "resource") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def urlPath = column[String]("urlpath")

    def * = (urlPath, id).mapTo[Resource]
  }

  val resources = TableQuery[ResourceTable]

  def findResourceId(urlPath: String): DBIO[Option[Int]] = resources.filter(_.urlPath === urlPath).map(_.id).result.headOption


  /*** AUTHORIZATION ***/

  final case class Authorization(resourceId: Int, userId: Int)

  final class AuthorizationTable(tag: Tag) extends Table[Authorization](tag, "authorization") {
    def resourceId = column[Int]("resourceId")
    def userId = column[Int]("userId")

    def pk = primaryKey("authrization_pk", (resourceId, userId))

    def resource = foreignKey("resource_fk", resourceId, resources)(_.id)
    def user = foreignKey("user_fk", userId, users)(_.id)

    def compoundIndex = index("c_idx", (resourceId, userId), unique=true)

    def * = (resourceId, userId).mapTo[Authorization]
  }

  val authorizations = TableQuery[AuthorizationTable]

  val schema = puppies.schema ++ users.schema ++ resources.schema ++ authorizations.schema // ++ other.schema
}




