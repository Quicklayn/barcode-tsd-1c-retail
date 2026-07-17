---
name: tsd-scanner-integration
description: Implement or review barcode input on Android TSD devices while keeping business logic independent of scanner vendors. Use for keyboard-wedge scanning, manual scan simulation, scan event normalization, focus behavior, or later Zebra DataWedge and Datalogic adapters.
---

# TSD Scanner Integration

Use one scan pipeline for physical scanner input and manual test input.

## Baseline

1. Start with keyboard-wedge input because it works without a vendor dependency.
2. Submit on Enter or the configured IME action, remove only the scanner terminator and surrounding whitespace, and preserve the barcode value otherwise.
3. Restore focus to the scan field after every success or recoverable error.
4. Prevent a single hardware event from being submitted twice.
5. Keep the scan field available for manual emulator testing.

## Boundaries

- Route the normalized barcode into operation logic; do not perform network or persistence work in key-event handlers.
- Keep the maximum payload length aligned with `docs/api/tsd-api.yaml`.
- Add a scanner-source interface only when the second input mechanism is implemented.
- Place Zebra DataWedge, Datalogic, or another vendor integration behind a separate adapter. Do not make vendor SDKs a baseline dependency.
- Treat GS1 parsing and marking validation as a separate capability and OpenSpec change.

## Verification

Test Enter/IME submission, manual input, duplicate event suppression, empty input, focus restoration, and compatibility with `minSdk=26`. Use a real target device for vendor-specific adapters.
