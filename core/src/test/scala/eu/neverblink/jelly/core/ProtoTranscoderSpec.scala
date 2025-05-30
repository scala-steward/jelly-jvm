package eu.neverblink.jelly.core

import com.google.protobuf.ByteString
import eu.neverblink.jelly.core.internal.ProtoTranscoderImpl
import eu.neverblink.jelly.core.{JellyConstants, JellyOptions, NamespaceDeclaration, RdfProtoDeserializationError, RdfProtoTranscodingError}
import eu.neverblink.jelly.core.ProtoTestCases.*
import eu.neverblink.jelly.core.helpers.RdfAdapter.*
import eu.neverblink.jelly.core.helpers.{MockConverterFactory, Mrl, ProtoCollector}
import eu.neverblink.jelly.core.proto.v1.*
import org.scalatest.Inspectors
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters.*
import scala.jdk.javaapi.CollectionConverters.asScala
import scala.util.Random

/**
 * Unit tests for the ProtoTranscoder class.
 * See also integration tests: [[eu.ostrzyciel.jelly.integration_tests.CrossTranscodingSpec]]
 */
class ProtoTranscoderSpec extends AnyWordSpec, Inspectors, Matchers:
  def smallOptions(prefixTableSize: Int) = rdfStreamOptions(
    maxNameTableSize = 4,
    maxPrefixTableSize = prefixTableSize,
    maxDatatypeTableSize = 8,
  )

  val testCases: Seq[(String, PhysicalStreamType,
    TestCase[Mrl.Triple | Mrl.Quad | (Mrl.Node, Iterable[Mrl.Triple]) | NamespaceDeclaration]
  )] = Seq(
    ("Triples1", PhysicalStreamType.TRIPLES, Triples1),
    ("Triples2NsDecl", PhysicalStreamType.TRIPLES, Triples2NsDecl),
    ("Quads1", PhysicalStreamType.QUADS, Quads1),
    ("Quads2RepeatDefault", PhysicalStreamType.QUADS, Quads2RepeatDefault),
    ("Graphs1", PhysicalStreamType.GRAPHS, Graphs1),
  )

  "ProtoTranscoder" should {
    "splice two identical streams" when {
      for (caseName, streamType, testCase) <- testCases do
        s"input is $caseName" in {
          val options: RdfStreamOptions = JellyOptions.SMALL_ALL_FEATURES.clone
            .setPhysicalType(streamType)
          val input: RdfStreamFrame = testCase.encodedFull(options, 100).head
          val transcoder = new ProtoTranscoderImpl(null, options)
          // First frame should be returned as is
          val out1 = transcoder.ingestFrame(input)
          out1 shouldEqual input
          // What's more, the rows should be the exact same objects (except the options)
          forAll(asScala(input.getRows).zip(asScala(out1.getRows)).drop(1)) { case (in, out) =>
            in eq out shouldBe true // reference equality
          }

          val out2 = transcoder.ingestFrame(input)
          out2.getRows.size shouldBe < (input.getRows.size)
          // No row in out2 should be an options row or a lookup entry row
          forAll(asScala(out2.getRows)) { (row: RdfStreamRow) =>
            row.hasOptions shouldBe false
            row.hasPrefix shouldBe false
            row.hasName shouldBe false
            row.hasDatatype shouldBe false
          }

          // If there is a row in out2 with same content as in input, it should be the same object
          var identicalRows = 0
          forAll(asScala(input.getRows)) { (row: RdfStreamRow) =>
            val sameRows = asScala(out2.getRows).filter(_ == row)
            if sameRows.nonEmpty then
              forAtLeast(1, sameRows) { (sameRow: RdfStreamRow) =>
                sameRow eq row shouldBe true
                identicalRows += 1
              }
          }
          // Something should be identical
          identicalRows shouldBe > (0)

          // Decode the output
          val collector = ProtoCollector()
          val decoder = MockConverterFactory.anyStatementDecoder(collector, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)
          asScala(out1.getRows).foreach(decoder.ingestRow)
          asScala(out2.getRows).foreach(decoder.ingestRow)

          val statements1 = collector.statements.slice(0, collector.statements.size / 2)
          val statements2 = collector.statements.slice(collector.statements.size / 2, collector.statements.size)
          statements1 shouldEqual statements2
        }
    }

    "splice multiple identical streams" when {
      for (caseName, streamType, testCase) <- testCases do
        s"input is $caseName" in {
          val options: RdfStreamOptions = JellyOptions.SMALL_ALL_FEATURES.clone
            .setPhysicalType(streamType)
          
          val input: RdfStreamFrame = testCase.encodedFull(options, 100).head
          val transcoder = new ProtoTranscoderImpl(null, options)
          val out1 = transcoder.ingestFrame(input)
          var lastOut = out1
          for i <- 1 to 100 do
            val outN = transcoder.ingestFrame(input)
            outN.getRows.size shouldBe < (input.getRows.size)
            // No row in out should be an options row or a lookup entry row
            forAll(asScala(outN.getRows)) { (row: RdfStreamRow) =>
              row.hasOptions shouldBe false
              row.hasPrefix shouldBe false
              row.hasName shouldBe false
              row.hasDatatype shouldBe false
            }
            if i != 1 then
              outN shouldBe lastOut
            lastOut = outN
        }
    }

    "splice multiple different streams" when {
      for seed <- 1 to 20 do
        f"random seed is $seed" in {
          val collector = ProtoCollector()
          val decoder = MockConverterFactory.quadsDecoder(collector, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)
          val options = JellyOptions.SMALL_ALL_FEATURES.clone
            .setPhysicalType(PhysicalStreamType.QUADS)

          val transcoder = new ProtoTranscoderImpl(null, options)
          val possibleCases = Seq(Quads1, Quads2RepeatDefault)
          val random = Random(seed)
          val usedIndices = Array.ofDim[Int](possibleCases.size)

          for i <- 1 to 100 do
            val index = random.nextInt(possibleCases.size)
            usedIndices(index) += 1
            val testCase = possibleCases(index)
            val out = transcoder.ingestFrame(testCase.encodedFull(options, 100).head)

            if usedIndices(index) > 1 then
              // No row in out should be an options row or a lookup entry row
              forAll(asScala(out.getRows)) { (row: RdfStreamRow) =>
                row.hasOptions shouldBe false
                row.hasPrefix shouldBe false
                row.hasName shouldBe false
                row.hasDatatype shouldBe false
              }

            asScala(out.getRows).foreach(decoder.ingestRow)
            collector.statements shouldBe testCase.mrl
            collector.clear()
        }
    }

    "handle named graphs" in {
      val options = JellyOptions.SMALL_STRICT.clone
        .setMaxPrefixTableSize(0)
        .setPhysicalType(PhysicalStreamType.GRAPHS)
        .setVersion(JellyConstants.PROTO_VERSION)

      val input: Seq[RdfStreamRow] = Seq[RdfStreamRow](
        rdfStreamRow(options),
        rdfStreamRow(rdfNameEntry(0, "some IRI")),
        rdfStreamRow(rdfNameEntry(4, "some IRI 2")),
        rdfStreamRow(rdfGraphStart(rdfIri(0, 0))),
        rdfStreamRow(rdfGraphStart(rdfIri(0, 4))),
      )

      val expectedOutput: Seq[RdfStreamRow] = Seq[RdfStreamRow](
        rdfStreamRow(options),
        rdfStreamRow(rdfNameEntry(0, "some IRI")),
        // ID 4 should be remapped to 2
        rdfStreamRow(rdfNameEntry(0, "some IRI 2")),
        rdfStreamRow(rdfGraphStart(rdfIri(0, 0))),
        rdfStreamRow(rdfGraphStart(rdfIri(0, 0))),
      )

      val transcoder = new ProtoTranscoderImpl(null, options)

      input.flatMap(entry => transcoder.ingestRow(entry).asScala) shouldBe expectedOutput
    }

    "remap prefix, name, and datatype IDs" in {
      val options = JellyOptions.SMALL_STRICT.clone
        .setVersion(JellyConstants.PROTO_VERSION)

      val input: Seq[RdfStreamRow] = Seq(
        rdfStreamRow(options),
        rdfStreamRow(rdfNameEntry(4, "some name")),
        rdfStreamRow(rdfPrefixEntry(4, "some prefix")),
        rdfStreamRow(rdfDatatypeEntry(4, "some IRI")),
        rdfStreamRow(rdfTriple(
          rdfTriple(
            rdfIri(4, 4),
            rdfIri(0, 4),
            rdfLiteral("some literal", 4),
          ),
          rdfIri(0, 4),
          rdfLiteral("some literal", 0),
        )),
        rdfStreamRow(rdfTriple(
          rdfTriple("", "", ""),
          rdfIri(0, 4),
          rdfLiteral("some literal", 0),
        )),
      )

      val expectedOutput: Seq[RdfStreamRow] = Seq(
        rdfStreamRow(options),
        rdfStreamRow(rdfNameEntry(0, "some name")),
        rdfStreamRow(rdfPrefixEntry(0, "some prefix")),
        rdfStreamRow(rdfDatatypeEntry(0, "some IRI")),
        rdfStreamRow(rdfTriple(
          rdfTriple(
            rdfIri(1, 0),
            rdfIri(0, 1),
            rdfLiteral("some literal", 1),
          ),
          rdfIri(0, 1),
          rdfLiteral("some literal", 0),
        )),
        rdfStreamRow(rdfTriple(
          rdfTriple("", "", ""),
          rdfIri(0, 1),
          rdfLiteral("some literal", 0),
        )),
      )

      val transcoder = new ProtoTranscoderImpl(null, options)
      val output = input.flatMap(entry => transcoder.ingestRow(entry).asScala)

      output.size shouldBe expectedOutput.size

      for (i <- input.indices) do
        output(i) shouldBe expectedOutput(i)
    }

    "maintain protocol version 1 if input uses it" in {
      val options = JellyOptions.SMALL_STRICT.clone
        .setVersion(JellyConstants.PROTO_VERSION_1_0_X)

      val input = rdfStreamRow(options)
      val transcoder = new ProtoTranscoderImpl(
        null,
        options.clone
          .setVersion(JellyConstants.PROTO_VERSION)
      )

      val output = transcoder.ingestRow(input).asScala
      output.head shouldBe input
    }

    "throw an exception on a null row" in {
      val transcoder = new ProtoTranscoderImpl(null, JellyOptions.SMALL_STRICT)
      val ex = intercept[RdfProtoTranscodingError] {
        transcoder.ingestRow(rdfStreamRow())
      }
      ex.getMessage should include ("Row kind is not set")
    }

    "throw an exception on mismatched physical types if checking is enabled" in {
      val transcoder = new ProtoTranscoderImpl(
        JellyOptions.DEFAULT_SUPPORTED_OPTIONS,
        JellyOptions.SMALL_STRICT.clone
          .setPhysicalType(PhysicalStreamType.TRIPLES)
      )

      val ex = intercept[RdfProtoTranscodingError] {
        transcoder.ingestRow(rdfStreamRow(
          JellyOptions.SMALL_STRICT
            .clone
            .setPhysicalType(PhysicalStreamType.QUADS)
        ))
      }

      ex.getMessage should include ("Input stream has a different physical type than the output")
      ex.getMessage should include ("QUADS")
      ex.getMessage should include ("TRIPLES")
    }

    "not throw an exception on mismatched physical types if checking is disabled" in {
      val transcoder = new ProtoTranscoderImpl(
        null,
        JellyOptions.SMALL_STRICT.clone
          .setPhysicalType(PhysicalStreamType.TRIPLES)
      )

      transcoder.ingestRow(rdfStreamRow(
        JellyOptions.SMALL_STRICT.clone
          .setPhysicalType(PhysicalStreamType.QUADS)
      ))
    }

    "throw an exception on unsupported options if checking is enabled" in {
      val transcoder = new ProtoTranscoderImpl(
        // Mark the prefix table as disabled
        JellyOptions.DEFAULT_SUPPORTED_OPTIONS.clone
          .setMaxPrefixTableSize(0),
        JellyOptions.SMALL_STRICT.clone
          .setPhysicalType(PhysicalStreamType.TRIPLES)
      )

      val ex = intercept[RdfProtoDeserializationError] {
        transcoder.ingestRow(rdfStreamRow(
          JellyOptions.SMALL_STRICT.clone
            .setPhysicalType(PhysicalStreamType.TRIPLES)
        ))
      }

      ex.getMessage should include ("larger than the maximum supported size")
    }

    "throw an exception if the input does not use prefixes but the output does" in {
      val transcoder = new ProtoTranscoderImpl(
        null,
        JellyOptions.SMALL_STRICT.clone
          .setPhysicalType(PhysicalStreamType.TRIPLES)
      )

      val ex = intercept[RdfProtoTranscodingError] {
        transcoder.ingestRow(rdfStreamRow(
          JellyOptions.SMALL_STRICT.clone
            .setPhysicalType(PhysicalStreamType.TRIPLES)
            .setMaxPrefixTableSize(0)
        ))
      }

      ex.getMessage should include ("Output stream uses prefixes, but the input stream does not")
    }

    "accept an input stream with valid options if checking is enabled" in {
      val transcoder = new ProtoTranscoderImpl(
        // Mark the prefix table as disabled
        JellyOptions.DEFAULT_SUPPORTED_OPTIONS.clone
          .setMaxPrefixTableSize(0),
        JellyOptions.SMALL_STRICT.clone
          .setPhysicalType(PhysicalStreamType.TRIPLES)
          .setMaxPrefixTableSize(0),
      )

      val inputOptions = JellyOptions.SMALL_STRICT.clone
        .setPhysicalType(PhysicalStreamType.TRIPLES)
        .setMaxPrefixTableSize(0)

      transcoder.ingestRow(rdfStreamRow(inputOptions))
    }

    "preserve lack of metadata in a frame (1.1.1)" in {
      val transcoder = new ProtoTranscoderImpl(null, JellyOptions.SMALL_STRICT)
      val input = rdfStreamFrame(
        rows = Seq(rdfStreamRow(
          JellyOptions.SMALL_STRICT.clone
            .setVersion(JellyConstants.PROTO_VERSION_1_1_X)
        )),
      )
      val output = transcoder.ingestFrame(input)
      output.getMetadata.size should be (0)
    }

    "preserve metadata in a frame (1.1.1)" in {
      val transcoder = new ProtoTranscoderImpl(null, JellyOptions.SMALL_STRICT)
      val input = rdfStreamFrame(
        rows = Seq(rdfStreamRow(
          JellyOptions.SMALL_STRICT.clone
            .setVersion(JellyConstants.PROTO_VERSION_1_1_X)
        )),
        metadata = Map(
          "key1" -> ByteString.copyFromUtf8("value"),
          "key2" -> ByteString.copyFromUtf8("value2"),
        ),
      )
      val output = transcoder.ingestFrame(input)
      output.getMetadata.size should be (2)
      val map = output.getMetadata.asScala.map(x => (x.getKey, x.getValue)).toMap
      map("key1").toStringUtf8 should be ("value")
      map("key2").toStringUtf8 should be ("value2")
    }
  }
