package pid2fhir

import ca.uhn.fhir.context.FhirContext
import org.junit.Before


class PID2FHIRConverterTest {

    var converter: PID2FHIRConverter? = null
    @Before
    fun setup () {
        val appConfig = AppConfig()
        converter = PID2FHIRConverter(appConfig)

    }

    @org.junit.Test
    fun convert() {
        var msg = PID2FHIRConverterTest::class.java.getResource("/PatientAdmission.hl7").readText()
        val patient = converter!!.convert("Test", msg)

        val ctx = FhirContext.forR4()
        val json = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(patient)
        println(json)

    }
}