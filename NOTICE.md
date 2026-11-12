# Third-party notice

The lock screen clock layouts `app/src/main/res/layout/zclock_ls_*.xml` and the fonts and drawables they
reference are taken from **Iconify** by Mahmud0808 / DrDisagree
(https://github.com/Mahmud0808/Iconify), licensed under the GNU General Public License v3.0.

Because of that, ZClock as a whole is distributed under GPL-3.0 (see `LICENSE`).
Changes made: layouts renamed (`preview_lockscreen_clock_N` -> `zclock_ls_N`), `app:tint` replaced with
`android:tint`, and Material-You (Android 12+) colours replaced with static fallback colours so they work
on Android 11. Fonts bundled with the layouts keep their own original licences.
