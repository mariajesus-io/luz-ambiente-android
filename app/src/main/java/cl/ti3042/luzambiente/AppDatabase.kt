package cl.ti3042.luzambiente

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

class AppDatabase(context: Context, databaseName: String = "light.db") : SQLiteOpenHelper(context, databaseName, null, 2) {
    private val vault = Vault()
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE users (name TEXT PRIMARY KEY, email TEXT, salt BLOB NOT NULL, hash BLOB NOT NULL, failures INTEGER NOT NULL DEFAULT 0, locked_until INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE UNIQUE INDEX users_email ON users(email) WHERE email IS NOT NULL")
        db.execSQL("CREATE TABLE readings (id TEXT NOT NULL, owner TEXT NOT NULL, received INTEGER NOT NULL, payload BLOB NOT NULL, PRIMARY KEY(id,owner))")
        db.execSQL("CREATE INDEX history_owner ON readings(owner,received DESC)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE users ADD COLUMN email TEXT")
            db.execSQL("CREATE UNIQUE INDEX users_email ON users(email) WHERE email IS NOT NULL")
        }
    }

    fun register(user: String, password: CharArray, email: String? = null) {
        val name = PasswordHasher.normalize(user)
        val address = email?.let(PasswordHasher::normalize)
        require(PasswordHasher.validUser(name)) { "Usuario: entre 3 y 32 letras, números, espacios, puntos o guiones." }
        require(PasswordHasher.validPassword(password)) { "La contraseña debe tener entre 6 y 12 caracteres." }
        require(address == null || PasswordHasher.validEmail(address)) { "Escribe un correo válido, por ejemplo nombre@correo.cl." }
        val salt = PasswordHasher.salt()
        val values = ContentValues().apply {
            put("name", name); put("email", address); put("salt", salt); put("hash", PasswordHasher.derive(password, salt))
        }
        check(writableDatabase.insertWithOnConflict("users", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L) {
            "Ese usuario o correo ya está registrado en este teléfono. Usa Ingresar o elige otros datos."
        }
    }

    fun login(user: String, password: CharArray): Boolean = loginUser(user, password) != null

    fun loginUser(user: String, password: CharArray): String? {
        val db = writableDatabase
        val identifier = PasswordHasher.normalize(user)
        db.query("users", arrayOf("salt", "hash", "failures", "locked_until", "name"), "name=? OR email=?", arrayOf(identifier, identifier), null, null, null).use {
            if (!it.moveToFirst()) {
                PasswordHasher.derive(password, ByteArray(16)) // Coste similar para usuarios inexistentes.
                return null
            }
            if (it.getLong(3) > System.currentTimeMillis()) return null
            val owner = it.getString(4)
            val valid = PasswordHasher.verify(password, it.getBlob(0), it.getBlob(1))
            val failures = if (valid) 0 else it.getInt(2) + 1
            db.update("users", ContentValues().apply {
                put("failures", if (failures >= 5) 0 else failures)
                put("locked_until", if (failures >= 5) System.currentTimeMillis() + 30000 else 0)
            }, "name=?", arrayOf(owner))
            return if (valid) owner else null
        }
    }

    fun save(owner: String, reading: Reading) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertWithOnConflict("readings", null, ContentValues().apply {
                put("id", reading.id); put("owner", owner); put("received", System.currentTimeMillis())
                put("payload", vault.encrypt(reading.toJson().toString(), "$owner:${reading.id}"))
            }, SQLiteDatabase.CONFLICT_IGNORE)
            // Retención acotada por usuario para no crecer indefinidamente.
            db.execSQL("DELETE FROM readings WHERE owner=? AND id NOT IN (SELECT id FROM readings WHERE owner=? ORDER BY received DESC LIMIT 5000)", arrayOf(owner, owner))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun history(owner: String, limit: Int = 100): List<Reading> {
        val result = mutableListOf<Reading>()
        readableDatabase.query("readings", arrayOf("id", "payload"), "owner=?", arrayOf(owner), null, null, "received DESC", limit.coerceIn(1, 5000).toString()).use {
            while (it.moveToNext()) result += Reading.parse(JSONObject(vault.decrypt(it.getBlob(1), "$owner:${it.getString(0)}")))
        }
        return result
    }
    fun clear(owner: String) { writableDatabase.delete("readings", "owner=?", arrayOf(owner)) }
}
