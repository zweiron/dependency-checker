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
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Path

class CheckDependenciesTaskTest {

    @TempDir
    static Path projectDir

    @BeforeEach
    void before() {
        TestResultListener.clear()
    }

    @Test
    void 'checkDependencies: no dependencies'() {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir.resolve("no-deps").toFile()).build()

        project.apply plugin: 'java'
        project.apply plugin: DependencyCheckerPlugin

        project.repositories {
            mavenCentral()
        }

        project.dependencies {
        }

        project.checkDependencies {
            resultListenerClass = 'com.stehno.gradle.depchecker.TestResultListener'
        }

        (project.tasks.getByName('checkDependencies') as CheckDependenciesTask).checkDependencies()

        assert !TestResultListener.hasDuplicates()
    }

    @Test
    void 'checkDependencies: normal'() {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir.resolve("normal").toFile()).build()

        project.apply plugin: 'java'
        project.apply plugin: DependencyCheckerPlugin

        project.repositories {
            mavenCentral()
        }

        project.dependencies {
        }

        (project.tasks.getByName('checkDependencies') as CheckDependenciesTask).checkDependencies()

        assert !TestResultListener.hasDuplicates()
    }

    @Test
    void 'checkDependencies: without duplicates'() {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir.resolve("no-dups").toFile()).build()

        project.apply plugin: 'java'
        project.apply plugin: DependencyCheckerPlugin

        project.repositories {
            mavenCentral()
        }

        project.dependencies {
            implementation('com.stehno.vanilla:vanilla-core:0.2.0') {
                exclude group: 'org.codehaus.groovy', module: 'groovy-all'
            }
            implementation 'commons-io:commons-io:2.4'

            runtimeOnly 'org.postgresql:postgresql:9.4.1207'

            testImplementation 'junit:junit:4.12'
            testImplementation('com.stehno.vanilla:vanilla-core:0.2.0') {
                exclude group: 'org.codehaus.groovy', module: 'groovy-all'
            }
        }

        project.checkDependencies {
            resultListenerClass = 'com.stehno.gradle.depchecker.TestResultListener'
        }

        (project.tasks.getByName('checkDependencies') as CheckDependenciesTask).checkDependencies()

        assert !TestResultListener.hasDuplicates()
    }

    @Test
    void 'checkDependencies: with duplicates'() {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir.resolve("with-dups").toFile()).build()

        project.apply plugin: 'java'
        project.apply plugin: DependencyCheckerPlugin

        project.repositories {
            mavenCentral()
        }

        project.dependencies {
            implementation('com.stehno.vanilla:vanilla-core:0.2.0') {
                exclude group: 'org.codehaus.groovy', module: 'groovy-all'
            }
            implementation 'commons-io:commons-io:2.4'
            implementation 'commons-io:commons-io:2.3'

            runtimeOnly 'org.postgresql:postgresql:9.4.1207'

            testImplementation 'junit:junit:4.12'
            testImplementation 'junit:junit:4.10'
            testImplementation('com.stehno.vanilla:vanilla-core:0.2.0') {
                exclude group: 'org.codehaus.groovy', module: 'groovy-all'
            }
        }

        project.checkDependencies {
            resultListenerClass = 'com.stehno.gradle.depchecker.TestResultListener'
        }

        try {
            (project.tasks.getByName('checkDependencies') as CheckDependenciesTask).checkDependencies()
            Assert.fail()
        } catch (RuntimeException rex){
            // success
        }

        assert TestResultListener.hasDuplicates()
        assert TestResultListener.duplicatesFor('implementation').size() == 1
        assert TestResultListener.duplicatesFor('implementation').contains('commons-io:commons-io')
        assert TestResultListener.duplicatesFor('runtimeOnly').size() == 0
        assert TestResultListener.duplicatesFor('testImplementation').size() == 1
        assert TestResultListener.duplicatesFor('testImplementation').contains('junit:junit')
    }

    @Test @Disabled // FIXME: put this back when the check funx is back in
    void 'check depends on checkDependencies'() {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir.resolve("dependency").toFile()).build()

        project.apply plugin: 'java'
        project.apply plugin: DependencyCheckerPlugin

        assert project.tasks['check'].dependsOn.contains(project.tasks['checkDependencies'])
    }

    @Test
    void 'checkDependencies: with duplicates (filtered)'() {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir.resolve("filtered").toFile()).build()

        project.apply plugin: 'java'
        project.apply plugin: DependencyCheckerPlugin

        project.repositories {
            mavenCentral()
        }

        project.dependencies {
            implementation('com.stehno.vanilla:vanilla-core:0.2.0') {
                exclude group: 'org.codehaus.groovy', module: 'groovy-all'
            }
            implementation 'commons-io:commons-io:2.4'
            implementation 'commons-io:commons-io:2.3'

            runtimeOnly 'org.postgresql:postgresql:9.4.1207'

            testImplementation 'junit:junit:4.12'
            testImplementation 'junit:junit:4.10'
            testImplementation('com.stehno.vanilla:vanilla-core:0.2.0') {
                exclude group: 'org.codehaus.groovy', module: 'groovy-all'
            }
        }

        project.checkDependencies {
            configurations = ['runtimeOnly', 'testImplementation']
            resultListenerClass = 'com.stehno.gradle.depchecker.TestResultListener'
        }

        try {
            (project.tasks.getByName('checkDependencies') as CheckDependenciesTask).checkDependencies()
            Assert.fail()
        } catch (RuntimeException rex){
            // success
        }

        Assertions.assertTrue(TestResultListener.hasDuplicates())
        assert TestResultListener.duplicatesFor('implementation').size() == 0
        assert TestResultListener.duplicatesFor('runtimeOnly').size() == 0
        assert TestResultListener.duplicatesFor('testImplementation').size() == 1
        assert TestResultListener.duplicatesFor('testImplementation').contains('junit:junit')
    }
}
