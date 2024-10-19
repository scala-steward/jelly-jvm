package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.proto.v1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

class IoUtilsSpec extends AnyWordSpec, Matchers:
  private val frameLarge = RdfStreamFrame(Seq(
    RdfStreamRow(RdfStreamRow.Row.Name(
      RdfNameEntry(1, "name name name name")
    ))
  ))
  private val frameSize10 = RdfStreamFrame(Seq(
    RdfStreamRow(RdfStreamRow.Row.Name(
      RdfNameEntry(0, "name")
    ))
  ))
  private val frameOptionsSize10 = RdfStreamFrame(Seq(
    RdfStreamRow(RdfStreamRow.Row.Options(
      RdfStreamOptions(streamName = "name12")
    ))
  ))

  "IoUtils" should {
    "guessDelimiting" when {
      "input stream is a non-delimited Jelly message (size >10)" in {
        val bytes = frameLarge.toByteArray
        bytes(0) shouldBe 0x0A
        bytes(1) should not be 0x0A

        val in = new ByteArrayInputStream(bytes)
        val (isDelimited, newIn) = IoUtils.guessDelimiting(in)
        isDelimited shouldBe false
        newIn.readAllBytes() shouldBe bytes
      }

      "input stream is a delimited Jelly message (size >10)" in {
        val os = ByteArrayOutputStream()
        frameLarge.writeDelimitedTo(os)
        val bytes = os.toByteArray
        bytes(0) should not be 0x0A
        bytes(1) shouldBe 0x0A

        val in = new ByteArrayInputStream(bytes)
        val (isDelimited, newIn) = IoUtils.guessDelimiting(in)
        isDelimited shouldBe true
        newIn.readAllBytes() shouldBe bytes
      }

      "input stream is a non-delimited Jelly message (size=10)" in {
        val bytes = frameSize10.toByteArray
        bytes.size shouldBe 10
        bytes(0) shouldBe 0x0A
        bytes(1) should not be 0x0A

        val in = new ByteArrayInputStream(bytes)
        val (isDelimited, newIn) = IoUtils.guessDelimiting(in)
        isDelimited shouldBe false
        newIn.readAllBytes() shouldBe bytes
      }

      "input stream is a delimited Jelly message (size=10)" in {
        val os = ByteArrayOutputStream()
        frameSize10.writeDelimitedTo(os)
        val bytes = os.toByteArray
        bytes.size shouldBe 11
        bytes(0) shouldBe 0x0A
        bytes(1) shouldBe 0x0A
        bytes(2) should not be 0x0A

        val in = new ByteArrayInputStream(bytes)
        val (isDelimited, newIn) = IoUtils.guessDelimiting(in)
        isDelimited shouldBe true
        newIn.readAllBytes() shouldBe bytes
      }

      "input stream is a non-delimited Jelly message (options size =10)" in {
        frameOptionsSize10.rows(0).toByteArray.size shouldBe 10
        val bytes = frameOptionsSize10.toByteArray
        bytes(0) shouldBe 0x0A
        bytes(1) shouldBe 0x0A
        bytes(2) shouldBe 0x0A

        val in = new ByteArrayInputStream(bytes)
        val (isDelimited, newIn) = IoUtils.guessDelimiting(in)
        isDelimited shouldBe false
        newIn.readAllBytes() shouldBe bytes
      }

      "input stream is a delimited Jelly message (options size =10)" in {
        val os = ByteArrayOutputStream()
        frameOptionsSize10.writeDelimitedTo(os)
        val bytes = os.toByteArray
        bytes(0) should not be 0x0A
        bytes(1) shouldBe 0x0A
        bytes(2) shouldBe 0x0A
        bytes(3) shouldBe 0x0A

        val in = new ByteArrayInputStream(bytes)
        val (isDelimited, newIn) = IoUtils.guessDelimiting(in)
        isDelimited shouldBe true
        newIn.readAllBytes() shouldBe bytes
      }
    }
  }
