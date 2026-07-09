package std.nooook.readinggardenkotlin.common

import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import std.nooook.readinggardenkotlin.modules.app.service.AppVersionQueryService
import std.nooook.readinggardenkotlin.modules.auth.service.AuthService
import std.nooook.readinggardenkotlin.modules.book.service.BookQueryService
import std.nooook.readinggardenkotlin.modules.garden.service.DefaultGardenQueryService
import std.nooook.readinggardenkotlin.modules.memo.service.MemoQueryService
import std.nooook.readinggardenkotlin.modules.push.service.PushPreferenceService
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TransactionalAnnotationPolicyTest {
    @Test
    fun `read-only service methods declare read-only transactions`() {
        val readOnlyMethods = listOf(
            TransactionalMethod(BookQueryService::class.java, "checkDuplication", Long::class.javaPrimitiveType, String::class.java),
            TransactionalMethod(BookQueryService::class.java, "getBookStatus", Long::class.javaPrimitiveType, Long::class.javaObjectType, Int::class.javaObjectType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType),
            TransactionalMethod(BookQueryService::class.java, "getBookRead", Long::class.javaPrimitiveType),
            TransactionalMethod(DefaultGardenQueryService::class.java, "getGardenList", Long::class.javaPrimitiveType),
            TransactionalMethod(DefaultGardenQueryService::class.java, "getGardenDetail", Long::class.javaPrimitiveType, Long::class.javaPrimitiveType),
            TransactionalMethod(MemoQueryService::class.java, "getMemoDetail", Long::class.javaPrimitiveType, Long::class.javaPrimitiveType),
            TransactionalMethod(AuthService::class.java, "getProfile", Long::class.javaPrimitiveType),
            TransactionalMethod(AppVersionQueryService::class.java, "getByPlatform", String::class.java),
        )

        readOnlyMethods.forEach { method ->
            val transactional = method.transactionalAnnotation()
            assertNotNull(transactional, "${method.owner.simpleName}.${method.name} must be transactional")
            assertTrue(transactional.readOnly, "${method.owner.simpleName}.${method.name} must be read-only")
        }
    }

    @Test
    fun `methods that can repair missing data stay read-write`() {
        val readWriteMethods = listOf(
            TransactionalMethod(PushPreferenceService::class.java, "getPush", Long::class.javaPrimitiveType),
        )

        readWriteMethods.forEach { method ->
            val transactional = method.transactionalAnnotation()
            assertNotNull(transactional, "${method.owner.simpleName}.${method.name} must be transactional")
            assertTrue(!transactional.readOnly, "${method.owner.simpleName}.${method.name} must stay read-write")
        }
    }

    @Test
    fun `methods that can perform cleanup writes stay read-write`() {
        val readWriteMethods = listOf(
            TransactionalMethod(AuthService::class.java, "checkPasswordResetAuth", String::class.java, String::class.java),
        )

        readWriteMethods.forEach { method ->
            val transactional = method.transactionalAnnotation()
            assertNotNull(transactional, "${method.owner.simpleName}.${method.name} must be transactional")
            assertTrue(!transactional.readOnly, "${method.owner.simpleName}.${method.name} must stay read-write")
            assertTrue(
                ResponseStatusException::class in transactional.noRollbackFor,
                "${method.owner.simpleName}.${method.name} must commit cleanup writes before bad request responses",
            )
        }
    }

    private data class TransactionalMethod(
        val owner: Class<*>,
        val name: String,
        val parameterTypes: List<Class<*>?>,
    ) {
        constructor(owner: Class<*>, name: String, vararg parameterTypes: Class<*>?) :
            this(owner, name, parameterTypes.toList())

        fun transactionalAnnotation(): Transactional? {
            val method = owner.getDeclaredMethod(name, *parameterTypes.toTypedArray())
            return method.getAnnotation(Transactional::class.java)
                ?: owner.getAnnotation(Transactional::class.java)
        }
    }
}
