# Make Myster Modules Compatible

## 1. Summary

Make Myster compatible with the Java Platform Module System (JPMS) in a staged way: first stop producing a misleading shaded artifact, define a stable `com.myster` module boundary for the current single Maven artifact, and then make packaging work with `jpackage`/`jlink` by resolving dependencies that are automatic modules or create split packages on the module path.

## 2. Non-goals

- Do not split the source tree into multiple Maven modules in this milestone.
- Do not publish `com.general.*` as a standalone Maven artifact in this milestone.
- Do not redesign the legacy plugin API beyond the minimum needed to keep existing plugin entrypoints loadable.
- Do not attempt strong encapsulation immediately; the first descriptor should preserve current test and plugin reachability.
- Do not replace the private types, networking, search, or UI architecture as part of this work.

## 3. Assumptions & open questions

- Assumption: "modules compatible" means JPMS/module-path compatibility for the Myster application and its packaging, not only compatibility between legacy plugin jars.
- Assumption: the first useful target is one named application module, `com.myster`, because the repo is currently one Maven artifact with packages under `com.general.*` and `com.myster.*`.
- Open question: should the first delivery stop at a stable automatic module/classpath package, or should it require a fully linked modular `jpackage` image? The latter is materially larger because automatic dependencies cannot be used by `jlink`.
- Open question: should external plugin compatibility remain "plugins can use many public Myster classes", or should a narrow supported plugin API be declared now?
- Open question: should the MP3 metadata implementation stay on Apache Tika if Tika remains awkward on the module path, or is replacing it acceptable?

## 4. Proposed design

Use a staged compatibility path.

Stage 1 fixes the current artifact problem and establishes a stable module identity. The shaded `bin/MysterBuild.jar` currently contains a dependency module descriptor from BouncyCastle, so `jar --describe-module --file bin/MysterBuild.jar --release 9` identifies it as `org.bouncycastle.pkix` instead of Myster. The shaded jar must exclude dependency `module-info.class` files and advertise a stable `Automatic-Module-Name: com.myster` if it remains as a classpath/fat-jar distribution.

Stage 2 adds a real `src/main/java/module-info.java` for the existing source tree. This module should be named `com.myster` and initially export all current application packages so tests, plugin code, and existing public reachability do not break during the migration. Tightening exports should be deferred until a supported plugin API is documented.

Stage 3 makes the runtime packaging modular. The current `jpackage` setup uses `--main-jar` against the shaded jar and manually adds only a few JDK modules. A modular package should use `--module com.myster/com.myster.Myster` and a module path containing the app jar plus runtime dependencies. This cannot be completed cleanly until non-modular dependencies are handled.

The dependency blockers are real:

- `jdeps` sees JDK module usage beyond the current `jpackage` allow-list: `java.datatransfer`, `java.desktop`, `java.logging`, `java.naming`, `java.prefs`, `java.sql`, `java.xml`, and `jdk.unsupported`.
- Several runtime dependencies are automatic modules: `org.apache.tika.core`, `org.apache.tika.parser.audiovideo`, `com.drew.metadata`, `xmpcore`, `msgpack.core`, `javax.jmdns`, and `com.simtechdata.waifupnp`.
- `jlink` rejects automatic modules.
- Tika is worse than a normal automatic-module case because `tika-core` and `tika-parser-audiovideo-module` create a split package on the module path (`org.apache.tika.detect`), so they cannot simply be required by `com.myster`.

The recommended implementation order is therefore:

- First make the existing shaded/classpath artifact honest and stable.
- Then add the `com.myster` descriptor and compile/test it, using a hybrid classpath/module-path only if necessary.
- Then resolve non-modular dependencies before converting `jpackage` to a fully modular image.

## 5. Architecture connections

Myster currently has logical subsystems but one Maven artifact. JPMS should initially describe the artifact boundary, not invent a finer-grained architecture. The most important compatibility connection is between the app module and plugins: plugins currently load as jars from a directory, are expected to contain `com.myster.plugins.Main`, and may reference public Myster classes that used to be globally visible on the classpath.

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| `com.myster` JPMS module | `src/main/java/module-info.java` | Maven compiler, Java launcher, jpackage | All current `com.myster.*` and `com.general.*` packages |
| Stable automatic module manifest | `maven-jar-plugin` / `maven-shade-plugin` manifest config | Legacy classpath/fat-jar users and module-path consumers | `bin/MysterBuild.jar` |
| Modular packaging path | `pom.xml` jpackage/dependency plugin config | Release packaging | Current `jpackage-maven-plugin` execution |
| Dependency module audit | Maven build and CI checks | Implementation and release validation | Runtime deps in `pom.xml` |
| Plugin compatibility contract | `com.myster.plugin`, exported packages, plugin loader | External plugin jars | `PluginLoader`, `JarClassLoader`, `MysterPlugin` |
| Metadata dependency decision | `TikaAudioMetadataProvider` or replacement provider | File metadata extraction | `Myster.createMetadataProvider()` and MP3 metadata tests |

The data flow stays the same at runtime. `com.myster.Myster` remains the entrypoint, creates the existing managers and UI, and starts server/client services. The module descriptor only changes how the JVM resolves packages and dependencies. For plugins, the loader still finds a jar entrypoint by name, instantiates it, and calls `MysterPlugin.pluginInit()`, but the classes the plugin can legally access must now be packages exported by `com.myster`.

New or changed contracts:

- Module name: `com.myster`.
- Legacy shaded artifact manifest: `Automatic-Module-Name: com.myster`.
- Plugin entrypoint remains `com.myster.plugins.Main` implementing `com.myster.plugin.MysterPlugin`.
- Export contract starts broad for compatibility; future work can reduce exports after supported plugin packages are documented.

## 6. Key decisions & edge cases

- Use a single module first. Splitting `com.general.*` into a separate module is attractive, but it would combine a JPMS migration with a source ownership refactor.
- Preserve broad exports initially. This keeps tests and plugins viable while module-path build mechanics are stabilized.
- Treat Tika as the main blocker for full modular packaging. The split package means a simple `requires org.apache.tika.core; requires org.apache.tika.parser.audiovideo;` descriptor is not enough.
- Keep the shaded jar as a legacy distribution if needed, but do not use it as the modular jpackage input.
- Service binding matters for SLF4J. The JUL provider module `org.slf4j.jul` must be included explicitly or via service binding so Tika logging does not fall back to a missing provider.
- Resource loading should be smoke-tested from a named module. Icons and `logging.properties` are loaded via classloader/class resources, while the currently disabled `ResourceBundle` path would need package openness if revived.
- Tests may need Maven module-path configuration. If broad exports are not enough for test execution, use Maven test patching rather than weakening production code.

## 7. Acceptance criteria

- [ ] `bin/MysterBuild.jar` no longer describes itself as a third-party module such as `org.bouncycastle.pkix`.
- [ ] Myster has a stable module identity of `com.myster`.
- [ ] The app compiles with a `module-info.java` descriptor or, for a smaller first delivery, the plan explicitly documents why the descriptor is deferred.
- [ ] `jdeps` output for Myster is checked into the implementation summary or otherwise recorded, including JDK modules needed by packaging.
- [ ] Existing unit tests pass.
- [ ] A launch smoke test verifies GUI startup or server-mode startup from the built artifact.
- [ ] Legacy plugin loading is either verified with a fixture plugin or explicitly documented as unsupported pending a plugin API follow-up.
- [ ] If modular `jpackage` is in scope, the package is built from `--module com.myster/com.myster.Myster`, not from the shaded `--main-jar` path.
- [ ] Automatic-module and split-package dependency blockers are resolved or called out as remaining blockers.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

## 8. Affected files / classes

- `pom.xml` - update compiler/module-path behavior, jar/shade manifest handling, dependency copying, and jpackage configuration.
- New `src/main/java/module-info.java` - declare the initial `com.myster` JPMS descriptor.
- `src/main/java/com/myster/plugin/JarClassLoader.java` - likely replace or modernize the custom zip-based class loader for modular/plugin compatibility.
- `src/main/java/com/myster/plugin/PluginLoader.java` - keep the entrypoint contract but use modern reflective construction and better compatibility errors.
- `src/main/java/com/myster/plugin/MysterPlugin.java` - Javadoc the supported plugin entrypoint contract.
- `src/main/java/com/myster/filemanager/TikaAudioMetadataProvider.java` - either isolate Tika behind a hybrid classpath approach or replace it if full jlink compatibility is required.
- `src/test/java/...` - add module/artifact/package smoke tests and a plugin fixture test.
- `docs/codebase-structure.md` - update after implementation to describe the module boundary and plugin compatibility expectations.

## 9. Step-by-step implementation

1. Reproduce and record the current baseline.
   - Run `mvn -q -DincludeScope=runtime dependency:build-classpath -Dmdep.outputFile=/tmp/myster-runtime-cp.txt`.
   - Run `jdeps --multi-release 25 --ignore-missing-deps --print-module-deps --class-path "$(cat /tmp/myster-runtime-cp.txt)" target/classes`.
   - Run `jar --describe-module --file bin/MysterBuild.jar --release 9`.
   - Record that the current JDK module set includes `java.base`, `java.datatransfer`, `java.desktop`, `java.logging`, `java.naming`, `java.prefs`, `java.sql`, `java.xml`, and `jdk.unsupported`.

2. Fix the shaded artifact identity.
   - In `maven-shade-plugin`, exclude root and multi-release module descriptors from shaded dependencies:
     - `module-info.class`
     - `META-INF/versions/*/module-info.class`
   - Keep the existing signature excludes.
   - Add `Automatic-Module-Name: com.myster` to the app jar manifest for the non-modular/fat-jar distribution.
   - Verify `jar --describe-module --file bin/MysterBuild.jar --release 9` reports an automatic module named `com.myster`, or at minimum no longer reports `org.bouncycastle.pkix`.

3. Decide the full modular packaging target before adding hard `requires` for automatic dependencies.
   - If the milestone requires only "stable automatic module" compatibility, stop after the shaded artifact fix plus documentation and tests.
   - If the milestone requires a named `com.myster` module, continue with the next steps.
   - If it requires a linked `jpackage` runtime image, resolve the automatic-module dependencies before switching jpackage to `--module`.

4. Add `src/main/java/module-info.java`.
   - Start with module name `com.myster`.
   - Include named dependency modules known to work on the module path:
     - `java.datatransfer`
     - `java.desktop`
     - `java.logging`
     - `java.naming`
     - `java.prefs`
     - `java.sql`
     - `java.xml`
     - `jdk.unsupported`
     - `com.formdev.flatlaf`
     - `com.formdev.flatlaf.extras`
     - `com.formdev.flatlaf.intellijthemes`
     - `com.github.weisj.jsvg`
     - `org.apache.commons.io`
     - `org.bouncycastle.pkix`
     - `org.bouncycastle.provider`
     - `org.bouncycastle.util`
     - `org.slf4j`
     - `org.slf4j.jul`
   - Do not add hard `requires` for Tika, MessagePack, JmDNS, XMP, metadata-extractor, or WaifUPnP until their module-path strategy is chosen.

5. Export packages broadly for the first pass.
   - Export every current source package under `src/main/java`, including all `com.general.*` and `com.myster.*` packages.
   - The current package list is:
     - `com.general.application`
     - `com.general.events`
     - `com.general.mclist`
     - `com.general.net`
     - `com.general.tab`
     - `com.general.thread`
     - `com.general.util`
     - `com.myster`
     - `com.myster.access`
     - `com.myster.application`
     - `com.myster.bandwidth`
     - `com.myster.client.ui`
     - `com.myster.filemanager`
     - `com.myster.filemanager.ui`
     - `com.myster.hash`
     - `com.myster.hash.ui`
     - `com.myster.identity`
     - `com.myster.message.ui`
     - `com.myster.mml`
     - `com.myster.net`
     - `com.myster.net.client`
     - `com.myster.net.datagram`
     - `com.myster.net.datagram.client`
     - `com.myster.net.datagram.message`
     - `com.myster.net.mdns`
     - `com.myster.net.server`
     - `com.myster.net.server.datagram`
     - `com.myster.net.stream.client`
     - `com.myster.net.stream.client.msdownload`
     - `com.myster.net.stream.server`
     - `com.myster.net.stream.server.transferqueue`
     - `com.myster.net.web`
     - `com.myster.plugin`
     - `com.myster.pref`
     - `com.myster.pref.ui`
     - `com.myster.progress.ui`
     - `com.myster.search`
     - `com.myster.search.ui`
     - `com.myster.server.event`
     - `com.myster.server.ui`
     - `com.myster.tracker`
     - `com.myster.tracker.ui`
     - `com.myster.transaction`
     - `com.myster.type`
     - `com.myster.type.ui`
     - `com.myster.ui`
     - `com.myster.ui.menubar`
     - `com.myster.ui.menubar.event`
     - `com.myster.ui.tray`
     - `com.myster.util`

6. Resolve automatic-module dependencies.
   - For simple automatic modules (`msgpack.core`, `javax.jmdns`, `com.simtechdata.waifupnp`, `com.drew.metadata`, `xmpcore`), prefer one of:
     - upgrade to versions with explicit module descriptors, if available and compatible;
     - add descriptors at build time with a tool such as Moditect;
     - keep the specific dependency on the classpath and add explicit compile/runtime reads as a temporary hybrid.
   - For Tika, do not put `tika-core` and `tika-parser-audiovideo-module` together on the module path as automatic modules; they have split package exposure.
   - Choose one Tika strategy:
     - replace Tika in `TikaAudioMetadataProvider` with a JPMS-compatible metadata reader;
     - build a deliberate merged/descriptor-patched Tika audio module for Myster;
     - keep Tika on the classpath with an explicit hybrid module setup and document that full jlink packaging remains blocked.

7. Update Maven build configuration.
   - Keep `maven-compiler-plugin` on a version that supports module descriptors.
   - Add any required `compilerArgs` only for the chosen hybrid classpath reads; avoid `--add-reads` if all dependencies are real named modules.
   - Add `maven-dependency-plugin` execution to copy runtime dependencies for modular jpackage input, for example into `target/jpackage-input/lib`.
   - Keep the existing shaded jar for legacy distribution, but do not use it as the modular image input.

8. Convert `jpackage` only after dependencies are module-path clean.
   - Replace `<mainJar>${outputName}.jar</mainJar>` and `<mainClass>${mainclass}</mainClass>` with `<module>com.myster/com.myster.Myster</module>`.
   - Add `<modulePaths>` entries for the application jar and runtime dependency directory.
   - Include the full JDK module set from `jdeps`; do not keep only the current `java.base`, `java.desktop`, `java.naming`, `java.prefs` list.
   - Ensure `org.slf4j.jul` is included, either as a required/root module or through service binding, so SLF4J has a provider.

9. Modernize plugin loading enough for JPMS compatibility.
   - Replace deprecated `Class.newInstance()` with `getDeclaredConstructor().newInstance()`.
   - Prefer `URLClassLoader` or a small dedicated loader over the current `ZipFile`/`defineClass` implementation unless there is a specific reason to keep `JarClassLoader`.
   - Set the plugin loader parent to the application class loader that can resolve exported `com.myster` packages.
   - Preserve the existing entrypoint lookup: `com.myster.plugins.Main`.
   - Add compatibility diagnostics that name the missing class/module/package when a plugin cannot load.

10. Add verification commands to the implementation summary.
    - `mvn test`
    - `mvn package` or the narrower package goal used by the repo
    - `jar --describe-module --file bin/MysterBuild.jar --release 9`
    - `jdeps --multi-release 25 --ignore-missing-deps --print-module-deps ...`
    - If modular package is in scope: `jlink`/`jpackage` build through Maven
    - Launch smoke: `java -jar bin/MysterBuild.jar -s` for legacy mode and `java --module-path ... --module com.myster/com.myster.Myster -s` for modular mode

## 10. Tests to write

- Add a unit or integration test that checks `bin/MysterBuild.jar` does not contain dependency module descriptors after shading.
- Add a small plugin fixture jar for `PluginLoader` that contains `com.myster.plugins.Main implements MysterPlugin` and verifies `pluginInit()` is called.
- Add a resource-loading smoke test for:
  - `logging.properties`
  - `com/myster/typedescriptionlist.mml`
  - at least one SVG icon loaded through `IconLoader`
- Keep existing metadata tests for `TikaAudioMetadataProvider` or equivalent replacement provider.
- Run existing access, type, filemanager, tracker, and networking tests because the module descriptor exports packages they exercise.
- If a named-module test profile is added, include a launch test using `java --module`.
- If a modular `jpackage` profile is added, include a CI/manual smoke step for the current OS package type.

## 11. Docs / Javadoc to update

- Update `docs/codebase-structure.md` with the `com.myster` module boundary and state whether `com.general.*` is still inside the app module.
- Add Javadoc to `MysterPlugin` describing the supported plugin entrypoint class name and exported package expectations.
- Add implementation summary under `docs/impl_summary/myster-modules-compatible.md` after the implementation, including the final dependency/module audit and any dependency that remains on the classpath.
- If Tika is retained through a hybrid workaround, document that as a known packaging limitation in the implementation summary.
