package pid2fhir


import io.micronaut.configuration.kafka.streams.ConfiguredStreamBuilder
import io.micronaut.context.annotation.Factory
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.KStream
import java.util.logging.Logger
import javax.inject.Named
import javax.inject.Singleton

/**
 * This kafka Streamer listens to a topic of Participants.
 * The Key should be the participant ID, the value the content of it's rows.
 *
 * The streamer will pick a participant and validate its content.
 * The Error Report will be placed on the Outgoing Topic, also using the participant ID as key.
 *
 * @Created - 2019-05-20
 * @Author Marcelo Caldas mcq1@cdc.gov
 */
@Factory
class PID2FHIRConverter(val appConfig: AppConfig) {

    companion object {
        val LOG = Logger.getLogger(PID2FHIRConverter::javaClass.name)
    }
    @Singleton
    @Named("dprp-validator")
    fun validateParticipant(builder: ConfiguredStreamBuilder): KStream<String, String> {
        LOG.info("AUDIT - PATIENT 2 FHIR  Streamer started")

        //val jsonSerde = Serdes.serdeFrom<JsonNode>(JsonSerializer(), JsonDeserializer())
        val validationStreams: KStream<String, String> = builder.stream(appConfig.incomingtopic,Consumed.with(Serdes.String(), Serdes.String()))

        validationStreams.map { k, v ->
            println("key: $k; Val: $v")
            val fhirResource = convert(k, v)
            KeyValue(k, fhirResource)
        }

        val streams = KafkaStreams(builder.build(), builder.configuration)
        streams.cleanUp()
        streams.start()

        validationStreams.to(appConfig.outgoingtopic)
        return validationStreams
    }

    fun convert( key: String, patient: String): String {
        LOG.info("Converting patient $key")
        return ""
    }
}