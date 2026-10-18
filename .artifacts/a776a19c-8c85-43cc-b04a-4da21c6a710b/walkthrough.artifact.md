# Neo-Brutalism UI Overhaul Walkthrough

## Summary of Changes

The entire application visual identity has been overhauled into **Neo-Brutalism (Brutalist UI)**, delivering high-contrast, high-energy aesthetics with raw geometry and bold tactile elements.

### 1. Neo-Brutalist Design Tokens & Drawables
- **Color Palette (`colors.xml`)**: Built around a warm cream canvas (`#FFFBF0`), pure black (`#000000`), and vibrant neon accents (Electric Yellow `#FFE600`, Hot Pink `#FF2A6D`, Neon Green `#00FF66`).
- **Offset Shadow Drawables**: Created custom layer-list drawables (`brutal_card.xml`, `brutal_button_yellow.xml`, `brutal_button_pink.xml`, `brutal_button_green.xml`, `brutal_input.xml`) featuring solid black 3dp borders and stark 4dp offset black drop shadows with zero blur.

### 2. Screen Overhauls
- **`MainActivity` & Dashboard**: Upgraded with bold uppercase typography, neon green badge headers, vibrant yellow primary action buttons, and sharp brutalist card framing.
- **`DrawingEdge` & Floating Control Panel (`popup_control_panel.xml`)**: High-contrast brutalist tool panels and docking controls.
- **Splash & Permissions Screens**: Striking brutalist typography, high-impact cards, and bold action buttons.

## Verification Results
- **Gradle Build**: Executed `app:assembleDebug` — **BUILD SUCCESSFUL**.
- **Linter Analysis**: 0 compilation errors across all files.
