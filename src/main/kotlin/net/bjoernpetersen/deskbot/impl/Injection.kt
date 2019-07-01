package net.bjoernpetersen.deskbot.impl

import com.google.inject.Injector
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.jvmErasure

inline fun <reified T> Injector.get(): T {
    return getInstance(T::class.java)
}

operator fun <T : Any> Injector.getValue(thisRef: Any?, property: KProperty<*>): T {
    val type = property.returnType.jvmErasure.java
    return getInstance(type) as T
}
