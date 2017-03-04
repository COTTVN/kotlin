/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.config.CoroutineSupport
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.KotlinPluginUtil
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.configuration.KotlinWithGradleConfigurator
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.facet.getRuntimeLibraryVersion
import org.jetbrains.kotlin.psi.KtFile

sealed class ChangeCoroutineSupportFix(
        element: PsiElement,
        protected val coroutineSupport: CoroutineSupport
) : KotlinQuickFixAction<PsiElement>(element) {
    class InModule(element: PsiElement, coroutineSupport: CoroutineSupport) : ChangeCoroutineSupportFix(element, coroutineSupport) {
        override fun getText() = "${super.getText()} in the current module"

        override fun invoke(project: Project, editor: Editor?, file: KtFile) {
            val module = ModuleUtilCore.findModuleForPsiElement(file) ?: return

            val runtimeUpdateRequired = coroutineSupport != CoroutineSupport.DISABLED &&
                                        (getRuntimeLibraryVersion(module)?.startsWith("1.0") ?: false)

            if (KotlinPluginUtil.isGradleModule(module)) {
                if (runtimeUpdateRequired) {
                    Messages.showErrorDialog(project,
                                             "Coroutines support requires version 1.1 or later of the Kotlin runtime library. " +
                                             "Please update the version in your build script.",
                                             super.getText())
                    return
                }

                val element = KotlinWithGradleConfigurator.changeCoroutineConfiguration(module, coroutineSupport.compilerArgument)
                element?.let {
                    OpenFileDescriptor(project, it.containingFile.virtualFile, it.textRange.startOffset).navigate(true)
                }
                return
            }

            if (runtimeUpdateRequired && !askUpdateRuntime(module, LanguageFeature.Coroutines.sinceApiVersion)) {
                return
            }

            val facetSettings = KotlinFacetSettingsProvider.getInstance(project).getSettings(module)
            ModuleRootModificationUtil.updateModel(module) {
                facetSettings.coroutineSupport = coroutineSupport
                facetSettings.apiLevel = LanguageVersion.KOTLIN_1_1
                facetSettings.languageLevel = LanguageVersion.KOTLIN_1_1
            }
        }

    }

    class InProject(element: PsiElement, coroutineSupport: CoroutineSupport) : ChangeCoroutineSupportFix(element, coroutineSupport) {
        override fun getText() = "${super.getText()} in the project"

        override fun invoke(project: Project, editor: Editor?, file: KtFile) {
            if (coroutineSupport != CoroutineSupport.DISABLED) {
                if (!checkUpdateRuntime(project, LanguageFeature.Coroutines.sinceApiVersion)) return
            }

            with (KotlinCommonCompilerArgumentsHolder.getInstance(project).settings) {
                coroutinesEnable = coroutineSupport == CoroutineSupport.ENABLED
                coroutinesWarn = coroutineSupport == CoroutineSupport.ENABLED_WITH_WARNING
                coroutinesError = coroutineSupport == CoroutineSupport.DISABLED
            }
            ProjectRootManagerEx.getInstanceEx(project).makeRootsChange({}, false, true)
        }

    }

    override fun getFamilyName() = "Enable/Disable coroutine support"

    override fun getText(): String {
        return when (coroutineSupport) {
            CoroutineSupport.DISABLED -> "Disable coroutine support"
            CoroutineSupport.ENABLED_WITH_WARNING -> "Enable coroutine support (with warning)"
            CoroutineSupport.ENABLED -> "Enable coroutine support"
        }
    }

    companion object : KotlinIntentionActionsFactory() {
        override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
            val newCoroutineSupports = when (diagnostic.factory) {
                Errors.EXPERIMENTAL_FEATURE_ERROR -> {
                    if (Errors.EXPERIMENTAL_FEATURE_ERROR.cast(diagnostic).a.first != LanguageFeature.Coroutines) return emptyList()
                    listOf(CoroutineSupport.ENABLED_WITH_WARNING, CoroutineSupport.ENABLED)
                }
                Errors.EXPERIMENTAL_FEATURE_WARNING -> {
                    if (Errors.EXPERIMENTAL_FEATURE_WARNING.cast(diagnostic).a.first != LanguageFeature.Coroutines) return emptyList()
                    listOf(CoroutineSupport.ENABLED, CoroutineSupport.DISABLED)
                }
                else -> return emptyList()
            }
            val module = ModuleUtilCore.findModuleForPsiElement(diagnostic.psiElement) ?: return emptyList()
            if (KotlinPluginUtil.isMavenModule(module)) return emptyList()
            val facetSettings = KotlinFacet.get(module)?.configuration?.settings

            val configureInProject = (facetSettings == null || facetSettings.useProjectSettings) &&
                                     !KotlinPluginUtil.isGradleModule(module)
            val quickFixConstructor: (PsiElement, CoroutineSupport) -> ChangeCoroutineSupportFix =
                    if (configureInProject) ::InProject else ::InModule
            return newCoroutineSupports.map { quickFixConstructor(diagnostic.psiElement, it) }
        }
    }
}
