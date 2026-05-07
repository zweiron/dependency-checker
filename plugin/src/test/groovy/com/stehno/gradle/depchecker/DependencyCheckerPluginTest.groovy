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

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Path

class DependencyCheckerPluginTest {

    @TempDir
    static Path projectDir

    // -------------------------------------------------------------------------
    // Task registration
    // -------------------------------------------------------------------------

    @Test
    void 'apply: registers checkDependencies task'() {
        def project = buildProject('reg-check-deps')
        assert project.tasks.findByName('checkDependencies') != null
    }

    @Test
    void 'apply: registers checkAvailability task'() {
        def project = buildProject('reg-check-avail')
        assert project.tasks.findByName('checkAvailability') != null
    }

    @Test
    void 'apply: both tasks are placed in the Verification group'() {
        def project = buildProject('groups')
        assert project.tasks.getByName('checkDependencies').group == 'Verification'
        assert project.tasks.getByName('checkAvailability').group  == 'Verification'
    }

    @Test
    void 'apply: works without the java plugin applied'() {
        // No java plugin → no configurations created → plugin must still apply cleanly
        Project project = ProjectBuilder.builder()
            .withProjectDir(projectDir.resolve('no-java').toFile())
            .build()
        project.apply plugin: DependencyCheckerPlugin

        assert project.tasks.findByName('checkDependencies') != null
        assert project.tasks.findByName('checkAvailability') != null
    }

    // -------------------------------------------------------------------------
    // configDeps provider (reads declared deps — no resolution, no network)
    // -------------------------------------------------------------------------

    @Test
    void 'configDeps: empty configurations property collects all project configuration names'() {
        def project = buildProject('all-configs') {
            dependencies {
                implementation 'commons-io:commons-io:2.4'
            }
        }

        CheckDependenciesTask task = project.tasks.getByName('checkDependencies') as CheckDependenciesTask
        // task.configurations defaults to [] (empty), so all config names must appear in the map
        Map<String, String> deps = task.configDeps.get()

        assert deps.containsKey('implementation')
        assert deps.containsKey('testImplementation')
    }

    @Test
    void 'configDeps: explicit configurations limits the result to those configs only'() {
        def project = buildProject('explicit-configs') {
            dependencies {
                implementation 'commons-io:commons-io:2.4'
                testImplementation 'junit:junit:4.12'
            }
        }

        CheckDependenciesTask task = project.tasks.getByName('checkDependencies') as CheckDependenciesTask
        project.checkDependencies {
            configurations = ['implementation']
        }

        Map<String, String> deps = task.configDeps.get()

        assert deps.keySet() == ['implementation'] as Set
        assert !deps.containsKey('testImplementation')
    }

    @Test
    void 'configDeps: dependency key uses group:name format without version'() {
        def project = buildProject('key-format') {
            dependencies {
                implementation 'commons-io:commons-io:2.4'
            }
        }

        CheckDependenciesTask task = project.tasks.getByName('checkDependencies') as CheckDependenciesTask
        project.checkDependencies {
            configurations = ['implementation']
        }

        Map<String, String> deps = task.configDeps.get()

        assert deps['implementation'] == 'commons-io:commons-io'
    }

    @Test
    void 'configDeps: multiple deps in one config are comma-joined'() {
        def project = buildProject('comma-join') {
            dependencies {
                implementation 'commons-io:commons-io:2.4'
                implementation 'junit:junit:4.12'
            }
        }

        CheckDependenciesTask task = project.tasks.getByName('checkDependencies') as CheckDependenciesTask
        project.checkDependencies {
            configurations = ['implementation']
        }

        Map<String, String> deps = task.configDeps.get()
        List<String> keys = deps['implementation'].split(',').toList()

        assert keys.size() == 2
        assert 'commons-io:commons-io' in keys
        assert 'junit:junit' in keys
    }

    @Test
    void 'configDeps: config with no dependencies maps to empty string'() {
        def project = buildProject('empty-config')  // no dependencies declared

        CheckDependenciesTask task = project.tasks.getByName('checkDependencies') as CheckDependenciesTask
        project.checkDependencies {
            configurations = ['implementation']
        }

        Map<String, String> deps = task.configDeps.get()

        assert deps['implementation'] == ''
    }

    @Test
    void 'configDeps: unknown configuration name maps to empty string'() {
        def project = buildProject('unknown-config')

        CheckDependenciesTask task = project.tasks.getByName('checkDependencies') as CheckDependenciesTask
        project.checkDependencies {
            configurations = ['doesNotExist']
        }

        Map<String, String> deps = task.configDeps.get()

        assert deps['doesNotExist'] == ''
    }

    // -------------------------------------------------------------------------
    // dependencyCoordinates provider
    // -------------------------------------------------------------------------

    @Test
    void 'dependencyCoordinates: returns empty list when no configurations exist'() {
        // No java plugin → no configurations → provider iterates nothing
        Project project = ProjectBuilder.builder()
            .withProjectDir(projectDir.resolve('dc-no-java').toFile())
            .build()
        project.apply plugin: DependencyCheckerPlugin

        CheckAvailabilityTask task = project.tasks.getByName('checkAvailability') as CheckAvailabilityTask

        assert task.dependencyCoordinates.get() == []
    }

    @Test
    void 'dependencyCoordinates: explicit configurations limits resolution to those configs only'() {
        // Exercises the non-empty branch of:
        //   task.configurations.get().empty ? all names : task.configurations.get()
        def project = buildProject('dc-explicit-configs')

        project.checkAvailability {
            configurations = ['compileClasspath']   // non-empty → uses explicit list
        }

        CheckAvailabilityTask task = project.tasks.getByName('checkAvailability') as CheckAvailabilityTask

        // No external deps declared, so resolution returns nothing but the root project
        // component which is filtered by instanceof ModuleComponentIdentifier → empty list
        assert task.dependencyCoordinates.get() == []
    }

    @Test
    void 'dependencyCoordinates: returns empty list when no module components are resolved'() {
        // java plugin creates resolvable configurations, but with no declared external
        // dependencies the resolution result contains only the root ProjectComponent,
        // which is filtered out by the instanceof ModuleComponentIdentifier check.
        def project = buildProject('dc-no-deps')  // java applied, no dependencies

        CheckAvailabilityTask task = project.tasks.getByName('checkAvailability') as CheckAvailabilityTask

        assert task.dependencyCoordinates.get() == []
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a ProjectBuilder project with the java plugin and DependencyCheckerPlugin
     * already applied. The optional closure is evaluated as a project configuration block.
     */
    private Project buildProject(String name, Closure configure = {}) {
        Project project = ProjectBuilder.builder()
            .withProjectDir(projectDir.resolve(name).toFile())
            .build()

        project.apply plugin: 'java'
        project.apply plugin: DependencyCheckerPlugin

        project.repositories {
            mavenCentral()
        }

        project.with(configure)

        project
    }
}
