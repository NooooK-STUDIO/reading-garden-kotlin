package std.nooook.readinggardenkotlin.modules.auth.integration

import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource
import org.springframework.mail.javamail.JavaMailSenderImpl
import kotlin.test.assertEquals

class SmtpMailSenderConfigurationTest {
    @Test
    fun `legacy credentials configure authenticated Gmail SMTP over TLS`() {
        val account = "sender@example.test"
        val password = "test-password"
        val environment = StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME

        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration::class.java))
            .withPropertyValues("spring.config.location=file:src/main/resources/application.yaml")
            .withInitializer { context ->
                context.environment.propertySources.replace(
                    environment,
                    SystemEnvironmentPropertySource(
                        environment,
                        mapOf("EMAIL_ACCOUNT" to account, "EMAIL_PASSWORD" to password),
                    ),
                )
                ConfigDataApplicationContextInitializer().initialize(context)
            }.run { context ->
                val sender = context.getBean(JavaMailSenderImpl::class.java)

                assertEquals("smtp.gmail.com", sender.host)
                assertEquals(465, sender.port)
                assertEquals(account, sender.username)
                assertEquals(password, sender.password)
                assertEquals("true", sender.javaMailProperties.getProperty("mail.smtp.auth"))
                assertEquals("true", sender.javaMailProperties.getProperty("mail.smtp.ssl.enable"))
                assertEquals("true", sender.javaMailProperties.getProperty("mail.smtp.ssl.checkserveridentity"))
                assertEquals("false", context.environment.getProperty("management.health.mail.enabled"))
            }
    }
}
