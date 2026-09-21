# Blueprint Reimplementation Verification

- [ ] Start a two-player development server and give player A an Empty Blueprint plus stone and directional blocks.
- [ ] Player A enters design mode; player B sees A's body remain at the activation position.
- [ ] Player B damages A; A loses health and design mode exits cleanly on death.
- [ ] Player A re-enters design mode and flies the soul viewpoint with movement and vertical controls while the body does not move.
- [ ] Player A places, replaces, and removes private fake blocks; player B sees none of them and the real world remains unchanged.
- [ ] Stairs, slabs, and directional blocks render with their saved models/states, blue tint, and 50 percent opacity.
- [ ] Saving changes the held item to Designed only after the server accepts it.
- [ ] Placement follows the crosshair; pressing R rotates exactly 90 degrees and old preview positions disappear.
- [ ] Occupying a destination before confirmation makes the server reject the placement and the item remains Designed.
- [ ] A valid right-click confirmation changes the item to Planned with the correct dimension, origin, and rotation.
- [ ] Escape, blueprint loss, dimension change, disconnect, and death each restore the normal camera/input and clear previews.
