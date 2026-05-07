/*
 * Copyright (C) 2018 Christopher J. Stehno <chris@stehno.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.stehno.gradle.depchecker

import groovy.transform.TypeChecked
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

@TypeChecked
abstract class CheckDependenciesTask extends DefaultTask {

    /** Configuration names to scan; empty means all configurations. */
    @Input abstract ListProperty<String> getConfigurations()

    /** Coordinates in "group:name" form to skip. */
    @Input abstract ListProperty<String> getIgnored()

    /**
     * Pre-computed map of configuration name → comma-joined "group:name" dependency keys, wired
     * by DependencyCheckerPlugin at configuration time so the task action never accesses project.
     */
    @Input abstract MapProperty<String, String> getConfigDeps()

    /** Test-only hook; not a real task input. */
    @Internal Class<? extends ResultListener> resultListenerClass = NoOpResultListener

    CheckDependenciesTask() {
        group = 'Verification'
        description = 'Checks the project dependencies for duplicate libraries with different versions.'
        configurations.convention([])
        ignored.convention([])
        configDeps.convention([:])
    }

    @TaskAction
    void checkDependencies() {
        DependencyCheckResults results = new DependencyCheckResults()
        ResultListener resultListener = resultListenerClass ? resultListenerClass.getDeclaredConstructor().newInstance() : null

        configDeps.get().each { String cname, String depsStr ->
            List<String> deps = depsStr ? depsStr.split(',').toList() : []
            Set<String> seen = [] as Set<String>
            deps.each { String key ->
                if (!ignored.get().contains(key) && !seen.add(key)) {
                    results[cname] = key
                    resultListener?.duplicated(cname, key)
                }
            }
        }

        if (results.hasDuplications()) {
            logger.error 'Dependency duplications detected ({}):', results.count()

            results.each { String cname, String groupModule ->
                logger.error '- Duplicated dependency in ({}) {}', cname, groupModule
            }

            throw new RuntimeException("Duplicate dependencies detected (${results.count()})")
        }
    }
}