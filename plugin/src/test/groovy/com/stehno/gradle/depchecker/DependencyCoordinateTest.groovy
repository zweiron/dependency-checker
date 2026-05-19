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

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.*

class DependencyCoordinateTest {

    // --- toString ---

    @Test
    void 'toString: produces group:name:version'() {
        def coord = new DependencyCoordinate('org.postgresql', 'postgresql', '9.4.1207')
        assertThat(coord.toString()).isEqualTo('org.postgresql:postgresql:9.4.1207')
    }

    @Test
    void 'toString: preserves dots in group'() {
        def coord = new DependencyCoordinate('org.apache.commons', 'commons-lang3', '3.12.0')
        assertThat(coord.toString()).isEqualTo('org.apache.commons:commons-lang3:3.12.0')
    }

    // --- toPathSuffix ---

    @Test
    void 'toPathSuffix: group with no dots is unchanged'() {
        def coord = new DependencyCoordinate('junit', 'junit', '4.12')
        assertThat(coord.toPathSuffix()).isEqualTo('junit/junit/4.12/junit-4.12.jar')
    }

    @Test
    void 'toPathSuffix: single dot in group becomes a slash'() {
        def coord = new DependencyCoordinate('org.postgresql', 'postgresql', '9.4.1207')
        assertThat(coord.toPathSuffix()).isEqualTo('org/postgresql/postgresql/9.4.1207/postgresql-9.4.1207.jar')
    }

    @Test
    void 'toPathSuffix: multiple dots in group each become a slash'() {
        def coord = new DependencyCoordinate('org.apache.commons', 'commons-lang3', '3.12.0')
        assertThat(coord.toPathSuffix()).isEqualTo('org/apache/commons/commons-lang3/3.12.0/commons-lang3-3.12.0.jar')
    }

    @Test
    void 'toPathSuffix: dots in name and version are not replaced'() {
        def coord = new DependencyCoordinate('junit', 'junit', '4.12')
        // name 'junit' has no dots — version '4.12' has a dot that must survive unchanged
        assertThat(coord.toPathSuffix()).endsWith('junit-4.12.jar')
    }

    @Test
    void 'toPathSuffix: dots in name are not replaced'() {
        // artifactId with a dot should pass through as-is
        def coord = new DependencyCoordinate('org.example', 'my.artifact', '1.0')
        assertThat(coord.toPathSuffix()).isEqualTo('org/example/my.artifact/1.0/my.artifact-1.0.jar')
    }
}
