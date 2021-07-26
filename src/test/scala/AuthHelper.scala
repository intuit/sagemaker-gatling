import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.slf4j.LoggerFactory


object AuthHelper {

  val logger = LoggerFactory.getLogger(getClass)

  val hashStringFormat = "%064x" // leading zeroes, 64 digits, hexadecimal

  // Based on "Signature Version 4 signing process"
  // https://docs.aws.amazon.com/general/latest/gr/signature-version-4.html
  def getAuthorizationHeader(method: String,
                             service: String,
                             host: String,
                             region: String,
                             endpoint: String,
                             payload: String,
                             awsAccessKeyId: String,
                             awsSecretAccessKey: String,
                             awsSessionToken: String,
                             date: Date,
                             amzDate: String) = {
    val dateStampFormat = new SimpleDateFormat("yyyyMMdd")
    dateStampFormat.setTimeZone(TimeZone.getTimeZone("UTC"))
    val dateStamp: String = dateStampFormat.format(date)
    val canonicalUri = "/endpoints/" + endpoint + "/invocations"
    val canonicalHeaders = "host:" + host + "\n" +
      "x-amz-date:" + amzDate + "\n" +
      "x-amz-security-token:" + awsSessionToken + "\n"
    val signedHeaders = "host;x-amz-date;x-amz-security-token"
    val hashedPayload = String.format(hashStringFormat, new BigInteger(1, doHash(payload)))
    val canonicalRequest = method + '\n' +
      canonicalUri + "\n" +
      "\n" + // canonicalQueryString is empty
      canonicalHeaders + "\n" + // each line in canonicalHeaders ends in \n, but add another here
      signedHeaders + "\n" +
      hashedPayload
    val algorithm = "AWS4-HMAC-SHA256"
    val credentialScope = dateStamp + "/" + region + "/" + service + "/" + "aws4_request"
    val stringToSign = algorithm + "\n" +
      amzDate + "\n" +
      credentialScope + "\n" +
      String.format(hashStringFormat, new BigInteger(1, doHash(canonicalRequest)))
    val signing_key = getSignatureKey(awsSecretAccessKey, dateStamp, region, service)
    val signature = HmacSHA256(stringToSign, signing_key)
    val authorization_header = algorithm + " " +
      "Credential=" + awsAccessKeyId + "/" + credentialScope + ", " +
      "SignedHeaders=" + signedHeaders + ", " +
      "Signature=" + String.format(hashStringFormat, new BigInteger(1, signature))
    authorization_header
  }

  @throws[Exception]
  def HmacSHA256(data: String, key: Array[Byte]): Array[Byte] = {
    val algorithm = "HmacSHA256"
    val mac = Mac.getInstance(algorithm)
    mac.init(new SecretKeySpec(key, algorithm))
    return mac.doFinal(data.getBytes(StandardCharsets.UTF_8))
  }

  @throws[Exception]
  def getSignatureKey(key: String, dateStamp: String, regionName: String, serviceName: String): Array[Byte] = {
    val kSecret = ("AWS4" + key).getBytes(StandardCharsets.UTF_8)
    val kDate = HmacSHA256(dateStamp, kSecret)
    val kRegion = HmacSHA256(regionName, kDate)
    val kService = HmacSHA256(serviceName, kRegion)
    return HmacSHA256("aws4_request", kService)
  }

  def doHash(text: String): Array[Byte] = {
    val md = MessageDigest.getInstance("SHA-256")
    md.reset()
    md.update(text.getBytes)
    return md.digest
  }

}
