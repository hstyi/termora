package app.termora

import com.formdev.flatlaf.extras.FlatSVGIcon

open class DynamicIcon(
    name: String,
    private val darkName: String = name,
    val allowColorFilter: Boolean = true,
    loader: ClassLoader?
) : FlatSVGIcon(name, loader) {
    constructor(name: String) : this(name, name)

    constructor(
        name: String,
        darkName: String = name,
        allowColorFilter: Boolean = true,
    ) : this(name, darkName, allowColorFilter, null) {

    }

    val dark by lazy { DynamicIcon(darkName, name, allowColorFilter, classLoader) }

}
