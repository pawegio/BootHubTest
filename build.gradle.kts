import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.dokka.gradle.DokkaTask
import org.gradle.api.Task
import nl.javadude.gradle.plugins.license.LicenseExtension
import org.gradle.jvm.tasks.Jar
import groovy.util.Node
import org.gradle.plugins.signing.Sign
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.swing.*

plugins {
    application
    idea
    eclipse
    kotlin("jvm") version "1.2.30"
    id("org.jetbrains.dokka") version "0.9.16"
    id ("maven-publish")
    id ("com.jfrog.bintray") version "1.7.2"
    id ("net.saliman.properties") version "1.4.6"
    id ("com.github.ethankhall.semantic-versioning") version "1.1.0"
    id ("com.github.hierynomus.license") version "0.12.1"
}

val bootHubTestVersionMajor by project
val bootHubTestVersionMinor by project
val bootHubTestVersionPatch by project
val bootHubTestReleaseBuild by project
val releaseBuild = bootHubTestReleaseBuild.toString().toBoolean()
val bootHubTestVersion = "" + bootHubTestVersionMajor + "." + bootHubTestVersionMinor + "." + bootHubTestVersionPatch + (if(releaseBuild) "" else "-SNAPSHOT")

repositories {
    jcenter()
    mavenCentral()
}

apply {
    plugin("application")
    plugin("eclipse")
    plugin("idea")
    plugin("signing")
    plugin("org.jetbrains.dokka")
    plugin("com.github.hierynomus.license")
}
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
group = "com.pawegio.boothubtest"
version = bootHubTestVersion

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

license {
    header = file("license-header.txt")
    skipExistingHeaders = true
    ignoreFailures = false
}

tasks.withType<Sign> {
    sign(configurations.archives)
    onlyIf { gradle.taskGraph.allTasks.any{task: Task -> isPublishTask(task)} }
}

dependencies {
    compile(kotlin("reflect"))
    compile(kotlin("stdlib"))
    compile("org.slf4j:slf4j-api:1.7.21")
    runtime ("ch.qos.logback:logback-classic:1.1.7")
    testCompile("io.kotlintest:kotlintest:2.0.7")
    testCompile("ch.qos.logback:logback-classic:1.1.7")
}

tasks.withType<Jar> {
    manifest {
        attributes(mapOf(
            "Implementation-Title" to "bootHubTest",
            "Main-Class" to "com.pawegio.boothubtest.BootHubTestMain",
            "Implementation-Version" to bootHubTestVersion
        ))
    }
}

application {
    mainClassName = "com.pawegio.boothubtest.BootHubTestMain"
}

val sourcesJar = task<Jar>("sourcesJar") {
    dependsOn("classes")
    from(sourceSets("main").allSource)
    classifier = "sources"
}

val dokkaJar = task<Jar>("dokkaJar") {
    dependsOn("dokka")
    classifier = "javadoc"
    from((tasks.getByName("dokka") as DokkaTask).outputDirectory)
}

artifacts {
    add("archives", sourcesJar)
    add("archives", dokkaJar)
}
publishing {
    (publications) {
        "bootHubTest".invoke(MavenPublication::class) {
            from(components["java"])
            artifact(sourcesJar) { classifier = "sources" }
            artifact(dokkaJar) { classifier = "javadoc" }
            groupId = "com.pawegio.boothubtest"
            artifactId = project.name
            version = bootHubTestVersion

            pom.withXml {
                val root = asNode()
                root.appendNode("name", "Module ${project.name}")
                root.appendNode("description", "The ${project.name} artifact")
                root.appendNode("url", "https://github.com/pawegio/BootHubTest")

                val scm = root.appendNode("scm")
                scm.appendNode("url", "https://github.com/pawegio/BootHubTest")
                scm.appendNode("connection", "https://github.com/pawegio/BootHubTest.git")
                scm.appendNode("developerConnection", "https://github.com/pawegio/BootHubTest.git")

                val developers = root.appendNode("developers")
                var developer : Node
                developer = developers.appendNode("developer")
                developer.appendNode("id", "pawegio")
                developer.appendNode("name", "Paweł Gajda")
                developer.appendNode("email", "pawegio@gmail.com")

                val licenseNode = root.appendNode("licenses").appendNode("license")
                licenseNode.appendNode("name", "The Apache Software License, Version 2.0")
                licenseNode.appendNode("url", "http://www.apache.org/licenses/LICENSE-2.0.txt")
                licenseNode.appendNode("distribution", "repo")
            }
        }
    }
}


fun readPasswordFromConsole(title: String, prompt: String) : String{
    val panel = JPanel()
    val label = JLabel(prompt)
    val pass = JPasswordField(24)
    panel.add(label)
    panel.add(pass)
    val options = arrayOf("OK", "Cancel")
    val option = JOptionPane.showOptionDialog(null, panel, title,
            JOptionPane.NO_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, null)
    if(option != 0) throw InvalidUserDataException("Operation cancelled by the user.")
    return String(pass.password)
}

fun isPublishTask(task: Task): Boolean {
    return task.name.startsWith("publish")
}

gradle.taskGraph.whenReady {
    if (gradle.taskGraph.allTasks.any {task : Task -> isPublishTask(task)}) {
        val signingKeyId = propertyOrElse("signingKeyId", "")
        val signingSecretKeyRingFile = propertyOrElse("signingSecretKeyRingFile", "")
        if(signingKeyId.isEmpty() || signingSecretKeyRingFile.isEmpty())
            throw InvalidUserDataException("Please configure your signing credentials in gradle-local.properties.")
        val password = readPasswordFromConsole("Please enter your PGP credentials", "PGP Private Key Password")
        allprojects { ext["signing.keyId"] = signingKeyId }
        allprojects { ext["signing.secretKeyRingFile"] = signingSecretKeyRingFile }
        allprojects { ext["signing.password"] = password }
    }
}

bintray {
    user = propertyOrElse("bintrayUser", "unknownUser")
    key = propertyOrElse("bintrayKey", "unknownKey")
    setPublications("bootHubTest")
    with(pkg) {
        repo = "maven"
        name = "bootHubTest"
        userOrg = "pawegio"
        setLicenses("Apache-2.0")
        vcsUrl = "https://github.com/pawegio/BootHubTest.git"
        with(version) {
            name = bootHubTestVersion
            desc = "bootHubTest $bootHubTestVersion"
            released = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZZ"))
            vcsTag = bootHubTestVersion
            with(gpg) {
                sign = true
            }
        }
    }
}

fun propertyOrElse(propName: String, defVal: String) : String = if(project.hasProperty(propName)) (project.property(propName) as String) else defVal
fun sourceSets(name: String) = the<JavaPluginConvention>().sourceSets.getByName(name)
