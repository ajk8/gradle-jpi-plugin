/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jenkinsci.gradle.plugins.jpi

import java.text.SimpleDateFormat
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.Configuration

import java.util.jar.Manifest

import static org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME
import static org.jenkinsci.gradle.plugins.jpi.JpiPlugin.OPTIONAL_PLUGINS_DEPENDENCY_CONFIGURATION_NAME
import static org.jenkinsci.gradle.plugins.jpi.JpiPlugin.PLUGINS_DEPENDENCY_CONFIGURATION_NAME

/**
 * Encapsulates the Jenkins plugin manifest and its generation.
 *
 * @author Kohsuke Kawaguchi
 */
class JpiManifest extends HashMap<String, Object> {
    private final Project project

    JpiManifest(Project project) {
        this.project = project

        def conv = project.extensions.getByType(JpiExtension)
        def javaPluginConvention = project.convention.getPlugin(JavaPluginConvention)
        def classDir = javaPluginConvention.sourceSets.getByName(MAIN_SOURCE_SET_NAME).output.classesDir

        File pluginImpl = new File(classDir, 'META-INF/services/hudson.Plugin')
        if (pluginImpl.exists()) {
            this['Plugin-Class'] = pluginImpl.readLines('UTF-8')[0]
        }

        this['Group-Id'] = project.group
        this['Short-Name'] = conv.shortName
        this['Long-Name'] = conv.displayName
        this['Url'] = conv.url
        this['Compatible-Since-Version'] = conv.compatibleSinceVersion
        if (conv.sandboxStatus) {
            this['Sandbox-Status'] = conv.sandboxStatus
        }

        v = project.version
        if (v == Project.DEFAULT_VERSION) {
            v = '1.0-SNAPSHOT'
        }
        if (v.toString().endsWith('-SNAPSHOT')) {
            String dt = new SimpleDateFormat('MM/dd/yyyy HH:mm', Locale.default).format(new Date())
            v += " (private-$dt-${System.getProperty('user.name')})"
        }
        this['Plugin-Version'] = v

        this['Jenkins-Version'] = conv.coreVersion

        this['Mask-Classes'] = conv.maskClasses

        def dep = findDependencyProjects(project)
        if (dep.length() > 0) {
            this['Plugin-Dependencies'] = dep
        }

        if (conv.pluginFirstClassLoader) {
            this['PluginFirstClassLoader'] = true
        }

        // more TODO
/*
        if (project.getDevelopers() != null) {
            mainSection.addAttributeAndCheck(new Attribute("Plugin-Developers",getDevelopersForManifest()))
        }

        Boolean b = isSupportDynamicLoading()
        if (b!=null)
            mainSection.addAttributeAndCheck(new Attribute("Support-Dynamic-Loading",b.toString()))
*/
        // remove null values
        for (Iterator itr = this.entrySet().iterator(); itr.hasNext();) {
            if (itr.next().value == null) {
                itr.remove()
            }
        }
    }

    private static String findDependencyProjects(Project project) {
        def buf = new StringBuilder()

        listUpDependencies(project.configurations.getByName(PLUGINS_DEPENDENCY_CONFIGURATION_NAME), false, buf)
        listUpDependencies(project.configurations.getByName(OPTIONAL_PLUGINS_DEPENDENCY_CONFIGURATION_NAME), true, buf)

        buf.toString()
    }

    private static listUpDependencies(Configuration c, boolean optional, StringBuilder buf) {
        for (ResolvedArtifact a : c.resolvedConfiguration.resolvedArtifacts) {
            if (buf.length() > 0) {
                buf.append(',')
            }
            buf.append(a.moduleVersion.id.name)
            buf.append(':')
            buf.append(a.moduleVersion.id.version)
            if (optional) {
                buf.append('resolution:=optional')
            }
        }
    }

    void writeTo(File f) {
        def m = new Manifest()
        m.mainAttributes.putValue('Manifest-Version', '1.0')
        this.each { k, v -> m.mainAttributes.putValue(k, v.toString()) }
        f.withOutputStream { o -> m.write(o) }
    }
}
