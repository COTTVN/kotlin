/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.script.examples.jvm.resolve.maven.resolvers

import com.jcabi.aether.Aether
import org.jetbrains.kotlin.script.examples.jvm.resolve.maven.DependsOn
import org.jetbrains.kotlin.script.examples.jvm.resolve.maven.Repository
import org.sonatype.aether.repository.RemoteRepository
import org.sonatype.aether.resolution.DependencyResolutionException
import org.sonatype.aether.util.artifact.DefaultArtifact
import org.sonatype.aether.util.artifact.JavaScopes
import java.io.File
import java.net.MalformedURLException
import java.net.URL
import java.util.*

val mavenCentral = RemoteRepository("maven-central", "default", "http://repo1.maven.org/maven2/")

class MavenResolver(val reportError: ((String) -> Unit)? = null) : Resolver {

    // TODO: make robust
    val localRepo = File(File(System.getProperty("user.home")!!, ".m2"), "repository")

    val repos: ArrayList<RemoteRepository> = arrayListOf()

    private fun currentRepos() = if (repos.isEmpty()) arrayListOf(mavenCentral) else repos

    private fun String.isValidParam() = isNotBlank()

    override fun tryResolve(dependsOn: DependsOn): Iterable<File>? {

        fun error(msg: String) {
            reportError?.invoke(msg) ?: throw RuntimeException(msg)
        }

        fun String?.orNullIfBlank(): String? = this?.takeUnless(String::isBlank)

        val artifactId: DefaultArtifact = when {
            dependsOn.groupId.isValidParam() || dependsOn.artifactId.isValidParam() -> {
                DefaultArtifact(
                    dependsOn.groupId.orNullIfBlank(),
                    dependsOn.artifactId.orNullIfBlank(),
                    null,
                    dependsOn.version.orNullIfBlank()
                )
            }
            dependsOn.value.isValidParam() && dependsOn.value.count { it == ':' } == 2 -> {
                DefaultArtifact(dependsOn.value)
            }
            else -> {
                error("Unknown set of arguments to maven resolver: ${dependsOn.value}")
                return null
            }
        }

        try {
            val deps = Aether(currentRepos(), localRepo).resolve(artifactId, JavaScopes.RUNTIME)
            if (deps != null)
                return deps.map { it.file }
            else {
                error("resolving ${artifactId.artifactId} failed: no results")
            }
        } catch (e: DependencyResolutionException) {
            reportError?.invoke("resolving ${artifactId.artifactId} failed: $e") ?: throw e
        }
        return null
    }

    fun tryAddRepo(annotation: Repository): Boolean {
        val urlStr = annotation.url.takeIf { it.isValidParam() } ?: annotation.value.takeIf { it.isValidParam() } ?: return false
        try {
            URL(urlStr)
        } catch (_: MalformedURLException) {
            return false
        }
        repos.add(
            RemoteRepository(
                if (annotation.id.isValidParam()) annotation.id else "central",
                "default",
                urlStr
            )
        )
        return true
    }
}
