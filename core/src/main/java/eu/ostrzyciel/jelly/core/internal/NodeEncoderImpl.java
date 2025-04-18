package eu.ostrzyciel.jelly.core.internal;

import eu.ostrzyciel.jelly.core.JellyExceptions;
import eu.ostrzyciel.jelly.core.NodeEncoder;
import eu.ostrzyciel.jelly.core.proto.v1.*;
import java.util.LinkedHashMap;

/**
 * Encodes RDF nodes native to the used RDF library (e.g., Apache Jena, RDF4J) into Jelly's protobuf objects.
 * This class performs a lot of caching to avoid encoding the same node multiple times. It is absolutely NOT
 * thread-safe, and should only be ever used by a single instance of ProtoEncoder.
 *
 * @param <TNode> The type of RDF nodes used by the RDF library.
 */
final class NodeEncoderImpl<TNode> implements NodeEncoder<TNode> {

    /**
     * A cached node that depends on other lookups (RdfIri and RdfLiteral in the datatype variant).
     */
    static final class DependentNode {

        // The actual cached node
        public UniversalTerm encoded;
        // 1: datatypes and IRI names
        // The pointer is the index in the lookup table, the serial is the serial number of the entry.
        // The serial in the lookup table must be equal to the serial here for the entry to be valid.
        public int lookupPointer1;
        public int lookupSerial1;
        // 2: IRI prefixes
        public int lookupPointer2;
        public int lookupSerial2;
    }

    /**
     * A simple LRU cache for already encoded nodes.
     * @param <K> Key type
     * @param <V> Value type
     */
    private static final class NodeCache<K, V> extends LinkedHashMap<K, V> {

        private final int maxSize;

        public NodeCache(int maxSize) {
            this.maxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<K, V> eldest) {
            return size() > maxSize;
        }
    }

    private final int maxPrefixTableSize;
    private int lastIriNameId;
    private int lastIriPrefixId = -1000;

    private final EncoderLookup datatypeLookup;
    private final EncoderLookup prefixLookup;
    private final EncoderLookup nameLookup;

    private final RowBufferAppender bufferAppender;

    // We split the node caches in three – the first two are for nodes that depend on the lookups
    // (IRIs and datatype literals). The third one is for nodes that don't depend on the lookups.
    private final NodeCache<Object, DependentNode> iriNodeCache;
    private final NodeCache<Object, DependentNode> dtLiteralNodeCache;
    private final NodeCache<Object, UniversalTerm> nodeCache;

    // Pre-allocated IRI that has prefixId=0 and nameId=0
    static final RdfIri zeroIri = new RdfIri(0, 0);
    // Pre-allocated IRIs that have prefixId=0
    private final RdfIri[] nameOnlyIris;

    /**
     * Creates a new NodeEncoder.
     * @param prefixTableSize The size of the prefix lookup table
     * @param nameTableSize The size of the name lookup table
     * @param dtTableSize The size of the datatype lookup table
     * @param nodeCacheSize The size of the node cache (for nodes that don't depend on lookups)
     * @param iriNodeCacheSize The size of the IRI dependent node cache (for prefix+name encoding)
     * @param dtLiteralNodeCacheSize The size of the datatype literal dependent node cache
     * @param bufferAppender consumer of the lookup entry rows
     */
    public NodeEncoderImpl(
        int prefixTableSize,
        int nameTableSize,
        int dtTableSize,
        int nodeCacheSize,
        int iriNodeCacheSize,
        int dtLiteralNodeCacheSize,
        RowBufferAppender bufferAppender
    ) {
        datatypeLookup = new EncoderLookup(dtTableSize, true);
        this.maxPrefixTableSize = prefixTableSize;
        if (maxPrefixTableSize > 0) {
            prefixLookup = new EncoderLookup(maxPrefixTableSize, true);
            iriNodeCache = new NodeCache<>(iriNodeCacheSize);
        } else {
            prefixLookup = null;
            iriNodeCache = null;
        }
        nameOnlyIris = new RdfIri[nameTableSize + 1];
        for (int i = 0; i < nameOnlyIris.length; i++) {
            nameOnlyIris[i] = new RdfIri(0, i);
        }
        dtLiteralNodeCache = new NodeCache<>(dtLiteralNodeCacheSize);
        nameLookup = new EncoderLookup(nameTableSize, maxPrefixTableSize > 0);
        nodeCache = new NodeCache<>(nodeCacheSize);
        this.bufferAppender = bufferAppender;
    }

    /**
     * Encodes an IRI using two layers of caching – both for the entire IRI, and the prefix and name tables.
     * @param iri The IRI to encode
     * @return The encoded IRI
     */
    @Override
    public UniversalTerm makeIri(String iri) {
        if (maxPrefixTableSize == 0) {
            // Fast path for no prefixes
            var nameEntry = nameLookup.getOrAddEntry(iri);
            if (nameEntry.newEntry) {
                bufferAppender.appendNameEntry(new RdfNameEntry(nameEntry.setId, iri));
            }
            int nameId = nameEntry.getId;
            if (lastIriNameId + 1 == nameId) {
                lastIriNameId = nameId;
                return zeroIri;
            } else {
                lastIriNameId = nameId;
                return nameOnlyIris[nameId];
            }
        }

        // Slow path, with splitting out the prefix
        var cachedNode = iriNodeCache.computeIfAbsent(iri, k -> new DependentNode());
        // Check if the value is still valid
        if (
            cachedNode.encoded != null &&
            cachedNode.lookupSerial1 == nameLookup.serials[cachedNode.lookupPointer1] &&
            cachedNode.lookupSerial2 == prefixLookup.serials[cachedNode.lookupPointer2]
        ) {
            nameLookup.onAccess(cachedNode.lookupPointer1);
            prefixLookup.onAccess(cachedNode.lookupPointer2);
            return outputIri(cachedNode);
        }

        int i = iri.indexOf('#', 8);
        String prefix;
        String postfix;
        if (i == -1) {
            i = iri.lastIndexOf('/');
            if (i != -1) {
                prefix = iri.substring(0, i + 1);
                postfix = iri.substring(i + 1);
            } else {
                prefix = "";
                postfix = iri;
            }
        } else {
            prefix = iri.substring(0, i + 1);
            postfix = iri.substring(i + 1);
        }

        var prefixEntry = prefixLookup.getOrAddEntry(prefix);
        var nameEntry = nameLookup.getOrAddEntry(postfix);
        if (prefixEntry.newEntry) {
            bufferAppender.appendPrefixEntry(new RdfPrefixEntry(prefixEntry.setId, prefix));
        }
        if (nameEntry.newEntry) {
            bufferAppender.appendNameEntry(new RdfNameEntry(nameEntry.setId, postfix));
        }
        int nameId = nameEntry.getId;
        int prefixId = prefixEntry.getId;
        cachedNode.lookupPointer1 = nameId;
        cachedNode.lookupSerial1 = nameLookup.serials[nameId];
        cachedNode.lookupPointer2 = prefixId;
        cachedNode.lookupSerial2 = prefixLookup.serials[prefixId];
        cachedNode.encoded = new RdfIri(prefixId, nameId);
        return outputIri(cachedNode);
    }

    @Override
    public UniversalTerm makeBlankNode(String label) {
        return nodeCache.computeIfAbsent(label, k -> new RdfTerm.Bnode(label));
    }

    @Override
    public UniversalTerm makeSimpleLiteral(String lex) {
        return nodeCache.computeIfAbsent(lex, k -> new RdfLiteral(lex, RdfLiteral$LiteralKind$Empty$.MODULE$));
    }

    @Override
    public UniversalTerm makeLangLiteral(TNode lit, String lex, String lang) {
        return nodeCache.computeIfAbsent(lit, k -> new RdfLiteral(lex, new RdfLiteral$LiteralKind$Langtag(lang)));
    }

    /**
     * Encodes a datatype literal using two layers of caching – both for the entire literal, and the datatype name.
     * @param key The literal key (the unencoded literal node)
     * @param lex The lexical form of the literal
     * @param datatypeName The name of the datatype
     * @return The encoded literal
     */
    @Override
    public UniversalTerm makeDtLiteral(TNode key, String lex, String datatypeName) {
        if (datatypeLookup.size == 0) {
            throw JellyExceptions.rdfProtoSerializationError(
                "Datatype literals cannot be " +
                "encoded when the datatype table is disabled. Set the datatype table size " +
                "to a positive value."
            );
        }
        var cachedNode = dtLiteralNodeCache.computeIfAbsent(key, k -> new DependentNode());
        // Check if the value is still valid
        if (
            cachedNode.encoded != null && cachedNode.lookupSerial1 == datatypeLookup.serials[cachedNode.lookupPointer1]
        ) {
            datatypeLookup.onAccess(cachedNode.lookupPointer1);
            return cachedNode.encoded;
        }

        // The node is not encoded, but we may already have the datatype encoded
        var dtEntry = datatypeLookup.getOrAddEntry(datatypeName);
        if (dtEntry.newEntry) {
            bufferAppender.appendDatatypeEntry(new RdfDatatypeEntry(dtEntry.setId, datatypeName));
        }
        int dtId = dtEntry.getId;
        cachedNode.lookupPointer1 = dtId;
        cachedNode.lookupSerial1 = datatypeLookup.serials[dtId];
        cachedNode.encoded = new RdfLiteral(lex, new RdfLiteral$LiteralKind$Datatype(dtId));

        return cachedNode.encoded;
    }

    @Override
    public SpoTerm makeQuotedTriple(SpoTerm s, SpoTerm p, SpoTerm o) {
        return new RdfTriple(s, p, o);
    }

    /**
     * Helper function to output an IRI from a cached node using same-prefix and next-name optimizations.
     * @param cachedNode The cached node
     * @return The encoded IRI
     */
    private UniversalTerm outputIri(DependentNode cachedNode) {
        int nameId = cachedNode.lookupPointer1;
        int prefixId = cachedNode.lookupPointer2;
        if (lastIriPrefixId == prefixId) {
            if (lastIriNameId + 1 == nameId) {
                lastIriNameId = nameId;
                return zeroIri;
            } else {
                lastIriNameId = nameId;
                return nameOnlyIris[nameId];
            }
        } else {
            lastIriPrefixId = prefixId;
            if (lastIriNameId + 1 == nameId) {
                lastIriNameId = nameId;
                return new RdfIri(prefixId, 0);
            } else {
                lastIriNameId = nameId;
                return cachedNode.encoded;
            }
        }
    }
}
