package eu.neverblink.jelly.convert.jena.patch;

import static eu.neverblink.jelly.core.proto.v1.patch.PatchStatementType.*;

import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.patch.PatchHandler;
import eu.neverblink.jelly.core.proto.v1.patch.PatchStatementType;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdfpatch.RDFChanges;

/**
 * Patch handler to convert Jena RDFChanges operations to Jelly-Patch operations.
 * <p>
 * This class provides a handler in Jena terms that relays all operations to a Jelly-Patch stream.
 */
@ExperimentalApi
final class JenaToJellyPatchHandler implements RDFChanges {

    private final PatchHandler.AnyPatchHandler<Node> jellyStream;
    private final PatchStatementType statementType;

    JenaToJellyPatchHandler(PatchHandler.AnyPatchHandler<Node> jellyStream, PatchStatementType statementType) {
        this.jellyStream = jellyStream;
        this.statementType = statementType;
    }

    @Override
    public void header(String field, Node value) {
        jellyStream.header(field, value);
    }

    @Override
    public void add(Node g, Node s, Node p, Node o) {
        switch (statementType) {
            case TRIPLES -> jellyStream.addTriple(s, p, o);
            case QUADS -> jellyStream.addQuad(s, p, o, g);
            case UNSPECIFIED -> {
                if (g == null) {
                    jellyStream.addTriple(s, p, o);
                } else {
                    jellyStream.addQuad(s, p, o, g);
                }
            }
            default -> {
                throw new IllegalArgumentException("Unsupported statement type: " + statementType);
            }
        }
    }

    @Override
    public void delete(Node g, Node s, Node p, Node o) {
        switch (statementType) {
            case TRIPLES -> jellyStream.deleteTriple(s, p, o);
            case QUADS -> jellyStream.deleteQuad(s, p, o, g);
            case UNSPECIFIED -> {
                if (g == null) {
                    jellyStream.deleteTriple(s, p, o);
                } else {
                    jellyStream.deleteQuad(s, p, o, g);
                }
            }
            default -> {
                throw new IllegalArgumentException("Unsupported statement type: " + statementType);
            }
        }
    }

    @Override
    public void addPrefix(Node gn, String prefix, String uriStr) {
        jellyStream.addNamespace(prefix, NodeFactory.createURI(uriStr), gn);
    }

    @Override
    public void deletePrefix(Node gn, String prefix) {
        jellyStream.deleteNamespace(prefix, null, gn);
    }

    @Override
    public void txnBegin() {
        jellyStream.transactionStart();
    }

    @Override
    public void txnCommit() {
        jellyStream.transactionCommit();
    }

    @Override
    public void txnAbort() {
        jellyStream.transactionAbort();
    }

    @Override
    public void segment() {
        jellyStream.punctuation();
    }

    @Override
    public void start() {
        // No-op
    }

    @Override
    public void finish() {
        // No-op
    }
}
