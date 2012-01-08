/*
 * Copyright 2004-2011 the original author or authors.
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

import griffon.util.GriffonUtil
import griffon.util.Metadata
import static griffon.util.GriffonNameUtils.capitalize
import static griffon.util.GriffonNameUtils.uncapitalize

/**
 * Gant script that handles the creation of Griffon applications
 *
 * @author Graeme Rocher (Griffon 0.4)
 */

includeTargets << griffonScript('_GriffonCreateArtifacts')
includeTargets << griffonScript('CreateMvc')
includeTargets << griffonScript('Package')
includeTargets << griffonScript('IntegrateWith')

griffonAppName = ''

target(name: 'createApp', description: "Creates a Griffon application for the given name",
        prehook: null, posthook: null) {
    depends(appName, resolveFileType)

    loadArchetypeFor 'application'
    createApplicationProject()

    // Set the default version number for the application
    updateMetadata("app.version": griffonAppVersion ?: "0.1", "archetype.${archetypeName}": archetypeVersion)
    def cfg = new File("${basedir}/griffon-app/conf/BuildConfig.groovy")
    cfg.append("""
app.fileType = '$fileType'
app.defaultPackageName = '$defaultPackageName'
""")

    ant.replace(dir: "${basedir}/griffon-app/conf", includes: "**/*.*") {
        replacefilter(token: "@griffonAppName@", value: capitalize(griffonAppName))
        replacefilter(token: "@griffonAppVersion@", value: griffonAppVersion ?: "0.1")
    }

    event('StatusFinal', ["Created Griffon Application at $basedir"])
}

createProjectWithDefaults = {
    metadataFile = new File("${basedir}/application.properties")
    initProject()
    ant.replace(dir: "${basedir}/griffon-app/conf", includes: "**/*.*") {
        replacefilter(token: "@griffon.app.class.name@", value: appClassName)
        replacefilter(token: "@griffon.version@", value: griffonVersion)
        replacefilter(token: "@griffon.project.name@", value: griffonAppName)
        replacefilter(token: "@griffon.application.name@", value: uncapitalize(appClassName))
        replacefilter(token: "@griffon.project.key@", value: griffonAppName.replaceAll(/\s/, '.').toLowerCase())
    }

    ant.touch(file: metadataFile)

    // Create a message bundle to get the user started.
    ant.touch(file: "${basedir}/griffon-app/i18n/messages.properties")

    argsMap["params"][0] = griffonAppName
}

resetBaseDirectory = { String basedir ->
    // Update the build settings and reload the build configuration.
    griffonSettings.baseDir = new File(basedir)
    griffonSettings.loadConfig()

    // Reload the application metadata.
    metadataFile = new File("$basedir/${Metadata.FILE}")
    metadata = Metadata.getInstance(metadataFile)

    applicationConfigFile = new File(basedir, 'griffon-app/conf/Application.groovy')
    builderConfigFile = new File(basedir, 'griffon-app/conf/Builder.groovy')
    configFile = new File(basedir, 'griffon-app/conf/Config.groovy')

    // Reset the plugin stuff.
    pluginsHome = griffonSettings.projectPluginsDir.path
}

target(name: 'createPlugin', description: '',
        prehook: null, posthook: null) {
    depends(appName, resolveFileType)
    metadataFile = new File("${basedir}/application.properties")
    projectType = "plugin"
    initProject()

    // Rename the plugin descriptor.
    pluginName = GriffonUtil.getNameFromScript(griffonAppName)
    if (!(pluginName ==~ /[a-zA-Z][a-zA-Z0-9-]*/)) {
        println "Error: Specified plugin name [$griffonAppName] is invalid. Plugin names can only contain word characters separated by hyphens."
        exit 1
    }
    ant.move(
            file: "${basedir}/GriffonPlugin.groovy",
            tofile: "${basedir}/${pluginName}GriffonPlugin.groovy",
            overwrite: true)

    // Insert the name of the plugin into whatever files need it.
    ant.replace(dir: "${basedir}") {
        include(name: "*GriffonPlugin.groovy")
        include(name: "scripts/*")
        replacefilter(token: "@plugin.name@", value: pluginName)
        replacefilter(token: "@plugin.short.name@", value: GriffonUtil.getScriptName(pluginName))
        replacefilter(token: "@plugin.version@", value: griffonAppVersion ?: "0.1")
        replacefilter(token: "@griffon.version@", value: griffonVersion)
    }

    event('StatusFinal', ["Created plugin ${pluginName}"])
}

target(name: 'createArchetype', description: '',
        prehook: null, posthook: null) {
    depends(appName)
    metadataFile = new File("${basedir}/application.properties")
    ant.mkdir(dir: "${basedir}/griffon-app")
    ant.touch(file: metadataFile)

    updateAppProperties()

    griffonUnpack(dest: basedir, src: "griffon-shared-files.jar")
    griffonUnpack(dest: basedir, src: "griffon-archetype-files.jar")
    ant.unzip(src: "${basedir}/griffon-wrapper-files.zip", dest: basedir)
    ant.delete(file: "${basedir}/griffon-wrapper-files.zip", quiet: true)

    // Rename the archetype descriptor.
    archetypeName = GriffonUtil.getNameFromScript(griffonAppName)
    if (!(archetypeName ==~ /[a-zA-Z][a-zA-Z0-9-]*/)) {
        println "Error: Specified archetype name [$griffonAppName] is invalid. Archetype names can only contain word characters separated by hyphens."
        exit 1
    }
    if (archetypeName == 'default') {
        println "Error: Specified archetype name [$archetypeName] is invalid. You cannot override the default archetype provided by Griffon."
        exit 1
    }

    ant.move(
            file: "${basedir}/GriffonArchetype.groovy",
            tofile: "${basedir}/${archetypeName}GriffonArchetype.groovy",
            overwrite: true)

    ant.replace(dir: basedir) {
        include(name: '*GriffonArchetype.groovy')
        replacefilter(token: '@archetype.name@', value: archetypeName)
        replacefilter(token: '@archetype.short.name@', value: GriffonUtil.getScriptName(archetypeName))
        replacefilter(token: '@archetype.version@', value: griffonAppVersion ?: '0.1')
        replacefilter(token: '@griffon.version@', value: griffonVersion)
    }

    event('StatusFinal', ["Created archetype ${archetypeName}"])
}

target(name: 'initProject', description: "Initialise an application or plugin project",
        prehook: null, posthook: null) {
    depends(createStructure, updateAppProperties)

    griffonUnpack(dest: basedir, src: "griffon-shared-files.jar")
    griffonUnpack(dest: basedir, src: "griffon-$projectType-files.jar")
    ant.unzip(src: "${basedir}/griffon-wrapper-files.zip", dest: basedir)
    ant.delete(file: "${basedir}/griffon-wrapper-files.zip", quiet: true)

    ant.delete(quiet: true, failonerror: false) {
        if (fileType == '.java') {
            fileset(dir: "${basedir}/griffon-app/lifecycle", excludes: '*.java')
        } else {
            fileset(dir: "${basedir}/griffon-app/lifecycle", includes: '*.java')
        }
    }

    // make sure Griffon central repo is prepped for default plugin set installation
    griffonSettings.dependencyManager.parseDependencies {
        repositories {
            griffonCentral()
        }
    }
}

target(name: 'appName', description: "Evaluates the application name",
        prehook: null, posthook: null) {
    if (argsMap["params"]) {
        griffonAppName = argsMap["params"].join(" ")
    } else {
        String type = projectType == 'plugin' ? 'Plugin' : 'Application'
        ant.input(message: "$type name not specified. Please enter:",
                addProperty: "griffon.app.name")
        griffonAppName = ant.antProject.properties."griffon.app.name"
    }

    createDefaultPackage()

    if (!argsMap["inplace"]) {
        basedir = "${basedir}/${griffonAppName}"
        resetBaseDirectory(basedir)
    }

    if (argsMap["appVersion"]) {
        griffonAppVersion = argsMap["appVersion"]
    }

    appClassName = GriffonUtil.getClassNameRepresentation(griffonAppName)
}
