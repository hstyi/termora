package app.termora.transport

import okio.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Supplier

class TransportSupportLoader(private val support: Supplier<TransportSupport>) : Supplier<TransportSupport> {
    private val loading = AtomicBoolean(false)
    private lateinit var mySupport: TransportSupport
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()
    private val exceptionReference = AtomicReference<Exception>(null)

    val isLoaded get() = ::mySupport.isInitialized


    override fun get(): TransportSupport {
        if (isLoaded) return mySupport

        if (loading.compareAndSet(false, true)) {
            try {
                mySupport = support.get()
            } catch (e: Exception) {
                exceptionReference.set(e)
                throw e
            } finally {
                lock.withLock {
                    loading.set(false)
                    condition.signalAll()
                }
            }
        } else {
            lock.lock()
            try {
                condition.await()
            } finally {
                lock.unlock()
            }
        }

        val exception = exceptionReference.get()
        if (exception != null) {
            throw exception
        }

        return get()
    }


}