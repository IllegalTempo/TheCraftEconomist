# Fabric Project Scaffold Design

## Purpose

Create the buildable technical foundation for The Economist without implementing any gameplay. The result must load as an inert Fabric mod and give later feature work a conventional, maintainable project structure.

The scaffold exists to prove that the selected Minecraft, Fabric, Gradle, and Java versions work together. It is not the first gameplay milestone.

## Scope

The scaffold includes:

- A Gradle wrapper and Fabric Loom build
- Minecraft Java Edition 26.3 dependencies
- Fabric Loader 0.19.5 or a newer 26.3-compatible stable patch if required to resolve the build
- Fabric API 0.161.0+26.3
- A Java 25 toolchain declaration
- Fabric mod metadata for `theeconomist`
- One common/server-safe Fabric entry point
- A small, tested module contract and ordered module loader for future features
- Standard source and resource directories
- A repository `.gitignore`
- Minimal project metadata in the existing README
- A repeatable build verification command

The scaffold excludes:

- Citizens or other entities
- Currency, items, blocks, tabs, recipes, loot tables, or tags
- Trading, markets, employment, households, births, services, or countries
- Commands, configuration screens, menus, networking, or saved world state
- Client-only initialization or rendering
- Mixins and access wideners
- Data generation
- Third-party runtime libraries beyond Fabric API
- A generated project icon
- A license decision
- Publishing configuration for Maven, Modrinth, CurseForge, or GitHub Releases

## Selected approach

Use the official Fabric example project as the structural reference and adapt it to Minecraft 26.3. Keep only the files needed for a minimal common mod.

This is preferred over Fabric CLI generation because it does not add a Node or CLI dependency to the project. It is preferred over a wholly manual build because the official template reflects current Fabric Loom conventions.

The official template is a reference, not content to preserve. Example classes, example mixins, sample assets, and template publishing metadata must not remain in the final scaffold.

## Project identity

| Field | Value |
| --- | --- |
| Display name | The Economist |
| Mod ID | `theeconomist` |
| Maven group | `com.jedts` |
| Java package | `com.jedts.theeconomist` |
| Initial version | `0.1.0` |
| Environment | All (`*`) |
| Main entry point | `com.jedts.theeconomist.TheEconomistMod` |

The Java class implements Fabric's `ModInitializer`. Its initializer delegates to an empty, explicit module list and changes no game behavior. A single informational log line is allowed because it verifies that Fabric invoked the entry point.

## Toolchain

The target toolchain is:

- Minecraft Java Edition 26.3
- Fabric Loom 1.17
- Fabric Loader 0.19.5
- Fabric API 0.161.0+26.3
- Gradle 9.6.0
- Java language and bytecode level 25

Gradle must declare a Java 25 toolchain. The developer machine currently exposes Java 8 as its default `java` command and Java 21 as `javac`, so the project cannot assume the default runtime is usable. Verification may launch the Gradle wrapper with an explicitly discovered Java 21-or-newer bootstrap JVM, while Gradle provisions or locates Java 25 for compilation through its toolchain support.

Dependency versions live in `gradle.properties` rather than being repeated throughout `build.gradle`. The Gradle wrapper pins the Gradle distribution.

## File layout

```text
TheEconomist/
├── .github/
│   └── workflows/
│       └── build.yml
├── docs/
│   └── superpowers/
│       └── specs/
│           └── 2026-09-19-fabric-project-scaffold-design.md
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── src/
│   └── main/
│       ├── java/
│       │   └── com/jedts/theeconomist/
│       │       ├── TheEconomistMod.java
│       │       ├── api/module/TheEconomistModule.java
│       │       └── core/module/
│       │           ├── ModModules.java
│       │           └── ModuleLoader.java
│       └── resources/
│           └── fabric.mod.json
├── src/test/java/com/jedts/theeconomist/core/module/
│   └── ModuleLoaderTest.java
├── .gitattributes
├── .gitignore
├── build.gradle
├── gradle.properties
├── gradlew
├── gradlew.bat
├── README.md
└── settings.gradle
```

`LICENSE` is intentionally absent until the project owner chooses one. Example client, test, mixin, and data-generation trees are intentionally absent.

## Build configuration

`settings.gradle` defines plugin repositories required by Fabric Loom and Maven Central. If automatic JDK provisioning requires a Gradle toolchain resolver plugin, it is configured here and used only for build-time toolchain discovery.

`build.gradle`:

- Applies Fabric Loom and Maven Publish only if the official minimal template requires the latter; otherwise publishing is omitted
- Sets group and version from `gradle.properties`
- Uses Maven Central
- Depends on Minecraft, Fabric Loader, and Fabric API
- Configures Java 25 through Gradle toolchains
- Includes source JAR generation if supported by the template without extra configuration
- Expands the project version into `fabric.mod.json`
- Produces reproducible UTF-8 Java compilation
- Does not define release publishing, signing, or upload tasks

`fabric.mod.json`:

- Uses the current Fabric schema version supported by Loader 0.19.5
- Identifies the mod as `theeconomist`
- Reads the version inserted by Gradle
- Declares the common entry point
- Declares compatibility with Minecraft 26.3, Java 25 or newer, Fabric Loader, and Fabric API
- Contains a concise description consistent with the README
- Does not reference an icon, mixin file, access widener, client entry point, or nonexistent contact URL

## Continuous integration

The basic project includes one GitHub Actions workflow because it catches build drift without adding runtime behavior. It checks out the repository, installs Java 25, validates the Gradle wrapper when the official validation action supports Gradle 9.6, and runs the build on Windows-independent Linux infrastructure.

The workflow triggers on pushes and pull requests. It does not publish artifacts or create releases.

If a current wrapper-validation action does not support the selected Gradle release, the workflow omits that action rather than pinning an obsolete Gradle version. The build itself remains mandatory.

## README change

The existing design README remains the primary project overview. Only its project-status section changes:

- State that the Fabric foundation exists but gameplay is not implemented
- List the build prerequisites
- Document the local build command
- Identify the expected development JAR location

No planned gameplay behavior is removed or marked complete.

## Error handling and failure boundaries

The scaffold has no gameplay error paths. Build failures must remain explicit:

- An unsupported Java runtime produces a clear Gradle JVM or toolchain error.
- An unavailable dependency fails dependency resolution; versions are not silently substituted except for an explicitly selected newer compatible patch.
- Invalid mod metadata fails Fabric or build validation.
- The entry point logs through SLF4J and does not write directly to standard output.

The entry point must not catch broad exceptions because it performs no fallible work.

## Verification

The scaffold is complete only when:

1. The Gradle wrapper reports Gradle 9.6.0.
2. `gradlew.bat build` completes successfully with a Java 25 compiler toolchain.
3. Unit tests prove that modules initialize in declared order and that invalid or duplicate module IDs fail clearly.
4. The generated JAR exists under `build/libs/` and contains `fabric.mod.json`, `TheEconomistMod.class`, and the module infrastructure.
5. The JAR contains no example package, test classes, mixin configuration, gameplay registrations, or client entry point.
6. `fabric.mod.json` contains the resolved project version rather than an unexpanded placeholder.
7. The source tree contains no NPC, economy, item, block, recipe, service, or simulation implementation.
8. The existing README's design content remains intact.

Launching a graphical Minecraft client is not required for scaffold acceptance. The successful Loom build and JAR inspection are the bounded verification for this phase.

## Future extension points

Later milestones may add packages such as `citizen`, `economy`, `settlement`, and `contract`, but the scaffold does not create empty speculative feature packages. Each feature will implement `TheEconomistModule` and be added once to `ModModules` in dependency order.

The common entry point remains the composition root. `ModModules` is the readable inventory of enabled modules, while `ModuleLoader` owns generic validation and ordered initialization. Feature internals stay inside focused packages instead of accumulating in the entry-point class.
