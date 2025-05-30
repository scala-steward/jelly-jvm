package eu.neverblink.jelly.pekko.stream

import com.typesafe.config.{Config, ConfigFactory}
import eu.neverblink.jelly.core.proto.v1.{LogicalStreamType, PhysicalStreamType, RdfStreamOptions}

/**
 * Convenience methods for building Jelly's options ([[RdfStreamOptions]]) from [[com.typesafe.Config]].
 *
 * See also [[eu.neverblink.jelly.core.JellyOptions]]
 */
object JellyOptionsFromTypesafe:
  private val defaultConfig = ConfigFactory.parseString("""
    |physical-type = UNSPECIFIED
    |logical-type = UNSPECIFIED
    |generalized-statements = false
    |rdf-star = false
    |name-table-size = 128
    |prefix-table-size = 16
    |datatype-table-size = 16
    |""".stripMargin)
  
  private val physicalStreamTypeMap = PhysicalStreamType.values
    .map(v => v.name.replace("PHYSICAL_STREAM_TYPE_", "") -> v)
    .toMap
  
  private val logicalStreamTypeMap = LogicalStreamType.values
    .map(v => v.name.replace("LOGICAL_STREAM_TYPE_", "") -> v)
    .toMap

  /**
   * Builds RdfStreamOptions from a typesafe config instance.
   *
   * @param config typesafe config with keys:
   *               - "physical-type", either UNSPECIFIED, TRIPLES, QUADS, or GRAPHS. Default: UNSPECIFIED.
   *               - "logical-type", one of the defined logical types like "FLAT_TRIPLES" in rdf.proto,
   *                  LogicalStreamType enum. Default: UNSPECIFIED.
   *               - "generalized-statements", boolean. Default: false.
   *               - "rdf-star", boolean. Default: false.
   *               - "name-table-size", integer. Default: 128.
   *               - "prefix-table-size", integer. Default: 16.
   *               - "datatype-table-size", integer. Default: 16.
   * @return
   */
  def fromTypesafeConfig(config: Config): RdfStreamOptions =
    val merged = config.withFallback(defaultConfig)
    RdfStreamOptions.newInstance()
      .setPhysicalType(
        physicalStreamTypeMap.get(merged.getString("physical-type")) match
          case Some(v) => v
          case None => throw new IllegalArgumentException(s"Unknown physical type: ${merged.getString("physical-type")}")
      )
      .setLogicalType(
        logicalStreamTypeMap.get(merged.getString("logical-type")) match
          case Some(v) => v
          case None => throw new IllegalArgumentException(s"Unknown logical type: ${merged.getString("logical-type")}")
      )
      .setGeneralizedStatements(merged.getBoolean("generalized-statements"))
      .setRdfStar(merged.getBoolean("rdf-star"))
      .setMaxNameTableSize(merged.getInt("name-table-size"))
      .setMaxPrefixTableSize(merged.getInt("prefix-table-size"))
      .setMaxDatatypeTableSize(merged.getInt("datatype-table-size"))

