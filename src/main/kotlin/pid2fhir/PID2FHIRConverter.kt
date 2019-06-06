package pid2fhir


import ca.uhn.fhir.context.FhirContext
import com.fasterxml.jackson.databind.JsonNode
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
import open.HL7PET.tools.HL7ParseUtils
import org.apache.kafka.common.serialization.Serdes.serdeFrom
import org.apache.kafka.connect.json.JsonDeserializer
import org.apache.kafka.connect.json.JsonSerializer
import org.apache.kafka.streams.StreamsConfig
import org.hl7.fhir.r4.model.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

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
    val ctx = FhirContext.forR4()

    companion object {
        val LOG = Logger.getLogger(PID2FHIRConverter::javaClass.name)

        const val DATE_FORMAT = "yyyyMMdd"

        const val PID_IDENTIFIER = "PID-3.1"                //CX -> Patient identifier List
        const val PID_FIRSTNAME = "PID-5.1"                 //XPN --> Patient Name .1 - Family Name
        const val PID_LASTNAME = "PID-5.2"                  // .2 - Given Name
        const val HOME_PHONE_NUMBER = "PID-13.1"            // XTN - Phone Number - home .1 Phone Number
        const val BIZ_PHONE_NUMBER = "PID-14.1"             // XTN - Phone Number - Business .1 - Phone Number
        const val PID_TELECOM_3 = "PID-40"                          // ?? Can't find this...
        const val PID_GENDER = "PID-8"                      // IS - Sex
        const val PID_BIRTHDATE  ="PID-7"                   // TS - Date/Time Birth
        const val PID_DECEASED_INDICATOR = "PID-30"              // ID - Patient Death Indicator
        const val PID_DECEASED_DATETIME = "PID-29"          // TS - patient Death Date and Time
        const val ADDRESS_LINE_ONE = "PID-11[1].1"          // XAD - Patient Address
        const val ADDRESS_CITY = "PID-11[1].3"
        const val ADDRESS_STATE = "PID-11[1].4"
        const val ADDRESS_ZIP = "PID-11[1].5"
        const val ADDRESS_COUNTRY = "PID-11[1].6"

        const val MARITAL_STATUS = "PID-16"                 // IS - Marital Status
        const val MULTIPLE_BIRTH_INDICATOR = "PID-24"             // ID - Multiple Birth Indicator
        const val MULTIPLE_BIRTH_ORDER= "PID-25"            // NM - Birth Order

//        const val PHOTO = "OBX-5" //???

        const val CONTACT_ROLE = "NK1-7"                    // CE - Role
        const val CONTACT_RELATIONSHIP = "NK1-3"            // CE - Relationship
        const val CONTACT_NAME = "NK1-2"                    // XPN - Name
        const val CONTACT_HOME_PHONE = "NK1-5"              // XTN - Phone Number
        const val CONTACT_BIZ_PHONE = "NK1-6"               // XTN - Business Phone number
        const val CONTACT_TELECOM_3 = "NK1-40"                      // ??? Can't find this...
        const val CONTACT_ADDRESS = "NK1-4"                 // XAD  - Address
        const val CONTACT_GENDER = "NK1-15"                 // IS - Sex
        const val CONTACT_ORGANIZATION_NAME = "NK1-13"      // XON - Organization Name
        const val CONTACT_ORG_CONTACT_NAME = "NK1-30"       // XPN - Contact Person Name
        const val CONTACT_ORG_CONTACT_PHONE = "NK1-31"      // XTN - Contact Person's Telephone Number
        const val CONTACT_ORG_CONTACT_ADDR = "NK1-32"       // XAD - Contact's Peson's Address
        const val CONTACT_ORGANIZATION_5 = "NK1-41"                // ???

        const val COMM_PRIMARY_LANGUAGE = "PID-15"                    //CE - Primary Langauge
//        const val COMM_LANG_2 = "LAN-2"                   // CE - Language Code

        const val COMM_PREFERRED = "PID-15"                 // CE - Primary Langauge

        const val GENERAL_PRACTITIONER = "PD1-4"              // XCN - Patient Primary Care provider name and ID no. (.1 - ID; 2 - family name; 3 -given Name)
//        const val MANAGING_ORG_LINK_1 = "PID-3"             // CX - Patient ID
//        const val MANAGING_ORG_LINK_2 = "MRG-1"             // CX - Prior Patient Identifier List
    }

    @Singleton
    @Named("PATIENT-2-FHIR")
    fun validateParticipant(builder: ConfiguredStreamBuilder): KStream<String, String> {
        LOG.info("AUDIT - PATIENT 2 FHIR  Streamer started")
        LOG.info("ID: ${appConfig.id}")
        LOG.info("Incoming Topic: ${appConfig.incomingtopic}")
        LOG.info("Outgoing Topic ${appConfig.outgoingtopic}")


        builder.configuration.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG,   Serdes.String()::class.java)
        builder.configuration.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String()::class.java)


        val jsonSerde = serdeFrom<JsonNode>(JsonSerializer(), JsonDeserializer())
        val adtMesages: KStream<String, String> = builder.stream(appConfig.incomingtopic,Consumed.with(Serdes.String(), Serdes.String()))

        val fhirPatients = adtMesages.map { k, v ->
            LOG.info("key: $k; Val: $v")
            val patientRes = convert(k, v)
            val patientJson = ctx.newJsonParser().encodeResourceToString(patientRes)
            //val gson = Gson()

            LOG.info("converted:\n$patientRes")
            KeyValue(k, patientJson)
        }

        fhirPatients.to(appConfig.outgoingtopic)
        return adtMesages
    }

    fun preparePhone(hl7Value: String, use: ContactPoint.ContactPointUse): ContactPoint {
        val number = ContactPoint()
        number.system = ContactPoint.ContactPointSystem.PHONE
        number.use = use
        number.value = hl7Value
        return number
    }

    fun convert(key: String, v2Patient: String): Patient {
        LOG.info("Converting v2Patient $key")
        val hl7Parser = HL7ParseUtils(v2Patient)

        val fhirPatient = Patient()

        val v2Id = hl7Parser.getFirstValue(PID_IDENTIFIER)
        if (v2Id.isDefined) {
            val id = Identifier()
            id.value = v2Id.get()
            fhirPatient.addIdentifier(id)
        }


        val humanName = HumanName()
        val v2Family = hl7Parser.getFirstValue(PID_LASTNAME)
        if (v2Family.isDefined)
            humanName.family = v2Family.get()
        val v2Given = hl7Parser.getFirstValue(PID_FIRSTNAME)
        if (v2Given.isDefined)
            humanName.addGiven( v2Given.get())

        fhirPatient.addName(humanName)

        val v2HomePhone = hl7Parser.getFirstValue(HOME_PHONE_NUMBER)
        if (v2HomePhone.isDefined) {
            fhirPatient.addTelecom(preparePhone(v2HomePhone.get(), ContactPoint.ContactPointUse.HOME))
        }
        val v2BizPhone = hl7Parser.getValue(BIZ_PHONE_NUMBER)
        if (v2BizPhone.isDefined) {
            v2BizPhone.get().forEach { repeatingSegments ->
                repeatingSegments.forEach { repeatingValues ->
                    fhirPatient.addTelecom(preparePhone(repeatingValues, ContactPoint.ContactPointUse.WORK))
                }
            }
        }

        val v2Gender = hl7Parser.getFirstValue(PID_GENDER)
        if (v2Gender.isDefined)
            when (v2Gender.get()?.toLowerCase()) {
                "male" -> fhirPatient.gender = Enumerations.AdministrativeGender.MALE
                "female" -> fhirPatient.gender = Enumerations.AdministrativeGender.FEMALE
            }

        val formatter = DateTimeFormatter.ofPattern(DATE_FORMAT)//, Locale.ENGLISH)

        val v2BirthDate = hl7Parser.getFirstValue(PID_BIRTHDATE)
        if (v2BirthDate.isDefined) {
            val localdate = LocalDate.parse(v2BirthDate.get(), formatter)
            fhirPatient.birthDate = localdate.toDate()
        }


        val v2Deceased = hl7Parser.getFirstValue(PID_DECEASED_INDICATOR)
        if (v2Deceased.isDefined)
            fhirPatient.deceased = BooleanType(v2Deceased.get())

        val v2DeceasedDate = hl7Parser.getFirstValue(PID_DECEASED_DATETIME)
        if (v2DeceasedDate.isDefined) {
            val localdate = LocalDate.parse(v2DeceasedDate.get(), formatter)
            val type = DateTimeType()
            type.value = localdate.toDate()
            fhirPatient.deceased = type
        }

        val v2MaritalStatus = hl7Parser.getFirstValue(MARITAL_STATUS)
        if (v2MaritalStatus.isDefined) {

            val maritalCode = Coding()
            maritalCode.code = v2MaritalStatus.get()
            fhirPatient.maritalStatus.addCoding(maritalCode)
        }

        val v2MultipleBirth = hl7Parser.getFirstValue(MULTIPLE_BIRTH_INDICATOR)
        if (v2MultipleBirth.isDefined)
            fhirPatient.multipleBirth = BooleanType(v2MultipleBirth.get())

        val v2MultipleBirthOrder = hl7Parser.getFirstValue(MULTIPLE_BIRTH_ORDER)
        if (v2MultipleBirthOrder.isDefined)
            fhirPatient.multipleBirth = IntegerType(v2MultipleBirthOrder.get())

        val v2PrimaryLang = hl7Parser.getFirstValue(COMM_PRIMARY_LANGUAGE)
        if (v2PrimaryLang.isDefined) {
            fhirPatient.language = v2PrimaryLang.get()
        }

        val address = Address()
        address.addLine(hl7Parser.getFirstValue(ADDRESS_LINE_ONE).getOrElse { "N/A" })
        address.city = hl7Parser.getFirstValue(ADDRESS_CITY).getOrElse{"N/A" }
        address.state =hl7Parser.getFirstValue(ADDRESS_STATE).getOrElse{"N/A" }
        address.postalCode = hl7Parser.getFirstValue(ADDRESS_ZIP).getOrElse{"N/A" }
        val country = hl7Parser.getFirstValue(ADDRESS_COUNTRY)
        if (country.isDefined)
            address.country = country.get()
        address.type = Address.AddressType.PHYSICAL

        fhirPatient.addAddress(address)

        return fhirPatient
    }

    //helper Function to convert LocalDate to Date...
    fun LocalDate.toDate(): Date = Date.from(this.atStartOfDay(ZoneId.systemDefault()).toInstant())
}