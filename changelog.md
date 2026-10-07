=2026.10-2=
========
- Add a My Log Book tool to browse, search and filter the personal log book, and to edit, locate or delete its entries.
- Add log book backup: save it to a file and restore it from one. The log book is also included in the Android device backup.
- Add an onsight climbed status to the log book.
- Tap a grade to see it converted to the other grade systems.
- Rework the crag route list: routes are shown as icons with their name, numbered by their order.
- Place A.R. routes at their elevation, using the Mapterhorn terrain model.
- Add an A.R. horizon toggle: off, horizon, horizon with elevation, or a wireframe of the surrounding terrain.
- Warn when the compass needs calibrating.
- Fix A.R. field of view and route position calculations.
- Reduce the default A.R. view distance to 2.5 km.
- Use WebRTC voice detection for the hands-free walkie-talkie, with a noise filter setting instead of the trigger sensitivity.
- Default the walkie-talkie to push to talk.
- Target Android 17.

=2026.10-1=
========
- Allow each different map view to have its own orientation style saved. 
- Port the map to MapLibre.
- Extend the info views to climbing areas and crags.
- Add Wi-Fi Aware support to the walkie-talkie.
- Add a personal log book: private notes on areas, crags and routes, and a climbed status for routes (flashed, sent, completed or not completed). Stored on the device only and kept when OSM data is updated or removed.

=2022.05-1=
========
- Add sunrise sunset calculator to the environment view.
- Add rotatable bazel to the compass view.
- Add temperature scales to the unit converter.
- lots of bugfixes

=2020.09=
========
- New icon rendering engine. Allows for a lot faster and more efficient rendering. This allows for individually customizable icons.
- Icons now display grade info, route name, and a pictographic representation of the climbing style.
- Text on icons now has an outline. Makes it easier to read in bad contrast conditions.
- Add Z-Indexing for map POIs. This will give a nice clean display while the map it being rotated.
- Implement frame limiting for map rendering. Should help a lot with preserving battery life.
- Add download button to the map view.
- Add support for manual map rotation.
- Fix bounding box not wrapping around ante-meridian.
- Implement routes searching.
- Add go-to location button on POI info dialogue box.

=2019.03=
========
- Add centre map on point functionality.
- Make upload data list elements clickable.
- Change country list lazy load.
- Add info dialog for cluster markers.
- change route icons so that now they can also display the route grade.
- Show climbing gyms.
- Show climbing crags even if they don't have any routes.
- Finish implementing walkie-talkie.
- New edit activity to allow for the new climbing tags.
- Open current edited node in external editors (web Id editor, Vespucci editor).
- Implement a JOSM tags editor.
