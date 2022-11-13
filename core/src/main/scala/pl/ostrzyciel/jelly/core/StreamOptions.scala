package pl.ostrzyciel.jelly.core

import pl.ostrzyciel.jelly.core.proto.RdfStreamOptions

object StreamOptions:
  def apply(opt: RdfStreamOptions): StreamOptions =
    StreamOptions(
      maxNameTableSize = opt.maxNameTableSize,
      maxPrefixTableSize = opt.maxPrefixTableSize,
      maxDatatypeTableSize = opt.maxDatatypeTableSize,
      useRepeat = opt.useRepeat,
    )

/**
 * Represents the compression options for a protobuf RDF stream.
 * @param maxNameTableSize maximum size of the name table
 * @param maxPrefixTableSize maximum size of the prefix table
 * @param maxDatatypeTableSize maximum size of the datatype table
 * @param useRepeat whether or not to use RDF_REPEAT terms
 */
final case class StreamOptions(maxNameTableSize: Int = 4000, maxPrefixTableSize: Int = 150,
                               maxDatatypeTableSize: Int = 32, useRepeat: Boolean = true):
  /**
   * @return a stream options row to be included as a header in the stream
   */
  def toProto: RdfStreamOptions =
    RdfStreamOptions(
      maxNameTableSize = maxNameTableSize,
      maxPrefixTableSize = maxPrefixTableSize,
      maxDatatypeTableSize = maxDatatypeTableSize,
      useRepeat = useRepeat,
    )
