package ud388

import com.auth0.jwt.algorithms.Algorithm
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._


class WebTokensTests extends FlatSpec with Matchers with WebTokens {

  //  override val secretKey = generateKey("Hola")
  //  override val algorithm = Algorithm.HMAC256(secretKey)

  override val issuer = "JuanPS"
  override val ttl = 10.seconds.toMillis
  val userId = 0

  "createJWT" should "create a key and be able to verify" in {

    val token = createJWT(issuer, userId, ttl)

    verifyJWT(token.token, issuer) shouldBe Some(userId)
  }

  "verifyJWT" should "return false if the ttl expired" in {

    val ttl = 100.milliseconds.toMillis
    val token = createJWT(issuer, userId, ttl)

    Thread sleep 1.second.toMillis

    verifyJWT(token.token, issuer) shouldBe None
  }

  it should "return false if some payload claim 'issuer' is different " in {

    val differentIssuer = "not Juan"
    val token = createJWT(issuer, userId, ttl)

    verifyJWT(token.token, differentIssuer) shouldBe None
  }
}
