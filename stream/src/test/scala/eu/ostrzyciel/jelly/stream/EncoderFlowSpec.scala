package eu.ostrzyciel.jelly.stream

import eu.ostrzyciel.jelly.core.helpers.Assertions.*
import eu.ostrzyciel.jelly.core.helpers.MockConverterFactory
import eu.ostrzyciel.jelly.core.proto.v1.*
import eu.ostrzyciel.jelly.core.{Constants, JellyOptions, ProtoTestCases}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration.*

class EncoderFlowSpec extends AnyWordSpec, Matchers, ScalaFutures:
  import ProtoTestCases.*

  given PatienceConfig = PatienceConfig(5.seconds, 100.millis)
  given MockConverterFactory.type = MockConverterFactory
  given ActorSystem = ActorSystem()

  "flatTripleStream" should {
    "encode triples" in {
      val encoded: Seq[RdfStreamFrame] = Source(Triples1.mrl)
        .via(EncoderFlow.builder
          .withLimiter(StreamRowCountLimiter(1000))
          .flatTriples(JellyOptions.smallGeneralized)
          .flow
        )
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Triples1.encoded(JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.TRIPLES)
          .withLogicalType(LogicalStreamType.FLAT_TRIPLES)
          .withVersion(Constants.protoVersion_1_0_x)
        )
      )
      encoded.size should be (1)
    }

    "encode triples with max message size" in {
      val encoded: Seq[RdfStreamFrame] = Source(Triples1.mrl)
        .via(EncoderFlow.builder
          .withLimiter(ByteSizeLimiter(80))
          .flatTriples(JellyOptions.smallGeneralized)
          .flow
        )
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Triples1.encoded(JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.TRIPLES)
          .withLogicalType(LogicalStreamType.FLAT_TRIPLES)
          .withVersion(Constants.protoVersion_1_0_x)
        )
      )
      encoded.size should be (3)
    }

    "encode triples with max row count" in {
      val encoded: Seq[RdfStreamFrame] = Source(Triples1.mrl)
        .via(EncoderFlow.builder
          .withLimiter(StreamRowCountLimiter(4))
          .flatTriples(JellyOptions.smallGeneralized)
          .flow
        )
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Triples1.encoded(JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.TRIPLES)
          .withLogicalType(LogicalStreamType.FLAT_TRIPLES)
          .withVersion(Constants.protoVersion_1_0_x)
        )
      )
      encoded.size should be (4)
    }

    "encode triples with namespace declarations" in {
      val encoded: Seq[RdfStreamFrame] = Source(Triples2NsDecl.mrl)
        .via(EncoderFlow.builder
          .withLimiter(StreamRowCountLimiter(4))
          .flatTriples(JellyOptions.smallGeneralized)
          .withNamespaceDeclarations
          .flow
        )
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Triples2NsDecl.encoded(JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.TRIPLES)
          .withLogicalType(LogicalStreamType.FLAT_TRIPLES)
          .withVersion(Constants.protoVersion)
        )
      )
      encoded.size should be (3)
    }
  }

  "flatTripleStreamGrouped" should {
    "encode grouped triples" in {
      val encoded: Seq[RdfStreamFrame] = Source(Triples1.mrl)
        .grouped(2)
        .via(EncoderFlow.builder
          .flatTriplesGrouped(JellyOptions.smallGeneralized)
          .flow
        )
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Triples1.encoded(JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.TRIPLES)
          .withLogicalType(LogicalStreamType.FLAT_TRIPLES)
          .withVersion(Constants.protoVersion_1_0_x)
        )
      )
      encoded.size should be (2)
      encoded.head.rows.count(_.row.isTriple) should be (2)
      encoded(1).rows.count(_.row.isTriple) should be (2)
    }

    "encode grouped triples with max row count" in {
      val encoded: Seq[RdfStreamFrame] = Source(Triples1.mrl)
        .grouped(2)
        .via(EncoderFlow.builder
          .withLimiter(StreamRowCountLimiter(4))
          .flatTriplesGrouped(JellyOptions.smallGeneralized)
          .flow
        )
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Triples1.encoded(JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.TRIPLES)
          .withLogicalType(LogicalStreamType.FLAT_TRIPLES)
          .withVersion(Constants.protoVersion_1_0_x)
        )
      )
      encoded.size should be (5)
      encoded.head.rows.count(_.row.isTriple) should be (0)
      encoded(1).rows.count(_.row.isTriple) should be (1)
      encoded(2).rows.count(_.row.isTriple) should be (1)
      encoded(3).rows.count(_.row.isTriple) should be (1)
      encoded(4).rows.count(_.row.isTriple) should be (1)
    }
  }

  "graphStream" should {
    "encode graphs" in {
      val encoded: Seq[RdfStreamFrame] = Source(Triples1.mrl)
        .grouped(2)
        .via(EncoderFlow.builder.graphs(JellyOptions.smallGeneralized).flow)
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Triples1.encoded(JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.TRIPLES)
          .withLogicalType(LogicalStreamType.GRAPHS)
          .withVersion(Constants.protoVersion_1_0_x)
        )
      )
      encoded.size should be(2)
      encoded.head.rows.count(_.row.isTriple) should be(2)
      encoded(1).rows.count(_.row.isTriple) should be(2)
    }
  }

  "flatQuadStream" should {
    "encode quads" in {
      val encoded: Seq[RdfStreamFrame] = Source(Quads1.mrl)
        .via(EncoderFlow.builder
          .withLimiter(StreamRowCountLimiter(1000))
          .flatQuads(JellyOptions.smallGeneralized)
          .flow
        )
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Quads1.encoded(JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.QUADS)
          .withLogicalType(LogicalStreamType.FLAT_QUADS)
          .withVersion(Constants.protoVersion_1_0_x)
        )
      )
      encoded.size should be (1)
    }
  }

  "flatQuadStreamGrouped" should {
    "encode grouped quads" in {
      val encoded: Seq[RdfStreamFrame] = Source(Quads1.mrl)
        .grouped(2)
        .via(EncoderFlow.builder
          .flatQuadsGrouped(JellyOptions.smallGeneralized)
          .flow
        )
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Quads1.encoded(JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.QUADS)
          .withLogicalType(LogicalStreamType.FLAT_QUADS)
          .withVersion(Constants.protoVersion_1_0_x)
        )
      )
      encoded.size should be (2)
      encoded.head.rows.count(_.row.isQuad) should be (2)
      encoded(1).rows.count(_.row.isQuad) should be (2)
    }
  }

  "datasetStreamFromQuads" should {
    "encode datasets" in {
      val encoded: Seq[RdfStreamFrame] = Source(Quads1.mrl)
        .grouped(2)
        .via(EncoderFlow.builder.datasetsFromQuads(JellyOptions.smallGeneralized).flow)
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Quads1.encoded(JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.QUADS)
          .withLogicalType(LogicalStreamType.DATASETS)
          .withVersion(Constants.protoVersion_1_0_x)
        )
      )
      encoded.size should be(2)
      encoded.head.rows.count(_.row.isQuad) should be(2)
      encoded(1).rows.count(_.row.isQuad) should be(2)
    }
  }

  "namedGraphStream" should {
    "encode named graphs" in {
      val encoded: Seq[RdfStreamFrame] = Source(Graphs1.mrl)
        .via(EncoderFlow.builder.namedGraphs(JellyOptions.smallGeneralized).flow)
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Graphs1.encoded(JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.GRAPHS)
          .withLogicalType(LogicalStreamType.NAMED_GRAPHS)
          .withVersion(Constants.protoVersion_1_0_x)
        )
      )
      encoded.size should be (2)
    }

    "encode named graphs with max row count" in {
      val encoded: Seq[RdfStreamFrame] = Source(Graphs1.mrl)
        .via(EncoderFlow.builder
          .withLimiter(StreamRowCountLimiter(4))
          .namedGraphs(JellyOptions.smallGeneralized)
          .flow
        )
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Graphs1.encoded(JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.GRAPHS)
          .withLogicalType(LogicalStreamType.NAMED_GRAPHS)
          .withVersion(Constants.protoVersion_1_0_x)
        )
      )
      // 1 additional split due to split by graph
      encoded.size should be (5)
    }
  }

  "datasetStream" should {
    "encode datasets" in {
      val encoded: Seq[RdfStreamFrame] = Source(Graphs1.mrl)
        .grouped(2)
        .via(EncoderFlow.builder.datasets(JellyOptions.smallGeneralized).flow)
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Graphs1.encoded(JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.GRAPHS)
          .withLogicalType(LogicalStreamType.DATASETS)
          .withVersion(Constants.protoVersion_1_0_x)
        )
      )
      encoded.size should be (1)
    }

    "encode datasets with max row count" in {
      val encoded: Seq[RdfStreamFrame] = Source(Graphs1.mrl)
        .grouped(2)
        .via(EncoderFlow.builder
          .withLimiter(StreamRowCountLimiter(4))
          .datasets(JellyOptions.smallGeneralized)
          .flow
        )
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Graphs1.encoded(JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.GRAPHS)
          .withLogicalType(LogicalStreamType.DATASETS)
          .withVersion(Constants.protoVersion_1_0_x)
        )
      )
      encoded.size should be (4)
    }
  }
