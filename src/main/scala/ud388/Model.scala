package ud388

// Use H2Driver to connect to an H2 database
import slick.jdbc.H2Profile.api._
import slick.lifted.Tag


final case class Puppy(name: String, description: String, id: Int = 0)

final class PuppyTable(tag: Tag) extends Table[Puppy](tag, "puppy") {
  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name")
  def description =column[String]("description")

  def * = (name, description, id).mapTo[Puppy]
}
//
//object PuppiesDatabase extends App {
//  val db = Database.forConfig("h2mem1")
//
//  try {
//    val puppies = TableQuery[PuppyTable]
//
//    puppies.schema.createStatements.mkString
//
//    val createAction: DBIO[Unit] = puppies.schema.create
//    db.run(createAction)
//
//    val insertAction: DBIO[Option[Int]] = puppies ++= Seq(
//      Puppy("Juan", "hola")
//      ,Puppy("Tocino", "culo")
//      ,Puppy("Marciano", "cosino"))
//    db.run(insertAction)
//
//    db.run(puppies.result)
//
//  } finally db.close
//}