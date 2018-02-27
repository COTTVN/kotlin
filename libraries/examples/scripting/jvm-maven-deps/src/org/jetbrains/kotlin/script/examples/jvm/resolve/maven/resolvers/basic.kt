/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.script.examples.jvm.resolve.maven.resolvers

import org.jetbrains.kotlin.script.examples.jvm.resolve.maven.DependsOn
import org.jetbrains.kotlin.script.examples.jvm.resolve.maven.Repository
import java.io.File

interface Resolver {
    fun tryResolve(dependsOn: DependsOn): Iterable<File>?
}

class DirectResolver : Resolver {
    override fun tryResolve(dependsOn: DependsOn): Iterable<File>? =
            dependsOn.value.takeUnless(String::isBlank)?.let(::File)?.takeIf { it.exists() && (it.isFile || it.isDirectory) }?.let { listOf(it) }
}

class FlatLibDirectoryResolver(val path: File) : Resolver {

    init {
        if (!path.exists() || !path.isDirectory) throw IllegalArgumentException("Invalid flat lib directory repository path '$path'")
    }

    override fun tryResolve(dependsOn: DependsOn): Iterable<File>? =
            // TODO: add coordinates and wildcard matching
            dependsOn.value.takeUnless(String::isBlank)?.let { File(path, it) }?.takeIf { it.exists() && (it.isFile || it.isDirectory) }?.let { listOf(it) }

    companion object {
        fun tryCreate(annotation: Repository): FlatLibDirectoryResolver? =
                annotation.value.takeUnless(String::isBlank)?.let(::File)?.takeIf { it.exists() && it.isDirectory }?.let(::FlatLibDirectoryResolver)
    }
}
