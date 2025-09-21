@file:Suppress("unused")

package io.legato.kazusa.lib.theme

import android.content.Context

/**
 * @author Karim Abou Zeid (kabouzeid)
 */
val Context.primaryColor: Int
    get() = ThemeStore.primaryColor(this)

val Context.secondaryColor: Int
    get() = ThemeStore.secondaryColor(this)

val Context.primaryContainerColor: Int
    get() = ThemeStore.primaryContainerColor(this)