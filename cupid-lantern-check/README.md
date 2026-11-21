# Cupid Bow and A Lantern for Both Shores: isolated visual prototype

Base: `e16af6a2a1a00cb68291b6c9611d4604a0b20171` (Playtest 9).

Branch: `codex/fallback/cupid-lantern-playtest9-805cfd`.

This branch contains the realistic Cupid Bow and marked held-book workstream. It does not merge the separate caves, terrain, settlement, or world-generation workstream.

## Realistic Cupid Bow

The five detailed GUI portraits mirror their painted silhouette while retaining the shipped diagonal and increasing icon scale to 1.08. Matching the diagonal alone left the outward curve facing the opposite way from the original pixel bow. All texture pixels and the original bow's models are preserved.

Four rounded-square plates crossing the detailed limbs are removed from each of the five OBJ poses. The already closed limb tubes keep their topology, with the covered bends, narrow trim, lace attachments, and round pearls blended together. The signature heart/pearl-star core and grip retain their original geometry.

The client setting `heldScale` in `uwudimension-cupid-visuals-client.toml` has default **1.0**, minimum **0.75**, and maximum **1.5**. It scales only the realistic-marked Cupid Bow around its exact grip. First-person placement has no forward correction; the existing partly off-screen mount remains. Third person adds half a model pixel of forward clearance at default size, increasing gently to 1.5 model pixels at maximum size. This changes visual placement only. Reach, damage, draw/release logic, projectiles, the original bow, and other weapons are unchanged.

## Marked illustrated book

Using the existing marked written book starts a 52-tick presentation: 12 ticks lifting from the held-hand side, 14 ticks opening the cover, 18 ticks fluttering three blank decorative leaves, and 8 ticks settling into the native reader's actual page 1.

The cover uses the existing animated atlas art. The decorative leaves have no story index. The native reader remains on index 0 until normal navigation becomes available. Escape cancels. Space, Enter, or left-click skips the presentation directly to page 1. Resize preserves progress. Changing the held book or leaving the level cancels. The story, illustrations, ordinary written/writable books, and placed-book interaction remain unchanged. The tablet is not referenced by the implementation.

## Hosted checks and evidence

Run only through the branch's `UwU Cupid and Lantern isolated visual check` GitHub Actions workflow. It uses the public repository and standard `ubuntu-24.04` runner. Do not run Gradle, compile, or launch Minecraft locally for this assignment.

The workflow reuses the existing disposable client fixture and `CupidBowProbe`, restricted to this workstream. It covers:

- The exact original, Playtest 9 detailed, and revised baked models rendered at 16 GUI pixels, with all five draw portraits and enlarged copies for inspection.
- Real draw/release and both hands in first and third person; detailed default/maximum size screenshots.
- Actual emitted vertices at 0.75/1.0/1.5 scale, fixed first-person grip, bounded third-person forward motion, and unaffected original/ordinary bow and non-held contexts.
- Resource reload, Creative choices, five distinct draw meshes, marked/renamed/ordinary book models, real book use in either hand, decorative page isolation, normal next/back navigation, cancellation, reopen, resize, and skip.
- Byte-for-byte preservation of 4,067 packaged entries outside the approved scope, compared with the successful baseline JAR from [run 44883224098](https://github.com/WeepingAngeIs/z-UwU_dm/actions/runs/44883224098). Its SHA-256 is `7e402979865fa5e56edc5f24d26fff9704e99fa53b9f821b8e074241bb4cd9d8`.

The source gate verifies exact baseline portrait copies, closed limb topology, retained UVs, and unchanged signature-core records. Test code and comparison models are outside the runtime JAR. Logs and screenshots are retained as the evidence artifact; the JAR is uploaded only after the focused checks succeed.

The two referenced Dot screenshots were not available in the retrieved context. The implementation follows the user's description and the exact shipped source/assets. Assess the hosted silhouette, held clipping, and cover/leaf alignment from rendered evidence before treating visual fidelity as accepted. Slot-center silhouette metrics are diagnostic rather than an exact curvature measure. The optional PlayerAnimator first-person guard is present, but the fixture does not runtime-test that optional integration.

No installation, release publication, merge, server access, or existing-world changes are part of this branch. Build/check outcome belongs to the linked workflow run; this source document does not assert a successful run in advance.
