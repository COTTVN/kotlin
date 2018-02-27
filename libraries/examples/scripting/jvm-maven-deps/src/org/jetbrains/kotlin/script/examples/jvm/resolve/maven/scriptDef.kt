/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.script.examples.jvm.resolve.maven

import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.basic.DefaultScriptSelector
import kotlin.script.experimental.jvm.runners.BasicJvmScriptRunner

@KotlinScript(
    DefaultScriptSelector::class,
    MyConfigurator::class,
    BasicJvmScriptRunner::class
)
abstract class MyScriptWithMavenDeps {
//    abstract fun body(vararg args: String): Int
}

// in case of flat or direct resolvers the value should be a direct path or file name of a jar respectively
// in case of maven resolver the maven coordinates string is accepted (resolved with com.jcabi.aether library)
@Target(AnnotationTarget.FILE, AnnotationTarget.EXPRESSION, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class DependsOn(val value: String = "", val groupId: String = "", val artifactId: String = "", val version: String = "")

// only flat directory repositories are supported now, so value should be a path to a directory with jars
// TODO: support other types of repos
@Target(AnnotationTarget.FILE, AnnotationTarget.EXPRESSION, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Repository(val value: String = "", val id: String = "", val url: String = "")
