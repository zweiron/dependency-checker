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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

class DependencyCheckerPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.tasks.register('checkDependencies', CheckDependenciesTask) { CheckDependenciesTask task ->
            // Capture direct dependency group:name keys per configuration at configuration time.
            // Using a provider defers evaluation until the config cache stores task inputs (end of
            // configuration phase), by which point all build-script dependencies are declared.
            task.configDeps.set(project.provider {
                List<String> configNames = task.configurations.get().empty
                    ? project.configurations.names.toList()
                    : task.configurations.get()

                configNames.collectEntries { String configName ->
                    def config = project.configurations.findByName(configName)
                    String deps = config
                        ? config.dependencies.collect { d -> "${d.group}:${d.name}" }.join(',')
                        : ''
                    [(configName): deps]
                } as Map<String, String>
            })
        }

        project.tasks.register('checkAvailability', CheckAvailabilityTask) { CheckAvailabilityTask task ->
            // Resolve transitive dependency coordinates at configuration time and store as plain
            // strings so the task action requires no project access at execution time.
            task.dependencyCoordinates.set(project.provider {
                List<String> configNames = task.configurations.get().empty
                    ? project.configurations.names.toList()
                    : task.configurations.get()

                Set<DependencyCoordinate> coords = new LinkedHashSet<>()
                configNames.each { String cname ->
                    def config = project.configurations.findByName(cname)
                    if (config?.canBeResolved) {
                        config.incoming.resolutionResult.allComponents.each { component ->
                            if (component.id instanceof ModuleComponentIdentifier) {
                                def mv = component.moduleVersion
                                coords.add(new DependencyCoordinate(mv.group, mv.name, mv.version))
                            }
                        }
                    }
                }
                coords.collect { it.toString() }
            })
        }

        project.plugins.withId('java') {
            project.tasks.named('check').configure { it.dependsOn(project.tasks.named('checkDependencies')) }
        }
    }
}