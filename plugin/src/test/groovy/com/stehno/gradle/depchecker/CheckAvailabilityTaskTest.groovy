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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Path

import static org.assertj.core.api.Assertions.*

class CheckAvailabilityTaskTest {

    @TempDir
    static Path projectDir

    /**
     * Reset any metaclass override on HttpHeadClient after each test so mocks never
     * leak between tests. HttpHeadClient.exists is a static call from @TypeChecked
     * (not @CompileStatic) code in CheckAvailabilityTask, so Groovy's metaclass
     * dispatch is still active at that call site and can be intercepted here.
     */
    @AfterEach
    void restoreHttpHeadClient() {
        HttpHeadClient.metaClass = null
    }

    // --- no repos configured ---

    @Test
    void 'checkAvailability: skipped when no repo URLs configured'() {
        // HttpHeadClient.exists must never be called — no mock needed
        def task = buildTask('no-repos') {
            dependencyCoordinates.set(['com.example:foo:1.0'])
        }

        task.checkAvailability()  // no exception, no HTTP call
    }

    // --- failOnMissing behaviour ---

    @Test
    void 'checkAvailability: all deps available does not throw even with failOnMissing=true'() {
        HttpHeadClient.metaClass.static.exists = { Collection urls, DependencyCoordinate coord -> true }

        def task = buildTask('all-pass') {
            repoUrls.set(['https://repo.example.com'])
            dependencyCoordinates.set(['com.example:foo:1.0', 'com.example:bar:2.0'])
            failOnMissing = true
        }

        task.checkAvailability()
    }

    @Test
    void 'checkAvailability: missing dep with failOnMissing=false does not throw'() {
        HttpHeadClient.metaClass.static.exists = { Collection urls, DependencyCoordinate coord -> false }

        def task = buildTask('missing-no-fail') {
            repoUrls.set(['https://repo.example.com'])
            dependencyCoordinates.set(['com.example:foo:1.0'])
            failOnMissing = false
        }

        task.checkAvailability()
    }

    @Test
    void 'checkAvailability: missing dep with failOnMissing=true throws RuntimeException'() {
        HttpHeadClient.metaClass.static.exists = { Collection urls, DependencyCoordinate coord -> false }

        def task = buildTask('missing-with-fail') {
            repoUrls.set(['https://repo.example.com'])
            dependencyCoordinates.set(['com.example:foo:1.0'])
            failOnMissing = true
        }

        try {
            task.checkAvailability()
            fail()
        } catch (RuntimeException ignored) {
            // expected
        }
    }

    // --- ignored filtering ---

    @Test
    void 'checkAvailability: ignored coords are not passed to HttpHeadClient'() {
        Set<String> checkedCoords = []
        HttpHeadClient.metaClass.static.exists = { Collection urls, DependencyCoordinate coord ->
            checkedCoords << coord.toString()
            true
        }

        def task = buildTask('ignored') {
            repoUrls.set(['https://repo.example.com'])
            dependencyCoordinates.set(['com.example:foo:1.0', 'com.example:bar:2.0'])
            ignored.set(['com.example:bar:2.0'])
        }

        task.checkAvailability()

        assertThat(checkedCoords)
                .contains('com.example:foo:1.0')
                .doesNotContain('com.example:bar:2.0')
    }

    @Test
    void 'checkAvailability: non-ignored missing dep still fails when failOnMissing=true'() {
        // bar is missing, foo is ignored — build must still fail because bar is unavailable
        HttpHeadClient.metaClass.static.exists = { Collection urls, DependencyCoordinate coord ->
            coord.name == 'foo'   // foo passes, bar fails
        }

        def task = buildTask('ignored-partial-fail') {
            repoUrls.set(['https://repo.example.com'])
            dependencyCoordinates.set(['com.example:foo:1.0', 'com.example:bar:2.0', 'com.example:baz:3.0'])
            ignored.set(['com.example:baz:3.0'])
            failOnMissing = true
        }

        try {
            task.checkAvailability()
            fail()
        } catch (RuntimeException ignored) {
            // expected — bar is unavailable and not ignored
        }
    }

    // --- mixed pass/fail in one run (exercises both branches of if (passed)) ---

    @Test
    void 'checkAvailability: logs both passed and failed when results are mixed'() {
        // foo passes, bar fails — both the logger.info (PASSED) and logger.error (FAILED)
        // branches inside list.each { if (passed) ... } must be exercised in a single run.
        HttpHeadClient.metaClass.static.exists = { Collection urls, DependencyCoordinate coord ->
            coord.name == 'foo'
        }

        def task = buildTask('mixed-results') {
            repoUrls.set(['https://repo.example.com'])
            dependencyCoordinates.set(['com.example:foo:1.0', 'com.example:bar:2.0'])
            failOnMissing = false   // don't throw so both results.each iterations complete
        }

        task.checkAvailability()
    }

    // --- coordinate parsing ---

    @Test
    void 'checkAvailability: parses group:name:version coordinate strings correctly'() {
        List<DependencyCoordinate> receivedCoords = []
        HttpHeadClient.metaClass.static.exists = { Collection urls, DependencyCoordinate coord ->
            receivedCoords << coord
            true
        }

        def task = buildTask('coord-parsing') {
            repoUrls.set(['https://repo.example.com'])
            dependencyCoordinates.set(['org.apache.commons:commons-lang3:3.12.0'])
        }

        task.checkAvailability()

        assertThat(receivedCoords)
                .containsOnly(new DependencyCoordinate('org.apache.commons', 'commons-lang3', '3.12.0'))
    }

    // --- helpers ---

    private static CheckAvailabilityTask buildTask(String name, Closure configure) {
        Project project = ProjectBuilder.builder()
            .withProjectDir(projectDir.resolve(name).toFile())
            .build()

        project.apply plugin: 'java'
        project.apply plugin: DependencyCheckerPlugin

        project.checkAvailability(configure)

        project.tasks.named('checkAvailability').get() as CheckAvailabilityTask
    }
}
