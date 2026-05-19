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

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.*

class DependencyCheckResultsTest {

    private DependencyCheckResults results

    @BeforeEach
    void setUp() {
        results = new DependencyCheckResults()
    }

    // --- empty state ---

    @Test
    void 'hasDuplications: returns false when empty'() {
        assertThat(results.hasDuplications()).isFalse()
    }

    @Test
    void 'count: returns 0 when empty'() {
        assertThat(results.count()).isEqualTo(0)
    }

    @Test
    void 'each: does not iterate when empty'() {
        int calls = 0
        results.each { String config, String module -> calls++ }
        assertThat(calls).isEqualTo(0)
    }

    // --- putAt ---

    @Test
    void 'putAt: first insert for a config creates a new entry'() {
        results['implementation'] = 'commons-io:commons-io'
        assertThat(results.hasDuplications()).isTrue()
        assertThat(results.count()).isEqualTo(1)
    }

    @Test
    void 'putAt: subsequent insert for the same config appends'() {
        results['implementation'] = 'commons-io:commons-io'
        results['implementation'] = 'junit:junit'
        assertThat(results.count()).isEqualTo(2)
    }

    @Test
    void 'putAt: inserts into different configs are tracked independently'() {
        results['implementation'] = 'commons-io:commons-io'
        results['testImplementation'] = 'junit:junit'
        assertThat(results.count()).isEqualTo(2)
    }

    // --- hasDuplications ---

    @Test
    void 'hasDuplications: returns true after any insert'() {
        results['runtimeOnly'] = 'org.slf4j:slf4j-api'
        assertThat(results.hasDuplications()).isTrue()
    }

    // --- count ---

    @Test
    void 'count: sums across multiple values in one config'() {
        results['implementation'] = 'a:a'
        results['implementation'] = 'b:b'
        assertThat(results.count()).isEqualTo(2)
    }

    @Test
    void 'count: sums across multiple configs'() {
        results['implementation'] = 'a:a'
        results['implementation'] = 'b:b'
        results['testImplementation'] = 'c:c'
        assertThat(results.count()).isEqualTo(3)
    }

    // --- each ---

    @Test
    void 'each: visits a single entry correctly'() {
        results['runtimeOnly'] = 'org.postgresql:postgresql'

        List<List<String>> calls = []
        results.each { String config, String module -> calls << [config, module] }

        assertThat(calls).containsOnly(['runtimeOnly', 'org.postgresql:postgresql'])
    }

    @Test
    void 'each: visits every value for a config with multiple entries'() {
        results['implementation'] = 'commons-io:commons-io'
        results['implementation'] = 'junit:junit'

        Map<String, List<String>> visited = [:]
        results.each { String config, String module ->
            visited.computeIfAbsent(config) { [] } << module
        }

        assertThat(visited).containsExactly(entry('implementation',['commons-io:commons-io', 'junit:junit']))
    }

    @Test
    void 'each: visits all entries across multiple configs'() {
        results['implementation'] = 'commons-io:commons-io'
        results['testImplementation'] = 'junit:junit'
        results['testImplementation'] = 'org.slf4j:slf4j-api'

        Map<String, List<String>> visited = [:]
        results.each { String config, String module ->
            visited.computeIfAbsent(config) { [] } << module
        }

        assertThat(visited).containsExactly(entry('implementation', ['commons-io:commons-io']),
                entry('testImplementation',['junit:junit', 'org.slf4j:slf4j-api']))
    }
}
