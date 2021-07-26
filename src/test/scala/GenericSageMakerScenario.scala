// GenericPredictScenario simulates model specified in application.conf.

import com.typesafe.config.ConfigFactory
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

class GenericSageMakerScenario extends Simulation {
  val logger = LoggerFactory.getLogger(getClass)

  val conf = ConfigFactory.load()
  val rampStartTps = conf.getDouble("scenario.rampStartTps")
  val rampMinutes = conf.getDouble("scenario.rampMinutes")
  val steadyStateTps = conf.getDouble("scenario.steadyStateTps")
  val steadyStateMinutes = conf.getDouble("scenario.steadyStateMinutes")
  val requests = conf.getString("scenario.requests")

  val requestDistribution = new FileWeightDistribution(requests)
  val sageMaker = new SageMaker(requestDistribution = requestDistribution)
  var callChainFlow = scenario("SageMaker") //TODO: reconcile with http requestName
    .exec(sageMaker.sageMakerTransaction)

  if (rampMinutes == 0 && steadyStateMinutes == 0) {
    throw new IllegalArgumentException("Ramp and steady state durations cannot both be zero.")
  }
  setUp(
    callChainFlow.inject(
      if (rampMinutes != 0) {
        rampUsersPerSec(rampStartTps) to steadyStateTps during (rampMinutes minutes)
      } else {
        nothingFor(1 second)
      },
      if (steadyStateMinutes != 0) {
        constantUsersPerSec(steadyStateTps) during (steadyStateMinutes minutes)
      } else {
        nothingFor(1 second)
      }
    )
  ).protocols(http)

}
