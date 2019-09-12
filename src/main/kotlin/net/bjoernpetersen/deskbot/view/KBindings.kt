@file:Suppress("unused", "SpreadOperator")

package net.bjoernpetersen.deskbot.view

import javafx.beans.Observable
import javafx.beans.binding.Bindings
import javafx.beans.binding.BooleanBinding
import javafx.beans.binding.DoubleBinding
import javafx.beans.binding.FloatBinding
import javafx.beans.binding.IntegerBinding
import javafx.beans.binding.LongBinding
import javafx.beans.binding.ObjectBinding
import javafx.beans.binding.StringBinding

// TODO: These methods are pretty terrible performance-wise (spread operator).

/**
 * Helper function to create a custom [BooleanBinding].
 *
 * @param dependencies The dependencies of this binding
 * @param func The function that calculates the value of this binding
 * @return The generated binding
 */
fun createBooleanBinding(vararg dependencies: Observable, func: () -> Boolean?): BooleanBinding {
    return Bindings.createBooleanBinding(func, *dependencies)
}

/**
 * Helper function to create a custom [DoubleBinding].
 *
 * @param dependencies The dependencies of this binding
 * @param func The function that calculates the value of this binding
 * @return The generated binding
 */
fun createDoubleBinding(vararg dependencies: Observable, func: () -> Double?): DoubleBinding {
    return Bindings.createDoubleBinding(func, *dependencies)
}

/**
 * Helper function to create a custom [FloatBinding].
 *
 * @param dependencies The dependencies of this binding
 * @param func The function that calculates the value of this binding
 * @return The generated binding
 */
fun createFloatBinding(vararg dependencies: Observable, func: () -> Float?): FloatBinding {
    return Bindings.createFloatBinding(func, *dependencies)
}

/**
 * Helper function to create a custom [IntegerBinding].
 *
 * @param dependencies The dependencies of this binding
 * @param func The function that calculates the value of this binding
 * @return The generated binding
 */
fun createIntegerBinding(vararg dependencies: Observable, func: () -> Int?): IntegerBinding {
    return Bindings.createIntegerBinding(func, *dependencies)
}

/**
 * Helper function to create a custom [LongBinding].
 *
 * @param dependencies The dependencies of this binding
 * @param func The function that calculates the value of this binding
 * @return The generated binding
 */
fun createLongBinding(vararg dependencies: Observable, func: () -> Long?): LongBinding {
    return Bindings.createLongBinding(func, *dependencies)
}

/**
 * Helper function to create a custom [ObjectBinding].
 *
 * @param <T> the type of the bound {@code Object}
 * @param dependencies The dependencies of this binding
 * @param func The function that calculates the value of this binding
 * @return The generated binding
 */
fun <T> createObjectBinding(vararg dependencies: Observable, func: () -> T?): ObjectBinding<T> {
    return Bindings.createObjectBinding(func, *dependencies)
}

/**
 * Helper function to create a custom [StringBinding].
 *
 * @param dependencies The dependencies of this binding
 * @param func The function that calculates the value of this binding
 * @return The generated binding
 */
fun createStringBinding(vararg dependencies: Observable, func: () -> String?): StringBinding {
    return Bindings.createStringBinding(func, *dependencies)
}
