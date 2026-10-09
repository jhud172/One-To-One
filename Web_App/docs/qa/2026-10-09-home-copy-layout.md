# Homepage copy and layout — 9 October 2026

Reworked the English and Welsh hero copy and the product, trust, workspace and closing introductions. The main message is now “Personal training. Built around you.” The brand wordmark is smaller, the introduction shorter, and the primary actions and trust notes have clearer spacing. Desktop copy aligns alongside the sculpture; tablet and phone layouts stack it above the scene. Section headings have a wider measure and balanced wrapping.

Removed the Fold the logo button from the shared homepage fragment and its JavaScript binding. View switching and Escape remain available, and the non-WebGL focus path now uses the Connections mode control.

Validation: browser bundle rebuilt; 6 animation-clock regression tests passed without failures or skips; Java 21 bootJar built successfully; git diff whitespace check passed. Visually inspected desktop 1440×1000, tablet 768×1024, phones 390×844 and 320×740, plus Welsh on 390px. Checked for horizontal overflow and exercised opening Connections, pinning Trainer details and switching to Sculpture. These checks do not constitute the full release gate.

Local screenshot artifacts are in `build/home-redesign-desktop-20261009.png` and `build/home-redesign-phone-20261009.png`. Asset version updated to 20261009v7k so cached styles refresh.
