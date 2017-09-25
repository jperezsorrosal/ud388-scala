package ud388

import akka.event.NoLogging
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{ContentTypes, Uri}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import io.circe.generic.auto._
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import slick.jdbc.H2Profile.api._

import scala.concurrent.Await
import scala.concurrent.duration._


class ServiceEndpointsTests extends FlatSpec with Matchers with ScalatestRouteTest with ServiceEndpoints with BeforeAndAfterAll {
  override def testConfigSource = "akka.loglevel = DEBUG"
  override def config = testConfig
  override val logger = NoLogging

  override lazy val db = Database.forConfig("h2testdb") // use test DB

  val puppiesList = Seq(
    Puppy("Juanito", "El perro bonito.", 1)
    ,Puppy("Tocino", "El perro gorrino.", 2)
    ,Puppy("Marciano", "El perro marrano.", 3)
  )

  val insertAction: DBIO[Option[Int]] = puppies ++= puppiesList

  override def beforeAll = {


    Await.result(db.run(schema.drop >> schema.create >> insertAction), 2.seconds)
  }

  "The service" should "Get all the puppies on the database" in {
    //db.run(insertAction)

    db.run(puppies.result).foreach(println(_))

    Get("/puppies") ~> routes ~> check {
      status shouldBe OK
      contentType shouldBe ContentTypes.`application/json`
      responseAs[Seq[Puppy]].sortBy(_.id) shouldBe puppiesList.sortBy(_.id)
    }
  }


  it should "return an answer for a concrete puppie" in {
    //db.run(insertAction)

    val id = "2"
    Get(s"/puppies/$id") ~> routes ~> check {
      status shouldBe OK
      contentType shouldBe ContentTypes.`application/json`
      responseAs[Puppy] shouldBe puppiesList(1)
    }
  }

  it should "respond to POST creating a new puppie" in {
    //db.run(insertAction)

    val newPuppie = Puppy("Pingu", "Its a dogwin.", 4)

    val params =  Map("name" -> newPuppie.name, "description" -> newPuppie.description)
    val query = Uri.Query(params)

    Post(Uri(s"/puppies").withQuery(query)) ~> routes ~> check {
      status shouldBe OK
      contentType shouldBe ContentTypes.`application/json`
      responseAs[Int] shouldBe 1
    }
  }

  it should "restpond to PUT /puppies/id updating the puppy with id" in {
    val id = 2

    val updated = Puppy("Juano", "Marrano", 2)
    val params =  Map("name" -> updated.name, "description" -> updated.description)
    val query = Uri.Query(params)

    //db.run(insertAction)

    Put(Uri(s"/puppies/$id").withQuery(query)) ~> routes ~> check {
      status shouldBe OK
      contentType shouldBe ContentTypes.`application/json`
      val responsePuppy = responseAs[Puppy]
      responsePuppy.name shouldBe updated.name
      responsePuppy.description shouldBe updated.description
    }
  }

  it should "respond to DELETE /puppies/id" in {
    val id = 2

    //db.run(insertAction)

    Delete(s"/puppies/$id") ~> routes ~> check {
      status shouldBe OK
      contentType shouldBe ContentTypes.`application/json`
      responseAs[Int] shouldBe 1
    }
  }
}

