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

    @Test
    void 'checkDependencies: with all duplicates ignored'() {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir.resolve("all-ignored").toFile()).build()

        project.apply plugin: 'java'
        project.apply plugin: DependencyCheckerPlugin

        project.repositories {
            mavenCentral()
        }

        project.dependencies {
            implementation 'commons-io:commons-io:2.4'
            implementation 'commons-io:commons-io:2.3'

            testImplementation 'junit:junit:4.12'
            testImplementation 'junit:junit:4.10'
        }

        project.checkDependencies {
            ignored = ['commons-io:commons-io', 'junit:junit']
            resultListenerClass = 'com.stehno.gradle.depchecker.TestResultListener'
        }

        (project.tasks.getByName('checkDependencies') as CheckDependenciesTask).checkDependencies()

        assert !TestResultListener.hasDuplicates()
    }

    @Test
    void 'checkDependencies: with partially ignored duplicates'() {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir.resolve("partial-ignored").toFile()).build()

        project.apply plugin: 'java'
        project.apply plugin: DependencyCheckerPlugin

        project.repositories {
            mavenCentral()
        }

        project.dependencies {
            implementation 'commons-io:commons-io:2.4'
            implementation 'commons-io:commons-io:2.3'  // ignored

            testImplementation 'junit:junit:4.12'
            testImplementation 'junit:junit:4.10'       // not ignored — should still be detected
        }

        project.checkDependencies {
            ignored = ['commons-io:commons-io']
            resultListenerClass = 'com.stehno.gradle.depchecker.TestResultListener'
        }

        try {
            (project.tasks.getByName('checkDependencies') as CheckDependenciesTask).checkDependencies()
            Assertions.fail()
        } catch (RuntimeException rex) {
            // expected
        }

        assert TestResultListener.hasDuplicates()
        assert TestResultListener.duplicatesFor('implementation').size() == 0
        assert TestResultListener.duplicatesFor('testImplementation').size() == 1
        assert TestResultListener.duplicatesFor('testImplementation').contains('junit:junit')
    }

    @Test
    void 'checkDependencies: with multiple occurrences of same duplicate key'() {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir.resolve("multi-dup").toFile()).build()

        project.apply plugin: 'java'
        project.apply plugin: DependencyCheckerPlugin

        project.repositories {
            mavenCentral()
        }

        project.dependencies {
            // three declarations of the same group:name — expect two duplicates recorded
            implementation 'commons-io:commons-io:2.4'
            implementation 'commons-io:commons-io:2.3'
            implementation 'commons-io:commons-io:2.2'
        }

        project.checkDependencies {
            resultListenerClass = 'com.stehno.gradle.depchecker.TestResultListener'
        }

        try {
            (project.tasks.getByName('checkDependencies') as CheckDependenciesTask).checkDependencies()
            Assertions.fail()
        } catch (RuntimeException rex) {
            // expected
        }

        assert TestResultListener.hasDuplicates()
        assert TestResultListener.duplicatesFor('implementation').size() == 2
        assert TestResultListener.duplicatesFor('implementation').every { it == 'commons-io:commons-io' }
    }

    @Test
    void 'checkDependencies: null resultListenerClass produces null listener without error'() {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir.resolve("null-listener").toFile()).build()

        project.apply plugin: 'java'
        project.apply plugin: DependencyCheckerPlugin

        project.repositories {
            mavenCentral()
        }

        // Setting resultListenerClass to null exercises the ternary false branch:
        // ResultListener resultListener = resultListenerClass ? ... : null
        project.checkDependencies {
            resultListenerClass = null
        }

        (project.tasks.getByName('checkDependencies') as CheckDependenciesTask).checkDependencies()
        // No duplicates, null listener — null-safe ?.duplicated call is never triggered,
        // but the null assignment path at line 55 is covered.
    }

    @Test
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
