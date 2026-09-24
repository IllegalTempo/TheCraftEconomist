# Fabric Project Scaffold Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a buildable, content-free Fabric mod project for The Economist on Minecraft Java Edition 26.3.

**Architecture:** Use Fabric's official example project as the source of current Gradle conventions, then reduce it to one common `ModInitializer` and its metadata. The entry point delegates to a tested ordered module loader with an initially empty module list, so future feature packages remain independent. Gradle owns version selection and the Java 25 toolchain; no client or gameplay modules exist.

**Tech Stack:** Minecraft Java Edition 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Fabric Loom 1.17, Gradle 9.6.0, Java 25, JUnit Jupiter

**Spec:** `docs/superpowers/specs/2026-09-19-fabric-project-scaffold-design.md`

## Global Constraints

- Mod ID is exactly `theeconomist`.
- Maven group is exactly `com.jedts`.
- Main package is exactly `com.jedts.theeconomist`.
- Initial project version is exactly `0.1.0`.
- The project targets Minecraft Java Edition 26.3.
- The project compiles to Java 25 bytecode.
- No NPC, economy, item, block, recipe, command, mixin, client, service, or simulation code may be added.
- No license may be selected on the owner's behalf.
- The existing gameplay design in `README.md` must remain intact.

## Review Focus

- Java 8 is the machine's default launcher: verification must explicitly use a Java 21-or-newer bootstrap JVM and a Java 25 compile toolchain.
- Fabric metadata placeholders must be expanded in the built JAR rather than shipped literally.
- Official template examples must not leak into package names, metadata, resources, or the JAR.
- A common/server environment must not reference client-only classes.
- Build output must contain only the inert entry point and Fabric metadata, with no gameplay registrations.

---

### Task 1: Create and verify the Fabric scaffold

**Files:**
- Create: `.github/workflows/build.yml`
- Create: `.gitattributes`
- Create: `.gitignore`
- Create: `build.gradle`
- Create: `gradle.properties`
- Create: `settings.gradle`
- Create: `gradlew`
- Create: `gradlew.bat`
- Create: `gradle/wrapper/gradle-wrapper.jar`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `src/main/java/com/jedts/theeconomist/TheEconomistMod.java`
- Create: `src/main/java/com/jedts/theeconomist/api/module/TheEconomistModule.java`
- Create: `src/main/java/com/jedts/theeconomist/core/module/ModModules.java`
- Create: `src/main/java/com/jedts/theeconomist/core/module/ModuleLoader.java`
- Create: `src/test/java/com/jedts/theeconomist/core/module/ModuleLoaderTest.java`
- Create: `src/main/resources/fabric.mod.json`
- Modify: `README.md`

**Interfaces:**
- Consumes: Gradle properties `minecraft_version`, `loader_version`, `loom_version`, `fabric_version`, `mod_version`, and `maven_group`.
- Produces: Fabric entry point `com.jedts.theeconomist.TheEconomistMod`, mod ID `theeconomist`, and a remapped JAR under `build/libs/`.

- [ ] **Step 1: Download the official Fabric example project into a temporary directory**

Use the official `FabricMC/fabric-example-mod` archive as the wrapper and Gradle-structure source. Do not overwrite `README.md` or the design documents.

Expected result: the temporary directory contains the Gradle wrapper and current example configuration.

- [ ] **Step 2: Copy only the base build files into the workspace**

Copy the wrapper, `.gitattributes`, `.gitignore`, `build.gradle`, `gradle.properties`, and `settings.gradle`. Remove example sources, mixins, assets, metadata, and publishing configuration before adapting the files.

Expected result: the workspace has a conventional Fabric Gradle skeleton and no example package.

- [ ] **Step 3: Configure exact project versions and Java toolchain**

Set:

```properties
org.gradle.jvmargs=-Xmx1G
org.gradle.parallel=true
org.gradle.configuration-cache=false

minecraft_version=26.3
loader_version=0.19.5
loom_version=1.17-SNAPSHOT
fabric_version=0.161.0+26.3

mod_version=0.1.0
maven_group=com.jedts
archives_base_name=theeconomist
```

Pin the wrapper to Gradle 9.6.0 and configure a Java 25 toolchain. Add a toolchain resolver in `settings.gradle` only if the local environment cannot locate Java 25.

Expected result: all versions have one source of truth and compilation targets Java 25.

- [ ] **Step 4: Write failing module-loader tests**

Add JUnit tests that require modules to initialize in declaration order and require blank or duplicate module IDs to throw clear `IllegalArgumentException`s. Run the tests before adding the module types.

Expected result: test compilation fails because the module contract and loader do not exist.

- [ ] **Step 5: Add the modular bootstrap and inert entry point**

Create `src/main/java/com/jedts/theeconomist/TheEconomistMod.java`:

```java
package com.jedts.theeconomist;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TheEconomistMod implements ModInitializer {
    public static final String MOD_ID = "theeconomist";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModuleLoader.initialize(ModModules.all());
        LOGGER.info("The Economist foundation loaded.");
    }
}
```

Add the minimal `TheEconomistModule`, `ModuleLoader`, and `ModModules` types required by the tests. `ModModules.all()` returns an immutable empty list until real features are implemented.

Expected result: module tests pass and the common entry point performs no gameplay registration.

- [ ] **Step 6: Add minimal Fabric metadata**

Create `src/main/resources/fabric.mod.json` with the current schema, `id` set to `theeconomist`, version set to `${version}`, environment set to `*`, and main entry point set to `com.jedts.theeconomist.TheEconomistMod`. Declare dependencies on Fabric Loader, Minecraft 26.3, Java 25 or newer, and Fabric API. Do not reference an icon, client entry point, mixin, or access widener.

Expected result: every referenced file and class exists.

- [ ] **Step 7: Add build-only continuous integration**

Create `.github/workflows/build.yml` that checks out the repository, installs Java 25, sets up Gradle, and runs `./gradlew build` for pushes and pull requests. Do not upload releases or publish packages.

Expected result: CI performs the same build acceptance check as local development.

- [ ] **Step 8: Update project status and build instructions**

Change only the status portion of `README.md` to state that the Fabric foundation exists but gameplay is not implemented. Add `gradlew.bat build` and the `build/libs/` output location. Preserve all planned behavior sections.

Expected result: documentation matches the scaffold without claiming gameplay completion.

- [ ] **Step 9: Run the build with an appropriate bootstrap JVM**

Run:

```powershell
.\gradlew.bat --version
.\gradlew.bat clean build
```

Set `JAVA_HOME` for these commands to an installed Java 21-or-newer JDK if the default Java 8 launcher is selected. Allow Gradle to provision Java 25 if it is not installed.

Expected: Gradle reports version 9.6.0 and `BUILD SUCCESSFUL`.

- [ ] **Step 10: Inspect the source tree and built JAR**

Verify that the JAR contains `fabric.mod.json` and `com/jedts/theeconomist/TheEconomistMod.class`. Search both source and archive listings for `example`, `mixin`, client entry points, and gameplay registrations.

Expected: metadata and the inert class are present; forbidden template and gameplay content are absent; `${version}` is expanded to `0.1.0`.

- [ ] **Step 11: Record verification without creating a Git commit**

Report the build command, resulting JAR path, resolved versions, and inspection outcome. This directory is not currently a Git repository, so no commit is created and Git is not initialized without an explicit request.

Expected result: the user receives evidence that the blank scaffold builds successfully.
