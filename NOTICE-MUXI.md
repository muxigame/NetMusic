# Muxi NetMusic fork

Official source history: https://github.com/TartaricAcid/NetMusic , branch 1.21, baseline b44ee583db5f946229c08371aac99aeae166c31d.
Maintained fork: https://github.com/muxigame/NetMusic . This is a modified build, not an official upstream release or endorsement.

The 1.21 snapshot lacked root license documents. LICENSE and LICENSE-ASSETS are preserved byte-for-byte from the official repository documentation commit b7cc0dfc33253c056089e87d36d9c6dca8fdc21b (1.20 default branch), which describes the project's source/assets licensing. Original sound library notices remain in src/main/resources/licenses. Assets are retained unchanged; error.ogg has the upstream CC0 attribution.

Muxi additions migrate the previously independent music-compat source. Existing protocol names, saved-data keys and Java package names remain to preserve saved discs and integration contracts; there is no independent muxi_music_compat mod entrypoint in this fork.

Playlist 4.3 and BetterLogin 1.0.4 remain separate dependencies. Their business classes/assets are not copied into this source or bundled by the fork build. QA code uses synthetic identities and owned media and must never ship in the player JAR.
