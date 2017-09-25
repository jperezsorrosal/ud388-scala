package ud388

import org.apache.commons.codec.digest.DigestUtils

import slick.jdbc.H2Profile.api._
import slick.lifted.Tag


final case class Puppy(name: String, description: String, id: Int = 0)

final class PuppyTable(tag: Tag) extends Table[Puppy](tag, "puppy") {
  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name")
  def description =column[String]("description")

  def * = (name, description, id).mapTo[Puppy]
}

final case class User(username: String, passwordHash: String, id: Int = 0)

// need this 'cause decoding json from the json request to create a user without an explicit 'id',
// circe doesn't know how to create a User object.
final case class UserForm(username: String, passwordHash: String)
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
