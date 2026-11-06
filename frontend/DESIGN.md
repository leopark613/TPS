# TPS document workbench

The audience draws business reports and labels. The sheet, placement tools, units and saved versions are the visual structure, rather than dashboard statistics.

Palette: ink #17324d, action blue #2463a6, work surface #eef2f6, paper #ffffff, unsaved amber #b86417, divider #d5dde5. Type: Malgun Gothic/Apple SD Gothic Neo system fonts for Korean UI and document preview. System fonts require no external fetch. 12/13/14/18/24px hierarchy, left aligned controls; centered paper only.

```
TPS | RPS / LPS mode                         account
catalog             title / status          save
                    text / rectangle tools
saved templates     paper + ruler          properties
                    document elements      versions
                                           document JSON
```

Review against brief: avoid KPI cards or marketing headings; use a visible A4 sheet, editable measurements, a quiet catalog and compact inspector. Rounded controls are small and functional, not floating cards. Primary focus is paper selection outline, with blue only for actions/selection. Saved and active are explicitly different; show 업무 미적용. Unknown document features remain preserved and readonly, not falsely rendered as equivalent output. This canvas is an editing aid, not a precision print preview.

Responsive: three columns become two and inspector moves below at narrower widths; the page fits its stage while retaining a minimum sheet width for usable drag handles. Keyboard focus is visible, element arrow keys move in 1mm steps (Shift 10mm), before-unload protects unsaved edits. No automatic animations.

TPS-IMPL-002: retain palette/type and paper focus. Place compact report data controls above the canvas. Field bindings show bracketed field labels; QR authoring shows an explicitly non-scannable placement tile. Saved-revision data preview occupies the paper area with a visible return action, never a modal. Preview QR uses only server modules with four white quiet-zone modules. Unsaved work requires explicit save; no automatic save. Data/row/revision changes invalidate displayed results.
