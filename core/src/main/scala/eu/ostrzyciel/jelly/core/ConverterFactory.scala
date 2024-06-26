package eu.ostrzyciel.jelly.core

import ProtoDecoderImpl.*
import eu.ostrzyciel.jelly.core.proto.v1.{LogicalStreamType, RdfStreamOptions}

import scala.reflect.ClassTag

/**
 * "Main" trait to be implemented by RDF conversion modules (e.g., for Jena and RDF4J).
 * Exposes factory methods for building protobuf encoders and decoders.
 *
 * This should typically be implemented as an object. You should also provide a package-scoped given for your
 * implementation so that users can easily make use of the connector in the stream package.
 *
 * @tparam TEncoder Implementation of [[ProtoEncoder]] for a given RDF library.
 * @tparam TDecConv Implementation of [[ProtoDecoderConverter]] for a given RDF library.
 * @tparam TNode Type of RDF nodes in the RDF library
 * @tparam TDatatype Type of RDF datatypes in the RDF library
 * @tparam TTriple Type of triple statements in the RDF library.
 * @tparam TQuad Type of quad statements in the RDF library.
 */
trait ConverterFactory[
  +TEncoder <: ProtoEncoder[TNode, TTriple, TQuad, ?],
  +TDecConv <: ProtoDecoderConverter[TNode, TDatatype, TTriple, TQuad],
  TNode, TDatatype : ClassTag, TTriple, TQuad
]:
  def decoderConverter: TDecConv

  /**
   * Create a new [[TriplesDecoder]].
   * @return
   */
  final def triplesDecoder(expLogicalType: Option[LogicalStreamType]): 
  TriplesDecoder[TNode, TDatatype, TTriple, TQuad] =
    new TriplesDecoder(decoderConverter, expLogicalType)

  /**
   * Create a new [[QuadsDecoder]].
   * @return
   */
  final def quadsDecoder(expLogicalType: Option[LogicalStreamType]): 
  QuadsDecoder[TNode, TDatatype, TTriple, TQuad] =
    new QuadsDecoder(decoderConverter, expLogicalType)

  /**
   * Create a new [[GraphsAsQuadsDecoder]].
   * @return
   */
  final def graphsAsQuadsDecoder(expLogicalType: Option[LogicalStreamType]): 
  GraphsAsQuadsDecoder[TNode, TDatatype, TTriple, TQuad] =
    new GraphsAsQuadsDecoder(decoderConverter, expLogicalType)

  /**
   * Create a new [[GraphsDecoder]].
   * @return
   */
  final def graphsDecoder(expLogicalType: Option[LogicalStreamType]): 
  GraphsDecoder[TNode, TDatatype, TTriple, TQuad] =
    new GraphsDecoder(decoderConverter, expLogicalType)

  /**
   * Create a new [[AnyStatementDecoder]].
   * @return
   */
  final def anyStatementDecoder: AnyStatementDecoder[TNode, TDatatype, TTriple, TQuad] =
    new AnyStatementDecoder(decoderConverter)

  /**
   * Create a new [[ProtoEncoder]].
   * @param options Jelly serialization options.
   * @return
   */
  def encoder(options: RdfStreamOptions): TEncoder
