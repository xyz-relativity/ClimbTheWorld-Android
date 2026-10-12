=2026.10-3=
========
- Download climbing data in the background, with a notification. Several countries can be queued, and each download, or all of them, can be cancelled.
- Show when each downloaded country was last updated and how many routes it has, and mark the downloads that failed.
- Add a Near you list to the country downloads, and show the country names in the device language.
- Add the missing countries to the download list.
- Ask before downloading climbing data over mobile data, which is now off by default.
- Stop retrying country downloads that would only fail again.
- Tap a map cluster to list the areas, crags and routes it holds, with a button to zoom into it.
- Show what a route, crag or area is part of in its info view, with its pin to open it. At the top level, show the country instead.
- Add a quick filter to the crag and area views, to find a route or crag by name, and a clear button to the filter and search fields.
- Number the routes on their map and A.R. pins, by their order in the crag.
- Count a route mapped with several elements, such as its bottom and top, only once.
- Prefer the UIAA grade when a route has grades in several systems, and recognise Hueco grades.
- Use slim route pins in the A.R. view, so that the routes along a cliff overlap less, with their own pin for route tops.
- Outline crags and areas on the ground in the A.R. view, as on the map.
- Add a settings button to the A.R. view.
- Split the A.R. horizon setting in two: the horizon style (none, horizon or virtual terrain), and whether routes follow the elevation. The horizon button cycles through every combination.
- Build the A.R. virtual terrain closer, and fix its flickering and gaps.
- Fix the map staying zoomed out after first finding the location on a fresh install.
- Edit the walkie-talkie channel and callsign straight from the walkie-talkie view.
- Reconnect to walkie-talkie buddies more reliably.
- Remove the unused Wi-Fi, Wi-Fi Direct and Bluetooth walkie-talkie settings, and the permissions they needed.

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
