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

object UserUtils {

  final def hashPassword(password: String): String = DigestUtils.sha256Hex(password)

  final def verifyPassword(user: User, password: String): Boolean = user.passwordHash == DigestUtils.sha256Hex(password)
}


final class UserTable(tag: Tag) extends Table[User](tag, "user") {
  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def username = column[String]("username")
  def passwordHash = column[String]("passwordHash")

  def * = (username, passwordHash, id).mapTo[User]
}
