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

import groovy.transform.CompileStatic
import groovy.transform.Immutable
import groovy.transform.TypeChecked
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

import javax.inject.Inject

import static java.lang.Boolean.FALSE

/**
 * Gradle build task used to check configured dependencies against a set of external artifact repository URLs to determine which
 * are not available in at least one of the repositories.
 *
 * Allows searched configurations to be limited by specifying a "configurations" property with a collection of configuration names to be searched.
 *
 * Missing artifacts may be ignored by specifying their coordinate values in the "ignored" collection property. This will cause the specified artifacts
 * to be ignored during searching.
 *
 * By default, missing dependencies in the remote repo, will not fail the build; however, this may be changed by setting the "failOnMissing"
 * property to "true". All dependencies will be checked before the build is failed (if any are missing).
 */
@TypeChecked
abstract class CheckAvailabilityTask extends DefaultTask {

    /** Configuration names to scan; empty means all resolvable configurations. */
    @Input abstract ListProperty<String> getConfigurations()

    /** Remote Maven repo base URLs to check against. Also settable via -PrepoUrls=url1,url2 on the CLI. */
    @Input abstract ListProperty<String> getRepoUrls()

    /** Coordinates in "group:name:version" form to skip. */
    @Input abstract ListProperty<String> getIgnored()

    @Input boolean failOnMissing = false

    /**
     * Pre-computed transitive dependency coordinates in "group:name:version" form, wired by
     * DependencyCheckerPlugin at configuration time so the task action never accesses project.
     */
    @Input abstract ListProperty<String> getDependencyCoordinates()

    @Inject abstract ProviderFactory getProviders()

    CheckAvailabilityTask() {
        group = 'Verification'
        description = 'Checks the availability of the required dependencies against a specified artifact repository.'
        configurations.convention([])
        repoUrls.convention([])
        ignored.convention([])
        dependencyCoordinates.convention([])
    }

    @TaskAction void checkAvailability() {
        Collection<String> activeRepos = collectRepoUrls()
        if (activeRepos) {
            Set<DependencyCoordinate> coords = dependencyCoordinates.get().collect { String s ->
                def parts = s.split(':')
                new DependencyCoordinate(parts[0], parts[1], parts[2])
            } as Set<DependencyCoordinate>

            Map<Boolean, List<DependencyCoordinate>> results = coords.findAll { !ignored.get().contains(it as String) }.groupBy { c ->
                HttpHeadClient.exists(activeRepos, c)
            }

            results.each { Boolean passed, List<DependencyCoordinate> list ->
                list.each { c ->
                    if (passed) {
                        logger.info "Availability check for ($c): PASSED"
                    } else {
                        logger.error "Availability check for ($c): FAILED"
                    }
                }
            }

            if (results[FALSE] && failOnMissing) {
                throw new RuntimeException('One or more dependencies were not resolvable from the configured repo urls.')
            }

        } else {
            logger.info 'Availability check SKIPPED since there are no configured repo URLs.'
        }
    }

    private Collection<String> collectRepoUrls() {
        def prop = providers.gradleProperty('repoUrls')
        prop.present ? (prop.get().split(',') as Collection<String>) : repoUrls.get()
    }

}

@Immutable
class DependencyCoordinate {
    String group
    String name
    String version

    @Override
    String toString() { "$group:$name:$version" }

    String toPathSuffix() {
        "${group.replaceAll('\\.', '/')}/${name}/${version}/${name}-${version}.jar"
    }
}

/**
 * Very simple HTTP client for verifying the existence of a URL on a remote server (using HEAD requests).
 */
@CompileStatic
class HttpHeadClient {

    static boolean exists(final Collection<String> baseUrls, final DependencyCoordinate coordinate) {
        baseUrls.any { u ->
            check("${u}/${coordinate.toPathSuffix()}")
        }
    }

    private static boolean check(final String url) {
        HttpURLConnection con = null
        try {
            con = (HttpURLConnection) new URL(url).openConnection()
            con.requestMethod = 'HEAD'
            con.responseCode == 200

        } catch (Exception ex) {
            ex.printStackTrace()
            false

        } finally {
            con?.disconnect()
        }
    }
}