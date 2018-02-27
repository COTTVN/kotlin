/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.script.examples.jvm.resolve.maven

import org.jetbrains.kotlin.script.InvalidScriptResolverAnnotation
import org.jetbrains.kotlin.script.examples.jvm.resolve.maven.resolvers.DirectResolver
import org.jetbrains.kotlin.script.examples.jvm.resolve.maven.resolvers.FlatLibDirectoryResolver
import org.jetbrains.kotlin.script.examples.jvm.resolve.maven.resolvers.MavenResolver
import org.jetbrains.kotlin.script.examples.jvm.resolve.maven.resolvers.Resolver
import java.io.File

// Extracted with simplification from the script_util
// The main reason - classloading problems due to optional script_util dependency on aether/maven and the fact that script_util is loaded
// into idea plugin by default. In result without some troublesome filtering classloader it is not possible to add missing aether/maven
// jars to the definition classpath and have maven dependent classes properly loaded.

open class KotlinAnnotatedScriptDependenciesResolver(val baseClassPath: List<File>, resolvers: Iterable<Resolver>) {
    private val resolvers: MutableList<Resolver> = resolvers.toMutableList()

    fun resolveFromAnnotations(annotations: Iterable<Annotation>): List<File> {
        annotations.forEach { annotation ->
            when (annotation) {
                is Repository -> FlatLibDirectoryResolver.tryCreate(annotation)?.apply { resolvers.add(this) }
                        ?: resolvers.find { it is MavenResolver }?.takeIf { (it as MavenResolver).tryAddRepo(annotation) }
                        ?: throw IllegalArgumentException("Illegal argument for Repository annotation: $annotation")
                is DependsOn -> {
                }
                is InvalidScriptResolverAnnotation -> throw Exception("Invalid annotation ${annotation.name}", annotation.error)
                else -> throw Exception("Unknown annotation ${annotation.javaClass}")
            }
        }
        return annotations.filterIsInstance(DependsOn::class.java).flatMap { dep ->
            resolvers.asSequence().mapNotNull { it.tryResolve(dep) }.firstOrNull() ?: throw Exception("Unable to resolve dependency $dep")
        }
    }
}

class LocalFilesResolver :
    KotlinAnnotatedScriptDependenciesResolver(emptyList(), arrayListOf(DirectResolver()))

class FilesAndMavenResolver :
    KotlinAnnotatedScriptDependenciesResolver(emptyList(), arrayListOf(DirectResolver(), MavenResolver()))
