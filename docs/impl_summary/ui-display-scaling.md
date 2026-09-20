# Arbitrary display scaling

`TreeMCList.mergeIcons` now paints its children directly with Swing's current graphics
transform and component, replacing a bitmap always rasterized at 2×. Logical size, spacing,
vertical alignment and null handling remain the same. Each child receives a graphics copy
so it cannot change the sibling's or caller's drawing state.

## Audit and scope

The rendering audit found this one explicit fixed-2× assumption. Existing remote thumbnail
sizing already reads the graphics-configuration transform and rounds up device-pixel sizes.
The shared IntelliJ `Myster` launch also forced `GDK_SCALE=2`; that override is removed so the
normal launch inherits the machine's scaling settings.
The SVG loader, multi-resolution PNG loader and tray sizing do not impose a 1×/2× switch.
Legacy AWT buffers and finite-resolution assets were inspected but not rewritten; removing
this assumption does not certify every old UI asset as sharp on every display.

The broader OS thumbnails Part 4 feature remains planned. Its scaling requirements now cover
arbitrary reported factors, rounding, protocol limits and movement between monitors. The
project-wide UI convention establishes the same rule for future work throughout Myster.

## Files and documentation

- `src/main/java/com/general/mclist/TreeMCList.java`: direct composition and updated Javadoc.
- `Myster.ipr`: remove the normal IDE launch's forced scale.
- `src/test/java/com/general/mclist/TestTreeMCListIcons.java`: new regression tests.
- `src/test/java/com/myster/thumbnail/ui/TestThumbnailUiUtils.java`: additional scale/rounding cases.
- `docs/conventions/myster-coding-conventions.md`: general display scaling rule.
- `docs/conventions/myster-important-patterns.md`: current compositor behavior.
- `docs/design/Myster OS Thumbnail Integration Design.md`: logical versus device-pixel sizing.
- `docs/plans/os-thumbnails-part-4.md`: arbitrary scaling and updated compositor baseline.
- `docs/plans/ui-display-scaling.md` and documentation indexes: this focused correction.

## Verification

Passed 37 tests with:

```sh
mvn -o -Djava.awt.headless=true -Dtest=TestTreeMCListIcons,TestTreeMCListTableModel,TestJMCListSelectionModel,TestThumbnailUiUtils,TestClientFilePreviewPane test
```

The compositor test repaints the same icon at 1×, 1.25×, 1.5×, 1.75×, 2×, 2.5× and 3×,
checking the actual component, transform, clip, alignment and graphics-state isolation.
Thumbnail sizing includes fractional pixel results that round up and factors above 2×.
`git diff --check` passes. No known test failures. The focused plan was extended after the
launch-config audit found the forced scale. The project XML parses successfully and the normal
run configuration no longer declares a `GDK_SCALE` override.

Physical mixed-DPI monitor movement was not exercised. No additional unit tests are deferred.
