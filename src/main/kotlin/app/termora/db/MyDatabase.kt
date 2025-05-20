package app.termora.db

import org.jetbrains.exposed.v1.jdbc.Database
import java.util.concurrent.locks.Lock

abstract class MyDatabase(protected val database: Database, protected val lock: Lock) {
}