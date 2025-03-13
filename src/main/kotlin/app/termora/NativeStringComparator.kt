package app.termora

import com.formdev.flatlaf.util.SystemInfo
import de.jangassen.jfa.foundation.Foundation
import de.jangassen.jfa.foundation.Foundation.NSAutoreleasePool
import java.text.Collator
import java.util.*

class NativeStringComparator private constructor() : Comparator<String> {
    private val collator by lazy { Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY } }

    companion object {
        fun getInstance(): NativeStringComparator {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(NativeStringComparator::class) { NativeStringComparator() }
        }

        private const val SORT_DIGITSASNUMBERS: Int = 0x00000008

    }

    override fun compare(o1: String, o2: String): Int {
        if (SystemInfo.isWindows) {
            // CompareStringEx returns 1, 2, 3 respectively instead of -1, 0, 1
            return Kernel32.INSTANCE.CompareStringEx(SORT_DIGITSASNUMBERS, o1, o2) - 2
        } else if (SystemInfo.isMacOS) {
            val pool = NSAutoreleasePool()
            try {
                val a = Foundation.nsString(o1)
                val b = Foundation.nsString(o2)
                return Foundation.invoke(a, "localizedStandardCompare:", b).toInt()
            } finally {
                pool.drain()
            }
        }

        return collator.compare(o1, o2)
    }


}