package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.proto.v1.*
import org.scalatest.Inspectors
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable.ListBuffer

class NameEncoderSpec extends AnyWordSpec, Inspectors, Matchers:
  def smallOptions(prefixTableSize: Int) = RdfStreamOptions(
    maxNameTableSize = 4,
    maxPrefixTableSize = prefixTableSize,
    maxDatatypeTableSize = 8,
  )

  private def getEncoder(prefixTableSize: Int = 8): (NameEncoder, ListBuffer[RdfStreamRow]) =
    val buffer = new ListBuffer[RdfStreamRow]()
    (NameEncoder(smallOptions(prefixTableSize)), buffer)

  "A NameEncoder" when {
    "encoding datatypes" should {
      "add a datatype" in {
        val (encoder, buffer) = getEncoder()
        val dt = encoder.encodeDatatype("dt1", buffer)
        dt.value should be (1)
        buffer.size should be (1)
        val dtEntry = buffer.head.row.datatype.get
        dtEntry.value should be ("dt1")
        dtEntry.id should be (0)
      }

      "add multiple datatypes and reuse existing ones" in {
        val (encoder, buffer) = getEncoder()
        for i <- 1 to 4 do
          val dt = encoder.encodeDatatype(s"dt$i", buffer)
          dt.value should be (i)

        // "dt3" should be reused
        val dt = encoder.encodeDatatype("dt3", buffer)
        dt.value should be (3)

        buffer.size should be (4)
        buffer.map(_.row.datatype.get) should contain only (
          RdfDatatypeEntry(0, "dt1"),
          RdfDatatypeEntry(0, "dt2"),
          RdfDatatypeEntry(0, "dt3"),
          RdfDatatypeEntry(0, "dt4"),
        )
      }

      "add datatypes evicting old ones" in {
        val (encoder, buffer) = getEncoder()
        for i <- 1 to 12 do
          val dt = encoder.encodeDatatype(s"dt$i", buffer)
          // first 4 should be evicted
          dt.value should be ((i - 1) % 8 + 1)

        for i <- 9 to 12 do
          val dt = encoder.encodeDatatype(s"dt$i", buffer)
          dt.value should be (i - 8)

        for i <- 5 to 8 do
          val dt = encoder.encodeDatatype(s"dt$i", buffer)
          dt.value should be (i)

        // 5–8 were used last, so they should be evicted last
        for i <- 13 to 16 do
          val dt = encoder.encodeDatatype(s"dt$i", buffer)
          dt.value should be (i - 12) // 1–4

        buffer.size should be (16)
        val expectedIds = Array.from(
          Iterable.fill(8)(0) ++ Seq(1) ++ Iterable.fill(3)(0) ++ Seq(1) ++ Iterable.fill(3)(0)
        )
        for (r, i) <- buffer.zipWithIndex do
          val dt = r.row.datatype.get
          dt.id should be (expectedIds(i))
          dt.value should be (s"dt${i + 1}")
      }
    }

    "encoding IRIs" should {
      "add a full IRI" in {
        val (encoder, buffer) = getEncoder()
        val iri = encoder.encodeIri("https://test.org/Cake", buffer)
        iri.nameId should be (0)
        iri.prefixId should be (1)

        buffer.size should be (2)
        buffer should contain (RdfStreamRow(RdfStreamRow.Row.Prefix(
          RdfPrefixEntry(id = 0, value = "https://test.org/")
        )))
        buffer should contain (RdfStreamRow(RdfStreamRow.Row.Name(
          RdfNameEntry(id = 0, value = "Cake")
        )))
      }

      "add a prefix-only IRI" in {
        val (encoder, buffer) = getEncoder()
        val iri = encoder.encodeIri("https://test.org/test/", buffer)
        iri.nameId should be (0)
        iri.prefixId should be (1)

        // an empty name entry still has to be allocated
        buffer.size should be (2)
        buffer should contain (RdfStreamRow(RdfStreamRow.Row.Prefix(
          RdfPrefixEntry(id = 0, value = "https://test.org/test/")
        )))
        buffer should contain(RdfStreamRow(RdfStreamRow.Row.Name(
          RdfNameEntry(id = 0, value = "")
        )))
      }

      "add a name-only IRI" in {
        val (encoder, buffer) = getEncoder()
        val iri = encoder.encodeIri("testTestTest", buffer)
        iri.nameId should be (0)
        iri.prefixId should be (1)

        // in the mode with the prefix table enabled, an empty prefix entry still has to be allocated
        buffer.size should be (2)
        buffer should contain(RdfStreamRow(RdfStreamRow.Row.Prefix(
          RdfPrefixEntry(id = 0, value = "")
        )))
        buffer should contain (RdfStreamRow(RdfStreamRow.Row.Name(
          RdfNameEntry(id = 0, value = "testTestTest")
        )))
      }

      "add a full IRI in no-prefix table mode" in {
        val (encoder, buffer) = getEncoder(0)
        val iri = encoder.encodeIri("https://test.org/Cake", buffer)
        iri.nameId should be (0)
        iri.prefixId should be (0)

        // in the no prefix mode, there must be no prefix entries
        buffer.size should be (1)
        buffer should contain (RdfStreamRow(RdfStreamRow.Row.Name(
          RdfNameEntry(id = 0, value = "https://test.org/Cake")
        )))
      }

      "add IRIs while evicting old ones" in {
        val (encoder, buffer) = getEncoder(3)
        val data = Seq(
          // IRI, expected prefix ID, expected name ID
          ("https://test.org/Cake1", 1, 0),
          ("https://test.org#Cake1", 2, 1),
          ("https://test.org/test/Cake1", 3, 1),
          ("https://test.org/Cake2", 1, 0),
          ("https://test.org#Cake2", 2, 2),
          ("https://test.org/other/Cake1", 3, 1),
          ("https://test.org/other/Cake2", 0, 0),
          ("https://test.org/other/Cake3", 0, 0),
          ("https://test.org/other/Cake4", 0, 0),
          ("https://test.org/other/Cake5", 0, 1),
          ("https://test.org#Cake2", 2, 0),
          // prefix "" evicts the previous number #1
          ("Cake2", 1, 2),
        )

        for (sIri, ePrefix, eName) <- data do
          val iri = encoder.encodeIri(sIri, buffer)
          iri.prefixId should be (ePrefix)
          iri.nameId should be (eName)

        val expectedBuffer = Seq(
          // Prefix? (name otherwise), ID, value
          (true, 0, "https://test.org/"),
          (false, 0, "Cake1"),
          (true, 0, "https://test.org#"),
          (true, 0, "https://test.org/test/"),
          (false, 0, "Cake2"),
          (true, 3, "https://test.org/other/"),
          (false, 0, "Cake3"),
          (false, 0, "Cake4"),
          (false, 1, "Cake5"),
          (true, 1, ""),
        )

        buffer.size should be (expectedBuffer.size)
        for ((isPrefix, eId, eVal), row) <- expectedBuffer.zip(buffer) do
          if isPrefix then
            row.row.isPrefix should be (true)
            val prefix = row.row.prefix.get
            prefix.id should be (eId)
            prefix.value should be (eVal)
          else
            row.row.isName should be (true)
            val name = row.row.name.get
            name.id should be (eId)
            name.value should be (eVal)
      }
    }
  }
