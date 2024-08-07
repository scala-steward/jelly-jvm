package eu.ostrzyciel.jelly.grpc

import com.typesafe.config.ConfigFactory
import eu.ostrzyciel.jelly.core.proto.v1.*
import eu.ostrzyciel.jelly.core.{JellyOptions, ProtoTestCases}
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.grpc.GrpcClientSettings
import org.apache.pekko.stream.scaladsl.*
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

class GrpcSpec extends AnyWordSpec, Matchers, ScalaFutures, BeforeAndAfterAll:
  import ProtoTestCases.*

  given PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 50.millis)
  val conf = ConfigFactory.parseString(
    """
      |pekko.http.server.preview.enable-http2 = on
      |pekko.grpc.client.jelly-no-gzip.host = 127.0.0.1
      |pekko.grpc.client.jelly-no-gzip.port = 8080
      |pekko.grpc.client.jelly-no-gzip.enable-gzip = false
      |pekko.grpc.client.jelly-no-gzip.use-tls = false
      |pekko.grpc.client.jelly-no-gzip.backend = netty
      |
      |pekko.grpc.client.jelly-gzip.host = 127.0.0.1
      |pekko.grpc.client.jelly-gzip.port = 8081
      |pekko.grpc.client.jelly-gzip.enable-gzip = true
      |pekko.grpc.client.jelly-gzip.use-tls = false
      |pekko.grpc.client.jelly-gzip.backend = netty
      |""".stripMargin)
    .withFallback(ConfigFactory.defaultApplication())

  val testKit = ActorTestKit(conf)
  val serverSystem: ActorSystem[_] = testKit.system

  class TestService(storedData: Map[String, Seq[RdfStreamFrame]]) extends RdfStreamService:
    given system: ActorSystem[_] = serverSystem
    given ExecutionContext = system.executionContext
    var receivedData: mutable.Map[String, Seq[RdfStreamFrame]] = mutable.Map()

    override def publishRdf(in: Source[RdfStreamFrame, NotUsed]) =
      in.toMat(Sink.seq)(Keep.right)
        .run()
        .map(data => {
          receivedData(data.head.rows.head.row.options.streamName) = data
          RdfStreamReceived()
        })

    override def subscribeRdf(in: RdfStreamSubscribe) =
      Source(storedData(in.topic))

  val data = Map(
    "triples" -> Triples1.encodedFull(
      JellyOptions.smallGeneralized
        .withStreamName("triples")
        .withPhysicalType(PhysicalStreamType.TRIPLES),
      1
    ),
    "quads" -> Quads1.encodedFull(
      JellyOptions.smallGeneralized
        .withStreamName("quads")
        .withPhysicalType(PhysicalStreamType.QUADS),
      3
    ),
    "quads_2" -> Quads2RepeatDefault.encodedFull(
      JellyOptions.smallGeneralized
        .withStreamName("quads_2")
        .withPhysicalType(PhysicalStreamType.QUADS),
      10
    ),
    "graphs" -> Graphs1.encodedFull(
      JellyOptions.smallGeneralized
        .withStreamName("graphs")
        .withPhysicalType(PhysicalStreamType.GRAPHS),
      1
    ),
  )

  val servers = Seq(
    ("no gzip", "jelly-no-gzip"),
    ("with gzip", "jelly-gzip"),
  ).map((name, confKey) => {
    val service = new TestService(data)
    val bound = new RdfStreamServer(
      RdfStreamServer.Options.fromConfig(conf.getConfig(s"pekko.grpc.client.$confKey")),
      service
    )(using serverSystem).run().futureValue
    (name, confKey, service, bound)
  })

  given clientSystem: ActorSystem[_] = ActorSystem(Behaviors.empty, "TestClient", conf)

  override def afterAll(): Unit =
    ActorTestKit.shutdown(clientSystem)
    testKit.shutdownTestKit()

  for (serverName, confKey, serverService, _) <- servers do
    val client = RdfStreamServiceClient(GrpcClientSettings.fromConfig(confKey))

    s"gRPC server ($serverName)" when {
      "receiving a subscription" should {
        for (caseName, toStream) <- data do
          s"stream $caseName" in {
            val received = client.subscribeRdf(RdfStreamSubscribe(caseName))
              .toMat(Sink.seq)(Keep.right)
              .run()
              .futureValue

            received should be (toStream)
          }
      }

      "receiving a stream from a publisher" should {
        for (caseName, toStream) <- data do
          s"stream $caseName" in {
            val received = client.publishRdf(Source(toStream))
              .futureValue

            received should be (RdfStreamReceived())
            serverService.receivedData(caseName) should be (toStream)
          }
      }
    }
