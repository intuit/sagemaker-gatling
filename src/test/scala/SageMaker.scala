import java.text.SimpleDateFormat
import java.util.{Calendar, TimeZone, UUID}

import com.typesafe.config.ConfigFactory
import io.gatling.core.Predef._
import io.gatling.core.feeder.Feeder
import io.gatling.http.Predef._
import org.slf4j.LoggerFactory

class SageMaker(requestFilePath: String = null,
                requestBody: String = null,
                requestDistribution: FileWeightDistribution = null) {

  val logger = LoggerFactory.getLogger(getClass)

  val conf = ConfigFactory.load()
  val awsAccessKeyId = conf.getString("auth.awsAccessKeyId")
  val awsSecretAccessKey = conf.getString("auth.awsSecretAccessKey")
  val awsSessionToken = conf.getString("auth.awsSessionToken")
  val host = conf.getString("sagemaker.host")
  val region = conf.getString("sagemaker.region")
  val endpoint = conf.getString("sagemaker.endpoint")

  val url = "https://" + host + "/endpoints/" + endpoint + "/invocations"
  val amzDateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'")
  amzDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"))

  // Assert only one request mode is specified.
  var useDistribution = false
  var singlePayload: String = null
  if (requestFilePath != null && requestBody == null && requestDistribution == null) {
    singlePayload = FileHelper.getFileAsString(requestFilePath)
  } else if (requestFilePath == null && requestBody != null && requestDistribution == null) {
    singlePayload = requestBody
  } else if (requestFilePath == null && requestBody == null && requestDistribution != null) {
    useDistribution = true
    // will determine next payload each time inside feeder
  } else {
    throw new IllegalArgumentException("Exactly one of (requestFilePath, requestBody, and requestDistribution) must be non-null.")
  }

  val customFeeder = new Feeder[String] {
    override def hasNext = true

    override def next: Map[String, String] = {
      val tid = UUID.randomUUID().toString

      var nextRequest = ""
      if (useDistribution) {
        nextRequest = requestDistribution.nextFileContent
      } else {
        nextRequest = singlePayload
      }

      // Create a date for headers and the credential string
      val date = Calendar.getInstance().getTime()
      val amzDate: String = amzDateFormat.format(date)
      val authorizationHeader: String = AuthHelper.getAuthorizationHeader(
        method = "POST",
        service = "sagemaker",
        host = host,
        region = region,
        endpoint = endpoint,
        payload = nextRequest,
        awsAccessKeyId = awsAccessKeyId,
        awsSecretAccessKey = awsSecretAccessKey,
        awsSessionToken = awsSessionToken,
        date = date,
        amzDate = amzDate)

      Map(
        ("tid", tid),
        ("nextRequest", nextRequest),
        ("amzDate", amzDate),
        ("authorizationHeader", authorizationHeader)
      )
    }
  }

  val sageMakerTransaction =
    feed(customFeeder)
      .exec(
        http("SageMaker-" + endpoint) // TODO: Add file weight name here?
          .post(url)
          .header("Content-Type", "plain/text")
          .header("Authorization", "${authorizationHeader}")
          .header("X-Amz-Date", "${amzDate}")
          .header("X-Amz-Security-Token", awsSessionToken)
          .header("tid", "${tid}")
          .body(StringBody(
            session => session.attributes.get("nextRequest").mkString
          )).asJson
          .check(status.is(200))
          .check(responseTimeInMillis.saveAs("execLatency"))
          .check(bodyString.saveAs("responseBody"))
      )
      .exec(session => {
        val latency = session("execLatency").as[Int]
        val responseBody = session("responseBody").as[String]
        logger.debug("execLatency: " + latency + " ms")
        logger.debug("responseBody:\n" + responseBody)
        session
      })
      .exitHereIfFailed

}
