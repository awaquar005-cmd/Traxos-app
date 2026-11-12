# Unslop experiment — reversible styling study

Baseline preserved on main at fd692ca (0.8.0), with its downloadable APKs and all completed offline features. This experiment lives on experiment/unslop-ui. It does not promote the styling to main.

Guidance: [Unslop](https://github.com/heydamir/unslop) by Damir Sokolovsky ([@heydamir](https://x.com/heydamir)), revision97daa3eb365670ea3f87ed85356cbfcee18105a5, licensed [CC BY4.0](third-party/unslop-LICENSE.txt). Adapted to native Kotlin/Compose; no web wrapper, new SDK, paid image or story generator. This document records the changes to the guidance's application. The user explicitly requested applying an experiment, so its optional direction-approval wait is not needed.

## Audit and direction before editing

Polish mode: the current app has real branding, native components, no gradients/glows, working financial data and a deliberate layout. Preserve its palette, OS sans/Indic fallback, sections and order. Remaining signs: uppercase section captions; stronger weight on nearly every figure/action;14/18dp inter-group gaps mixed with8/10dp sibling gaps; all tool/settings icons painted accent blue; over-emphasized chrome. Existing finance direction icons are useful and stay. Do not invent charts, numbers, payment-card props, extra widgets or visual filler.

- Product: offline personal finance for daily self-managed tracking.
- Character: calm, exact, reassuring.
- World: a private ledger in navy ink.
- Moments: the existing deep-navy balance hero and the real financial figures.
- Signature: the original five-square T and dashboard watermark.

User-protected constraints: logo unoutlined; watermark and hero eye button remain. The eye button is functional and must not be replaced or removed. No new decorative hero icons, cards or unnecessary UX. The latest user feedback removes action-button outline borders (Add Account, Back, overflow and other actions) in both comparison modes; preserve meaningful icons. Native touch targets remain48dp despite smaller web suggestions. Sample preview banners, currency codes and the TRAXOS brand remain unmistakable.

## Planned passes

1. Spacing: align shared grouping to4/8/12/16dp without enlarging padding; compact siblings and preserve layout/order.
2. Color: retain the existing navy/accent palettes and readable neutrals; use neutral chrome instead of accent on every tool/action.
3. Icons: retain the native line set and semantic user-selected category/account colors; align utility icon sizes; preserve the finance direction icons and all useful/visually meaningful existing icons. Preserve the useful hero eye button.
4. Copy: sentence-case ordinary section captions; specific first-account copy. No feature, financial claim or required warning is removed.
5. Type: OS sans stays; calmer400/500 hierarchy with readable captions and tabular money. Keep proper brand/currency codes.
6. Layout: same dashboard sections, navigation, settings categories and routes; no widgets or fake charts.
7. Details: existing radii, borderless action controls with tonal/pressed feedback, no logo outline or new card frames; clear selection/disabled/error states.
8. Visuals: keep actual logo and watermark code/assets byte-identical; no generated objects or paid assets.
9. Motion: retain instant content-safe navigation and existing short entrance/press feedback; no new looping/transition effects.

## Compare and return

The experimental APK uses the same package, signing identity, version code9 and Room schema8 as0.8.0. In Settings → Appearance & Navigation → Experimental interface, turn the single comparison switch off to restore the previous styling with the latest borderless-button corrections immediately. It only persists a non-sensitive local UI preference, outside Room, so there is no migration or financial record change. Both modes retain all0.8 functions. The app version name identifies the experiment.

A same-code/same-key baseline APK is also retained for reinstalling as Update; the in-app switch is the simplest comparison. Never uninstall/clear storage. No claim of portable backup or native-phone acceptance. Final build/test/lint results and synthetic comparisons will be recorded below before delivery.

Latest delivery instruction: build and publish the Personal experiment only; do not build a new Play experiment. The top-right overflow, Back, Add Account and other action controls are borderless, while their icons,48dp targets, disabled/pressed feedback and actions remain. The comparison switch has an explicit accessibility label. No new animation, dependency, chart or image is added.

## Pass outcome and scope

Spacing now uses12dp dashboard section gaps/16dp hero padding and4dp shared sibling gaps when enabled, retaining48dp controls and expanding for larger fonts. Accent choices/palette are unchanged; utility chrome becomes neutral while meaningful financial/category colors remain. Existing icons, including the hero eye and inflow/outflow arrows, are preserved. Ordinary section captions use sentence case, wordmark tracking is calmer, and figures/actions use500 weight with tabular amounts. The section order, real features, instant navigation, radius system, logo/watermark code and every brand/splash asset remain. No scene, fake chart, generated image, new widget or animation is introduced. Borderless action corrections apply in both comparison modes per the user's latest feedback.

The guidance's smaller web touch sizes, wholesale palette/layout changes, paid images and extra visual objects were not applied; native accessibility and the user's compact existing UX take priority. The brand/currency codes and explicit preview safety labels retain their spelling/case. Only the comparison preference and its accessibility label are new. Source attribution is in About and this document, with the license bundled in docs/third-party.

## Actual validation and delivery

Personal assemble/test/lint passed;163 tests, zero failures/errors/skips; lint zero errors and21 advisory warnings. No Play experiment built. [Full evidence, checksum and synthetic comparisons](validation/unslop-experiment/README.md). Baseline comparison frames include the latest borderless-button corrections; they are not pixel-identical to the frozen0.8 APK. Financial/security/model directories, schema, logo/splash assets and mark/watermark bodies compare unchanged. The first full run exhausted temporary disk; duplicate identical APK copies were deduplicated, and the full Personal suite rerun successfully. Legacy UI waits now accept heading case changes and the eye check waits for its updated semantics; functional assertions remain.

`downloads/TRAXOS-0.8.0-unslop-personal.apk`,35,813,647 bytes, same certificate/package/code9/schema8 as baseline. Install as Update and use the in-app switch for immediate old/new comparison. There is no native-phone or measured speed-up claim. This remains an experiment on its own branch until the user chooses the visual direction.
