package eu.ostrzyciel.jelly.integration_tests.rdf.io

import eu.ostrzyciel.jelly.convert.jena.given
import eu.ostrzyciel.jelly.core.JellyOptions
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions
import eu.ostrzyciel.jelly.stream.*
import org.apache.jena.query.Dataset
import org.apache.jena.rdf.model.Model
import org.apache.pekko.stream.Materializer

import java.io.{InputStream, OutputStream}
import scala.concurrent.Await
import scala.concurrent.duration.*

class JenaReactiveSerDes(implicit mat: Materializer) extends NativeSerDes[Model, Dataset]:

  val name = "Reactive writes (Apache Jena)"

  override def readTriplesW3C(is: InputStream) = JenaSerDes.readTriplesW3C(is)

  override def readQuadsW3C(is: InputStream): Dataset = JenaSerDes.readQuadsW3C(is)

  override def readQuadsJelly(is: InputStream, supportedOptions: Option[RdfStreamOptions]): Dataset = 
    JenaSerDes.readQuadsJelly(is, supportedOptions)

  override def readTriplesJelly(is: InputStream, supportedOptions: Option[RdfStreamOptions]): Model = 
    JenaSerDes.readTriplesJelly(is, supportedOptions)

  override def writeQuadsJelly
  (os: OutputStream, dataset: Dataset, opt: Option[RdfStreamOptions], frameSize: Int): Unit =
    val f = RdfSource.builder.datasetAsQuads(dataset).source
      .via(EncoderFlow.builder
        .withLimiter(ByteSizeLimiter(32_000))
        .flatQuads(opt.getOrElse(JellyOptions.smallAllFeatures))
        .flow
      )
      .runWith(JellyIo.toIoStream(os))
    Await.ready(f, 10.seconds)

  override def writeTriplesJelly
  (os: OutputStream, model: Model, opt: Option[RdfStreamOptions], frameSize: Int): Unit =
    val f = RdfSource.builder.graphAsTriples(model).source
      .via(EncoderFlow.builder
        .withLimiter(ByteSizeLimiter(32_000))
        .flatTriples(opt.getOrElse(JellyOptions.smallAllFeatures))
        .flow
      )
      .runWith(JellyIo.toIoStream(os))
    Await.ready(f, 10.seconds)
