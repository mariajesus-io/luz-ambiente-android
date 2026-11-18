package cl.ti3042.luzambiente

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class EmailAccountTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun emailAndUsernameResolveSameAccountAndDuplicatesAreRejected() {
        val id = UUID.randomUUID().toString().take(8)
        val user = "María $id"
        val email = "Maria.$id@Correo.cl"
        AppDatabase(context).use { db ->
            db.register(user, "luz123".toCharArray(), email)
            assertEquals("maría $id", db.loginUser(email.uppercase(), "luz123".toCharArray()))
            assertTrue(db.login(user, "luz123".toCharArray()))
            assertThrows(IllegalStateException::class.java) { db.register("otra $id", "luz123".toCharArray(), email.lowercase()) }
            assertThrows(IllegalArgumentException::class.java) { db.register("corta $id", "12345".toCharArray(), "a$id@correo.cl") }
            assertThrows(IllegalArgumentException::class.java) { db.register("larga $id", "1234567890123".toCharArray(), "b$id@correo.cl") }
        }
    }
    @Test fun upgradePreservesExistingAccount() {
        val name = "migration_${UUID.randomUUID()}.db"
        val salt = PasswordHasher.salt(); val pass = "luz123".toCharArray()
        val old = context.openOrCreateDatabase(name, 0, null)
        old.execSQL("CREATE TABLE users (name TEXT PRIMARY KEY, salt BLOB NOT NULL, hash BLOB NOT NULL, failures INTEGER NOT NULL DEFAULT 0, locked_until INTEGER NOT NULL DEFAULT 0)")
        old.execSQL("CREATE TABLE readings (id TEXT NOT NULL, owner TEXT NOT NULL, received INTEGER NOT NULL, payload BLOB NOT NULL, PRIMARY KEY(id,owner))")
        old.execSQL("INSERT INTO users(name,salt,hash) VALUES(?,?,?)", arrayOf("maria", salt, PasswordHasher.derive(pass, salt)))
        old.version = 1; old.close()
        try {
            AppDatabase(context, name).use { db ->
                assertTrue(db.login("maria", pass))
                assertTrue(db.history("maria").isEmpty())
                db.register("nuevo", pass, "nuevo@correo.cl")
                assertEquals("nuevo", db.loginUser("nuevo@correo.cl", pass))
            }
        } finally { context.deleteDatabase(name); pass.fill('\u0000') }
    }
}
