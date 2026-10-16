# Where these seven sprites come from

The gui sprites in this folder are the drawing of the resource pack **Reimagined GUI (Dark)** by
AmongstReality, copied into Immersive Titles with the permission of the pack's author, so that the
biome collection is read in the pack's dark gui whether or not a player runs the pack itself:

| sprite | what the collection draws with it |
| --- | --- |
| `widget/button` | a row of a biome that was found, and the face of each of the screen's own buttons |
| `widget/button_disabled` | a row whose reveal is still waiting |
| `widget/button_highlighted` | the face of one of those buttons under the mouse or the keyboard, which is how the tab of the page being read is told apart from the other one |
| `container/beacon/cancel` | the cross at the end of a row of a discovery, which gives it back |
| `social_interactions/background` | the window the list is held in, frame and panel at once |
| `hud/experience_bar_background` | the trough of the bar counting the collection |
| `hud/experience_bar_progress` | the found share of that bar |

They are the pack's files byte for byte, and the copies are the mod's own: they are named out of the
`immersive_titles` namespace, so they are read whatever resource pack a player has in front of them.

The pack itself, and everything of the game's gui it redraws beyond these six sprites:

- Modrinth: <https://modrinth.com/resourcepack/reimaginedguidark>
- CurseForge: <https://www.curseforge.com/minecraft/texture-packs/reimaginedguidark>

These files are the work of the pack's author and are not covered by the MIT licence the rest of this
mod is released under (see `LICENSE`). The pack's own terms of use are the ones that apply to them.
