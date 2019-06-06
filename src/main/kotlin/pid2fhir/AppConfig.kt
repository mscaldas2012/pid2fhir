package pid2fhir

import io.micronaut.context.annotation.ConfigurationProperties
import javax.validation.constraints.NotBlank

@ConfigurationProperties(value = "app")
class AppConfig {
    @NotBlank
    lateinit var id:String
    @NotBlank
    lateinit var incomingtopic:String
    @NotBlank
    lateinit var outgoingtopic:String

}
