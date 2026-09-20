# Arbitrary display scaling

## Design Section (for the owner/reviewer)

### 1. Summary

Respect the display scale reported by Java throughout Myster. Audit existing rendering for
fixed 2× assumptions, correct the tree icon compositor, and make arbitrary scaling a project
convention and an explicit requirement of OS thumbnails Part 4.

### 2. Non-goals

- Implementing the Part 4 thumbnail provider or loading controller.
- Replacing legacy bitmap assets or rewriting legacy AWT widgets.
- Overriding the platform's scaling support or changing logical UI geometry.

### 3. Assumptions & open questions

- The source audit found one explicit fixed-2× compositor, in `TreeMCList.mergeIcons`, and
  the shared IntelliJ launch configuration forces `GDK_SCALE=2`.
- Thumbnail request sizing already uses the graphics configuration's transform, rounds up
  device-pixel dimensions and respects the protocol cap.
- SVGs can paint at the destination scale. Discrete multi-resolution image assets do not
  require discrete display scales; Java selects and scales their available representations.

### 4. Proposed design

Compose tree icons by painting their children directly into the destination graphics,
preserving its transform, component and clip. Keep logical dimensions, spacing, vertical
alignment and null-icon behavior. Give each child a graphics copy so its drawing state cannot
affect the other child or caller. Repainting the same composite on another monitor naturally
uses the new scale, without a bitmap cache or scale listeners.
Remove the shared launch configuration's scale override so the normal IDE launch inherits
the machine's settings; an explicit local test override can still be supplied separately.

### 5. Architecture connections

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| Direct icon composition | `TreeMCList.mergeIcons` | Existing tree renderer | Swing `Icon`, component and destination graphics |
| Default IDE launch | Shared IntelliJ run configuration | Developer launches | Inherited display/environment settings |
| Arbitrary scaling convention | Project UI conventions | All UI work, including Part 4 | Existing thumbnail sizing and Swing rendering |

No protocol or file format changes. The tree renderer continues to derive icon size from
row height and to apply its existing colors, chevrons and indentation.

### 6. Key decisions & edge cases

- Display scale is a factor, not a boolean 1×/2× setting; include fractional and greater-than-2× scales.
- Compositing performs no painting until Swing calls `paintIcon` with the real component.
- Fixed asset dimensions and image downsampling steps are not themselves display-scale assumptions.

### 7. Acceptance criteria

- [x] The fixed 2× tree raster buffer is removed without changing logical icon geometry.
- [x] Both children receive the current transform/component, including after a scale change.
- [x] Child graphics mutations do not leak; null-icon behavior remains unchanged.
- [x] Part 4 and project conventions explicitly require arbitrary reported scaling.
- [x] Focused compositor, tree and thumbnail geometry tests pass.
- [x] The shared IDE launch no longer forces `GDK_SCALE=2` and its XML remains valid.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

### 8. Affected files / classes

- `src/main/java/com/general/mclist/TreeMCList.java`: replace fixed raster merge with a passive `Icon`.
- `Myster.ipr`: remove the default run configuration's `GDK_SCALE=2` override.
- New `src/test/java/com/general/mclist/TestTreeMCListIcons.java`: composition regression tests.
- `src/test/java/com/myster/thumbnail/ui/TestThumbnailUiUtils.java`: extend scale and rounding coverage.
- `docs/conventions/myster-coding-conventions.md`: project-wide display scaling contract.
- `docs/conventions/myster-important-patterns.md`: update compositor description.
- `docs/plans/os-thumbnails-part-4.md`: arbitrary scale requirements and current compositor baseline.

### 9. Step-by-step implementation

1. Replace the bitmap creation in `mergeIcons` with an `Icon` retaining its two children.
   Preserve width, maximum height, vertical centering and spacing. Paint each child using an
   independently created/disposed copy of the supplied graphics and the supplied component.
2. Document logical dimensions, live painting and existing null semantics in its Javadoc.
3. Test composition with changing scales, component propagation, clipping, geometry and
   graphics-state isolation. Extend thumbnail request tests to a non-integral pixel result
   and a factor greater than 2.
4. Update Part 4 and conventions. Run the focused tests and write the implementation summary.
5. Remove the shared IDE launch override and check that the project XML still parses.

### 10. Tests to write

- Paint the same composition at 1×, 1.25×, 1.5×, 1.75×, 2×, 2.5× and 3×. Verify children
  see the current transform and actual component and that geometry stays logical.
- Verify child drawing cannot alter its sibling's or caller's transform or clip.
- Verify absent icons retain existing passthrough behavior.
- Verify thumbnail sizes round up fractional device-pixel results and support scales above 2×.
- Run `TestTreeMCListIcons`, `TestTreeMCListTableModel`, `TestJMCListSelectionModel`,
  `TestThumbnailUiUtils` and `TestClientFilePreviewPane` headlessly.
- A physical mixed-DPI monitor smoke test remains useful beyond headless verification.

### 11. Docs / Javadoc to update

- `TreeMCList.mergeIcons` contract and the general UI scaling convention.
- Part 4's assumptions, acceptance criteria and implementation baseline.
- `docs/impl_summary/ui-display-scaling.md` with audit scope, tests and remaining limitations.
