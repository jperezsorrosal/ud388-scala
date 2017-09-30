package ud388

import java.util.{Date, UUID}

import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.{JWT, JWTCreator, JWTVerifier}
import org.apache.commons.codec.digest.DigestUtils

import scala.util.control.Exception.catching
import scala.concurrent.duration._


trait WebTokens {

  case class Token(token: String)

  def generateRandomKey = {
    val uuid = UUID.randomUUID.toString
    DigestUtils.sha256Hex(uuid)
  }

  def generateKey(s: String) = {
    DigestUtils.sha256Hex(s)
  }

  val secretKey = generateRandomKey
  val algorithm = Algorithm.HMAC256(secretKey)
  val issuer = "ud388"
  val ttl = 5.minutes.toMillis

  val userIdClaimName = "userId"

  def createJWT(issuer: String, userId: Int, ttlMillis: Long): Token = {
    assert(ttlMillis >= 0)

    val nowMillis = System.currentTimeMillis()
    val now = new Date(nowMillis)
    val expMillis: Long = nowMillis + ttlMillis
    val exp: Date = new Date(expMillis)

    val builder: JWTCreator.Builder = JWT.create()
      .withIssuedAt(now)
      .withIssuer(issuer)
      .withExpiresAt(exp)
      .withClaim(userIdClaimName, int2Integer(userId))

    val token = builder.sign(algorithm)

    Token(token)
  }

  def verifyJWT(token: String, issuer: String): Option[Int] = {
    val verifier: JWTVerifier = JWT.require(algorithm)
      .withIssuer(issuer)
      .build();

    catching (classOf[JWTVerificationException]) either {
      verifier.verify(token)
    } match {
      case Right(decodedJWT) => Some(decodedJWT.getClaim(userIdClaimName).asInt())
      case _ => None
    }
  }
}

//object Culo extends WebTokens with App {
//  val token = createJWT("ud388", 2.second.toMillis)
//  Thread sleep 1.seconds.toMillis
//  verifyJWT(token.token, "ud388")
//}
