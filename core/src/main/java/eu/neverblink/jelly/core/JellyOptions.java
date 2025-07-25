package eu.neverblink.jelly.core;

import static eu.neverblink.jelly.core.internal.BaseJellyOptions.*;

import eu.neverblink.jelly.core.proto.v1.LogicalStreamType;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import eu.neverblink.jelly.core.utils.LogicalStreamTypeUtils;

/**
 * A collection of convenient streaming option presets.
 * None of the presets specifies the stream type – do that with the .clone().setPhysicalType() method.
 */
public class JellyOptions {

    private JellyOptions() {}

    /**
     * "Big" preset suitable for high-volume streams and larger machines.
     * Does not allow generalized RDF statements.
     */
    public static final RdfStreamOptions BIG_STRICT = RdfStreamOptions.newInstance()
        .setMaxNameTableSize(BIG_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(BIG_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(BIG_DT_TABLE_SIZE);

    /**
     * "Big" preset suitable for high-volume streams and larger machines.
     * Allows generalized RDF statements.
     */
    public static final RdfStreamOptions BIG_GENERALIZED = RdfStreamOptions.newInstance()
        .setMaxNameTableSize(BIG_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(BIG_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(BIG_DT_TABLE_SIZE)
        .setGeneralizedStatements(true);

    /**
     * "Big" preset suitable for high-volume streams and larger machines.
     * Allows RDF-star statements.
     */
    public static final RdfStreamOptions BIG_RDF_STAR = RdfStreamOptions.newInstance()
        .setMaxNameTableSize(BIG_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(BIG_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(BIG_DT_TABLE_SIZE)
        .setRdfStar(true);

    /**
     * "Big" preset suitable for high-volume streams and larger machines.
     * Allows all protocol features (including generalized RDF statements and RDF-star statements).
     */
    public static final RdfStreamOptions BIG_ALL_FEATURES = RdfStreamOptions.newInstance()
        .setMaxNameTableSize(BIG_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(BIG_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(BIG_DT_TABLE_SIZE)
        .setGeneralizedStatements(true)
        .setRdfStar(true);

    /**
     * "Small" preset suitable for low-volume streams and smaller machines.
     * Does not allow generalized RDF statements.
     */
    public static final RdfStreamOptions SMALL_STRICT = RdfStreamOptions.newInstance()
        .setMaxNameTableSize(SMALL_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(SMALL_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(SMALL_DT_TABLE_SIZE);

    /**
     * "Small" preset suitable for low-volume streams and smaller machines.
     * Allows generalized RDF statements.
     */
    public static final RdfStreamOptions SMALL_GENERALIZED = RdfStreamOptions.newInstance()
        .setMaxNameTableSize(SMALL_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(SMALL_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(SMALL_DT_TABLE_SIZE)
        .setGeneralizedStatements(true);
    /**
     * "Small" preset suitable for low-volume streams and smaller machines.
     * Allows RDF-star statements.
     */
    public static final RdfStreamOptions SMALL_RDF_STAR = RdfStreamOptions.newInstance()
        .setMaxNameTableSize(SMALL_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(SMALL_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(SMALL_DT_TABLE_SIZE)
        .setRdfStar(true);

    /**
     * "Small" preset suitable for low-volume streams and smaller machines.
     * Allows all protocol features (including generalized RDF statements and RDF-star statements).
     */
    public static final RdfStreamOptions SMALL_ALL_FEATURES = RdfStreamOptions.newInstance()
        .setMaxNameTableSize(SMALL_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(SMALL_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(SMALL_DT_TABLE_SIZE)
        .setGeneralizedStatements(true)
        .setRdfStar(true);

    /**
     * Default maximum supported options for Jelly decoders.
     * <p>
     * This means that by default Jelly-JVM will refuse to read streams that exceed these limits (e.g., with a
     * name lookup table larger than 4096 entries).
     * <p>
     * To change these defaults, you should pass a different RdfStreamOptions object to the decoder.
     * You should use this method to get the default options and then modify them as needed.
     * For example, to disable RDF-star support, you can do this:
     * <code>
     * final var myOptions = JellyOptions.DEFAULT_SUPPORTED_OPTIONS
     *      .clone()
     *      .setRdfStar(false);
     * </code>
     * <p>
     * If you were to pass a default RdfStreamOptions object to the decoder, it would simply refuse to read any stream
     * as (by default) it will have all max table sizes set to 0. So, you should always use this method as the base.
     */
    public static final RdfStreamOptions DEFAULT_SUPPORTED_OPTIONS = RdfStreamOptions.newInstance()
        .setVersion(JellyConstants.PROTO_VERSION)
        .setGeneralizedStatements(true)
        .setRdfStar(true)
        .setMaxNameTableSize(4096)
        .setMaxPrefixTableSize(1024)
        .setMaxDatatypeTableSize(256);

    /**
     * Checks if the requested stream options are supported. Throws an exception if not.
     * <p>
     * This is used in two places:
     * - By ProtoDecoder implementations to check if it's safe to decode the stream
     *   This MUST be called before any data (besides the stream options) is ingested. Otherwise, the options may
     *   request something dangerous, like allocating a very large lookup table, which could be used to perform a
     *   denial-of-service attack.
     * - By implementations the gRPC streaming service from the jelly-pekko-grpc module to check if the client is
     *   requesting stream options that the server can support.
     * <p>
     * We check:
     * - version (must be &lt;= Constants.protoVersion and &lt;= supportedOptions.version)
     * - generalized statements (must be &lt;= supportedOptions.generalizedStatements)
     * - RDF star (must be &lt;= supportedOptions.rdfStar)
     * - max name table size (must be &lt;= supportedOptions.maxNameTableSize and &gt;= 16).
     * - max prefix table size (must be &lt;= supportedOptions.maxPrefixTableSize)
     * - max datatype table size (must be &lt;= supportedOptions.maxDatatypeTableSize and &gt;= 8)
     * - logical stream type (must be compatible with physical stream type and compatible with expected log. stream type)
     * <p>
     * We don't check:
     * - physical stream type (this is done by the implementations of ProtoDecoderImpl)
     * - stream name (we don't care about it)
     * <p>
     * See also the stream options handling table in the gRPC spec:
     * <a href="https://w3id.org/jelly/dev/specification/streaming/#stream-options-handling">link</a>
     * This is not exactly what we are doing here (the table is about client-server interactions), but it's a good
     * reference for the logic used here.
     *
     * @param requestedOptions Requested options of the stream.
     * @param supportedOptions Options that can be safely supported.
     *
     * @throws RdfProtoDeserializationError if the requested options are not supported.
     */
    public static void checkCompatibility(RdfStreamOptions requestedOptions, RdfStreamOptions supportedOptions) {
        checkBaseCompatibility(requestedOptions, supportedOptions, JellyConstants.PROTO_VERSION);
        checkLogicalStreamType(requestedOptions, supportedOptions.getLogicalType());
    }

    /**
     * Checks if the logical and physical stream types are compatible. Additionally, if the expected logical stream type
     * is provided, checks if the actual logical stream type is a subtype of the expected one.
     *
     * @param options Options of the stream.
     * @param expectedLogicalType Expected logical stream type. If UNSPECIFIED, no check is performed.
     *
     * @throws RdfProtoDeserializationError if the requested options are not supported.
     */
    private static void checkLogicalStreamType(RdfStreamOptions options, LogicalStreamType expectedLogicalType) {
        final var requestedLogicalType = options.getLogicalType();
        final var requestedPhysicalType = options.getPhysicalType();

        final var baseLogicalType = LogicalStreamTypeUtils.toBaseType(requestedLogicalType);

        final var conflict =
            switch (baseLogicalType) {
                case FLAT_TRIPLES, GRAPHS -> switch (requestedPhysicalType) {
                    case QUADS, GRAPHS -> true;
                    default -> false;
                };
                case FLAT_QUADS, DATASETS -> switch (requestedPhysicalType) {
                    case TRIPLES -> true;
                    default -> false;
                };
                default -> false;
            };

        if (conflict) {
            throw new RdfProtoDeserializationError(
                "Logical stream type %s is incompatible with physical stream type %s.".formatted(
                        requestedLogicalType,
                        requestedPhysicalType
                    )
            );
        }

        if (expectedLogicalType == LogicalStreamType.UNSPECIFIED) {
            return;
        }

        if (!LogicalStreamTypeUtils.isEqualOrSubtypeOf(requestedLogicalType, expectedLogicalType)) {
            throw new RdfProtoDeserializationError(
                "Expected logical stream type %s, got %s. %s is not a subtype of %s.".formatted(
                        expectedLogicalType,
                        requestedLogicalType,
                        requestedLogicalType,
                        expectedLogicalType
                    )
            );
        }
    }
}
