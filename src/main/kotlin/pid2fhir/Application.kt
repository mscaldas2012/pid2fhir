package pid2fhir

import io.micronaut.runtime.Micronaut

object Application {

    @JvmStatic
    fun main(args: Array<String>) {
        Micronaut.build()
                .packages("pid2fhir")
                .mainClass(Application.javaClass)
                .start()
    }
}