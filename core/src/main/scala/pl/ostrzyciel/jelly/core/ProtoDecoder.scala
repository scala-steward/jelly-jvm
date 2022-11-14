package pl.ostrzyciel.jelly.core

import pl.ostrzyciel.jelly.core.proto.*

import scala.annotation.tailrec
import scala.reflect.ClassTag

/**
 * Stateful decoder of a protobuf RDF stream.
 */
abstract class ProtoDecoder[TNode >: Null <: AnyRef, TDatatype : ClassTag, TTriple, TQuad]:
  final type TripleOrQuad = TTriple | TQuad

  // Methods to implement
  protected def makeSimpleLiteral(lex: String): TNode
  protected def makeLangLiteral(lex: String, lang: String): TNode
  protected def makeDtLiteral(lex: String, dt: TDatatype): TNode
  protected def makeDatatype(dt: String): TDatatype
  protected def makeBlankNode(label: String): TNode
  protected def makeIriNode(iri: String): TNode
  protected def makeTripleNode(s: TNode, p: TNode, o: TNode): TNode
  protected def makeTriple(s: TNode, p: TNode, o: TNode): TTriple
  protected def makeQuad(s: TNode, p: TNode, o: TNode, g: TNode): TQuad

  private var streamOpt: Option[JellyOptions] = None
  private lazy val nameDecoder = new NameDecoder(streamOpt getOrElse JellyOptions())
  private lazy val dtLookup = new DecoderLookup[TDatatype](streamOpt.map(o => o.maxDatatypeTableSize) getOrElse 20)

  private val lastSubject: LastNodeHolder[TNode] = new LastNodeHolder()
  private val lastPredicate: LastNodeHolder[TNode] = new LastNodeHolder()
  private val lastObject: LastNodeHolder[TNode] = new LastNodeHolder()
  private val lastGraph: LastNodeHolder[TNode] = new LastNodeHolder()

  private def convertTerm(term: RdfTerm): TNode = term.term match
    case RdfTerm.Term.Iri(iri) =>
      makeIriNode(nameDecoder.decode(iri))
    case RdfTerm.Term.Bnode(bnode) =>
      makeBlankNode(bnode.label)
    case RdfTerm.Term.Literal(literal) =>
      literal.literalKind match
        case RdfLiteral.LiteralKind.Simple(_) =>
          makeSimpleLiteral(literal.lex)
        case RdfLiteral.LiteralKind.Langtag(lang) =>
          makeLangLiteral(literal.lex, lang)
        case RdfLiteral.LiteralKind.Datatype(dt) =>
          makeDtLiteral(literal.lex, dtLookup.get(dt.dtId))
        case RdfLiteral.LiteralKind.Empty =>
          throw new RdfProtoDeserializationError("Literal kind not set.")
    case RdfTerm.Term.TripleTerm(triple) =>
      // ! No support for RdfRepeat in quoted triples
      makeTripleNode(
        convertQuotedTerm(triple.s),
        convertQuotedTerm(triple.p),
        convertQuotedTerm(triple.o),
      )
    case _: RdfTerm.Term.Repeat =>
      throw new RdfProtoDeserializationError("Use convertedTermWrapped.")
    case RdfTerm.Term.Empty =>
      throw new RdfProtoDeserializationError("Term kind is not set.")

  private def convertTermWrapped(term: Option[RdfTerm], lastNodeHolder: LastNodeHolder[TNode]): TNode = term match
    case Some(t) => t.term match
      case _: RdfTerm.Term.Repeat =>
        lastNodeHolder.node match
          case null =>
            throw new RdfProtoDeserializationError("RdfRepeat without previous term")
          case n => n
      case _ =>
        val node = convertTerm(t)
        lastNodeHolder.node = node
        node
    case None => throw new RdfProtoDeserializationError("Term not set.")

  private def convertQuotedTerm(term: Option[RdfTerm]): TNode = term match
    case Some(t) => convertTerm(t)
    case None => throw new RdfProtoDeserializationError("Term not set.")

  private def convertTriple(triple: RdfTriple): TTriple =
    makeTriple(
      convertTermWrapped(triple.s, lastSubject),
      convertTermWrapped(triple.p, lastPredicate),
      convertTermWrapped(triple.o, lastObject),
    )

  private def convertQuad(quad: RdfQuad): TQuad =
    makeQuad(
      convertTermWrapped(quad.g, lastGraph),
      convertTermWrapped(quad.s, lastSubject),
      convertTermWrapped(quad.p, lastPredicate),
      convertTermWrapped(quad.o, lastObject),
    )

  final def ingestRow(row: RdfStreamRow): Option[TripleOrQuad] = row.row match
    case RdfStreamRow.Row.Options(opts) =>
      streamOpt = Some(JellyOptions(opts))
      None
    case RdfStreamRow.Row.Name(nameRow) =>
      nameDecoder.updateNames(nameRow)
      None
    case RdfStreamRow.Row.Prefix(prefixRow) =>
      nameDecoder.updatePrefixes(prefixRow)
      None
    case RdfStreamRow.Row.Datatype(dtRow) =>
      dtLookup.update(dtRow.id, makeDatatype(dtRow.value))
      None
    case RdfStreamRow.Row.Triple(triple) =>
      Some(convertTriple(triple))
    case RdfStreamRow.Row.Quad(quad) =>
      Some(convertQuad(quad))
    case RdfStreamRow.Row.Empty =>
      throw new RdfProtoDeserializationError("Row is not set.")