package eu.ostrzyciel.jelly.integration_tests.rdf.io

import eu.ostrzyciel.jelly.convert.jena.traits.JenaTest
import eu.ostrzyciel.jelly.core.*
import eu.ostrzyciel.jelly.core.proto.v1.*
import eu.ostrzyciel.jelly.integration_tests.rdf.TestCases
import eu.ostrzyciel.jelly.integration_tests.util.Measure
import org.apache.pekko.actor.ActorSystem
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File, FileInputStream}

/**
 * Tests for IO ser/des (Jena RIOT, Jena RIOT streaming, RDF4J Rio, and semi-reactive IO over Pekko Streams).
 */
class IoSerDesSpec extends AnyWordSpec, Matchers, ScalaFutures, JenaTest:
  given ActorSystem = ActorSystem("test")

  val presets: Seq[(Option[RdfStreamOptions], Int, String)] = Seq(
    (Some(JellyOptions.smallGeneralized), 1, "small generalized"),
    (Some(JellyOptions.smallRdfStar), 1_000_000, "small RDF-star"),
    (Some(JellyOptions.smallStrict), 30, "small strict"),
    (Some(JellyOptions.smallAllFeatures), 13, "small all features"),
    (Some(JellyOptions.bigGeneralized), 256, "big generalized"),
    (Some(JellyOptions.bigRdfStar), 10_000, "big RDF-star"),
    (Some(JellyOptions.bigStrict), 3, "big strict"),
    (Some(JellyOptions.bigAllFeatures), 2, "big all features"),
    (None, 10, "no options"),
  )

  val presetsUnsupported: Seq[(RdfStreamOptions, RdfStreamOptions, String)] = Seq(
    (
      JellyOptions.smallGeneralized,
      JellyOptions.defaultSupportedOptions.withGeneralizedStatements(false),
      "generalized statements unsupported"
    ),
    (
      JellyOptions.smallRdfStar,
      JellyOptions.defaultSupportedOptions.withRdfStar(false),
      "RDF-star unsupported"
    ),
    (
      JellyOptions.smallStrict,
      JellyOptions.defaultSupportedOptions.withMaxNameTableSize(
        JellyOptions.smallStrict.maxNameTableSize - 5
      ),
      "supported name table size too small"
    ),
    (
      JellyOptions.smallStrict,
      JellyOptions.defaultSupportedOptions.withMaxPrefixTableSize(
        JellyOptions.smallStrict.maxPrefixTableSize - 5
      ),
      "supported prefix table size too small"
    ),
    (
      JellyOptions.smallStrict,
      JellyOptions.defaultSupportedOptions.withMaxDatatypeTableSize(
        JellyOptions.smallStrict.maxDatatypeTableSize - 5
      ),
      "supported datatype table size too small"
    ),
    (
      JellyOptions.smallStrict,
      JellyOptions.defaultSupportedOptions.withVersion(JellyOptions.smallStrict.version - 1),
      "unsupported version"
    )
  )

  private def checkStreamOptions(bytes: Array[Byte], expectedType: String, expectedOpt: Option[RdfStreamOptions]) =
    val expOpt = expectedOpt.getOrElse(JellyOptions.smallAllFeatures)
    val frame = RdfStreamFrame.parseDelimitedFrom(new ByteArrayInputStream(bytes)).get
    frame.rows.size should be > 0
    frame.rows.head.row.isOptions should be (true)
    val options = frame.rows.head.row.options
    if expectedType == "triples" then
      options.physicalType should be (PhysicalStreamType.TRIPLES)
      options.logicalType should be (LogicalStreamType.FLAT_TRIPLES)
    else if expectedType == "quads" then
      options.physicalType should be (PhysicalStreamType.QUADS)
      options.logicalType should be (LogicalStreamType.FLAT_QUADS)
    options.generalizedStatements should be (expOpt.generalizedStatements)
    options.rdfStar should be (expOpt.rdfStar)
    options.maxNameTableSize should be (expOpt.maxNameTableSize)
    options.maxPrefixTableSize should be (expOpt.maxPrefixTableSize)
    options.maxDatatypeTableSize should be (expOpt.maxDatatypeTableSize)
    options.version should be (Constants.protoVersion_1_0_x)

  /**
   * Check if a given Jelly implementation supports the given options (RDF-star and gen. statements).
   */
  private def checkImplOptSupport(impl: NativeSerDes[?, ?], opt: Option[RdfStreamOptions]) =
    (if opt.isEmpty || opt.get.rdfStar then impl.supportsRdfStar else true) &&
    (if opt.isEmpty || opt.get.generalizedStatements then impl.supportsGeneralizedStatements else true)


  runTest(JenaSerDes, JenaSerDes)
  runTest(JenaSerDes, JenaStreamSerDes)
  runTest(JenaSerDes, Rdf4jSerDes)
  runTest(JenaSerDes, Rdf4jReactiveSerDes())

  runTest(JenaStreamSerDes, JenaSerDes)
  runTest(JenaStreamSerDes, JenaStreamSerDes)
  runTest(JenaStreamSerDes, Rdf4jSerDes)
  runTest(JenaStreamSerDes, Rdf4jReactiveSerDes())

  runTest(Rdf4jSerDes, JenaSerDes)
  runTest(Rdf4jSerDes, JenaStreamSerDes)
  runTest(Rdf4jSerDes, Rdf4jSerDes)
  runTest(Rdf4jSerDes, Rdf4jReactiveSerDes())

  runTest(Rdf4jReactiveSerDes(), JenaSerDes)
  runTest(Rdf4jReactiveSerDes(), JenaStreamSerDes)
  runTest(Rdf4jReactiveSerDes(), Rdf4jSerDes)
  runTest(Rdf4jReactiveSerDes(), Rdf4jReactiveSerDes())

  // the Jena reactive implementation only has a serializer
  runTest(JenaReactiveSerDes(), JenaSerDes)
  runTest(JenaReactiveSerDes(), JenaStreamSerDes)
  runTest(JenaReactiveSerDes(), Rdf4jSerDes)
  runTest(JenaReactiveSerDes(), Rdf4jReactiveSerDes())

  // Titanium as serializer
  runTest(TitaniumSerDes, TitaniumSerDes)
  runTest(TitaniumSerDes, JenaSerDes)
  runTest(TitaniumSerDes, JenaStreamSerDes)
  runTest(TitaniumSerDes, Rdf4jSerDes)
  runTest(TitaniumSerDes, Rdf4jReactiveSerDes())
  runTest(TitaniumSerDes, JenaReactiveSerDes())

  // Titanium as deserializer
  runTest(JenaSerDes, TitaniumSerDes)
  runTest(JenaStreamSerDes, TitaniumSerDes)
  runTest(Rdf4jSerDes, TitaniumSerDes)
  runTest(Rdf4jReactiveSerDes(), TitaniumSerDes)
  runTest(JenaReactiveSerDes(), TitaniumSerDes)

  private def runTest[TMSer : Measure, TDSer : Measure, TMDes : Measure, TDDes : Measure](
    ser: NativeSerDes[TMSer, TDSer],
    des: NativeSerDes[TMDes, TDDes],
  ) =
    f"${ser.name} serializer + ${des.name} deserializer" should {
      for (encOptions, decOptions, presetName) <- presetsUnsupported.filter(
        p => checkImplOptSupport(ser, Some(p._1))
      ) do
        for (name, file) <- TestCases.triples.filter(
          f => ser.supportsRdfStar && des.supportsRdfStar || !f._1.contains("star")
        ) do
        s"not accept unsupported options (file $name, $presetName)" in {
          val model = ser.readTriplesW3C(FileInputStream(file))
          val originalSize = summon[Measure[TMSer]].size(model)
          originalSize should be > 0L

          val os = ByteArrayOutputStream()
          ser.writeTriplesJelly(os, model, Some(encOptions), 100)
          os.flush()
          os.close()
          val data = os.toByteArray
          data.size should be > 0

          intercept[java.util.concurrent.ExecutionException | RdfProtoDeserializationError] {
            des.readTriplesJelly(ByteArrayInputStream(data), Some(decOptions))
          }
        }

      for (preset, size, presetName) <- presets.filter(
        p => checkImplOptSupport(ser, p._1) && checkImplOptSupport(des, p._1)
      ) do
        for (name, file) <- TestCases.triples.filter(
          f => ser.supportsRdfStar && des.supportsRdfStar || !f._1.contains("star")
        ) do
          s"ser/des file $name with preset $presetName, frame size $size" in {
            val model = ser.readTriplesW3C(FileInputStream(file))
            val originalSize = summon[Measure[TMSer]].size(model)
            originalSize should be > 0L

            val os = ByteArrayOutputStream()
            ser.writeTriplesJelly(os, model, preset, size)
            os.flush()
            os.close()
            val data = os.toByteArray
            data.size should be > 0
            // In case we are leaving the default settings, RDF4J has no way of knowing if the data is
            // triples or quads, so it's going to default to quads.
            // Titanium just always does quads.
            val mayBeQuads = ser.name == "RDF4J" && preset.isEmpty || ser.name == "Titanium"
            checkStreamOptions(data, if mayBeQuads then "quads" else "triples", preset)

            // Do not test this if we are encoding with RDF4J with default settings with the streaming Jena parser,
            // because the parser will just discard any quads completely.
            // Funnily enough, this is not the case with the classic Jena parser, which forces quads in the
            // default graph to be triples. Fun.
            if !(mayBeQuads && des.name == "Jena (StreamRDF)") then
              val model2 = des.readTriplesJelly(ByteArrayInputStream(data), None)
              val deserializedSize = summon[Measure[TMDes]].size(model2)
              // Add -1 to account for the different statement counting of RDF4J and Jena
              deserializedSize should be <= originalSize
              deserializedSize should be >= originalSize - 1
          }

        for (name, file) <- TestCases.quads do
          s"ser/des file $name with preset $presetName, frame size $size" in {
            val ds = ser.readQuadsW3C(FileInputStream(file))
            val originalSize = summon[Measure[TDSer]].size(ds)
            originalSize should be > 0L

            val os = ByteArrayOutputStream()
            ser.writeQuadsJelly(os, ds, preset, size)
            os.flush()
            os.close()
            val data = os.toByteArray
            data.size should be > 0
            checkStreamOptions(data, "quads", preset)

            val ds2 = des.readQuadsJelly(ByteArrayInputStream(data), None)
            val deserializedSize = summon[Measure[TDDes]].size(ds2)
            // Add -2 to account for the different statement counting of RDF4J and Jena
            deserializedSize should be <= originalSize
            deserializedSize should be >= originalSize - 2
          }
    }
