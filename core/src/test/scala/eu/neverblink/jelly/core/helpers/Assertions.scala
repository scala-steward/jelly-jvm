package eu.neverblink.jelly.core.helpers

import eu.neverblink.jelly.core.helpers.Mrl.Statement
import eu.neverblink.jelly.core.helpers.RdfAdapter.extractRdfStreamRow
import eu.neverblink.jelly.core.proto.v1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object Assertions extends AnyWordSpec, Matchers:
  def assertEncoded(observed: Seq[RdfStreamRow], expected: Seq[RdfStreamRow]): Unit =
    for ix <- 0 until observed.size.min(expected.size) do
      withClue(s"Row $ix:") {
        val obsRow = extractRdfStreamRow(observed.applyOrElse(ix, null))
        val expRow = extractRdfStreamRow(expected.applyOrElse(ix, null))
        obsRow should be(expRow)
      }
    observed.size should be(expected.size)

  def assertDecoded(observed: Seq[Statement], expected: Seq[Statement]): Unit =
    for ix <- 0 until observed.size.min(expected.size) do
      withClue(s"Row $ix:") {
        val obsRow = observed.applyOrElse(ix, null)
        val expRow = expected.applyOrElse(ix, null)
        obsRow should be(expRow)
      }
    observed.size should be(expected.size)

  def assertSizesPrecomputed(observed: Seq[RdfStreamRow]): Unit =
    for (row, ix) <- observed.zipWithIndex do
      withClue(s"Row $ix: ${row.getRow}") {
        row.getCachedSize should be > 0
      }
